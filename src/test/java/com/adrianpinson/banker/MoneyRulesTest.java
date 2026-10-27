package com.adrianpinson.banker;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.*;

class MoneyRulesTest {
    @ParameterizedTest @NullSource @ValueSource(strings={"0", "-1", "1.001", "10000000000000.00"})
    void rejectsInvalidAmounts(String value) {
        assertThatThrownBy(() -> MoneyRules.positive(value == null ? null : new BigDecimal(value)))
                .isInstanceOf(BankException.class);
    }
    @Test void addsDecimalMoneyExactly() {
        assertThat(MoneyRules.apply(new BigDecimal("0.10"), new BigDecimal("0.20"), true)).isEqualByComparingTo("0.30");
    }
    @Test void allowsFullWithdrawalAndRejectsOverdraft() {
        assertThat(MoneyRules.apply(new BigDecimal("10.00"), new BigDecimal("10.00"), false)).isEqualByComparingTo("0.00");
        assertThatThrownBy(() -> MoneyRules.apply(new BigDecimal("10.00"), new BigDecimal("10.01"), false)).isInstanceOf(BankException.class);
    }
    @Test void rejectsBalanceOverflow() {
        assertThatThrownBy(() -> MoneyRules.apply(MoneyRules.MAX_BALANCE, new BigDecimal("0.01"), true)).isInstanceOf(BankException.class);
    }
}
