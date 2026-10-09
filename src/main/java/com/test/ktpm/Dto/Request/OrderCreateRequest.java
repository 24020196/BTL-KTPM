package com.test.ktpm.Dto.Request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCreateRequest {

    @NotNull(message = "Mã khách hàng không được để trống")
    private Long customerNumber;

    @NotNull(message = "Mã nhân viên không được để trống")
    private Long employeeNumber;

    @NotEmpty(message = "Đơn hàng phải chứa ít nhất 1 sản phẩm")
    @Valid
    private List<OrderItemRequest> items;
}
