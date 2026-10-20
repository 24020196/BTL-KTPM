package com.test.ktpm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.ktpm.Dto.Request.ProductRequest;
import com.test.ktpm.Entity.Product;
import com.test.ktpm.Repositories.OrderDetailRepository;
import com.test.ktpm.Repositories.OrderRepository;
import com.test.ktpm.Repositories.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class ProductControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderDetailRepository orderDetailRepository;

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        orderDetailRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /api/v1/products - Tạo mới sản phẩm thành công (201 Created)")
    void testCreateProduct_Success() throws Exception {
        ProductRequest request = ProductRequest.builder()
                .name("Bàn phím cơ AKKO")
                .qty(50)
                .price(new BigDecimal("1250000.00"))
                .build();

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Bàn phím cơ AKKO")))
                .andExpect(jsonPath("$.qty", is(50)))
                .andExpect(jsonPath("$.price", is(1250000.00)));
    }

    @Test
    @DisplayName("POST /api/v1/products - Dữ liệu không hợp lệ (400 Bad Request)")
    void testCreateProduct_ValidationError() throws Exception {
        ProductRequest request = ProductRequest.builder()
                .name("") // Blank name
                .qty(-5) // Negative qty
                .price(new BigDecimal("-100.00")) // Negative price
                .build();

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name", notNullValue()))
                .andExpect(jsonPath("$.errors.qty", notNullValue()))
                .andExpect(jsonPath("$.errors.price", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/products/{id} - Lấy chi tiết sản phẩm thành công (200 OK)")
    void testGetProductById_Success() throws Exception {
        Product savedProduct = productRepository.save(Product.builder()
                .productName("Chuột không dây Logitech")
                .quantityInStock(30)
                .buyPrice(new BigDecimal("650000.00"))
                .build());

        mockMvc.perform(get("/api/v1/products/" + savedProduct.getProductCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(savedProduct.getProductCode().intValue())))
                .andExpect(jsonPath("$.name", is("Chuột không dây Logitech")))
                .andExpect(jsonPath("$.qty", is(30)))
                .andExpect(jsonPath("$.price", is(650000.00)));
    }

    @Test
    @DisplayName("GET /api/v1/products/{id} - Không tìm thấy sản phẩm (404 Not Found)")
    void testGetProductById_NotFound() throws Exception {
        mockMvc.perform(get("/api/v1/products/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Không tìm thấy sản phẩm với mã: 999999")));
    }

    @Test
    @DisplayName("GET /api/v1/products - Lấy danh sách sản phẩm phân trang (200 OK)")
    void testGetAllProducts_Success() throws Exception {
        productRepository.save(Product.builder()
                .productName("Sản phẩm 1")
                .quantityInStock(10)
                .buyPrice(new BigDecimal("100000.00"))
                .build());

        productRepository.save(Product.builder()
                .productName("Sản phẩm 2")
                .quantityInStock(20)
                .buyPrice(new BigDecimal("200000.00"))
                .build());

        mockMvc.perform(get("/api/v1/products?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements", is(2)));
    }

    @Test
    @DisplayName("PUT /api/v1/products/{id} - Cập nhật sản phẩm thành công (200 OK)")
    void testUpdateProduct_Success() throws Exception {
        Product savedProduct = productRepository.save(Product.builder()
                .productName("Tai nghe ban đầu")
                .quantityInStock(15)
                .buyPrice(new BigDecimal("500000.00"))
                .build());

        ProductRequest updateRequest = ProductRequest.builder()
                .name("Tai nghe Sony WH-1000XM5")
                .qty(25)
                .price(new BigDecimal("8000000.00"))
                .build();

        mockMvc.perform(put("/api/v1/products/" + savedProduct.getProductCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(savedProduct.getProductCode().intValue())))
                .andExpect(jsonPath("$.name", is("Tai nghe Sony WH-1000XM5")))
                .andExpect(jsonPath("$.qty", is(25)))
                .andExpect(jsonPath("$.price", is(8000000.00)));
    }

    @Test
    @DisplayName("PUT /api/v1/products/{id} - Không tìm thấy sản phẩm để cập nhật (404 Not Found)")
    void testUpdateProduct_NotFound() throws Exception {
        ProductRequest updateRequest = ProductRequest.builder()
                .name("Tai nghe mới")
                .qty(10)
                .price(new BigDecimal("100000.00"))
                .build();

        mockMvc.perform(put("/api/v1/products/888888")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Không tìm thấy sản phẩm với mã: 888888")));
    }
}
