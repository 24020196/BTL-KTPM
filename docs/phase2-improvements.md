# Những điểm gợi ý cải tiến ở Phase 2

> [← Quay lại README chính](../README.md)

## 4.1. Request trùng lặp dữ liệu & Gửi lặp Request (Duplicate Request)

**Kịch bản gây lỗi:**

- Gửi lặp Request tạo đơn: khi gọi `POST /api/v1/orders`, Client gửi lặp 2 Request cùng lúc thay vì 1 Request, làm Server tạo thừa 1 đơn hàng và trừ bảng `products` 2 lần.

## 4.2. Lỗ hổng thu hồi quyền truy cập của Stateless JWT (Token Revocation Problem)

**Kịch bản gây lỗi:**

- Ở Phase 1, API `POST /api/v1/auth/login` cấp phát JWT Token theo cơ chế Stateless (Server không lưu trạng thái đăng nhập mà chỉ kiểm tra chữ ký và hạn sử dụng của Token trên Header `Authorization`).
- Nếu một tài khoản User (quyền USER) bị lộ Token hoặc nhân viên bị cho nghỉ việc đột xuất khi Token vẫn còn hạn (ví dụ: 24 giờ), người này vẫn có thể dùng Token cũ gọi `GET /api/v1/customers` xem dữ liệu khách hàng hoặc gửi Request phá hoại hệ thống cho đến khi Token tự hết hạn.

**Hướng giải quyết đề xuất:**

- Giảm thời gian sống của Access Token xuống ngắn (15-30 phút) kết hợp với Refresh Token.
- Tích hợp bộ nhớ đệm (Redis Cache) ở Phase 2 để lưu danh sách đen (Token Blacklist) hoặc kiểm tra tức thời trạng thái hoạt động của nhân viên (`ACTIVE` / `TERMINATED`) trước khi cho phép thực thi Request.

## 4.3. Lỗ hổng tự cấp quyền ADMIN khi đăng ký (Privilege Escalation)

**Kịch bản gây lỗi:**

- Ở Phase 1, API đăng ký công khai `POST /api/v1/auth/register` nhận trực tiếp trường `role` từ `RegisterRequest` của Client trong `AuthService.register`.
- Người dùng bất kỳ có thể truyền `"role": "ADMIN"` (hoặc `"ROLE_ADMIN"`) trong body khi đăng ký để tự tạo tài khoản quản trị và chiếm toàn quyền thao tác trên các API kho hàng, đơn hàng và danh sách người dùng.

**Hướng giải quyết đề xuất:**

- Loại bỏ trường `role` khỏi `RegisterRequest` và luôn gán cứng quyền mặc định `ROLE_USER` cho mọi tài khoản đăng ký qua endpoint công khai `POST /api/v1/auth/register`.
- Tách việc khởi tạo tài khoản quản trị hoặc nâng quyền lên `ROLE_ADMIN` sang API quản trị riêng.

## 4.4. Xử lý bất đồng bộ và cảnh báo tồn kho chạm ngưỡng an toàn (Asynchronous Low-Stock Alert)

**Kịch bản gây lỗi / Vấn đề hiệu năng:**

- Khi khấu trừ tồn kho lúc tạo/cập nhật đơn hàng (`POST /api/v1/orders`, `PATCH /api/v1/orders/{id}`) hoặc khi điều chỉnh kho, nếu hệ thống gửi thông báo cảnh báo ngay trong luồng giao dịch chính (chờ SMTP Server), API sẽ bị chậm và kéo dài thời gian giữ khóa bi quan `SELECT ... FOR UPDATE` trên bảng `products`, gây nghẽn các giao dịch mua hàng đồng thời; hoặc gửi cảnh báo giả nếu giao dịch bị Rollback ở bước sau.

**Hướng giải quyết đề xuất:**

- **Bổ sung ngưỡng cảnh báo tồn kho:** Thêm cột `min_stock_threshold` vào bảng `products` với ngưỡng tồn kho an toàn mặc định (ví dụ `= 5`). Khi tồn kho sau khi trừ rơi từ trên ngưỡng xuống thấp hơn hoặc bằng ngưỡng an toàn (`oldStock > min_stock_threshold` và `newStock <= min_stock_threshold`), hệ thống sẽ kích hoạt cảnh báo cho Admin (tránh gửi lặp cảnh báo cho các đơn tiếp theo khi tồn kho đã ở dưới ngưỡng).
- **Cô lập luồng giao dịch và luồng thông báo (Asynchronous Processing):** Việc gửi cảnh báo (qua Email / Log cảnh báo hệ thống) được tách rời hoàn toàn và chạy ngầm bất đồng bộ sau khi giao dịch chính đã hoàn tất (`After-Commit` thông qua `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` kết hợp `@Async`).

## 4.5. Mở rộng xử lý nghiệp vụ và tối ưu hóa hiệu năng

**Mở rộng thực thể tham gia vào hệ thống (Employee):**

- Bổ sung thực thể Employee với mối quan hệ tự tham chiếu đệ quy (`reportsTo`) để quản lý sơ đồ phân cấp báo cáo.
- Chuẩn hóa liên kết giữa Employee với Customer (nhân viên Sales Rep chăm sóc trong `POST/PUT /api/v1/customers`) và với Order (trường `employeeId` trong `POST /api/v1/orders`) nhằm thống kê KPI doanh số.

**Phân quyền chi tiết & tối ưu hóa truy vấn:**

- Liên kết tài khoản User với hồ sơ Employee để nâng cấp phân quyền từ mức ADMIN/USER sang phân quyền truy cập dữ liệu theo cấp bậc.
- Mở rộng bộ lọc tìm kiếm đa tiêu chí trên `GET /api/v1/products` và `GET /api/v1/orders` (lọc theo khoảng giá `price`, ngưỡng tồn kho `qty`, thời gian đặt hàng) kết hợp phân trang (`?page=0&size=10`).
- Tích hợp Caching (Redis) cho danh mục sản phẩm (`GET /api/v1/products`) để giảm tải đọc trực tiếp xuống Persistence Database.

**Bổ sung bảng lịch sử biến động kho (Inventory Audit Log):**

- Ghi nhận tự động mọi thay đổi về `qty` của Product (khi tạo sản phẩm `POST /api/v1/products`, điều chỉnh kho `PATCH /api/v1/products/{id}`, trừ kho khi `POST /api/v1/orders` hoặc hoàn kho khi `PATCH /api/v1/orders/{id}` sang `CANCELLED`), lưu rõ `employeeId`/User thực hiện và lý do điều chỉnh.
