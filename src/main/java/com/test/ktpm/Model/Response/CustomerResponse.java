package com.test.ktpm.Model.Response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerResponse {
    private Long customerNumber;
    private String customerName;
    private String phone;
    private String address;
    private Long userId;
    private Long salesRepEmployeeNumber;
    private String salesRepEmployeeName;
}