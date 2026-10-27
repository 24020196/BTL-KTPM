package com.test.ktpm.Model.Response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerResponse {
    private Long customerNumber;
    private Long userId;
    private String customerName;
    private String phone;
}