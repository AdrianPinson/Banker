package com.adrianpinson.banker;

import static com.adrianpinson.banker.BankModels.*;
import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class PersistenceRestartTest {
    @TempDir Path directory;
    private ConfigurableApplicationContext start(String url) {
        return new SpringApplicationBuilder(BankerApplication.class).web(WebApplicationType.NONE)
                .run("--spring.datasource.url=" + url, "--spring.datasource.username=" + System.getenv().getOrDefault("BANKER_TEST_DB_USER", "sa"),
                        "--spring.datasource.password=" + System.getenv().getOrDefault("BANKER_TEST_DB_PASSWORD", ""), "--spring.main.banner-mode=off");
    }
    @Test void accountsQueueLedgerAndRetryReceiptsSurviveFullContextRestart() {
        String url = System.getenv().getOrDefault("BANKER_TEST_DB_URL",
                "jdbc:h2:file:" + directory.resolve("restart") + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        UUID key = UUID.randomUUID();
        LedgerEntry original;
        long id;
        try (var context = start(url)) {
            BankService bank = context.getBean(BankService.class);
            var jdbc = context.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
            jdbc.update("DELETE FROM commands");
            jdbc.update("DELETE FROM ledger");
            jdbc.update("DELETE FROM tickets");
            jdbc.update("UPDATE accounts SET status = 'OPEN', balance = CASE id WHEN 'DEMO-1001' THEN 1000.00 WHEN 'DEMO-1002' THEN 250.00 ELSE 0.00 END");
            jdbc.update("UPDATE branch SET open = TRUE");
            id = bank.checkIn(UUID.randomUUID(), new CheckIn("DEMO-1001", "Alex", "River")).id();
            bank.claim(UUID.randomUUID(), new Claim("one"));
            original = bank.transact(key, id, new Money(new BigDecimal("12.34")), true);
            bank.checkIn(UUID.randomUUID(), new CheckIn("DEMO-1002", "Jordan", "Vale"));
        }
        try (var context = start(url)) {
            BankService bank = context.getBean(BankService.class);
            assertThat(bank.account("DEMO-1001").balance()).isEqualByComparingTo("1012.34");
            assertThat(bank.queue()).extracting(Ticket::status).containsExactly("SERVING", "WAITING");
            assertThat(bank.history("DEMO-1001")).containsExactly(original);
            assertThat(bank.transact(key, id, new Money(new BigDecimal("12.34")), true)).isEqualTo(original);
            assertThat(bank.history("DEMO-1001")).hasSize(1);
            bank.complete(UUID.randomUUID(), id);
            assertThat(bank.claim(UUID.randomUUID(), new Claim("two")).accountId()).isEqualTo("DEMO-1002");
        }
    }
}
