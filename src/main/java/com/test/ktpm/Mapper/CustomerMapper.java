package com.test.ktpm.Mapper;

import com.test.ktpm.Dto.Response.CustomerDto;
import com.test.ktpm.Entity.Customer;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class CustomerMapper {

    public CustomerDto toDto(Customer customer) {
        if (customer == null) {
            return null;
        }

        return CustomerDto.builder()
                .customerNumber(customer.getCustomerNumber())
                .customerName(customer.getCustomerName())
                .phone(customer.getPhone())
                .address(customer.getAddress())
                .salesRepEmployeeNumber(customer.getSalesRepEmployee() != null ? customer.getSalesRepEmployee().getEmployeeNumber() : null)
                .salesRepEmployeeName(customer.getSalesRepEmployee() != null ? customer.getSalesRepEmployee().getEmployeeName() : null)
                .userId(customer.getUser() != null ? customer.getUser().getUserId() : null)
                .build();
    }

    public List<CustomerDto> toDtoList(List<Customer> customers) {
        if (customers == null) {
            return Collections.emptyList();
        }
        return customers.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }
}
