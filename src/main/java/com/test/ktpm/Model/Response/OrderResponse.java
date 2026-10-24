package com.test.ktpm.Model.Response;

import com.test.ktpm.Entity.OrderStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {
    private Long orderNumber;
    private LocalDateTime orderDate;
    private Long customerNumber;
    private String customerName;
    private OrderStatus status;
    private List<OrderItemResponse> items;
    private BigDecimal totalAmount;
}
