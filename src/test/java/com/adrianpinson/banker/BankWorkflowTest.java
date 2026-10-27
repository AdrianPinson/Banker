package com.adrianpinson.banker;

import static com.adrianpinson.banker.BankModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntFunction;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class BankWorkflowTest {
    @Autowired BankService bank;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc http;
    @MockitoSpyBean BankRepository repo;
    @BeforeEach void resetDatabase() {
        reset(repo);
        jdbc.update("DELETE FROM commands");
        jdbc.update("DELETE FROM ledger");
        jdbc.update("DELETE FROM tickets");
        jdbc.update("UPDATE accounts SET status = 'OPEN', balance = CASE id WHEN 'DEMO-1001' THEN 1000.00 WHEN 'DEMO-1002' THEN 250.00 ELSE 0.00 END");
        jdbc.update("UPDATE branch SET open = TRUE");
    }
    private UUID key() { return UUID.randomUUID(); }
    private CheckIn alex() { return new CheckIn("DEMO-1001", "Alex", "River"); }
    private Ticket serving() {
        bank.checkIn(key(), alex());
        return bank.claim(key(), new Claim("teller-1"));
    }
    private Money money(String amount) { return new Money(new BigDecimal(amount)); }
    private void error(String code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BankException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
    @Test void preservesFifoAndQueueDoesNotChangeAccounts() {
        Ticket first = bank.checkIn(key(), alex());
        Ticket second = bank.checkIn(key(), new CheckIn("DEMO-1002", "Jordan", "Vale"));
        assertThat(bank.claim(key(), new Claim("one")).id()).isEqualTo(first.id());
        assertThat(bank.claim(key(), new Claim("two")).id()).isEqualTo(second.id());
        assertThat(bank.accounts()).hasSize(3);
        assertThat(bank.account("DEMO-1001").balance()).isEqualByComparingTo("1000");
    }
    @Test void verifiesTrimmedCaseInsensitiveNames() {
        assertThat(bank.checkIn(key(), new CheckIn(" demo-1001 ", " aLEX ", " rIVER ")).accountId()).isEqualTo("DEMO-1001");
    }
    @Test void rejectsWrongNameAndMissingAccountWithoutQueueChanges() {
        error("IDENTITY_MISMATCH", () -> bank.checkIn(key(), new CheckIn("DEMO-1001", "Wrong", "River")));
        error("ACCOUNT_NOT_FOUND", () -> bank.checkIn(key(), new CheckIn("UNKNOWN", "Alex", "River")));
        assertThat(bank.queue()).isEmpty();
    }
    @Test void duplicateCheckInHasOneActiveTicket() {
        bank.checkIn(key(), alex());
        error("ALREADY_CHECKED_IN", () -> bank.checkIn(key(), alex()));
        assertThat(bank.queue()).hasSize(1);
    }
    @Test void checkInRetryReturnsOriginalTicketEvenAfterItIsCompleted() {
        UUID key = key();
        Ticket original = bank.checkIn(key, alex());
        bank.claim(key(), new Claim("one"));
        bank.complete(key(), original.id());
        assertThat(bank.checkIn(key, alex())).isEqualTo(original);
        assertThat(bank.queue()).isEmpty();
    }
    @Test void moneyIsAtomicAndAuditedInOrder() {
        Ticket ticket = serving();
        bank.transact(key(), ticket.id(), money("0.10"), true);
        bank.transact(key(), ticket.id(), money("0.20"), true);
        bank.transact(key(), ticket.id(), money("50.30"), false);
        assertThat(bank.account(ticket.accountId()).balance()).isEqualByComparingTo("950.00");
        assertThat(bank.history(ticket.accountId())).extracting(LedgerEntry::kind).containsExactly("DEPOSIT", "DEPOSIT", "WITHDRAWAL");
        assertThat(bank.history(ticket.accountId()).get(2).balanceAfter()).isEqualByComparingTo("950.00");
    }
    @Test void moneyRetryReturnsSameEntryAndRejectsDifferentPayloadOrOperation() {
        Ticket ticket = serving();
        UUID key = key();
        LedgerEntry original = bank.transact(key, ticket.id(), money("10"), true);
        assertThat(bank.transact(key, ticket.id(), money("10.00"), true)).isEqualTo(original);
        error("KEY_REUSED", () -> bank.transact(key, ticket.id(), money("11"), true));
        error("KEY_REUSED", () -> bank.transact(key, ticket.id(), money("10"), false));
        assertThat(bank.history(ticket.accountId())).hasSize(1);
        assertThat(bank.account(ticket.accountId()).balance()).isEqualByComparingTo("1010");
    }
    @Test void overdraftAndInvalidAmountLeaveNoLedgerOrReceipt() {
        Ticket ticket = serving();
        UUID declined = key();
        error("INSUFFICIENT_FUNDS", () -> bank.transact(declined, ticket.id(), money("1000.01"), false));
        error("INVALID_REQUEST", () -> bank.transact(key(), ticket.id(), money("-5"), true));
        assertThat(bank.history(ticket.accountId())).isEmpty();
        assertThat(repo.receipt(declined.toString())).isEmpty();
        assertThat(bank.account(ticket.accountId()).balance()).isEqualByComparingTo("1000");
    }
    @Test void waitingAndCompletedTicketsCannotTransact() {
        Ticket ticket = bank.checkIn(key(), alex());
        error("TICKET_NOT_SERVING", () -> bank.transact(key(), ticket.id(), money("1"), true));
        error("TICKET_NOT_SERVING", () -> bank.complete(key(), ticket.id()));
        bank.claim(key(), new Claim("one"));
        bank.complete(key(), ticket.id());
        error("TICKET_NOT_SERVING", () -> bank.transact(key(), ticket.id(), money("1"), true));
        assertThat(bank.checkIn(key(), alex()).id()).isGreaterThan(ticket.id());
    }
    @Test void claimIsIdempotentAndTellerCannotClaimTwo() {
        bank.checkIn(key(), alex());
        bank.checkIn(key(), new CheckIn("DEMO-1002", "Jordan", "Vale"));
        UUID key = key();
        Ticket claim = bank.claim(key, new Claim(" One "));
        assertThat(bank.claim(key, new Claim("one"))).isEqualTo(claim);
        error("TELLER_BUSY", () -> bank.claim(key(), new Claim("ONE")));
        assertThat(bank.queue()).filteredOn(t -> t.status().equals("WAITING")).hasSize(1);
    }
    @Test void emptyQueueIsExplicit() { error("QUEUE_EMPTY", () -> bank.claim(key(), new Claim("one"))); }
    @Test void accountClosureRequiresZeroAndPreservesHistory() {
        Ticket ticket = serving();
        error("BALANCE_NOT_ZERO", () -> bank.closeAccount(key(), ticket.id()));
        bank.transact(key(), ticket.id(), money("1000"), false);
        UUID key = key();
        Account closed = bank.closeAccount(key, ticket.id());
        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(bank.closeAccount(key, ticket.id())).isEqualTo(closed);
        assertThat(bank.ticket(ticket.id()).status()).isEqualTo("COMPLETED");
        assertThat(bank.history(closed.id())).extracting(LedgerEntry::kind).containsExactly("WITHDRAWAL", "CLOSE_ACCOUNT");
        error("ACCOUNT_CLOSED", () -> bank.checkIn(key(), alex()));
        assertThat(bank.accounts()).hasSize(3);
    }
    @Test void branchClosureChecksBothWaitingAndServing() {
        Ticket ticket = bank.checkIn(key(), alex());
        error("ACTIVE_TICKETS", () -> bank.setOpen(key(), false));
        bank.claim(key(), new Claim("one"));
        error("ACTIVE_TICKETS", () -> bank.setOpen(key(), false));
        bank.complete(key(), ticket.id());
        assertThat(bank.setOpen(key(), false).open()).isFalse();
        error("BRANCH_CLOSED", () -> bank.checkIn(key(), alex()));
        error("BRANCH_CLOSED", () -> bank.claim(key(), new Claim("one")));
        assertThat(bank.setOpen(key(), true).open()).isTrue();
    }
    @Test void rollsBackBalanceWhenLedgerWriteFails() {
        Ticket ticket = serving();
        doThrow(new IllegalStateException("injected ledger failure")).when(repo).ledger(any(), anyString(), any(), any());
        assertThatThrownBy(() -> bank.transact(key(), ticket.id(), money("10"), true)).isInstanceOf(IllegalStateException.class);
        assertThat(bank.account(ticket.accountId()).balance()).isEqualByComparingTo("1000");
        assertThat(bank.history(ticket.accountId())).isEmpty();
    }
    @Test void rollsBackBalanceAndLedgerWhenReceiptWriteFails() {
        Ticket ticket = serving();
        doThrow(new IllegalStateException("injected receipt failure")).when(repo).receipt(anyString(), anyString(), anyString());
        assertThatThrownBy(() -> bank.transact(key(), ticket.id(), money("10"), true)).isInstanceOf(IllegalStateException.class);
        assertThat(bank.account(ticket.accountId()).balance()).isEqualByComparingTo("1000");
        assertThat(bank.history(ticket.accountId())).isEmpty();
    }
    @Test void concurrentRetriesDepositOnlyOnce() throws Exception {
        Ticket ticket = serving();
        UUID key = key();
        List<Object> responses = race(8, i -> bank.transact(key, ticket.id(), money("25"), true));
        assertThat(responses).allMatch(LedgerEntry.class::isInstance);
        assertThat(responses).allMatch(responses.get(0)::equals);
        assertThat(bank.account(ticket.accountId()).balance()).isEqualByComparingTo("1025");
        assertThat(bank.history(ticket.accountId())).hasSize(1);
    }
    @Test void concurrentWithdrawalsCannotOverdraw() throws Exception {
        Ticket ticket = serving();
        List<Object> responses = race(2, i -> bank.transact(key(), ticket.id(), money("700"), false));
        assertThat(responses.stream().filter(LedgerEntry.class::isInstance)).hasSize(1);
        assertThat(responses.stream().filter(BankException.class::isInstance)).hasSize(1);
        assertThat(bank.account(ticket.accountId()).balance()).isEqualByComparingTo("300");
        assertThat(bank.history(ticket.accountId())).hasSize(1);
    }
    @Test void concurrentCheckInsCannotDuplicateAccount() throws Exception {
        List<Object> responses = race(4, i -> bank.checkIn(key(), alex()));
        assertThat(responses.stream().filter(Ticket.class::isInstance)).hasSize(1);
        assertThat(bank.queue()).hasSize(1);
    }
    @Test void concurrentTellersCannotClaimSameTicket() throws Exception {
        bank.checkIn(key(), alex());
        List<Object> responses = race(2, i -> bank.claim(key(), new Claim("teller-" + i)));
        assertThat(responses.stream().filter(Ticket.class::isInstance)).hasSize(1);
        assertThat(bank.queue()).extracting(Ticket::status).containsExactly("SERVING");
    }
    private List<Object> race(int count, IntFunction<Object> action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count), start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Race did not start");
                    try { return action.apply(index); } catch (BankException e) { return e; }
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Object> responses = new ArrayList<>();
            for (Future<Object> future : futures) responses.add(future.get(20, TimeUnit.SECONDS));
            return responses;
        } finally { pool.shutdownNow(); }
    }
    @Test void httpWorkflowAndRetryContract() throws Exception {
        String key = key().toString();
        String body = "{\"accountId\":\"DEMO-1001\",\"firstName\":\"Alex\",\"lastName\":\"River\"}";
        String response = http.perform(post("/api/queue/check-in").header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WAITING")).andReturn().getResponse().getContentAsString();
        http.perform(post("/api/queue/check-in").header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(content().json(response));
        http.perform(get("/api/accounts/DEMO-1001")).andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(1000.00));
    }
    @Test void httpRejectsMissingInvalidKeysBadJsonAndUnknownFields() throws Exception {
        http.perform(post("/api/branch/close")).andExpect(status().isBadRequest());
        http.perform(post("/api/branch/close").header("Idempotency-Key", "bad")).andExpect(status().isBadRequest());
        http.perform(post("/api/queue/check-in").header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        http.perform(post("/api/queue/claim-next").header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON).content("{\"teller\":\"a\",\"extra\":true}"))
                .andExpect(status().isBadRequest());
    }
    @Test void httpReturnsValidationAndBusinessProblemDetails() throws Exception {
        http.perform(post("/api/queue/claim-next").header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON).content("{\"teller\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        http.perform(get("/api/accounts/missing")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
        Ticket ticket = serving();
        http.perform(post("/api/tickets/" + ticket.id() + "/withdrawals").header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1001}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        http.perform(post("/api/tickets/" + ticket.id() + "/deposits").header("Idempotency-Key", key()).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":0.001}"))
                .andExpect(status().isBadRequest());
    }
}
