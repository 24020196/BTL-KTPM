package com.test.ktpm.Controllers;

import com.test.ktpm.Model.Request.OrderCreateRequest;
import com.test.ktpm.Model.Request.OrderPatchRequest;
import com.test.ktpm.Model.Response.OrderResponse;
import com.test.ktpm.Services.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "API quản lý đơn hàng")
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "Lấy danh sách đơn hàng có phân trang")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lấy danh sách thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực (Unauthorized)")
    })
    @GetMapping
    public ResponseEntity<Page<OrderResponse>> getAllOrders(
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(orderService.getAllOrders(pageable));
    }

    @Operation(summary = "Lấy chi tiết đơn hàng theo mã đơn hàng")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lấy chi tiết đơn hàng thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực (Unauthorized)"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy đơn hàng")
    })
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    @Operation(summary = "Tạo mới một đơn hàng")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Tạo đơn hàng thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ hoặc sản phẩm không đủ tồn kho"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực (Unauthorized)"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy khách hàng hoặc sản phẩm")
    })
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderCreateRequest request) {
        OrderResponse createdOrder = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdOrder);
    }

    @Operation(summary = "Cập nhật một phần thông tin đơn hàng (Partial Update)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cập nhật đơn hàng thành công"),
            @ApiResponse(responseCode = "400", description = "Không thể cập nhật thông tin khi đơn hàng không ở trạng thái PENDING"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực (Unauthorized)"),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập (Cần quyền ROLE_ADMIN)"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy đơn hàng")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}")
    public ResponseEntity<OrderResponse> patchOrder(
            @PathVariable Long id,
            @Valid @RequestBody OrderPatchRequest request) {
        return ResponseEntity.ok(orderService.patchOrder(id, request));
    }
}
