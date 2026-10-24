package com.test.ktpm.Controllers;

import com.test.ktpm.Model.Request.LoginRequest;
import com.test.ktpm.Model.Request.RegisterRequest;
import com.test.ktpm.Model.Response.AuthResponse;
import com.test.ktpm.Services.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "API xác thực và phân quyền người dùng")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Đăng nhập hệ thống")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Đăng nhập thành công, trả về JWT Token"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu đầu vào không hợp lệ"),
            @ApiResponse(responseCode = "401", description = "Tên đăng nhập hoặc mật khẩu không chính xác")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "Đăng ký tài khoản người dùng")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Đăng ký tài khoản thành công"),
            @ApiResponse(responseCode = "400", description = "Tên đăng nhập đã tồn tại hoặc dữ liệu không hợp lệ")
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
