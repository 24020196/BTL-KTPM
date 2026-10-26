package com.test.ktpm.Controllers;

import com.test.ktpm.Model.Request.ProductRequest;
import com.test.ktpm.Model.Request.ProductUpdateRequest;
import com.test.ktpm.Model.Response.ProductResponse;
import com.test.ktpm.Services.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "API quản lý sản phẩm")
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "Lấy danh sách sản phẩm có phân trang")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lấy danh sách sản phẩm thành công")
    })
    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getAllProducts(
            @PageableDefault(size = 10, sort = "productCode", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(productService.getAllProducts(pageable));
    }

    @Operation(summary = "Lấy thông tin sản phẩm theo mã")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lấy thông tin sản phẩm thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy sản phẩm")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @Operation(summary = "Tạo mới sản phẩm")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Tạo mới sản phẩm thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu nhập vào không hợp lệ")
    })
    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        ProductResponse createdProduct = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdProduct);
    }

    @Operation(summary = "Cập nhật thông tin thương mại của sản phẩm")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cập nhật sản phẩm thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu nhập vào không hợp lệ"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy sản phẩm để cập nhật"),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập (Cần quyền ROLE_ADMIN)")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}")
    public ResponseEntity<ProductResponse> patchProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductUpdateRequest request) {
        return ResponseEntity.ok(productService.patchProduct(id, request));
    }
}
