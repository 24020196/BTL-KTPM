package com.test.ktpm.Model.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerRequest {

    @NotBlank(message = "Tên khách hàng không được để trống")
    private String customerName;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Size(max = 20, message = "Số điện thoại không quá 20 ký tự")
    private String phone;

    @NotBlank(message = "Địa chỉ không được để trống")
    private String address;

    private Long userId;
    private Long salesRepEmployeeNumber;
}