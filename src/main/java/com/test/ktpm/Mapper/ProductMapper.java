package com.test.ktpm.Mapper;

import com.test.ktpm.Model.Request.ProductRequest;
import com.test.ktpm.Model.Response.ProductResponse;
import com.test.ktpm.Entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class ProductMapper {

    public Product toEntity(ProductRequest request) {
        if (request == null) {
            return null;
        }
        return Product.builder()
                .productName(request.getName())
                .quantityInStock(request.getQty())
                .buyPrice(request.getPrice())
                .build();
    }

    public ProductResponse toResponse(Product product) {
        if (product == null) {
            return null;
        }
        return ProductResponse.builder()
                .id(product.getProductCode())
                .name(product.getProductName())
                .qty(product.getQuantityInStock())
                .price(product.getBuyPrice())
                .build();
    }

    public void updateEntity(Product product, ProductRequest request) {
        if (product == null || request == null) {
            return;
        }
        product.setProductName(request.getName());
        product.setQuantityInStock(request.getQty());
        product.setBuyPrice(request.getPrice());
    }

    public Page<ProductResponse> toResponsePage(Page<Product> page) {
        if (page == null) {
            return Page.empty();
        }
        return page.map(this::toResponse);
    }

    public List<ProductResponse> toResponseList(List<Product> products) {
        if (products == null) {
            return Collections.emptyList();
        }
        return products.stream().map(this::toResponse).toList();
    }
}
