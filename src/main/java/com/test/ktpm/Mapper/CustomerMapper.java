package com.test.ktpm.Mapper;

import com.test.ktpm.Dto.Response.CustomerResponse;
import com.test.ktpm.Entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    public CustomerResponse toResponse(Customer customer) {
        if (customer == null) {
            return null;
        }

        return CustomerResponse.builder()
                .customerNumber(customer.getCustomerNumber())
                .customerName(customer.getCustomerName())
                .phone(customer.getPhone())
                .address(customer.getAddress())
                .salesRepEmployeeNumber(
                        customer.getSalesRepEmployee() != null ? customer.getSalesRepEmployee().getEmployeeNumber()
                                : null)
                .salesRepEmployeeName(
                        customer.getSalesRepEmployee() != null ? customer.getSalesRepEmployee().getEmployeeName()
                                : null)
                .build();
    }

    public Page<CustomerResponse> toResponsePage(Page<Customer> page) {
        if (page == null) {
            return Page.empty();
        }
        return page.map(this::toResponse);
    }
}