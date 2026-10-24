package com.test.ktpm.Model.Response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemResponse {
    private Long productCode;
    private String productName;
    private Integer quantityOrdered;
    private BigDecimal priceEach;
    private BigDecimal subtotal;
}
