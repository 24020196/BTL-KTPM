package com.test.ktpm.Dto.Response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderSummaryDto {
    private Long orderNumber;
    private LocalDateTime orderDate;
    private Long customerNumber;
    private String customerName;
    private Long employeeNumber;
    private String employeeName;
    private BigDecimal totalAmount;
    private Integer totalItems;
}
