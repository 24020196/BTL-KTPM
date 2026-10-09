package com.test.ktpm.Dto.Response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerDto {
    private Long customerNumber;
    private String customerName;
    private String phone;
    private String address;
    private Long salesRepEmployeeNumber;
    private String salesRepEmployeeName;
    private Long userId;
}
