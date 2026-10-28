package com.test.ktpm.Controllers;

import com.test.ktpm.BaseIntegrationTest;
import com.test.ktpm.Entity.Product;
import com.test.ktpm.Entity.ProductStatus;
import com.test.ktpm.Model.Request.ProductPatchRequest;
import com.test.ktpm.Model.Request.ProductRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Kiểm thử ProductController")
public class ProductControllerTest extends BaseIntegrationTest {

    @BeforeEach
    void setUp() {
        testData.cleanDatabase();
    }

    @Test
    @DisplayName("1. User lấy danh sách sản phẩm phân trang -> 200 OK")
    void testGetAllProducts_Success() throws Exception {
        testData.createDefaultProduct("Bàn phím cơ", 20);
        testData.createDefaultProduct("Chuột gaming", 30);

        mockMvc.perform(get("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, getUserToken())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    @DisplayName("2. User lấy chi tiết sản phẩm theo ID -> 200 OK")
    void testGetProductById_Success() throws Exception {
        Product product = testData.createDefaultProduct("Màn hình 27 inch", 15);

        mockMvc.perform(get("/api/v1/products/" + product.getProductCode())
                        .header(HttpHeaders.AUTHORIZATION, getUserToken())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Màn hình 27 inch")))
                .andExpect(jsonPath("$.qty", is(15)));
    }

    @Test
    @DisplayName("3. Admin tạo mới sản phẩm -> 201 Created")
    void testCreateProduct_AdminSuccess() throws Exception {
        ProductRequest request = testData.createProductRequest("Tai nghe Bluetooth", 50, new BigDecimal("1200000"));

        mockMvc.perform(post("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Tai nghe Bluetooth")));
    }

    @Test
    @DisplayName("4. User thường tạo sản phẩm -> 403 Forbidden")
    void testCreateProduct_UserRoleForbidden() throws Exception {
        ProductRequest request = testData.createProductRequest("USB 64GB", 100, new BigDecimal("150000"));

        mockMvc.perform(post("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, getUserToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.error", is("Forbidden")))
                .andExpect(jsonPath("$.message", containsString("không có quyền")));
    }

    @Test
    @DisplayName("5. Admin cập nhật thông tin sản phẩm (PATCH) -> 200 OK")
    void testPatchProduct_AdminSuccess() throws Exception {
        Product product = testData.createDefaultProduct("Sản phẩm cần sửa", 20);

        ProductPatchRequest patchRequest = testData.createProductPatchRequest("Tên mới sau sửa", new BigDecimal("350000"), ProductStatus.ACTIVE);

        mockMvc.perform(patch("/api/v1/products/" + product.getProductCode())
                        .header(HttpHeaders.AUTHORIZATION, getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Tên mới sau sửa")));
    }

    @Test
    @DisplayName("6. User thường cập nhật sản phẩm (PATCH) -> 403 Forbidden")
    void testPatchProduct_UserRoleForbidden() throws Exception {
        Product product = testData.createDefaultProduct("Sản phẩm cấm sửa", 20);

        ProductPatchRequest patchRequest = testData.createProductPatchRequest("Cố tình sửa", new BigDecimal("100000"), null);

        mockMvc.perform(patch("/api/v1/products/" + product.getProductCode())
                        .header(HttpHeaders.AUTHORIZATION, getUserToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.error", is("Forbidden")))
                .andExpect(jsonPath("$.message", containsString("không có quyền")));
    }
}
