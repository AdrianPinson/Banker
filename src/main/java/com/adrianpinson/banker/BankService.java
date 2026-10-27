package com.adrianpinson.banker;

import static com.adrianpinson.banker.BankModels.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Supplier;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BankService {
    private final BankRepository repo;
    private final TransactionTemplate transactions;
    private final ObjectMapper json;
    private final Validator validator;

    public BankService(BankRepository repo, PlatformTransactionManager manager, ObjectMapper json, Validator validator) {
        this.repo = repo;
        this.transactions = new TransactionTemplate(manager);
        this.transactions.setTimeout(10);
        this.transactions.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.json = json;
        this.validator = validator;
    }
    public List<Account> accounts() { return repo.accounts(); }
    public Account account(String id) { return repo.account(id); }
    public List<LedgerEntry> history(String id) { repo.account(id); return repo.history(id); }
    public List<Ticket> queue() { return repo.queue(); }
    public Branch branch() { return repo.branch(); }
    public Ticket ticket(long id) { return repo.ticket(id); }

    public Ticket checkIn(UUID key, CheckIn request) {
        validate(request);
        var normalized = new CheckIn(request.accountId().trim().toUpperCase(Locale.ROOT),
                request.firstName().trim().toLowerCase(Locale.ROOT), request.lastName().trim().toLowerCase(Locale.ROOT));
        return command(key, "check-in", normalized, Ticket.class, () -> {
            requireOpen();
            Account account = repo.account(normalized.accountId());
            requireAccountOpen(account);
            if (!account.firstName().equalsIgnoreCase(normalized.firstName()) || !account.lastName().equalsIgnoreCase(normalized.lastName())) {
                throw new BankException(400, "IDENTITY_MISMATCH", "Name does not match the demo account.");
            }
            if (repo.activeAccount(account.id())) throw new BankException(409, "ALREADY_CHECKED_IN", "This account already has an active ticket.");
            return repo.checkIn(account.id());
        });
    }
    public Ticket claim(UUID key, Claim request) {
        validate(request);
        String teller = request.teller().trim().toLowerCase(Locale.ROOT);
        return command(key, "claim-next", teller, Ticket.class, () -> {
            requireOpen();
            if (repo.activeTeller(teller)) throw new BankException(409, "TELLER_BUSY", "Complete the teller's active ticket first.");
            Ticket next = repo.waiting().orElseThrow(() -> new BankException(409, "QUEUE_EMPTY", "No clients are waiting."));
            return repo.serve(next.id(), teller);
        });
    }
    public Ticket complete(UUID key, long ticketId) {
        return command(key, "complete", ticketId, Ticket.class, () -> {
            requireOpen();
            serving(ticketId);
            return repo.complete(ticketId);
        });
    }
    public LedgerEntry transact(UUID key, long ticketId, Money request, boolean deposit) {
        validate(request);
        BigDecimal amount = MoneyRules.positive(request.amount());
        String kind = deposit ? "DEPOSIT" : "WITHDRAWAL";
        return command(key, kind, List.of(ticketId, amount.toPlainString()), LedgerEntry.class, () -> {
            requireOpen();
            Ticket ticket = serving(ticketId);
            Account account = repo.account(ticket.accountId());
            requireAccountOpen(account);
            BigDecimal balance = MoneyRules.apply(account.balance(), amount, deposit);
            repo.updateBalance(account.id(), balance);
            return repo.ledger(ticket, kind, amount, balance);
        });
    }
    public Account closeAccount(UUID key, long ticketId) {
        return command(key, "close-account", ticketId, Account.class, () -> {
            requireOpen();
            Ticket ticket = serving(ticketId);
            Account account = repo.account(ticket.accountId());
            requireAccountOpen(account);
            if (account.balance().signum() != 0) throw new BankException(409, "BALANCE_NOT_ZERO", "Withdraw the remaining balance before closing the account.");
            repo.closeAccount(account.id());
            repo.ledger(ticket, "CLOSE_ACCOUNT", new BigDecimal("0.00"), account.balance());
            repo.complete(ticket.id());
            return repo.account(account.id());
        });
    }
    public Branch setOpen(UUID key, boolean open) {
        return command(key, "branch-open", open, Branch.class, () -> {
            if (!open && !repo.queue().isEmpty()) throw new BankException(409, "ACTIVE_TICKETS", "Complete all waiting and serving tickets before closing the branch.");
            repo.setOpen(open);
            return repo.branch();
        });
    }
    private Ticket serving(long id) {
        Ticket ticket = repo.ticket(id);
        if (!ticket.status().equals("SERVING")) throw new BankException(409, "TICKET_NOT_SERVING", "The ticket must be serving for this operation.");
        return ticket;
    }
    private void requireOpen() {
        if (!repo.branch().open()) throw new BankException(409, "BRANCH_CLOSED", "The branch is closed.");
    }
    private void requireAccountOpen(Account account) {
        if (!account.status().equals("OPEN")) throw new BankException(409, "ACCOUNT_CLOSED", "This account is closed.");
    }
    private void validate(Object request) {
        if (request == null || !validator.validate(request).isEmpty()) throw new BankException(400, "INVALID_REQUEST", "Check required fields and allowed lengths or amounts.");
    }
    // One database row serializes mutations across processes, not just threads in this JVM.
    // This deliberately favors correctness and clear FIFO behavior for a single demo branch.
    private <T> T command(UUID key, String operation, Object payload, Class<T> type, Supplier<T> action) {
        if (key == null) throw new BankException(400, "MISSING_KEY", "An Idempotency-Key UUID is required.");
        String fingerprint = fingerprint(operation, payload);
        return transactions.execute(status -> {
            repo.lockWorkflow();
            var saved = repo.receipt(key.toString());
            if (saved.isPresent()) {
                if (!saved.get().fingerprint().equals(fingerprint)) throw new BankException(409, "KEY_REUSED", "Use a new idempotency key for a different command.");
                try { return json.readValue(saved.get().response(), type); }
                catch (JsonProcessingException e) { throw new IllegalStateException("Invalid stored command response", e); }
            }
            T response = action.get();
            try { repo.receipt(key.toString(), fingerprint, json.writeValueAsString(response)); }
            catch (JsonProcessingException e) { throw new IllegalStateException("Cannot persist command response", e); }
            return response;
        });
    }
    private String fingerprint(String operation, Object payload) {
        try {
            byte[] data = json.writeValueAsBytes(List.of(operation, payload));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
