# Những điểm gợi ý cải tiến ở Phase 2

> [← Quay lại README chính](../README.md)

## 4.1. Request trùng lặp dữ liệu & Gửi lặp Request (Duplicate Request)

**Kịch bản gây lỗi:**

- Gửi lặp Request tạo đơn: khi gọi `POST /api/v1/orders`, Client gửi lặp 2 Request cùng lúc thay vì 1 Request, làm Server tạo thừa 1 đơn hàng và trừ bảng `products` 2 lần.

## 4.2. Lỗ hổng thu hồi quyền truy cập của Stateless JWT (Token Revocation Problem)

**Kịch bản gây lỗi:**

- Ở Phase 1, API `POST /api/v1/auth/login` cấp phát JWT Token theo cơ chế Stateless (Server không lưu trạng thái đăng nhập mà chỉ kiểm tra chữ ký và hạn sử dụng của Token trên Header `Authorization`).
- Nếu một tài khoản User (quyền USER) bị lộ Token hoặc nhân viên bị cho nghỉ việc đột xuất khi Token vẫn còn hạn (ví dụ: 24 giờ), người này vẫn có thể dùng Token cũ gọi `GET /api/v1/customers` xem dữ liệu khách hàng hoặc gửi Request phá hoại hệ thống cho đến khi Token tự hết hạn.

**Hướng giải quyết (kiến nghị nâng cấp):**

- Giảm thời gian sống của Access Token xuống ngắn (15-30 phút) kết hợp với Refresh Token.
- Tích hợp bộ nhớ đệm (Redis Cache) ở Phase 2 để lưu danh sách đen (Token Blacklist) hoặc kiểm tra tức thời trạng thái hoạt động của nhân viên (`ACTIVE` / `TERMINATED`) trước khi cho phép thực thi Request.

## 4.3. Mở rộng xử lý nghiệp vụ và tối ưu hóa hiệu năng

**Mở rộng thực thể tham gia vào hệ thống (Employee):**

- Bổ sung thực thể Employee với mối quan hệ tự tham chiếu đệ quy (`reportsTo`) để quản lý sơ đồ phân cấp báo cáo.
- Chuẩn hóa liên kết giữa Employee với Customer (nhân viên Sales Rep chăm sóc trong `POST/PUT /api/v1/customers`) và với Order (trường `employeeId` trong `POST /api/v1/orders`) nhằm thống kê KPI doanh số.

**Phân quyền chi tiết & tối ưu hóa truy vấn:**

- Liên kết tài khoản User với hồ sơ Employee để nâng cấp phân quyền từ mức ADMIN/USER sang phân quyền truy cập dữ liệu theo cấp bậc.
- Mở rộng bộ lọc tìm kiếm đa tiêu chí trên `GET /api/v1/products` và `GET /api/v1/orders` (lọc theo khoảng giá `price`, ngưỡng tồn kho `qty`, thời gian đặt hàng) kết hợp phân trang (`?page=0&size=10`).
- Tích hợp Caching (Redis) cho danh mục sản phẩm (`GET /api/v1/products`) để giảm tải đọc trực tiếp xuống Persistence Database.

**Bổ sung bảng lịch sử biến động kho (Inventory Audit Log):**

- Ghi nhận tự động mọi thay đổi về `qty` của Product (khi tạo sản phẩm `POST /api/v1/products`, điều chỉnh kho `PATCH /api/v1/products/{id}`, trừ kho khi `POST /api/v1/orders` hoặc hoàn kho khi `PATCH /api/v1/orders/{id}` sang `CANCELLED`), lưu rõ `employeeId`/User thực hiện và lý do điều chỉnh.

