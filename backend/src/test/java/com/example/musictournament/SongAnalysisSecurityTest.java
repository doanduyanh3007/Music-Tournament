package com.example.musictournament;

import com.example.musictournament.model.AdminAccount;
import com.example.musictournament.repo.AdminAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:analysis-sec-test;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.h2.console.enabled=false",
    "app.admin-token=admin-demo-2026"
})
@AutoConfigureMockMvc
class SongAnalysisSecurityTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AdminAccountRepository adminAccountRepository;

    @BeforeEach
    void setUp() {
        adminAccountRepository.deleteAll();
        // Create an admin account and a normal user account
        adminAccountRepository.save(new AdminAccount("admin@test.com", "admin123", "Admin User", "ADMIN"));
        adminAccountRepository.save(new AdminAccount("user@test.com", "user123", "Regular User", "USER"));
    }

    @Test
    @DisplayName("Unauthenticated request to Song Analysis returns 401 Unauthorized")
    void unauthenticatedRequest_Returns401() throws Exception {
        mvc.perform(get("/api/admin/analysis")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("Invalid token to Song Analysis returns 401 Unauthorized")
    void invalidToken_Returns401() throws Exception {
        mvc.perform(get("/api/admin/analysis")
                .header("X-Admin-Token", "wrong-secret-token")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Non-admin user account credentials returns 403 Forbidden")
    void nonAdminAccount_Returns403() throws Exception {
        mvc.perform(get("/api/admin/analysis")
                .header("X-Admin-Email", "user@test.com")
                .header("X-Admin-Password", "user123")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @DisplayName("Valid admin token returns 200 OK")
    void validAdminToken_Returns200() throws Exception {
        mvc.perform(get("/api/admin/analysis")
                .header("X-Admin-Token", "admin-demo-2026")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("Valid admin credentials via header returns 200 OK")
    void validAdminCredentials_Returns200() throws Exception {
        mvc.perform(get("/api/admin/analysis")
                .header("X-Admin-Email", "admin@test.com")
                .header("X-Admin-Password", "admin123")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}
