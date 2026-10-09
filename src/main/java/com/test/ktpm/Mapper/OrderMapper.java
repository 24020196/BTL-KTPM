package com.test.ktpm.Mapper;

import com.test.ktpm.Dto.Response.OrderItemResponse;
import com.test.ktpm.Dto.Response.OrderResponse;
import com.test.ktpm.Dto.Response.OrderSummaryDto;
import com.test.ktpm.Entity.Order;
import com.test.ktpm.Entity.OrderDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderMapper {

    private final OrderDetailMapper orderDetailMapper;

    public OrderResponse toResponse(Order order) {
        if (order == null) {
            return null;
        }

        List<OrderItemResponse> itemResponses = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        if (order.getOrderDetails() != null) {
            for (OrderDetail detail : order.getOrderDetails()) {
                OrderItemResponse itemResponse = orderDetailMapper.toResponse(detail);
                itemResponses.add(itemResponse);
                total = total.add(itemResponse.getSubtotal());
            }
        }

        return OrderResponse.builder()
                .orderNumber(order.getOrderNumber())
                .orderDate(order.getOrderDate())
                .customerNumber(order.getCustomer() != null ? order.getCustomer().getCustomerNumber() : null)
                .customerName(order.getCustomer() != null ? order.getCustomer().getCustomerName() : null)
                .employeeNumber(order.getEmployee() != null ? order.getEmployee().getEmployeeNumber() : null)
                .employeeName(order.getEmployee() != null ? order.getEmployee().getEmployeeName() : null)
                .items(itemResponses)
                .totalAmount(total)
                .build();
    }

    public OrderSummaryDto toSummaryDto(Order order) {
        if (order == null) {
            return null;
        }

        BigDecimal total = BigDecimal.ZERO;
        int totalQuantity = 0;

        if (order.getOrderDetails() != null) {
            for (OrderDetail detail : order.getOrderDetails()) {
                OrderItemResponse itemResponse = orderDetailMapper.toResponse(detail);
                if (itemResponse != null && itemResponse.getSubtotal() != null) {
                    total = total.add(itemResponse.getSubtotal());
                    totalQuantity += (itemResponse.getQuantityOrdered() != null ? itemResponse.getQuantityOrdered() : 0);
                }
            }
        }

        return OrderSummaryDto.builder()
                .orderNumber(order.getOrderNumber())
                .orderDate(order.getOrderDate())
                .customerNumber(order.getCustomer() != null ? order.getCustomer().getCustomerNumber() : null)
                .customerName(order.getCustomer() != null ? order.getCustomer().getCustomerName() : null)
                .employeeNumber(order.getEmployee() != null ? order.getEmployee().getEmployeeNumber() : null)
                .employeeName(order.getEmployee() != null ? order.getEmployee().getEmployeeName() : null)
                .totalAmount(total)
                .totalItems(totalQuantity)
                .build();
    }

    public Page<OrderResponse> toResponsePage(Page<Order> page) {
        if (page == null) {
            return Page.empty();
        }
        return page.map(this::toResponse);
    }
}
