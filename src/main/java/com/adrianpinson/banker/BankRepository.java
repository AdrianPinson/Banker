package com.adrianpinson.banker;

import static com.adrianpinson.banker.BankModels.*;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class BankRepository {
    private final JdbcTemplate jdbc;
    private static final RowMapper<Account> ACCOUNT = (r, n) -> new Account(r.getString("id"),
            r.getString("first_name"), r.getString("last_name"), r.getBigDecimal("balance"), r.getString("status"));
    private static final RowMapper<Ticket> TICKET = (r, n) -> new Ticket(r.getLong("id"), r.getString("account_id"),
            r.getString("status"), r.getString("teller"), time(r, "checked_in_at"), time(r, "served_at"), time(r, "completed_at"));
    private static final RowMapper<LedgerEntry> LEDGER = (r, n) -> new LedgerEntry(r.getLong("id"),
            r.getString("account_id"), r.getLong("ticket_id"), r.getString("kind"), r.getBigDecimal("amount"),
            r.getBigDecimal("balance_after"), time(r, "created_at"));

    public BankRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static OffsetDateTime time(ResultSet r, String column) throws SQLException {
        OffsetDateTime value = r.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.withOffsetSameInstant(java.time.ZoneOffset.UTC);
    }
    public void lockWorkflow() { jdbc.queryForObject("SELECT id FROM branch WHERE id = 1 FOR UPDATE", Integer.class); }
    public Branch branch() { return new Branch(Boolean.TRUE.equals(jdbc.queryForObject("SELECT open FROM branch WHERE id = 1", Boolean.class))); }
    public void setOpen(boolean open) { jdbc.update("UPDATE branch SET open = ? WHERE id = 1", open); }
    public List<Account> accounts() { return jdbc.query("SELECT * FROM accounts ORDER BY id", ACCOUNT); }
    public Account account(String id) {
        return jdbc.query("SELECT * FROM accounts WHERE id = ?", ACCOUNT, id).stream().findFirst()
                .orElseThrow(() -> new BankException(404, "ACCOUNT_NOT_FOUND", "Account was not found."));
    }
    public Ticket ticket(long id) {
        return jdbc.query("SELECT * FROM tickets WHERE id = ?", TICKET, id).stream().findFirst()
                .orElseThrow(() -> new BankException(404, "TICKET_NOT_FOUND", "Ticket was not found."));
    }
    public List<Ticket> queue() { return jdbc.query("SELECT * FROM tickets WHERE active_slot = 1 ORDER BY id", TICKET); }
    public Optional<Ticket> waiting() {
        return jdbc.query("SELECT * FROM tickets WHERE status = 'WAITING' ORDER BY id LIMIT 1", TICKET).stream().findFirst();
    }
    public boolean activeAccount(String id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tickets WHERE account_id = ? AND active_slot = 1", Long.class, id) > 0;
    }
    public boolean activeTeller(String teller) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tickets WHERE teller = ? AND status = 'SERVING'", Long.class, teller) > 0;
    }
    public Ticket checkIn(String account) {
        long id = insert("INSERT INTO tickets (account_id, status, active_slot, checked_in_at) VALUES (?, 'WAITING', 1, ?)", account, OffsetDateTime.now());
        return ticket(id);
    }
    public Ticket serve(long id, String teller) {
        jdbc.update("UPDATE tickets SET status = 'SERVING', teller = ?, served_at = ? WHERE id = ?", teller, OffsetDateTime.now(), id);
        return ticket(id);
    }
    public Ticket complete(long id) {
        jdbc.update("UPDATE tickets SET status = 'COMPLETED', active_slot = NULL, completed_at = ? WHERE id = ?", OffsetDateTime.now(), id);
        return ticket(id);
    }
    public void updateBalance(String id, java.math.BigDecimal balance) { jdbc.update("UPDATE accounts SET balance = ? WHERE id = ?", balance, id); }
    public void closeAccount(String id) { jdbc.update("UPDATE accounts SET status = 'CLOSED' WHERE id = ?", id); }
    public LedgerEntry ledger(Ticket ticket, String kind, java.math.BigDecimal amount, java.math.BigDecimal balance) {
        long id = insert("INSERT INTO ledger (account_id, ticket_id, kind, amount, balance_after, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                ticket.accountId(), ticket.id(), kind, amount, balance, OffsetDateTime.now());
        return jdbc.queryForObject("SELECT * FROM ledger WHERE id = ?", LEDGER, id);
    }
    public List<LedgerEntry> history(String account) {
        return jdbc.query("SELECT * FROM ledger WHERE account_id = ? ORDER BY id", LEDGER, account);
    }
    public Optional<Receipt> receipt(String key) {
        return jdbc.query("SELECT fingerprint, response FROM commands WHERE request_key = ?",
                (r, n) -> new Receipt(r.getString(1), r.getString(2)), key).stream().findFirst();
    }
    public void receipt(String key, String fingerprint, String response) {
        jdbc.update("INSERT INTO commands (request_key, fingerprint, response, created_at) VALUES (?, ?, ?, ?)", key, fingerprint, response, OffsetDateTime.now());
    }
    public record Receipt(String fingerprint, String response) {}
    private long insert(String sql, Object... args) {
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, new String[]{"id"});
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, holder);
        return Objects.requireNonNull(holder.getKey()).longValue();
    }
}
