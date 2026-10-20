package com.test.ktpm.Mapper;

import com.test.ktpm.Dto.Response.OrderItemResponse;
import com.test.ktpm.Entity.OrderDetail;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class OrderDetailMapper {

    public OrderItemResponse toResponse(OrderDetail detail) {
        BigDecimal subtotal = detail.getPriceEach().multiply(BigDecimal.valueOf(detail.getQuantityOrdered()));

        return OrderItemResponse.builder()
                .productCode(detail.getProduct().getProductCode())
                .productName(detail.getProduct().getProductName())
                .quantityOrdered(detail.getQuantityOrdered())
                .priceEach(detail.getPriceEach())
                .subtotal(subtotal)
                .build();
    }
}
