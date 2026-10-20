package com.test.ktpm.Entity;

import com.test.ktpm.Exceptions.BadRequestException;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_code")
    private Long productCode;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "quantity_in_stock", nullable = false)
    @Builder.Default
    private Integer quantityInStock = 0;

    @Column(name = "buy_price", precision = 15, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal buyPrice = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ProductStatus status = ProductStatus.ACTIVE;

    public void decreaseStock(int quantity) {
        if (this.quantityInStock < quantity) {
            throw new BadRequestException("Sản phẩm '" + this.productName + "' (mã: " + this.productCode +
                    ") không đủ số lượng tồn kho. Hiện có: " + this.quantityInStock +
                    ", yêu cầu: " + quantity);
        }
        this.quantityInStock -= quantity;
    }

    public void increaseStock(int quantity) {
        this.quantityInStock += quantity;
    }
}
