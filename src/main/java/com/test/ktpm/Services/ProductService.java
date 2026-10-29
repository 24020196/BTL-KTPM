package com.test.ktpm.Services;

import com.test.ktpm.Model.Request.ProductRequest;
import com.test.ktpm.Model.Request.ProductPatchRequest;
import com.test.ktpm.Model.Response.ProductResponse;
import com.test.ktpm.Entity.Product;
import com.test.ktpm.Exceptions.BadRequestException;
import com.test.ktpm.Exceptions.ResourceNotFoundException;
import com.test.ktpm.Mapper.ProductMapper;
import com.test.ktpm.Repositories.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Transactional(readOnly = true)
    public Page<ProductResponse> getAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable)
                .map(productMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với mã: " + id));
        return productMapper.toResponse(product);
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        String trimmedName = request.getName() != null ? request.getName().trim() : "";
        Optional<Product> existingOpt = productRepository.findFirstByProductName(trimmedName);

        if (existingOpt.isPresent() && existingOpt.get().getProductName().equals(trimmedName)) {
            Product existingProduct = existingOpt.get();
            existingProduct.setQuantityInStock(existingProduct.getQuantityInStock() + request.getQty());
            if (request.getPrice() != null && existingProduct.getBuyPrice().compareTo(request.getPrice()) != 0) {
                existingProduct.setBuyPrice(request.getPrice());
            }
            Product savedProduct = productRepository.save(existingProduct);
            return productMapper.toResponse(savedProduct);
        }

        Product product = productMapper.toEntity(request);
        Product savedProduct = productRepository.save(product);
        return productMapper.toResponse(savedProduct);
    }

    @Transactional
    public ProductResponse patchProduct(Long id, ProductPatchRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với mã: " + id));

        productMapper.patchEntity(product, request);
        Product updatedProduct = productRepository.save(product);
        return productMapper.toResponse(updatedProduct);
    }

    @Transactional
    public void decreaseStock(Product product, int quantity) {
        if (product.getQuantityInStock() < quantity) {
            throw new BadRequestException("Sản phẩm '" + product.getProductName() + "' (mã: " + product.getProductCode() +
                    ") không đủ số lượng tồn kho. Hiện có: " + product.getQuantityInStock() +
                    ", yêu cầu: " + quantity);
        }
        product.setQuantityInStock(product.getQuantityInStock() - quantity);
        productRepository.save(product);
    }

    @Transactional
    public void increaseStock(Product product, int quantity) {
        product.setQuantityInStock(product.getQuantityInStock() + quantity);
        productRepository.save(product);
    }
}
