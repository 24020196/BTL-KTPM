package com.test.ktpm.Services;

import com.test.ktpm.Dto.Request.EmployeeRequest;
import com.test.ktpm.Dto.Response.CustomerDto;
import com.test.ktpm.Dto.Response.EmployeeDto;
import com.test.ktpm.Dto.Response.OrderSummaryDto;
import com.test.ktpm.Dto.Response.SalesSummaryDto;
import com.test.ktpm.Entity.Customer;
import com.test.ktpm.Entity.Employee;
import com.test.ktpm.Entity.Order;
import com.test.ktpm.Entity.OrderDetail;
import com.test.ktpm.Entity.User;
import com.test.ktpm.Exceptions.BadRequestException;
import com.test.ktpm.Exceptions.ResourceNotFoundException;
import com.test.ktpm.Mapper.CustomerMapper;
import com.test.ktpm.Mapper.EmployeeMapper;
import com.test.ktpm.Mapper.OrderMapper;
import com.test.ktpm.Repositories.CustomerRepository;
import com.test.ktpm.Repositories.EmployeeRepository;
import com.test.ktpm.Repositories.OrderRepository;
import com.test.ktpm.Repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final EmployeeMapper employeeMapper;
    private final OrderMapper orderMapper;
    private final CustomerMapper customerMapper;

    @Transactional(readOnly = true)
    public List<EmployeeDto> getAllEmployees() {
        return employeeMapper.toDtoList(employeeRepository.findAll());
    }

    @Transactional(readOnly = true)
    public EmployeeDto getEmployeeById(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên với mã: " + id));
        return employeeMapper.toDto(employee);
    }

    @Transactional
    public EmployeeDto createEmployee(EmployeeRequest request) {
        Employee reportsTo = null;
        if (request.getReportsTo() != null) {
            reportsTo = employeeRepository.findById(request.getReportsTo())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người quản lý với mã: " + request.getReportsTo()));
        }

        User user = null;
        if (request.getUserId() != null) {
            user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với mã: " + request.getUserId()));
        }

        Employee employee = Employee.builder()
                .employeeName(request.getEmployeeName())
                .reportsTo(reportsTo)
                .user(user)
                .build();

        Employee savedEmployee = employeeRepository.save(employee);
        return employeeMapper.toDto(savedEmployee);
    }

    @Transactional
    public EmployeeDto updateEmployee(Long id, EmployeeRequest request) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên với mã: " + id));

        if (request.getReportsTo() != null) {
            if (request.getReportsTo().equals(id)) {
                throw new BadRequestException("Nhân viên không thể tự báo cáo cho chính mình");
            }
            Employee reportsTo = employeeRepository.findById(request.getReportsTo())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người quản lý với mã: " + request.getReportsTo()));
            employee.setReportsTo(reportsTo);
        } else {
            employee.setReportsTo(null);
        }

        if (request.getUserId() != null) {
            User user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với mã: " + request.getUserId()));
            employee.setUser(user);
        } else {
            employee.setUser(null);
        }

        employee.setEmployeeName(request.getEmployeeName());

        Employee updatedEmployee = employeeRepository.save(employee);
        return employeeMapper.toDto(updatedEmployee);
    }

    @Transactional(readOnly = true)
    public List<EmployeeDto> getSubordinates(Long id) {
        if (!employeeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy nhân viên với mã: " + id);
        }
        List<Employee> subordinates = employeeRepository.findByReportsToEmployeeNumber(id);
        return employeeMapper.toDtoList(subordinates);
    }

    @Transactional(readOnly = true)
    public Page<OrderSummaryDto> getOrdersByEmployee(Long id, Pageable pageable) {
        if (!employeeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy nhân viên với mã: " + id);
        }
        Page<Order> orders = orderRepository.findByEmployeeNumber(id, pageable);
        return orders.map(orderMapper::toSummaryDto);
    }

    @Transactional(readOnly = true)
    public SalesSummaryDto getSalesSummary(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên với mã: " + id));

        List<Order> orders = orderRepository.findAllByEmployeeNumberWithDetails(id);
        long totalOrders = orders.size();
        BigDecimal totalSales = BigDecimal.ZERO;

        for (Order order : orders) {
            if (order.getOrderDetails() != null) {
                for (OrderDetail detail : order.getOrderDetails()) {
                    if (detail.getPriceEach() != null && detail.getQuantityOrdered() != null) {
                        BigDecimal itemTotal = detail.getPriceEach().multiply(BigDecimal.valueOf(detail.getQuantityOrdered()));
                        totalSales = totalSales.add(itemTotal);
                    }
                }
            }
        }

        return SalesSummaryDto.builder()
                .employeeNumber(employee.getEmployeeNumber())
                .employeeName(employee.getEmployeeName())
                .totalOrders(totalOrders)
                .totalSales(totalSales)
                .totalAmount(totalSales)
                .build();
    }

    @Transactional(readOnly = true)
    public List<CustomerDto> getCustomersByEmployee(Long id) {
        if (!employeeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy nhân viên với mã: " + id);
        }
        List<Customer> customers = customerRepository.findBySalesRepEmployeeNumber(id);
        return customerMapper.toDtoList(customers);
    }
}
