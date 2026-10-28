package com.test.ktpm.Controllers;

import com.test.ktpm.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Kiểm thử UserController")
public class UserControllerTest extends BaseIntegrationTest {

    @BeforeEach
    void setUp() {
        testData.cleanDatabase();
    }

    @Test
    @DisplayName("1. Lấy danh sách users với quyền ROLE_ADMIN -> 200 OK")
    void testGetAllUsers_AdminSuccess() throws Exception {
        testData.createAdminUser();
        testData.createRegularUser();

        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("2. User thường (ROLE_USER) gọi xem danh sách User -> 403 Forbidden")
    void testGetAllUsers_UserRoleForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, getUserToken())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.error", is("Forbidden")))
                .andExpect(jsonPath("$.message", containsString("không có quyền")));
    }

    @Test
    @DisplayName("3. Chưa đăng nhập (không token) gọi endpoint User -> 401 Unauthorized")
    void testGetAllUsers_Unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")))
                .andExpect(jsonPath("$.message", containsString("cần đăng nhập")));
    }
}

