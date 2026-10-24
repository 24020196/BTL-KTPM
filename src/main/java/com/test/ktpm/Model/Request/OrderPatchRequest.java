package com.test.ktpm.Model.Request;

import com.test.ktpm.Entity.OrderStatus;
import jakarta.validation.Valid;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderPatchRequest {

    private OrderStatus status;

    @Valid
    private List<OrderItemRequest> items;
}
