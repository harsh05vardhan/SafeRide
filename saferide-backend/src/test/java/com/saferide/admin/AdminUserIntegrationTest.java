package com.saferide.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferide.testsupport.AbstractIntegrationTest;
import com.saferide.user.RoleName;
import com.saferide.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code /api/admin/users} - platform-wide user management, distinct from
 * {@link DriverVerificationIntegrationTest}'s driver-specific verification flow. Deletion is a
 * soft delete (see {@code UserStatus.DELETED}'s own doc) - the deleted account simply can never
 * authenticate again, nothing is physically removed.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminUserIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserService userService;
    @Autowired private PasswordEncoder passwordEncoder;

    private String loginAndGetToken(String identifier, String password) throws Exception {
        String payload = "{\"identifier\":\"%s\",\"password\":\"%s\"}".formatted(identifier, password);
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/accessToken").asText();
    }

    private String provisionAdminAndLogin(String email, String phone) throws Exception {
        userService.createUser("Admin User", email, phone, passwordEncoder.encode("Password123!"), RoleName.ADMIN);
        return loginAndGetToken(email, "Password123!");
    }

    private long registerCustomer(String email, String phone) throws Exception {
        String payload = """
                {"name":"Test Customer","email":"%s","phone":"%s","password":"Password123!","role":"CUSTOMER"}
                """.formatted(email, phone);
        String body = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/user/id").asLong();
    }

    @Test
    void adminCanListUsersFilteredByRole() throws Exception {
        String adminToken = provisionAdminAndLogin("user-admin-list@example.com", "9633300001");
        registerCustomer("user-list-customer@example.com", "9633300002");

        String body = mockMvc.perform(get("/api/admin/users").param("role", "CUSTOMER").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode content = objectMapper.readTree(body).at("/data/content");
        boolean found = false;
        for (JsonNode node : content) {
            if (node.at("/user/email").asText().equals("user-list-customer@example.com")) {
                found = true;
                assertThat(node.at("/status").asText()).isEqualTo("ACTIVE");
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void deletingACustomerPermanentlyBlocksTheirLogin() throws Exception {
        String adminToken = provisionAdminAndLogin("user-admin-delete@example.com", "9633300003");
        long customerId = registerCustomer("user-delete-target@example.com", "9633300004");

        mockMvc.perform(delete("/api/admin/users/" + customerId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Account deleted"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"user-delete-target@example.com\",\"password\":\"Password123!\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCannotDeleteTheirOwnAccount() throws Exception {
        String adminEmail = "user-admin-self@example.com";
        userService.createUser("Self Admin", adminEmail, "9633300005", passwordEncoder.encode("Password123!"), RoleName.ADMIN);
        String adminToken = loginAndGetToken(adminEmail, "Password123!");

        String meBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + adminEmail + "\",\"password\":\"Password123!\"}"))
                .andReturn().getResponse().getContentAsString();
        long selfId = objectMapper.readTree(meBody).at("/data/user/id").asLong();

        mockMvc.perform(delete("/api/admin/users/" + selfId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_DELETE_USER"));
    }

    @Test
    void adminCannotDeleteAnotherAdmin() throws Exception {
        String adminToken = provisionAdminAndLogin("user-admin-deleter@example.com", "9633300006");
        userService.createUser("Other Admin", "user-admin-victim@example.com", "9633300007",
                passwordEncoder.encode("Password123!"), RoleName.ADMIN);
        String victimLoginBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"user-admin-victim@example.com\",\"password\":\"Password123!\"}"))
                .andReturn().getResponse().getContentAsString();
        long victimId = objectMapper.readTree(victimLoginBody).at("/data/user/id").asLong();

        mockMvc.perform(delete("/api/admin/users/" + victimId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_DELETE_USER"));
    }

    @Test
    void nonAdminCannotAccessUserManagement() throws Exception {
        String customerEmail = "user-nonadmin@example.com";
        registerCustomer(customerEmail, "9633300008");
        String customerToken = loginAndGetToken(customerEmail, "Password123!");

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/users/1").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/users/admins")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Sneaky Admin","email":"sneaky@example.com","phone":"9633300099","password":"Password123!"}
                                """))
                .andExpect(status().isForbidden());
    }

    /** The one legitimate way an admin account ever comes into existence outside DevDataSeeder/tests - AuthService#register refuses role: ADMIN outright, so this endpoint is the real path. */
    @Test
    void anExistingAdminCanCreateAnotherAdminWhoCanImmediatelyLogIn() throws Exception {
        String adminToken = provisionAdminAndLogin("user-admin-creator@example.com", "9633300009");

        String createBody = mockMvc.perform(post("/api/admin/users/admins")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Second Admin","email":"second-admin@example.com","phone":"9633300010","password":"Password123!"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.email").value("second-admin@example.com"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(createBody).at("/data/user/roles/0").asText()).isEqualTo("ADMIN");

        String newAdminBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"second-admin@example.com\",\"password\":\"Password123!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(newAdminBody).at("/data/user/roles/0").asText()).isEqualTo("ADMIN");
    }

    @Test
    void creatingAnAdminWithADuplicateEmailIsRejected() throws Exception {
        String adminToken = provisionAdminAndLogin("user-admin-dup@example.com", "9633300011");

        mockMvc.perform(post("/api/admin/users/admins")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Duplicate","email":"user-admin-dup@example.com","phone":"9633300012","password":"Password123!"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }
}
