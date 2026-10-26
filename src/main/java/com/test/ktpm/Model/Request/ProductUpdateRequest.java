package com.test.ktpm.Model.Request;

import com.test.ktpm.Entity.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductUpdateRequest {

    @Pattern(regexp = ".*\\S.*", message = "Tên sản phẩm không được để trống")
    private String name;

    @DecimalMin(value = "0", message = "Giá phải lớn hơn hoặc bằng 0")
    private BigDecimal price;

    private ProductStatus status;
}
