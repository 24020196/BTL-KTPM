package com.test.ktpm;

import com.test.ktpm.Entity.Customer;
import com.test.ktpm.Entity.OrderStatus;
import com.test.ktpm.Entity.Product;
import com.test.ktpm.Entity.ProductStatus;
import com.test.ktpm.Entity.User;
import com.test.ktpm.Model.Request.*;
import com.test.ktpm.Repositories.*;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@Getter
public class TestDataHelper {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderDetailRepository orderDetailRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Dọn dẹp toàn bộ dữ liệu trong Database theo đúng thứ tự ràng buộc khóa ngoại (FK)
     */
    public void cleanDatabase() {
        orderDetailRepository.deleteAll();
        orderRepository.deleteAll();
        customerRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ==================== USER DATA ====================

    public User createUser(String username, String role) {
        return userRepository.findByUsername(username).orElseGet(() ->
                userRepository.save(User.builder()
                        .username(username)
                        .password(passwordEncoder.encode("password123"))
                        .role(role)
                        .build())
        );
    }

    public User createAdminUser() {
        return createUser("test_admin", "ROLE_ADMIN");
    }

    public User createRegularUser() {
        return createUser("test_user", "ROLE_USER");
    }

    // ==================== PRODUCT DATA ====================

    public Product createProduct(String name, Integer quantity, BigDecimal price, ProductStatus status) {
        return productRepository.save(Product.builder()
                .productName(name)
                .quantityInStock(quantity)
                .buyPrice(price)
                .status(status != null ? status : ProductStatus.ACTIVE)
                .build());
    }

    public Product createDefaultProduct(String name, Integer quantity) {
        return createProduct(name, quantity, new BigDecimal("100000.00"), ProductStatus.ACTIVE);
    }

    public ProductRequest createProductRequest(String name, Integer qty, BigDecimal price) {
        return ProductRequest.builder()
                .name(name)
                .qty(qty)
                .price(price)
                .build();
    }

    public ProductPatchRequest createProductPatchRequest(String name, BigDecimal price, ProductStatus status) {
        return ProductPatchRequest.builder()
                .name(name)
                .price(price)
                .status(status)
                .build();
    }

    // ==================== CUSTOMER DATA ====================

    public Customer createCustomer(String name, String phone, User user) {
        return customerRepository.save(Customer.builder()
                .customerName(name)
                .phone(phone)
                .user(user)
                .build());
    }

    public CustomerRequest createCustomerRequest(String name, String phone, Long userId) {
        return CustomerRequest.builder()
                .customerName(name)
                .phone(phone)
                .userId(userId)
                .build();
    }

    // ==================== ORDER DATA ====================

    public OrderItemRequest createOrderItem(Long productCode, Integer quantity) {
        return OrderItemRequest.builder()
                .productCode(productCode)
                .quantityOrdered(quantity)
                .build();
    }

    public OrderCreateRequest createOrderRequest(Long customerNumber, List<OrderItemRequest> items) {
        return OrderCreateRequest.builder()
                .customerNumber(customerNumber)
                .items(items)
                .build();
    }

    public OrderPatchRequest createOrderPatchRequest(OrderStatus status, List<OrderItemRequest> items) {
        return OrderPatchRequest.builder()
                .status(status)
                .items(items)
                .build();
    }

    // ==================== AUTH REQUESTS ====================

    public RegisterRequest createRegisterRequest(String username, String password) {
        return RegisterRequest.builder()
                .username(username)
                .password(password)
                .build();
    }

    public LoginRequest createLoginRequest(String username, String password) {
        return LoginRequest.builder()
                .username(username)
                .password(password)
                .build();
    }
}

