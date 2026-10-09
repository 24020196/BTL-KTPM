package com.test.ktpm.Dto.Request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemRequest {

    @NotNull(message = "Mã sản phẩm không được để trống")
    private Long productCode;

    @NotNull(message = "Số lượng mua không được để trống")
    @Min(value = 1, message = "Số lượng mua tối thiểu là 1")
    private Integer quantityOrdered;
}
