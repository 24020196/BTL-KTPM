package com.test.ktpm.Entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "order_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDetail {

    @EmbeddedId
    private OrderDetailId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("orderNumber")
    @JoinColumn(name = "order_number")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("productCode")
    @JoinColumn(name = "product_code")
    private Product product;

    @Column(name = "quantity_ordered", nullable = false)
    private Integer quantityOrdered;

    @NotNull(message = "Đơn giá không được để trống")
    @Column(name = "price_each", precision = 15, scale = 2, nullable = false)
    private BigDecimal priceEach;
}
