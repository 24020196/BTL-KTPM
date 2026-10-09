package com.test.ktpm.Dto.Response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesSummaryDto {
    private Long employeeNumber;
    private String employeeName;
    private Long totalOrders;
    private BigDecimal totalSales;
    private BigDecimal totalAmount;
}
