package com.adrianpinson.banker;

import static org.assertj.core.api.Assertions.*;
import static com.adrianpinson.banker.BankModels.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class WorkflowRevisionTest {
    @Autowired BankService bank;
    @Autowired BankRepository repo;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void clear() {
        jdbc.update("DELETE FROM commands"); jdbc.update("DELETE FROM transfers"); jdbc.update("DELETE FROM ledger"); jdbc.update("DELETE FROM tickets");
        jdbc.update("UPDATE accounts SET status='OPEN', balance=1000"); jdbc.update("UPDATE branch SET open=TRUE");
    }
    @Test void onlyNewCommittedCommandsAdvanceTheLiveRevision() {
        long before = repo.revision(); UUID key = UUID.randomUUID(); var request = new CheckIn("DEMO-1001", "Alex", "River");
        bank.checkIn(key, request); assertThat(repo.revision()).isEqualTo(before + 1);
        bank.checkIn(key, request); assertThat(repo.revision()).isEqualTo(before + 1);
        assertThatThrownBy(() -> bank.checkIn(UUID.randomUUID(), request)).isInstanceOf(BankException.class);
        assertThat(repo.revision()).isEqualTo(before + 1);
    }
}
