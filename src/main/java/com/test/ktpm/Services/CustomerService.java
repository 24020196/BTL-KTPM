package com.test.ktpm.Services;

import com.test.ktpm.Model.Request.CustomerRequest;
import com.test.ktpm.Model.Response.CustomerResponse;
import com.test.ktpm.Model.Response.OrderResponse;
import com.test.ktpm.Entity.Customer;
import com.test.ktpm.Entity.Order;
import com.test.ktpm.Entity.User;
import com.test.ktpm.Exceptions.ResourceNotFoundException;
import com.test.ktpm.Mapper.CustomerMapper;
import com.test.ktpm.Mapper.OrderMapper;
import com.test.ktpm.Repositories.CustomerRepository;
import com.test.ktpm.Repositories.OrderRepository;
import com.test.ktpm.Repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final CustomerMapper customerMapper;
    private final OrderMapper orderMapper;

    @Transactional(readOnly = true)
    public Page<CustomerResponse> getAllCustomers(Pageable pageable) {
        return customerRepository.findAll(pageable)
                .map(customerMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomerById(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng với mã: " + id));
        return customerMapper.toResponse(customer);
    }

    @Transactional
    public CustomerResponse createCustomer(CustomerRequest request) {
        User user = null;
        if (request.getUserId() != null) {
            user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với mã: " + request.getUserId()));
        }

        Customer customer = Customer.builder()
                .user(user)
                .customerName(request.getCustomerName())
                .phone(request.getPhone())
                .build();

        Customer savedCustomer = customerRepository.save(customer);
        return customerMapper.toResponse(savedCustomer);
    }

    @Transactional
    public CustomerResponse updateCustomer(Long id, CustomerRequest request) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng với mã: " + id));

        if (request.getUserId() != null) {
            User user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với mã: " + request.getUserId()));
            customer.setUser(user);
        } else {
            customer.setUser(null);
        }

        customer.setCustomerName(request.getCustomerName());
        customer.setPhone(request.getPhone());

        Customer updatedCustomer = customerRepository.save(customer);
        return customerMapper.toResponse(updatedCustomer);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getCustomerOrders(Long customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Không tìm thấy khách hàng với mã: " + customerId);
        }
        List<Order> orders = orderRepository.findByCustomerCustomerNumber(customerId);
        return orders.stream()
                .map(orderMapper::toResponse)
                .toList();
    }
}
