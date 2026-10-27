package com.test.ktpm.Controllers;

import com.test.ktpm.Model.Request.CustomerRequest;
import com.test.ktpm.Model.Response.CustomerResponse;
import com.test.ktpm.Model.Response.OrderResponse;
import com.test.ktpm.Services.CustomerService;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Tag(name = "Customers", description = "API quản lý hồ sơ khách hàng")
public class CustomerController {

    private final CustomerService customerService;

    @Operation(summary = "Lấy danh sách khách hàng")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lấy danh sách thành công")
    })
    @GetMapping
    public ResponseEntity<Page<CustomerResponse>> getAllCustomers(
            @PageableDefault(size = 10, sort = "customerNumber", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(customerService.getAllCustomers(pageable));
    }

    @Operation(summary = "Xem thông tin 1 khách hàng")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lấy chi tiết thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy khách hàng")
    })
    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> getCustomerById(@PathVariable Long id) {
        return ResponseEntity.ok(customerService.getCustomerById(id));
    }

    @Operation(summary = "Tạo mới hồ sơ khách hàng")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Tạo mới khách hàng thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu nhập vào không hợp lệ"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy User tương ứng")
    })
    @PostMapping
    public ResponseEntity<CustomerResponse> createCustomer(@Valid @RequestBody CustomerRequest request) {
        CustomerResponse createdCustomer = customerService.createCustomer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdCustomer);
    }

    @Operation(summary = "Cập nhật hồ sơ khách hàng")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cập nhật khách hàng thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu nhập vào không hợp lệ"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy khách hàng để cập nhật")
    })
    @PutMapping("/{id}")
    public ResponseEntity<CustomerResponse> updateCustomer(
            @PathVariable Long id,
            @Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.ok(customerService.updateCustomer(id, request));
    }

    @Operation(summary = "Xem lịch sử đơn hàng của khách hàng")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lấy danh sách đơn hàng thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy khách hàng")
    })
    @GetMapping("/{id}/orders")
    public ResponseEntity<List<OrderResponse>> getCustomerOrders(@PathVariable Long id) {
        return ResponseEntity.ok(customerService.getCustomerOrders(id));
    }
}