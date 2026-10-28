package com.test.ktpm.Controllers;

import com.test.ktpm.BaseIntegrationTest;
import com.test.ktpm.Entity.Customer;
import com.test.ktpm.Entity.User;
import com.test.ktpm.Model.Request.CustomerRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Kiểm thử CustomerController")
public class CustomerControllerTest extends BaseIntegrationTest {

    @BeforeEach
    void setUp() {
        testData.cleanDatabase();
    }

    @Test
    @DisplayName("1. User lấy danh sách khách hàng phân trang -> 200 OK")
    void testGetAllCustomers_Success() throws Exception {
        testData.createCustomer("Khách hàng A", "0901111111", null);
        testData.createCustomer("Khách hàng B", "0902222222", null);

        mockMvc.perform(get("/api/v1/customers")
                        .header(HttpHeaders.AUTHORIZATION, getUserToken())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    @DisplayName("2. User lấy chi tiết khách hàng theo ID -> 200 OK")
    void testGetCustomerById_Success() throws Exception {
        Customer customer = testData.createCustomer("Khách hàng C", "0903333333", null);

        mockMvc.perform(get("/api/v1/customers/" + customer.getCustomerNumber())
                        .header(HttpHeaders.AUTHORIZATION, getUserToken())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName", is("Khách hàng C")))
                .andExpect(jsonPath("$.phone", is("0903333333")));
    }

    @Test
    @DisplayName("3. Tạo mới hồ sơ khách hàng -> 201 Created")
    void testCreateCustomer_Success() throws Exception {
        User user = testData.createUser("customer_owner", "ROLE_USER");
        CustomerRequest request = testData.createCustomerRequest("Công ty ABC", "0987654321", user.getUserId());

        mockMvc.perform(post("/api/v1/customers")
                        .header(HttpHeaders.AUTHORIZATION, getUserToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerNumber", notNullValue()))
                .andExpect(jsonPath("$.customerName", is("Công ty ABC")))
                .andExpect(jsonPath("$.phone", is("0987654321")));
    }

    @Test
    @DisplayName("4. Cập nhật hồ sơ khách hàng -> 200 OK")
    void testUpdateCustomer_Success() throws Exception {
        Customer customer = testData.createCustomer("Tên Ban Đầu", "0911223344", null);

        CustomerRequest updateRequest = testData.createCustomerRequest("Tên Đã Cập Nhật", "0911223344", null);

        mockMvc.perform(put("/api/v1/customers/" + customer.getCustomerNumber())
                        .header(HttpHeaders.AUTHORIZATION, getUserToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName", is("Tên Đã Cập Nhật")));
    }
}
