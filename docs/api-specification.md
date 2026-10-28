# Đặc tả các API (RESTful API Specification)

> [← Quay lại README chính](../README.md)

- **Base URL:** `http://localhost:3000/api/v1`
- **Định dạng dữ liệu:** JSON
- **Cơ chế xác thực:** JWT Token (truyền qua Header `Authorization: Bearer <token>` đối với các endpoint yêu cầu đăng nhập)
- **Tài liệu tương tác (Swagger UI):** `http://localhost:3000/swagger-ui/index.html`
- **File đặc tả OpenAPI 3.1:** [`openapi.json`](./openapi.json)

## 2.1. Phân hệ Xác thực & Người dùng (Auth & Users)

Mục tiêu: Quản lý đăng ký, đăng nhập cấp phát Token và quản trị tài khoản hệ thống.

| Method | Endpoint | Role (Phân quyền) | Mô tả chức năng | Quy tắc xử lý chính |
| :--- | :--- | :--- | :--- | :--- |
| POST | `/auth/register` | Công khai | Đăng ký tài khoản mới | Kiểm tra trùng username; mã hóa password. |
| POST | `/auth/login` | Công khai | Đăng nhập hệ thống | Đối chiếu mật khẩu; sinh JWT nếu hợp lệ. |
| GET | `/users` | ADMIN | Lấy danh sách tài khoản | Chỉ dành cho quyền ADMIN; ẩn trường mật khẩu. |

## 2.2. Phân hệ Khách hàng (Customers)

Mục tiêu: Quản lý hồ sơ thông tin khách hàng và tra cứu lịch sử mua sắm.

| Method | Endpoint | Role (Phân quyền) | Mô tả chức năng | Quy tắc xử lý chính |
| :--- | :--- | :--- | :--- | :--- |
| GET | `/customers` | ADMIN, USER | Lấy danh sách khách hàng | Truy vấn danh sách khách hàng có phân trang. |
| GET | `/customers/{id}` | ADMIN, USER | Xem thông tin 1 khách hàng | Xem chi tiết liên hệ và nhân viên Sales Rep phụ trách. |
| POST | `/customers` | ADMIN | Tạo mới hồ sơ khách hàng | Lưu thông tin khách hàng và gán nhân viên chăm sóc. |
| PUT | `/customers/{id}` | ADMIN | Cập nhật hồ sơ khách hàng | Cập nhật thông tin hoặc đổi nhân viên chăm sóc. |
| GET | `/customers/{id}/orders` | ADMIN, USER | Xem lịch sử đơn hàng của KH | Liệt kê tất cả các đơn hàng khách hàng `{id}` đã đặt. |

## 2.3. Phân hệ Sản phẩm (Products)

Mục tiêu: Quản lý danh mục hàng hóa, niêm yết giá và kiểm soát số lượng tồn kho.

| Method | Endpoint | Role (Phân quyền) | Mô tả chức năng | Quy tắc xử lý chính |
| :--- | :--- | :--- | :--- | :--- |
| GET | `/products` | ADMIN, USER | Lấy danh sách sản phẩm | Lọc theo tên sản phẩm, khoảng giá hoặc trạng thái kho. |
| GET | `/products/{id}` | ADMIN, USER | Xem chi tiết sản phẩm | Truy xuất tên, đơn giá hiện tại và số lượng tồn kho. |
| POST | `/products` | ADMIN | Thêm mới sản phẩm và sửa số lượng | Ràng buộc số lượng và đơn giá. Xác thực người dùng admin. |
| PATCH | `/products/{id}` | ADMIN | Cập nhật thông tin thương mại | Cập nhật thông tin sản phẩm. |

## 2.4. Phân hệ Đơn hàng (Orders & Order Details)

Mục tiêu: Xử lý giao dịch mua bán trọn gói (gộp chung thông tin Order và Order Details).

| Method | Endpoint | Role (Phân quyền) | Mô tả chức năng | Quy tắc xử lý chính |
| :--- | :--- | :--- | :--- | :--- |
| GET | `/orders` | ADMIN, USER | Lấy danh sách đơn hàng | Hiển thị thông tin tóm tắt các đơn hàng (không kèm danh sách sản phẩm). |
| GET | `/orders/{id}` | ADMIN, USER | Xem chi tiết đơn hàng | Trả về thông tin chung kèm danh sách sản phẩm (`order_details`). |
| POST | `/orders` | ADMIN, USER | Tạo đơn hàng mới | Chạy trong 1 Transaction: kiểm tra đủ tồn kho -> tạo đơn & chi tiết đơn -> trừ kho. |
| PATCH | `/orders/{id}` | ADMIN | Cập nhật trạng thái đơn hàng | Cập nhật trạng thái một chiều (PENDING -> SHIPPING -> COMPLETED). Nếu chuyển sang CANCELLED từ PENDING/SHIPPING: chạy Transaction đổi trạng thái và cộng hoàn kho theo `order_details`. Chặn tuyệt đối nếu đơn đã COMPLETED hoặc CANCELLED. Cho phép thay đổi số lượng sản phẩm khi đơn hàng ở trạng thái PENDING. |

## 2.5. Định dạng phản hồi lỗi

```json
{
  "timestamp": "2026-10-27T00:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Sản phẩm 'A' (mã: 1) không đủ số lượng tồn kho. Hiện có: 2, yêu cầu: 5"
}
```

| Mã HTTP | Ý nghĩa |
| :--- | :--- |
| 400 | Dữ liệu không hợp lệ (kèm `errors` theo từng trường) hoặc vi phạm quy tắc nghiệp vụ |
| 401 | Sai tài khoản/mật khẩu, thiếu hoặc sai token |
| 403 | Không đủ quyền truy cập |
| 404 | Không tìm thấy tài nguyên |
| 409 | Xung đột dữ liệu do cập nhật đồng thời |

