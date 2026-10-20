package com.test.ktpm.Mapper;

import com.test.ktpm.Dto.Response.OrderItemResponse;
import com.test.ktpm.Dto.Response.OrderResponse;
import com.test.ktpm.Entity.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderMapper {

    private final OrderDetailMapper orderDetailMapper;

    public OrderResponse toResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getOrderDetails().stream()
                .map(orderDetailMapper::toResponse)
                .toList();

        return OrderResponse.builder()
                .orderNumber(order.getOrderNumber())
                .orderDate(order.getOrderDate())
                .customerNumber(order.getCustomer().getCustomerNumber())
                .customerName(order.getCustomer().getCustomerName())
                .status(order.getStatus())
                .items(itemResponses)
                .totalAmount(order.getTotalAmount())
                .build();
    }

    public Page<OrderResponse> toResponsePage(Page<Order> page) {
        return page.map(this::toResponse);
    }
}
