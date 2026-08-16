package com.saferide.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferide.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Black-box coverage of the full auth flow through real HTTP-shaped
 * requests (no {@code @WithMockUser} shortcuts) - register, login, use the
 * access token against a protected endpoint, cross-role rejection, token
 * refresh + rotation, and logout revocation. Login rate limiting here runs
 * against a real Redis (Phase 14, via {@link AbstractIntegrationTest}), not
 * the in-memory fallback - see {@code InMemoryLoginRateLimiterTest} for
 * that one directly.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LoginRateLimitProperties loginRateLimitProperties;

    private String registerAndGetBody(String name, String email, String phone, String role, String license) throws Exception {
        String payload = """
                {
                  "name": "%s",
                  "email": "%s",
                  "phone": "%s",
                  "password": "Password123!",
                  "role": "%s"%s
                }
                """.formatted(name, email, phone, role, license == null ? "" : ",\"licenseNumber\":\"" + license + "\"");

        return mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void registerLoginAndAccessOwnProfile() throws Exception {
        String body = registerAndGetBody("Alice Customer", "alice@example.com", "9111100001", "CUSTOMER", null);
        JsonNode json = objectMapper.readTree(body);
        String accessToken = json.at("/data/accessToken").asText();

        assertThat(accessToken).isNotBlank();

        mockMvc.perform(get("/api/customers/profile")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("alice@example.com"));
    }

    @Test
    void registeringTheSameEmailTwiceIsRejected() throws Exception {
        registerAndGetBody("Bob", "bob@example.com", "9111100002", "CUSTOMER", null);

        String duplicatePayload = """
                {"name":"Bob Two","email":"bob@example.com","phone":"9111100099","password":"Password123!","role":"CUSTOMER"}
                """;
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicatePayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void driverSignupWithoutLicenseNumberFailsValidation() throws Exception {
        String payload = """
                {"name":"Carl","email":"carl@example.com","phone":"9111100003","password":"Password123!","role":"DRIVER"}
                """;
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void selfRegisteringAsAdminIsRejected() throws Exception {
        String payload = """
                {"name":"Evil","email":"evil@example.com","phone":"9111100004","password":"Password123!","role":"ADMIN"}
                """;
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void loginWithWrongPasswordReturns401WithConsistentEnvelope() throws Exception {
        registerAndGetBody("Dana", "dana@example.com", "9111100005", "CUSTOMER", null);

        String payload = """
                {"identifier":"dana@example.com","password":"WrongPassword!"}
                """;
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void repeatedFailedLoginsAreRateLimitedPerIdentifier() throws Exception {
        registerAndGetBody("Jill Customer", "jill@example.com", "9111100011", "CUSTOMER", null);

        String wrongPasswordPayload = """
                {"identifier":"jill@example.com","password":"WrongPassword!"}
                """;
        for (int i = 0; i < loginRateLimitProperties.getMaxAttempts(); i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(wrongPasswordPayload))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }

        // One more, even with the CORRECT password now, is rejected before credentials are even checked.
        String correctPasswordPayload = """
                {"identifier":"jill@example.com","password":"Password123!"}
                """;
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(correctPasswordPayload))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));

        // A different identifier is a completely independent counter.
        registerAndGetBody("Kim Customer", "kim@example.com", "9111100012", "CUSTOMER", null);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"kim@example.com","password":"Password123!"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/customers/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void customerCannotAccessDriverOnlyEndpoint() throws Exception {
        String body = registerAndGetBody("Erin Customer", "erin@example.com", "9111100006", "CUSTOMER", null);
        String accessToken = objectMapper.readTree(body).at("/data/accessToken").asText();

        mockMvc.perform(get("/api/drivers/profile")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void driverSignupCreatesAccessibleDriverProfile() throws Exception {
        String body = registerAndGetBody("Frank Driver", "frank@example.com", "9111100007", "DRIVER", "DL-TEST-0001");
        String accessToken = objectMapper.readTree(body).at("/data/accessToken").asText();

        mockMvc.perform(get("/api/drivers/profile")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.licenseNumber").value("DL-TEST-0001"))
                .andExpect(jsonPath("$.data.verificationStatus").value("PENDING"));
    }

    @Test
    void refreshRotatesTokenAndInvalidatesThePreviousOne() throws Exception {
        String body = registerAndGetBody("Gita Customer", "gita@example.com", "9111100008", "CUSTOMER", null);
        String oldRefreshToken = objectMapper.readTree(body).at("/data/refreshToken").asText();

        String refreshPayload = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("refreshToken", oldRefreshToken);
        }});

        String refreshedBody = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshPayload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newRefreshToken = objectMapper.readTree(refreshedBody).at("/data/refreshToken").asText();
        assertThat(newRefreshToken).isNotEqualTo(oldRefreshToken);

        // Replaying the now-rotated-out old refresh token must fail.
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshPayload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void logoutRevokesTheRefreshTokenSoItCanNoLongerBeUsed() throws Exception {
        String body = registerAndGetBody("Hank Customer", "hank@example.com", "9111100009", "CUSTOMER", null);
        String refreshToken = objectMapper.readTree(body).at("/data/refreshToken").asText();
        String payload = "{\"refreshToken\":\"" + refreshToken + "\"}";

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void updatingProfileValidatesInput() throws Exception {
        String body = registerAndGetBody("Ivy Customer", "ivy@example.com", "9111100010", "CUSTOMER", null);
        String accessToken = objectMapper.readTree(body).at("/data/accessToken").asText();

        String badPayload = """
                {"name":"I","phone":"not-a-phone"}
                """;
        mockMvc.perform(put("/api/customers/profile")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        String goodPayload = """
                {"name":"Ivy Updated","phone":"9111100099"}
                """;
        mockMvc.perform(put("/api/customers/profile")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goodPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Ivy Updated"));
    }
}
