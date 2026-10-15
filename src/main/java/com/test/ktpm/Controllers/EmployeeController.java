package com.test.ktpm.Controllers;

import com.test.ktpm.Dto.Request.EmployeeRequest;
import com.test.ktpm.Dto.Response.CustomerResponse;
import com.test.ktpm.Dto.Response.EmployeeDto;
import com.test.ktpm.Dto.Response.OrderSummaryDto;
import com.test.ktpm.Dto.Response.SalesSummaryDto;
import com.test.ktpm.Services.EmployeeService;
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
@RequestMapping({"/api/v1/employees", "/employees"})
@RequiredArgsConstructor
@Tag(name = "Employees", description = "API quản lý nhân viên")
public class EmployeeController {

    private final EmployeeService employeeService;

    @Operation(summary = "Lấy danh sách nhân viên")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lấy danh sách thành công")
    })
    @GetMapping
    public ResponseEntity<List<EmployeeDto>> getAllEmployees() {
        return ResponseEntity.ok(employeeService.getAllEmployees());
    }

    @Operation(summary = "Xem chi tiết 1 nhân viên")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lấy thông tin nhân viên thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy nhân viên")
    })
    @GetMapping("/{id}")
    public ResponseEntity<EmployeeDto> getEmployeeById(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.getEmployeeById(id));
    }

    @Operation(summary = "Thêm mới nhân viên")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Thêm mới nhân viên thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy người quản lý hoặc tài khoản người dùng liên kết")
    })
    @PostMapping
    public ResponseEntity<EmployeeDto> createEmployee(@Valid @RequestBody EmployeeRequest request) {
        EmployeeDto createdEmployee = employeeService.createEmployee(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdEmployee);
    }

    @Operation(summary = "Cập nhật thông tin nhân viên")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cập nhật nhân viên thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy nhân viên, người quản lý hoặc tài khoản người dùng")
    })
    @PutMapping("/{id}")
    public ResponseEntity<EmployeeDto> updateEmployee(@PathVariable Long id, @Valid @RequestBody EmployeeRequest request) {
        return ResponseEntity.ok(employeeService.updateEmployee(id, request));
    }

    @Operation(summary = "Xem các nhân viên cấp dưới (Reports To)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lấy danh sách nhân viên cấp dưới thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy nhân viên")
    })
    @GetMapping("/{id}/subordinates")
    public ResponseEntity<List<EmployeeDto>> getSubordinates(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.getSubordinates(id));
    }

    @Operation(summary = "Lấy danh sách đơn hàng do nhân viên phụ trách")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lấy danh sách đơn hàng thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy nhân viên")
    })
    @GetMapping("/{id}/orders")
    public ResponseEntity<Page<OrderSummaryDto>> getOrdersByEmployee(
            @PathVariable Long id,
            @PageableDefault(size = 10, page = 0, sort = "orderNumber", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(employeeService.getOrdersByEmployee(id, pageable));
    }

    @Operation(summary = "Thống kê doanh số của nhân viên (tổng đơn, tổng tiền)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Thống kê doanh số thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy nhân viên")
    })
    @GetMapping("/{id}/sales")
    public ResponseEntity<SalesSummaryDto> getSalesSummary(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.getSalesSummary(id));
    }

    @Operation(summary = "Xem danh sách khách hàng mà nhân viên này chăm sóc (Sales Rep)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lấy danh sách khách hàng thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy nhân viên")
    })
    @GetMapping("/{id}/customers")
    public ResponseEntity<List<CustomerResponse>> getCustomersByEmployee(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.getCustomersByEmployee(id));
    }
}
