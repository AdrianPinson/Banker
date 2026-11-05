package com.adrianpinson.banker;

import static com.adrianpinson.banker.BankModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
class TransferTest {
    @Autowired BankService bank;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean BankRepository repo;
    @BeforeEach void resetDatabase() {
        reset(repo);
        jdbc.update("DELETE FROM commands"); jdbc.update("DELETE FROM transfers"); jdbc.update("DELETE FROM ledger"); jdbc.update("DELETE FROM tickets");
        jdbc.update("UPDATE branch SET open=TRUE"); jdbc.update("UPDATE accounts SET status='OPEN', balance=1000");
    }
    long serving(String account, String first, String last, String teller) {
        var ticket = bank.checkIn(UUID.randomUUID(), new CheckIn(account, first, last));
        bank.claim(UUID.randomUUID(), new Claim(teller)); return ticket.id();
    }
    TransferRequest amount(String value) { return new TransferRequest("DEMO-1002", new BigDecimal(value)); }
    @Test void conservesMoneyLinksBothHistoriesAndRetriesAfterCompletion() {
        long ticket = serving("DEMO-1001", "Alex", "River", "one"); UUID key = UUID.randomUUID();
        Transfer transfer = bank.transfer(key, ticket, amount("12.34"));
        assertThat(bank.account("DEMO-1001").balance()).isEqualByComparingTo("987.66");
        assertThat(bank.account("DEMO-1002").balance()).isEqualByComparingTo("1012.34");
        assertThat(bank.history("DEMO-1001").get(0).transferId()).isEqualTo(transfer.id());
        assertThat(bank.history("DEMO-1002").get(0).counterpartyAccountId()).isEqualTo("DEMO-1001");
        bank.complete(UUID.randomUUID(), ticket);
        assertThat(bank.transfer(key, ticket, amount("12.34"))).isEqualTo(transfer);
        assertThat(bank.history("DEMO-1002")).hasSize(1);
    }
    @Test void failuresRollbackBothAccountsBothLedgerRowsAndReceipt() {
        long ticket = serving("DEMO-1001", "Alex", "River", "one"); UUID key = UUID.randomUUID();
        doThrow(new IllegalStateException("injected credit failure")).when(repo).ledgerFor(eq("DEMO-1002"), anyLong(), anyString(), any(), any());
        assertThatThrownBy(() -> bank.transfer(key, ticket, amount("5"))).isInstanceOf(IllegalStateException.class);
        assertThat(bank.account("DEMO-1001").balance()).isEqualByComparingTo("1000");
        assertThat(bank.account("DEMO-1002").balance()).isEqualByComparingTo("1000");
        assertThat(bank.history("DEMO-1001")).isEmpty(); assertThat(repo.receipt(key.toString())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transfers", Integer.class)).isZero();
    }
    @Test void rejectsInvalidDestinationOverdraftAndRecipientOverflow() {
        long ticket = serving("DEMO-1001", "Alex", "River", "one");
        for (TransferRequest request : List.of(new TransferRequest("DEMO-1001", BigDecimal.ONE),
                new TransferRequest("MISSING", BigDecimal.ONE), amount("1001"), amount("0"), amount("0.001")))
            assertThatThrownBy(() -> bank.transfer(UUID.randomUUID(), ticket, request)).isInstanceOf(BankException.class);
        jdbc.update("UPDATE accounts SET balance=9999999999999.99 WHERE id='DEMO-1002'");
        assertThatThrownBy(() -> bank.transfer(UUID.randomUUID(), ticket, amount("1"))).isInstanceOf(BankException.class);
        jdbc.update("UPDATE accounts SET balance=0, status='CLOSED' WHERE id='DEMO-1002'");
        assertThatThrownBy(() -> bank.transfer(UUID.randomUUID(), ticket, amount("1"))).isInstanceOf(BankException.class);
        assertThat(bank.history("DEMO-1001")).isEmpty();
    }
    @Test void concurrentDuplicateTransfersApplyExactlyOnce() throws Exception {
        long ticket = serving("DEMO-1001", "Alex", "River", "one"); UUID key = UUID.randomUUID();
        var pool = Executors.newFixedThreadPool(4);
        try {
            var tasks = new ArrayList<Callable<Transfer>>();
            for (int i=0; i<8; i++) tasks.add(() -> bank.transfer(key, ticket, amount("600")));
            var futures = pool.invokeAll(tasks); Transfer first = futures.get(0).get();
            for (var future : futures) assertThat(future.get()).isEqualTo(first);
            assertThat(bank.account("DEMO-1001").balance()).isEqualByComparingTo("400");
            assertThat(bank.account("DEMO-1002").balance()).isEqualByComparingTo("1600");
            assertThat(bank.history("DEMO-1001")).hasSize(1);
        } finally { pool.shutdownNow(); }
    }
}
