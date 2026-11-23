package org.example.dto;

import jakarta.validation.constraints.NotNull;
import org.example.entity.OrderStatus;

public record StatusRequest(
        @NotNull(message = "status là bắt buộc")
        OrderStatus status) {
}
