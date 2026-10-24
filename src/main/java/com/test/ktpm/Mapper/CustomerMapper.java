package com.test.ktpm.Mapper;

import com.test.ktpm.Model.Response.CustomerResponse;
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
                .userId(customer.getUser() != null ? customer.getUser().getUserId() : null)
                .build();
    }

    public Page<CustomerResponse> toResponsePage(Page<Customer> page) {
        if (page == null) {
            return Page.empty();
        }
        return page.map(this::toResponse);
    }
}
