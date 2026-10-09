package com.test.ktpm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.ktpm.Dto.Request.EmployeeRequest;
import com.test.ktpm.Entity.Customer;
import com.test.ktpm.Entity.Employee;
import com.test.ktpm.Entity.Order;
import com.test.ktpm.Entity.OrderDetail;
import com.test.ktpm.Entity.OrderDetailId;
import com.test.ktpm.Entity.Product;
import com.test.ktpm.Repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class EmployeeControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderDetailRepository orderDetailRepository;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        orderDetailRepository.deleteAll();
        orderRepository.deleteAll();
        customerRepository.deleteAll();
        employeeRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    void testCreateAndGetEmployee() throws Exception {
        EmployeeRequest request = EmployeeRequest.builder()
                .employeeName("Nguyen Van A")
                .build();

        String responseJson = mockMvc.perform(post("/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employeeName", is("Nguyen Van A")))
                .andExpect(jsonPath("$.employeeNumber", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        Long employeeId = objectMapper.readTree(responseJson).get("employeeNumber").asLong();

        // Test GET /employees/{id}
        mockMvc.perform(get("/employees/" + employeeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeNumber", is(employeeId.intValue())))
                .andExpect(jsonPath("$.employeeName", is("Nguyen Van A")));

        // Test GET /employees
        mockMvc.perform(get("/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].employeeName", is("Nguyen Van A")));

        // Also test with /api/v1 prefix
        mockMvc.perform(get("/api/v1/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void testUpdateEmployee() throws Exception {
        Employee manager = employeeRepository.save(Employee.builder().employeeName("Manager").build());
        Employee staff = employeeRepository.save(Employee.builder().employeeName("Staff").build());

        EmployeeRequest updateRequest = EmployeeRequest.builder()
                .employeeName("Staff Updated")
                .reportsTo(manager.getEmployeeNumber())
                .build();

        mockMvc.perform(put("/employees/" + staff.getEmployeeNumber())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeName", is("Staff Updated")))
                .andExpect(jsonPath("$.reportsTo", is(manager.getEmployeeNumber().intValue())))
                .andExpect(jsonPath("$.reportsToName", is("Manager")));
    }

    @Test
    void testGetSubordinates() throws Exception {
        Employee manager = employeeRepository.save(Employee.builder().employeeName("Manager").build());
        employeeRepository.save(Employee.builder().employeeName("Subordinate 1").reportsTo(manager).build());
        employeeRepository.save(Employee.builder().employeeName("Subordinate 2").reportsTo(manager).build());

        mockMvc.perform(get("/employees/" + manager.getEmployeeNumber() + "/subordinates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].employeeName", containsInAnyOrder("Subordinate 1", "Subordinate 2")));
    }

    @Test
    void testGetOrdersAndSales() throws Exception {
        Employee employee = employeeRepository.save(Employee.builder().employeeName("Sales Employee").build());
        Customer customer = customerRepository.save(Customer.builder()
                .customerName("Customer X")
                .phone("0123456789")
                .address("Hanoi")
                .salesRepEmployee(employee)
                .build());

        Product product = productRepository.save(Product.builder()
                .productName("Laptop")
                .quantityInStock(10)
                .buyPrice(new BigDecimal("1000.00"))
                .build());

        Order order = Order.builder()
                .customer(customer)
                .employee(employee)
                .orderDate(LocalDateTime.now())
                .build();
        order = orderRepository.save(order);

        OrderDetail orderDetail = OrderDetail.builder()
                .id(OrderDetailId.builder().orderNumber(order.getOrderNumber()).productCode(product.getProductCode()).build())
                .order(order)
                .product(product)
                .quantityOrdered(2)
                .priceEach(new BigDecimal("1000.00"))
                .build();
        orderDetailRepository.save(orderDetail);

        // Test GET /employees/{id}/orders
        mockMvc.perform(get("/employees/" + employee.getEmployeeNumber() + "/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].totalAmount", is(2000.0)))
                .andExpect(jsonPath("$.content[0].totalItems", is(2)));

        // Test GET /employees/{id}/sales
        mockMvc.perform(get("/employees/" + employee.getEmployeeNumber() + "/sales"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeNumber", is(employee.getEmployeeNumber().intValue())))
                .andExpect(jsonPath("$.totalOrders", is(1)))
                .andExpect(jsonPath("$.totalSales", is(2000.0)));

        // Test GET /employees/{id}/customers
        mockMvc.perform(get("/employees/" + employee.getEmployeeNumber() + "/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].customerName", is("Customer X")))
                .andExpect(jsonPath("$[0].salesRepEmployeeNumber", is(employee.getEmployeeNumber().intValue())));
    }
}
