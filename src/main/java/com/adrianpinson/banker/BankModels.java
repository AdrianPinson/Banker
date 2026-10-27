package com.adrianpinson.banker;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import jakarta.validation.constraints.*;

public final class BankModels {
    private BankModels() {}

    public record Account(String id, String firstName, String lastName, BigDecimal balance, String status) {}
    public record Ticket(long id, String accountId, String status, String teller,
                         OffsetDateTime checkedInAt, OffsetDateTime servedAt, OffsetDateTime completedAt) {}
    public record LedgerEntry(long id, String accountId, long ticketId, String kind,
                              BigDecimal amount, BigDecimal balanceAfter, OffsetDateTime createdAt) {}
    public record Branch(boolean open) {}
    public record CheckIn(@NotBlank @Size(max=16) String accountId,
                          @NotBlank @Size(max=60) String firstName,
                          @NotBlank @Size(max=60) String lastName) {}
    public record Claim(@NotBlank @Size(max=40) String teller) {}
    public record Money(@NotNull @DecimalMin("0.01") @DecimalMax("9999999999999.99")
                        @Digits(integer=13, fraction=2) BigDecimal amount) {}
}
