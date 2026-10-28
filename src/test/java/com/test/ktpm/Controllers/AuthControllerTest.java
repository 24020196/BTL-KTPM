package com.test.ktpm.Controllers;

import com.test.ktpm.BaseIntegrationTest;
import com.test.ktpm.Model.Request.LoginRequest;
import com.test.ktpm.Model.Request.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Kiểm thử AuthController")
public class AuthControllerTest extends BaseIntegrationTest {

    @BeforeEach
    void setUp() {
        testData.cleanDatabase();
    }

    @Test
    @DisplayName("1. Đăng ký tài khoản mới thành công -> 201 Created")
    void testRegister_Success() throws Exception {
        RegisterRequest request = testData.createRegisterRequest("new_registered_user", "password123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.tokenType", is("Bearer")));
    }

    @Test
    @DisplayName("2. Đăng ký tài khoản với username trùng lặp -> 400 Bad Request")
    void testRegister_DuplicateUsername() throws Exception {
        testData.createUser("existing_user", "ROLE_USER");

        RegisterRequest duplicateReq = testData.createRegisterRequest("existing_user", "anotherPassword");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("đã tồn tại")));
    }

    @Test
    @DisplayName("3. Đăng nhập thành công với thông tin đúng -> 200 OK + JWT Token")
    void testLogin_Success() throws Exception {
        testData.createUser("login_user", "ROLE_USER");

        LoginRequest request = testData.createLoginRequest("login_user", "password123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.tokenType", is("Bearer")));
    }

    @Test
    @DisplayName("4. Đăng nhập thất bại do mật khẩu sai -> 401 Unauthorized")
    void testLogin_WrongPassword() throws Exception {
        testData.createUser("login_user2", "ROLE_USER");

        LoginRequest request = testData.createLoginRequest("login_user2", "wrong_password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
