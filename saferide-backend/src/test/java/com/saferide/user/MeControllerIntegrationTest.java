package com.saferide.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferide.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code PUT /api/me/avatar} - role-agnostic, any authenticated account
 * (customer/driver/admin alike) can set or clear their own photo. A blank
 * value clears it back to the initial-letter fallback rather than being
 * rejected as invalid input.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MeControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private String registerAndLogin(String email, String phone) throws Exception {
        String payload = """
                {"name":"Avatar Test","email":"%s","phone":"%s","password":"Password123!","role":"CUSTOMER"}
                """.formatted(email, phone);
        String body = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/accessToken").asText();
    }

    @Test
    void settingAndClearingAnAvatarUrlBothWork() throws Exception {
        String token = registerAndLogin("avatar-test@example.com", "9622200001");

        mockMvc.perform(put("/api/me/avatar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarUrl\":\"https://example.com/photo.jpg\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value("https://example.com/photo.jpg"));

        // The next login response also carries it - Navbar/UserMenu shows it with no extra fetch.
        String loginBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"avatar-test@example.com\",\"password\":\"Password123!\"}"))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(objectMapper.readTree(loginBody).at("/data/user/avatarUrl").asText())
                .isEqualTo("https://example.com/photo.jpg");

        mockMvc.perform(put("/api/me/avatar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarUrl\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").doesNotExist());
    }
}
