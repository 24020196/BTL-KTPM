package com.test.ktpm.Controllers;

import com.test.ktpm.BaseIntegrationTest;
import com.test.ktpm.Entity.Customer;
import com.test.ktpm.Entity.OrderStatus;
import com.test.ktpm.Entity.Product;
import com.test.ktpm.Model.Request.OrderCreateRequest;
import com.test.ktpm.Model.Request.OrderPatchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Kiểm thử OrderController")
public class OrderControllerTest extends BaseIntegrationTest {

        @BeforeEach
        void setUp() {
                testData.cleanDatabase();
        }

        @Test
        @DisplayName("1. User tạo đơn hàng thành công -> 201 Created và tồn kho bị trừ")
        void testCreateOrder_Success() throws Exception {
                Customer customer = testData.createCustomer("Khách đặt hàng", "0909999888", null);
                Product product = testData.createDefaultProduct("Sản phẩm đơn", 20);

                OrderCreateRequest request = testData.createOrderRequest(
                                customer.getCustomerNumber(),
                                List.of(testData.createOrderItem(product.getProductCode(), 5)));

                mockMvc.perform(post("/api/v1/orders")
                                .header(HttpHeaders.AUTHORIZATION, getUserToken())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.orderNumber", notNullValue()))
                                .andExpect(jsonPath("$.status", is("PENDING")))
                                .andExpect(jsonPath("$.items", hasSize(1)));
        }

        @Test
        @DisplayName("2. User tạo đơn hàng nhưng số lượng vượt quá tồn kho -> 400 Bad Request")
        void testCreateOrder_InsufficientStock() throws Exception {
                Customer customer = testData.createCustomer("Khách mua quá số lượng", "0909999777", null);
                Product product = testData.createDefaultProduct("Sản phẩm ít hàng", 3);

                OrderCreateRequest request = testData.createOrderRequest(
                                customer.getCustomerNumber(),
                                List.of(testData.createOrderItem(product.getProductCode(), 10)));

                mockMvc.perform(post("/api/v1/orders")
                                .header(HttpHeaders.AUTHORIZATION, getUserToken())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message", containsString("không đủ")));
        }

        @Test
        @DisplayName("3. User lấy danh sách đơn hàng có phân trang -> 200 OK")
        void testGetAllOrders_Success() throws Exception {
                Customer customer = testData.createCustomer("Khách A", "0901112233", null);
                Product product = testData.createDefaultProduct("SP A", 10);
                OrderCreateRequest request = testData.createOrderRequest(
                                customer.getCustomerNumber(),
                                List.of(testData.createOrderItem(product.getProductCode(), 1)));

                // Tạo 1 đơn hàng
                mockMvc.perform(post("/api/v1/orders")
                                .header(HttpHeaders.AUTHORIZATION, getUserToken())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)));

                mockMvc.perform(get("/api/v1/orders")
                                .header(HttpHeaders.AUTHORIZATION, getUserToken())
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", hasSize(1)));
        }

        @Test
        @DisplayName("4. User lấy chi tiết đơn hàng theo ID -> 200 OK")
        void testGetOrderById_Success() throws Exception {
                Customer customer = testData.createCustomer("Khách B", "0904445566", null);
                Product product = testData.createDefaultProduct("SP B", 10);
                OrderCreateRequest request = testData.createOrderRequest(
                                customer.getCustomerNumber(),
                                List.of(testData.createOrderItem(product.getProductCode(), 2)));

                String createResponse = mockMvc.perform(post("/api/v1/orders")
                                .header(HttpHeaders.AUTHORIZATION, getUserToken())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                Long orderNumber = objectMapper.readTree(createResponse).get("orderNumber").asLong();

                mockMvc.perform(get("/api/v1/orders/" + orderNumber)
                                .header(HttpHeaders.AUTHORIZATION, getUserToken())
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.orderNumber", is(orderNumber.intValue())))
                                .andExpect(jsonPath("$.status", is("PENDING")));
        }

        @Test
        @DisplayName("5. Admin cập nhật trạng thái đơn hàng (PATCH) -> 200 OK")
        void testPatchOrder_AdminSuccess() throws Exception {
                Customer customer = testData.createCustomer("Khách Admin Sửa", "0909999666", null);
                Product product = testData.createDefaultProduct("Sản phẩm đơn sửa", 10);

                OrderCreateRequest createReq = testData.createOrderRequest(
                                customer.getCustomerNumber(),
                                List.of(testData.createOrderItem(product.getProductCode(), 2)));

                String createResponse = mockMvc.perform(post("/api/v1/orders")
                                .header(HttpHeaders.AUTHORIZATION, getUserToken())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createReq)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                Long orderNumber = objectMapper.readTree(createResponse).get("orderNumber").asLong();

                OrderPatchRequest patchReq = testData.createOrderPatchRequest(OrderStatus.SHIPPING, null);

                mockMvc.perform(patch("/api/v1/orders/" + orderNumber)
                                .header(HttpHeaders.AUTHORIZATION, getAdminToken())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(patchReq)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status", is("SHIPPING")));
        }

        @Test
        @DisplayName("6. User thường cập nhật trạng thái đơn hàng (PATCH) -> 403 Forbidden")
        void testPatchOrder_UserForbidden() throws Exception {
                Customer customer = testData.createCustomer("Khách User Sửa", "0909999555", null);
                Product product = testData.createDefaultProduct("Sản phẩm đơn cấm", 10);

                OrderCreateRequest createReq = testData.createOrderRequest(
                                customer.getCustomerNumber(),
                                List.of(testData.createOrderItem(product.getProductCode(), 2)));

                String createResponse = mockMvc.perform(post("/api/v1/orders")
                                .header(HttpHeaders.AUTHORIZATION, getUserToken())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createReq)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                Long orderNumber = objectMapper.readTree(createResponse).get("orderNumber").asLong();

                OrderPatchRequest patchReq = testData.createOrderPatchRequest(OrderStatus.CANCELLED, null);

                mockMvc.perform(patch("/api/v1/orders/" + orderNumber)
                                .header(HttpHeaders.AUTHORIZATION, getUserToken())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(patchReq)))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.status", is(403)))
                                .andExpect(jsonPath("$.error", is("Forbidden")))
                                .andExpect(jsonPath("$.message", containsString("không có quyền")));
        }
}
