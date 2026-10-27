package com.adrianpinson.banker;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyRules {
    public static final BigDecimal MAX_BALANCE = new BigDecimal("9999999999999.99");
    private MoneyRules() {}

    public static BigDecimal positive(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.scale() > 2 || value.compareTo(MAX_BALANCE) > 0) {
            throw new BankException(400, "INVALID_AMOUNT", "Use a positive amount with at most two decimal places.");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal apply(BigDecimal balance, BigDecimal amount, boolean deposit) {
        amount = positive(amount);
        BigDecimal result = deposit ? balance.add(amount) : balance.subtract(amount);
        if (result.signum() < 0) {
            throw new BankException(409, "INSUFFICIENT_FUNDS", "Withdrawal exceeds the available balance.");
        }
        if (result.compareTo(MAX_BALANCE) > 0) {
            throw new BankException(409, "BALANCE_LIMIT", "The resulting balance exceeds the demo account limit.");
        }
        return result.setScale(2, RoundingMode.UNNECESSARY);
    }
}
