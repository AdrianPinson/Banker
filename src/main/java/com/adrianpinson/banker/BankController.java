package com.adrianpinson.banker;

import static com.adrianpinson.banker.BankModels.*;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class BankController {
    private final BankService bank;
    public BankController(BankService bank) { this.bank = bank; }

    @GetMapping("/accounts") public List<Account> accounts() { return bank.accounts(); }
    @GetMapping("/accounts/{id}") public Account account(@PathVariable String id) { return bank.account(id); }
    @GetMapping("/accounts/{id}/transactions") public List<LedgerEntry> history(@PathVariable String id) { return bank.history(id); }
    @GetMapping("/queue") public List<Ticket> queue() { return bank.queue(); }
    @GetMapping("/tickets/{id}") public Ticket ticket(@PathVariable long id) { return bank.ticket(id); }
    @GetMapping("/branch") public Branch branch() { return bank.branch(); }
    @PostMapping("/queue/check-in") public Ticket checkIn(@RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody CheckIn request) { return bank.checkIn(key, request); }
    @PostMapping("/queue/claim-next") public Ticket claim(@RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody Claim request) { return bank.claim(key, request); }
    @PostMapping("/tickets/{id}/complete") public Ticket complete(@RequestHeader("Idempotency-Key") UUID key, @PathVariable long id) { return bank.complete(key, id); }
    @PostMapping("/tickets/{id}/deposits") public LedgerEntry deposit(@RequestHeader("Idempotency-Key") UUID key, @PathVariable long id, @Valid @RequestBody Money request) { return bank.transact(key, id, request, true); }
    @PostMapping("/tickets/{id}/withdrawals") public LedgerEntry withdraw(@RequestHeader("Idempotency-Key") UUID key, @PathVariable long id, @Valid @RequestBody Money request) { return bank.transact(key, id, request, false); }
    @PostMapping("/tickets/{id}/close-account") public Account closeAccount(@RequestHeader("Idempotency-Key") UUID key, @PathVariable long id) { return bank.closeAccount(key, id); }
    @PostMapping("/branch/open") public Branch open(@RequestHeader("Idempotency-Key") UUID key) { return bank.setOpen(key, true); }
    @PostMapping("/branch/close") public Branch close(@RequestHeader("Idempotency-Key") UUID key) { return bank.setOpen(key, false); }
}
