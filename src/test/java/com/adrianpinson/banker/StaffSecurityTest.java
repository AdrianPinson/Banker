package com.adrianpinson.banker;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"banker.security.enabled=true", "spring.datasource.url=jdbc:h2:mem:security;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class StaffSecurityTest {
    @Autowired MockMvc http;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void reset() {
        jdbc.update("DELETE FROM commands"); jdbc.update("DELETE FROM ledger"); jdbc.update("DELETE FROM tickets");
        jdbc.update("UPDATE branch SET open = TRUE");
        jdbc.update("UPDATE accounts SET status='OPEN', balance=1000");
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postAs(String path, String user, String body) {
        return post("/api" + path).with(user(user).roles(user.equals("manager") ? "MANAGER" : "TELLER"))
                .with(csrf()).header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(body);
    }
    private long serving() throws Exception {
        http.perform(postAs("/queue/check-in", "teller-1", "{\"accountId\":\"DEMO-1001\",\"firstName\":\"Alex\",\"lastName\":\"River\"}"))
                .andExpect(status().isOk());
        http.perform(postAs("/queue/claim-next", "teller-1", "{\"teller\":\"teller-1\"}")).andExpect(status().isOk());
        return jdbc.queryForObject("SELECT id FROM tickets WHERE status='SERVING'", Long.class);
    }
    @Test void anonymousReadsAndMissingCsrfAreRejected() throws Exception {
        http.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
        http.perform(post("/api/branch/close").with(user("manager").roles("MANAGER")))
                .andExpect(status().isForbidden());
        http.perform(get("/api/auth")).andExpect(status().isOk()).andExpect(jsonPath("$.username").value(""))
                .andExpect(jsonPath("$.csrfToken").isNotEmpty());
    }
    @Test void loginChecksPasswordAndLogoutInvalidatesTheSession() throws Exception {
        http.perform(post("/api/login").with(csrf()).param("username", "teller-1").param("password", "incorrect"))
                .andExpect(status().isUnauthorized());
        var result = http.perform(post("/api/login").with(csrf()).param("username", "teller-1").param("password", "teller-demo-only"))
                .andExpect(status().isNoContent()).andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) result.getRequest().getSession(false);
        http.perform(get("/api/auth").session(session)).andExpect(jsonPath("$.username").value("teller-1"))
                .andExpect(jsonPath("$.manager").value(false));
        http.perform(post("/api/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
    }
    @Test void tellerCannotImpersonateAnotherTellerOrChangeBranchState() throws Exception {
        http.perform(postAs("/queue/claim-next", "teller-1", "{\"teller\":\"teller-2\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TELLER_MISMATCH"));
        http.perform(postAs("/branch/close", "teller-1", ""))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MANAGER_REQUIRED"));
        http.perform(postAs("/branch/close", "manager", "")).andExpect(status().isOk());
    }
    @Test void anotherStaffMemberCannotOperateOnAnOwnedTicket() throws Exception {
        long id = serving();
        for (String action : new String[]{"deposits", "withdrawals", "complete"}) {
            http.perform(postAs("/tickets/" + id + "/" + action, "teller-2", action.equals("complete") ? "" : "{\"amount\":\"1\"}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TICKET_OWNER_REQUIRED"));
        }
        http.perform(postAs("/tickets/" + id + "/close-account", "teller-1", "")).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger", Integer.class)).isZero();
    }
    @Test void receiptsAreBoundToTheirActorAndKeepOriginalResult() throws Exception {
        long id = serving(); String key = UUID.randomUUID().toString();
        var original = http.perform(post("/api/tickets/" + id + "/deposits").with(user("teller-1").roles("TELLER")).with(csrf())
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"5\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        http.perform(post("/api/tickets/" + id + "/deposits").with(user("teller-2").roles("TELLER")).with(csrf())
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"5\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("KEY_REUSED"));
        http.perform(post("/api/tickets/" + id + "/deposits").with(user("teller-1").roles("TELLER")).with(csrf())
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"5\"}"))
                .andExpect(status().isOk()).andExpect(content().json(original));
        assertThat(jdbc.queryForObject("SELECT actor FROM commands WHERE request_key=?", String.class, key)).isEqualTo("teller-1");
        assertThat(jdbc.queryForObject("SELECT balance FROM accounts WHERE id='DEMO-1001'", java.math.BigDecimal.class)).isEqualByComparingTo("1005");
    }
}
