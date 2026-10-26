# KTPM - Hệ thống Quản lý Kho hàng, Khách hàng và Đơn hàng

## Mục lục

- [Phần 1: Mô tả bài toán và nghiệp vụ cơ bản](#phần-1-mô-tả-bài-toán-và-nghiệp-vụ-cơ-bản)
- [Phần 2: Đặc tả các API (RESTful API Specification)](#phần-2-đặc-tả-các-api-restful-api-specification)
- [Phần 3: Một số vấn đề của hệ thống (đã xử lý ở Phase 1)](#phần-3-một-số-vấn-đề-của-hệ-thống-đã-xử-lý-ở-phase-1)
- [Phần 4: Những điểm gợi ý cải tiến ở Phase 2](#phần-4-những-điểm-gợi-ý-cải-tiến-ở-phase-2)
- [Phần 5: Tổ chức mã nguồn](#phần-5-tổ-chức-mã-nguồn)
- [Phần 6: Cài đặt và triển khai (Deploy)](#phần-6-cài-đặt-và-triển-khai-deploy)

---

## PHẦN 1: MÔ TẢ BÀI TOÁN VÀ NGHIỆP VỤ CƠ BẢN

### 1.1. Tổng quan bài toán

Hệ thống cung cấp nền tảng quản lý kho hàng, quan hệ khách hàng thông qua giao thức RESTful API. Mục tiêu cốt lõi là số hóa quy trình vận hành thương mại, chăm sóc khách hàng, kiểm soát hàng hóa và xử lý giao dịch đơn hàng.

### 1.2. Mô hình thực thể cốt lõi

| Thực thể | Vai trò trong hệ thống | Mối quan hệ nghiệp vụ |
| :--- | :--- | :--- |
| 1. User | Tài khoản định danh truy cập hệ thống. | Phân quyền người dùng (ADMIN / USER), xác thực qua JWT. |
| 2. Customer | Hồ sơ khách hàng mua sắm. | Lưu trữ thông tin liên hệ và sở hữu lịch sử nhiều đơn hàng. |
| 3. Product | Danh mục hàng hóa thương mại. | Quản lý đơn giá và tồn kho; tham chiếu trong chi tiết đơn hàng. |
| 4. Order & Order Details | Giao dịch mua bán. | Kết nối Customer và danh sách Product trong một giao dịch duy nhất. |

### 1.3. Quy trình và quy tắc nghiệp vụ cơ bản

#### a. Nghiệp vụ Xác thực & Phân quyền (Auth & Security)

- Mật khẩu người dùng phải được mã hóa một chiều khi đăng ký.
- Sử dụng cơ chế Stateless Authentication qua JWT Token cấp phát sau khi đăng nhập thành công.
- Các API quản trị (như xem danh sách tài khoản) yêu cầu quyền ADMIN.

#### b. Nghiệp vụ Quản lý khách hàng

- Kiểm soát tính duy nhất của Email và Số điện thoại trên toàn hệ thống khi tạo mới cũng như khi cập nhật hồ sơ.
- Hỗ trợ tra cứu lịch sử đơn hàng theo từng khách hàng.

#### c. Nghiệp vụ Quản lý Tồn kho

- **Bảo toàn lịch sử và trạng thái kinh doanh:** Sản phẩm không sử dụng cơ chế xóa khỏi CSDL.
- Mỗi sản phẩm có trạng thái hoạt động (`ACTIVE` - Đang kinh doanh, `INACTIVE` - Ngừng kinh doanh). Chỉ các sản phẩm `ACTIVE` mới được phép giao dịch.
- **Cô lập nghiệp vụ sửa thông tin và điều chỉnh kho:** Việc cập nhật thông tin định danh (tên, giá bán, trạng thái) được tách rời hoàn toàn khỏi việc cập nhật số lượng tồn kho. Tồn kho chỉ biến động qua các luồng duy nhất:
  1. Khởi tạo sản phẩm ban đầu.
  2. Điều chỉnh kiểm kê/nhập hàng tăng giảm theo lượng chênh lệch.
  3. Khấu trừ tự động khi tạo đơn hàng và hoàn trả tự động khi hủy đơn.

#### d. Nghiệp vụ Xử lý Giao dịch Đơn hàng

**Toàn vẹn giao dịch:** Order và OrderDetails được xử lý trong một Transaction duy nhất.

**Quy trình khởi tạo đơn và khấu trừ kho (`POST /orders`):**

1. **Chuẩn hóa giỏ hàng:** Kiểm tra đơn hàng không rỗng, số lượng mỗi sản phẩm lớn hơn 0. Tự động cộng dồn số lượng nếu có các `productId` trùng lặp trong cùng một body.
2. **Kiểm tra điều kiện ràng buộc:** Toàn bộ sản phẩm trong đơn hàng phải đang ở trạng thái `ACTIVE` và có số lượng tồn kho hiện tại lớn hơn hoặc bằng số lượng đặt mua. Nếu bất kỳ sản phẩm nào vi phạm, hủy toàn bộ giao dịch.
3. **Ghi nhận và trừ kho:** Khấu trừ tồn kho tương ứng, lưu đơn giá tại thời điểm mua vào `order_details`, tính tổng tiền và tạo đơn ở trạng thái `PENDING`.

**Quy tắc chuyển đổi trạng thái và hoàn kho:**

Đơn hàng tuân thủ nghiêm ngặt luồng trạng thái một chiều (không cho phép lùi trạng thái):

| Trạng thái hiện tại | Được phép chuyển sang |
| :--- | :--- |
| `PENDING` | `SHIPPING` hoặc `CANCELLED` |
| `SHIPPING` | `COMPLETED` hoặc `CANCELLED` |
| `COMPLETED` / `CANCELLED` | Khóa hoàn toàn, chặn mọi thao tác thay đổi trạng thái |

Khi chuyển sang `CANCELLED` (từ `PENDING` hoặc `SHIPPING`), hệ thống thực thi Transaction: cập nhật trạng thái đơn sang `CANCELLED` và tự động cộng hoàn trả số lượng từng mặt hàng trong đơn về lại tồn kho của sản phẩm tương ứng (ngay cả khi sản phẩm đó hiện đã chuyển sang `INACTIVE`).

---

## PHẦN 2: ĐẶC TẢ CÁC API (RESTFUL API SPECIFICATION)

- **Base URL:** `http://localhost:3000/api/v1`
- **Định dạng dữ liệu:** JSON
- **Cơ chế xác thực:** JWT Token (truyền qua Header `Authorization: Bearer <token>` đối với các endpoint yêu cầu đăng nhập)
- **Tài liệu tương tác (Swagger UI):** `http://localhost:3000/swagger-ui/index.html`

### 2.1. Phân hệ Xác thực & Người dùng (Auth & Users)

Mục tiêu: Quản lý đăng ký, đăng nhập cấp phát Token và quản trị tài khoản hệ thống.

| Method | Endpoint | Mô tả chức năng | Request Body / Params | Response | Quy tắc xử lý chính |
| :--- | :--- | :--- | :--- | :--- | :--- |
| POST | `/auth/register` | Đăng ký tài khoản mới | `RegisterRequest` (username, password) | 201 Created - Message thành công | Kiểm tra trùng username; mã hóa password. |
| POST | `/auth/login` | Đăng nhập hệ thống | `LoginRequest` (username, password) | 200 OK - Chuỗi JWT Token | Đối chiếu mật khẩu; sinh JWT nếu hợp lệ. |
| GET | `/users` | Lấy danh sách tài khoản | None | 200 OK - `List<UserDto>` | Chỉ dành cho quyền ADMIN; ẩn trường mật khẩu. |

### 2.2. Phân hệ Khách hàng (Customers)

Mục tiêu: Quản lý hồ sơ thông tin khách hàng và tra cứu lịch sử mua sắm.

| Method | Endpoint | Mô tả chức năng | Request Body / Params | Response | Quy tắc xử lý chính |
| :--- | :--- | :--- | :--- | :--- | :--- |
| GET | `/customers` | Lấy danh sách khách hàng | Query: `?page=0&size=10` | 200 OK - `Page<CustomerDto>` | Truy vấn danh sách khách hàng có phân trang. |
| GET | `/customers/{id}` | Xem thông tin 1 khách hàng | Path: `id` | 200 OK - `CustomerDto` | Xem chi tiết liên hệ và nhân viên Sales Rep phụ trách. |
| POST | `/customers` | Tạo mới hồ sơ khách hàng | `CustomerRequest` | 201 Created - `CustomerDto` | Lưu thông tin khách hàng và gán nhân viên chăm sóc. |
| PUT | `/customers/{id}` | Cập nhật hồ sơ khách hàng | Path: `id`; Body: `CustomerRequest` | 200 OK - `CustomerDto` | Cập nhật thông tin hoặc đổi nhân viên chăm sóc. |
| GET | `/customers/{id}/orders` | Xem lịch sử đơn hàng của KH | Path: `id` | 200 OK - `List<OrderResponseDto>` | Liệt kê tất cả các đơn hàng khách hàng `{id}` đã đặt. |

### 2.3. Phân hệ Sản phẩm (Products)

Mục tiêu: Quản lý danh mục hàng hóa, niêm yết giá và kiểm soát số lượng tồn kho.

| Method | Endpoint | Mô tả chức năng | Request Body / Params | Response | Quy tắc xử lý chính |
| :--- | :--- | :--- | :--- | :--- | :--- |
| GET | `/products` | Lấy danh sách sản phẩm | Query: `?page=0&size=10` | 200 OK - `List<ProductDto>` | Lọc theo tên sản phẩm, khoảng giá hoặc trạng thái kho. |
| GET | `/products/{id}` | Xem chi tiết sản phẩm | Path: `id` | 200 OK - `ProductDto` | Truy xuất tên, đơn giá hiện tại và số lượng tồn kho. |
| POST | `/products` | Thêm mới sản phẩm và sửa số lượng | `ProductRequest` (name, qty, price) | 201 Created - `ProductDto` | Ràng buộc số lượng và đơn giá. Xác thực người dùng admin. |
| PATCH | `/products/{id}` | Cập nhật thông tin thương mại | Path: `id`; Body: `ProductUpdateRequest` (name, price, status) | 200 OK - `ProductDto` | Cập nhật thông tin sản phẩm. |

### 2.4. Phân hệ Đơn hàng (Orders & Order Details)

Mục tiêu: Xử lý giao dịch mua bán trọn gói (gộp chung thông tin Order và Order Details).

| Method | Endpoint | Mô tả chức năng | Request Body / Params | Response | Quy tắc xử lý chính |
| :--- | :--- | :--- | :--- | :--- | :--- |
| GET | `/orders` | Lấy danh sách đơn hàng | Query: `?page=0&size=10` | 200 OK - `List<OrderSummaryDto>` | Hiển thị thông tin tóm tắt các đơn hàng (không kèm danh sách sản phẩm). |
| GET | `/orders/{id}` | Xem chi tiết đơn hàng | Path: `id` | 200 OK - `OrderDetailResponse` | Trả về thông tin chung kèm danh sách sản phẩm (`order_details`). |
| POST | `/orders` | Tạo đơn hàng mới | `OrderRequest` (customerId, employeeId, `List<Items>`) | 201 Created - `OrderDetailResponse` | Chạy trong 1 Transaction: kiểm tra đủ tồn kho -> tạo đơn & chi tiết đơn -> trừ kho. |
| PATCH | `/orders/{id}` | Cập nhật trạng thái đơn hàng | Path: `id`; Body: `OrderRequest` | 200 OK - `OrderDetailResponse` | Cập nhật trạng thái một chiều (PENDING -> SHIPPING -> COMPLETED). Nếu chuyển sang CANCELLED từ PENDING/SHIPPING: chạy Transaction đổi trạng thái và cộng hoàn kho theo `order_details`. Chặn tuyệt đối nếu đơn đã COMPLETED hoặc CANCELLED. Cho phép thay đổi số lượng sản phẩm khi đơn hàng ở trạng thái PENDING. |

### 2.5. Định dạng phản hồi lỗi

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

---

## PHẦN 3: MỘT SỐ VẤN ĐỀ CỦA HỆ THỐNG (ĐÃ XỬ LÝ Ở PHASE 1)

Khi Backend Server xử lý đồng thời nhiều HTTP Request (Concurrent Requests) và tương tác bất đồng bộ xuống Persistence Database, độ trễ đọc/ghi dễ gây sai lệch dữ liệu.

### 3.1. Lỗi ghi đè dữ liệu khi cập nhật tồn kho

**Kịch bản gây lỗi:**

1. Thực thể Product (mã A) đang có tồn kho `qty = 10`.
2. Lúc 09:00: Admin gọi `GET /api/v1/products/A`, nhận về Response có `qty = 10` và chuẩn bị nhập thêm 5 sản phẩm (tính thành 15).
3. Lúc 09:01: Customer gửi Request `POST /api/v1/orders` với `OrderRequest(customerId, employeeId, List<Items>)` đặt mua 3 sản phẩm A. Server xử lý và trừ kho trong Database xuống còn `qty = 7`, trả về 201 Created.
4. Lúc 09:02: Admin gửi Request `PUT` mang toàn bộ dữ liệu cũ kèm `ProductRequest(name, qty = 15, price)` lên Server. Database bị ghi đè ngược thành `qty = 15` (lệch 3 sản phẩm so với thực tế là 7 + 5 = 12).

**Cách hệ thống Phase 1 đã xử lý:**

- Trong vận hành thực tế, mọi biến động `qty` bắt buộc đi qua các Request giao dịch `POST` (như `POST /api/v1/orders`) hoặc điều chỉnh theo lượng chênh lệch.
- API cập nhật thông tin sản phẩm được chuyển thành `PATCH /api/v1/products/{id}` và chỉ nhận `name`, `price`, `status`, không cho phép ghi đè trực tiếp `qty`.

### 3.2. Đứt gãy giao dịch giữa chừng

**Kịch bản gây lỗi:**

- Client gửi `POST /api/v1/orders` với `List<Items>` gồm 5 sản phẩm. Server phải ghi liên tiếp vào bảng `orders`, `order_details` và trừ `qty` trong bảng `products`.
- Khi Server vừa trừ kho xong 3 sản phẩm đầu trong Database, đến sản phẩm thứ 4 thì phát hiện `qty` không đủ (hoặc sản phẩm đang `INACTIVE`, hoặc ngắt kết nối Database đột ngột). Nếu các lệnh chạy độc lập, Server trả Response lỗi nhưng 3 sản phẩm đầu đã bị trừ kho vô lý trong khi đơn hàng không được tạo.

**Cách hệ thống Phase 1 đã xử lý:**

- Bọc toàn bộ quá trình kiểm tra trạng thái `ACTIVE`, đối chiếu và trừ/hoàn trả `qty`, lưu bảng `orders` + `order_details` (trong cả `POST /api/v1/orders` và `PATCH /api/v1/orders/{id}`) vào một Database Transaction (ACID) duy nhất.
- Tuân thủ nguyên tắc All-or-Nothing: bất kỳ bước nào lỗi sẽ tự động kích hoạt ROLLBACK, khôi phục nguyên trạng `qty` của các dòng trước đó.

### 3.3. Sai số trong Database khi có Request đồng thời

**Kịch bản gây lỗi:**

- Sản phẩm A còn tồn kho `qty = 5`. Cùng một thời điểm, 2 khách hàng gửi đồng thời 2 Request `POST /api/v1/orders` cùng đặt mua sản phẩm A với số lượng lần lượt là 4 và 3.
- Hai luồng xử lý trên Server cùng đọc Database lúc `qty = 5`, cùng kiểm tra thấy hợp lệ (5 >= 4 và 5 >= 3) và cùng phát lệnh cập nhật trừ kho xuống Database, dẫn đến bán vượt tồn kho (Overselling) hoặc ghi đè sai số dư `qty`.

**Cách hệ thống Phase 1 đã xử lý:**

- Áp dụng khóa dòng mức cơ sở dữ liệu (Row-level Locking - `SELECT ... FOR UPDATE`) trên bảng `products` bên trong Transaction.
- Khi Request 1 đang kiểm tra và trừ `qty` của sản phẩm A, dòng dữ liệu này bị khóa độc quyền. Request 2 phải chờ ở hàng đợi Database cho đến khi Request 1 COMMIT xong (lúc này `qty` còn 1), nhờ đó Request 2 đọc được giá trị mới nhất và báo lỗi hết hàng chính xác.

### 3.4. Bế tắc hệ thống do khóa chéo (Database Deadlock)

**Kịch bản gây lỗi:** Khi đã áp dụng khóa dòng (Row-level Locking) để chống âm kho cho `POST /api/v1/orders`:

- Request 1 gửi `List<Items>` theo thứ tự [Sản phẩm A, Sản phẩm B]: Luồng 1 khóa dòng A trước, rồi chờ khóa dòng B.
- Cùng lúc đó, Request 2 gửi `List<Items>` theo thứ tự [Sản phẩm B, Sản phẩm A]: Luồng 2 khóa dòng B trước, rồi chờ khóa dòng A.
- Hệ quả: Luồng 1 chờ Luồng 2 nhả B, Luồng 2 chờ Luồng 1 nhả A. Cả hai treo cứng (Deadlock), làm nghẽn kết nối Database.

**Cách hệ thống Phase 1 đã phòng ngừa:**

- Ngay ở bước chuẩn hóa `OrderRequest`, hệ thống áp dụng quy tắc: "Gộp và sắp xếp `productId` trong `List<Items>` theo thứ tự tăng dần trước khi kiểm tra/trừ kho".
- Nhờ đó, cả Request 1 và Request 2 đều luôn khóa dòng Sản phẩm A trước rồi mới đến Sản phẩm B, triệt tiêu hoàn toàn nguy cơ Deadlock.

---

## PHẦN 4: NHỮNG ĐIỂM GỢI Ý CẢI TIẾN Ở PHASE 2

### 4.1. Request trùng lặp dữ liệu & Gửi lặp Request (Duplicate Request)

**Kịch bản gây lỗi:**

- Gửi lặp Request tạo đơn: khi gọi `POST /api/v1/orders`, Client gửi lặp 2 Request cùng lúc thay vì 1 Request, làm Server tạo thừa 1 đơn hàng và trừ bảng `products` 2 lần.

### 4.2. Lỗ hổng thu hồi quyền truy cập của Stateless JWT (Token Revocation Problem)

**Kịch bản gây lỗi:**

- Ở Phase 1, API `POST /api/v1/auth/login` cấp phát JWT Token theo cơ chế Stateless (Server không lưu trạng thái đăng nhập mà chỉ kiểm tra chữ ký và hạn sử dụng của Token trên Header `Authorization`).
- Nếu một tài khoản User (quyền USER) bị lộ Token hoặc nhân viên bị cho nghỉ việc đột xuất khi Token vẫn còn hạn (ví dụ: 24 giờ), người này vẫn có thể dùng Token cũ gọi `GET /api/v1/customers` xem dữ liệu khách hàng hoặc gửi Request phá hoại hệ thống cho đến khi Token tự hết hạn.

**Hướng giải quyết (kiến nghị nâng cấp):**

- Giảm thời gian sống của Access Token xuống ngắn (15-30 phút) kết hợp với Refresh Token.
- Tích hợp bộ nhớ đệm (Redis Cache) ở Phase 2 để lưu danh sách đen (Token Blacklist) hoặc kiểm tra tức thời trạng thái hoạt động của nhân viên (`ACTIVE` / `TERMINATED`) trước khi cho phép thực thi Request.

### 4.3. Mở rộng xử lý nghiệp vụ và tối ưu hóa hiệu năng

**Mở rộng thực thể tham gia vào hệ thống (Employee):**

- Bổ sung thực thể Employee với mối quan hệ tự tham chiếu đệ quy (`reportsTo`) để quản lý sơ đồ phân cấp báo cáo.
- Chuẩn hóa liên kết giữa Employee với Customer (nhân viên Sales Rep chăm sóc trong `POST/PUT /api/v1/customers`) và với Order (trường `employeeId` trong `POST /api/v1/orders`) nhằm thống kê KPI doanh số.

**Phân quyền chi tiết & tối ưu hóa truy vấn:**

- Liên kết tài khoản User với hồ sơ Employee để nâng cấp phân quyền từ mức ADMIN/USER sang phân quyền truy cập dữ liệu theo cấp bậc.
- Mở rộng bộ lọc tìm kiếm đa tiêu chí trên `GET /api/v1/products` và `GET /api/v1/orders` (lọc theo khoảng giá `price`, ngưỡng tồn kho `qty`, thời gian đặt hàng) kết hợp phân trang (`?page=0&size=10`).
- Tích hợp Caching (Redis) cho danh mục sản phẩm (`GET /api/v1/products`) để giảm tải đọc trực tiếp xuống Persistence Database.

**Bổ sung bảng lịch sử biến động kho (Inventory Audit Log):**

- Ghi nhận tự động mọi thay đổi về `qty` của Product (khi tạo sản phẩm `POST /api/v1/products`, điều chỉnh kho `PATCH /api/v1/products/{id}`, trừ kho khi `POST /api/v1/orders` hoặc hoàn kho khi `PATCH /api/v1/orders/{id}` sang `CANCELLED`), lưu rõ `employeeId`/User thực hiện và lý do điều chỉnh.

---

## PHẦN 5: TỔ CHỨC MÃ NGUỒN

### 5.1. Công nghệ sử dụng

| Thành phần | Công nghệ |
| :--- | :--- |
| Ngôn ngữ | Java 21 |
| Framework | Spring Boot 4.1.1 (Web, Data JPA, Security, Validation) |
| Cơ sở dữ liệu | MySQL 8.0 |
| Quản lý phiên bản CSDL | Flyway |
| Xác thực | JWT (jjwt 0.12.6), mã hóa mật khẩu BCrypt |
| Tài liệu API | springdoc-openapi (Swagger UI) |
| Tiện ích | Lombok |
| Build / Đóng gói | Maven Wrapper, Docker (multi-stage build), Docker Compose |

### 5.2. Kiến trúc phân tầng

Ứng dụng tổ chức theo kiến trúc phân tầng (Layered Architecture). Một request đi qua các tầng theo thứ tự:

```
Client
  -> JwtAuthenticationFilter   (xác thực token)
  -> Controller                (nhận request, validate dữ liệu, phân quyền)
  -> Service                   (xử lý nghiệp vụ, transaction, khóa dữ liệu)
  -> Repository                (truy xuất CSDL)
  -> MySQL
```

Dữ liệu trả về được Mapper chuyển từ Entity sang DTO trước khi gửi cho Client.

### 5.3. Cấu trúc thư mục

```
BTL-KTPM/
├── Dockerfile                     # Build image multi-stage (Maven -> JRE Alpine)
├── docker-compose.yml             # Khởi chạy MySQL + App
├── .env.example                   # File mẫu biến môi trường
├── pom.xml                        # Khai báo dependency Maven
└── src/main/
    ├── java/com/test/ktpm/
    │   ├── KtpmApplication.java   # Điểm khởi chạy ứng dụng
    │   ├── Controllers/           # Định nghĩa endpoint REST
    │   │   ├── AuthController.java
    │   │   ├── UserController.java
    │   │   ├── CustomerController.java
    │   │   ├── ProductController.java
    │   │   └── OrderController.java
    │   ├── Services/              # Xử lý nghiệp vụ
    │   │   ├── AuthService.java
    │   │   ├── UserService.java
    │   │   ├── CustomerService.java
    │   │   ├── ProductService.java   # Tăng/giảm tồn kho
    │   │   └── OrderService.java     # Tạo/cập nhật/hủy đơn, khóa dòng, chống deadlock
    │   ├── Repositories/          # Spring Data JPA
    │   │   └── ProductRepository.java  # Truy vấn SELECT ... FOR UPDATE
    │   ├── Entity/                # Entity ánh xạ bảng CSDL
    │   │   ├── User.java, Customer.java, Product.java
    │   │   ├── Order.java, OrderDetail.java, OrderDetailId.java (khóa chính phức hợp)
    │   │   └── OrderStatus.java, ProductStatus.java (enum trạng thái)
    │   ├── Model/
    │   │   ├── Request/           # DTO đầu vào, kèm ràng buộc validation
    │   │   └── Response/          # DTO đầu ra
    │   ├── Mapper/                # Chuyển đổi Entity <-> DTO
    │   ├── Security/              # SecurityConfig, JwtTokenProvider, JwtAuthenticationFilter
    │   └── Exceptions/            # Exception tùy biến + GlobalExceptionHandler
    └── resources/
        ├── application.yaml       # Cấu hình ứng dụng (đọc từ biến môi trường)
        └── db/migration/          # Script Flyway
            ├── V1__create.sql
            └── V2__add_version_to_products_and_orders.sql
```

### 5.4. Trách nhiệm từng tầng

| Tầng | Trách nhiệm | Ví dụ |
| :--- | :--- | :--- |
| Controller | Nhận HTTP request, kiểm tra dữ liệu đầu vào (`@Valid`), phân quyền (`@PreAuthorize`), trả mã HTTP | `ProductController.patchProduct` |
| Service | Thực thi quy tắc nghiệp vụ, quản lý transaction (`@Transactional`), khóa dữ liệu | `OrderService.createOrder` |
| Repository | Truy vấn CSDL qua Spring Data JPA | `ProductRepository.findAllByIdWithLock` |
| Entity | Ánh xạ bảng CSDL, khai báo quan hệ | `Order` 1-n `OrderDetail` |
| Model (DTO) | Tách biệt dữ liệu API với cấu trúc bảng | `ProductUpdateRequest`, `OrderResponse` |
| Mapper | Chuyển đổi Entity và DTO | `ProductMapper.patchEntity` |
| Exceptions | Chuẩn hóa định dạng lỗi JSON trả về Client | `GlobalExceptionHandler` |

### 5.5. Ánh xạ cơ chế xử lý (Phần 3) vào mã nguồn

| Vấn đề | Cơ chế | Vị trí trong mã nguồn |
| :--- | :--- | :--- |
| Ghi đè tồn kho | Tách API cập nhật thông tin (`PATCH`) khỏi tồn kho | `ProductController`, `ProductUpdateRequest` |
| Đứt gãy giao dịch | `@Transactional` bao toàn bộ nghiệp vụ đơn hàng | `OrderService` |
| Overselling | `@Lock(PESSIMISTIC_WRITE)` sinh `SELECT ... FOR UPDATE` | `ProductRepository.findAllByIdWithLock` |
| Deadlock | Lọc trùng và sắp xếp `productCode` tăng dần trước khi khóa | `OrderService.createOrder`, `OrderService.patchOrder` |
| Cập nhật đồng thời cùng bản ghi | Khóa lạc quan `@Version`, trả về 409 Conflict | `Order.java`, `Product.java`, `GlobalExceptionHandler` |

### 5.6. Cơ sở dữ liệu

| Bảng | Khóa chính | Khóa ngoại / Ràng buộc |
| :--- | :--- | :--- |
| `users` | `user_id` | `username` UNIQUE |
| `customers` | `customer_number` | `user_id` -> `users`, `phone` UNIQUE |
| `products` | `product_code` | `version` (khóa lạc quan) |
| `orders` | `order_number` | `customer_number` -> `customers`, `version` (khóa lạc quan) |
| `order_details` | (`order_number`, `product_code`) | `order_number` -> `orders` (ON DELETE CASCADE), `product_code` -> `products` |

- Schema được quản lý hoàn toàn bằng Flyway; Hibernate đặt `ddl-auto: validate` (chỉ kiểm tra, không tự sửa bảng).
- Khi cần thay đổi schema, tạo file migration mới dạng `V{n}__mo_ta.sql`. Không sửa các file migration đã chạy vì Flyway sẽ báo lỗi checksum.

---

## PHẦN 6: CÀI ĐẶT VÀ TRIỂN KHAI (DEPLOY)

### 6.1. Yêu cầu môi trường

| Cách triển khai | Yêu cầu |
| :--- | :--- |
| Docker Compose (khuyến nghị) | Docker Desktop (hoặc Docker Engine + Docker Compose v2) |
| Chạy trực tiếp | JDK 21, MySQL 8.0 (hoặc dùng MySQL qua Docker) |

### 6.2. Cấu hình biến môi trường

Tạo file `.env` từ file mẫu:

```bash
# Linux / macOS
cp .env.example .env

# Windows PowerShell
Copy-Item .env.example .env
```

| Biến | Ý nghĩa | Giá trị mẫu |
| :--- | :--- | :--- |
| `DB_NAME` | Tên database | `KTPM` |
| `DB_USERNAME` | Tài khoản MySQL | `root` |
| `DB_PASSWORD` | Mật khẩu MySQL (đồng thời là mật khẩu root của container MySQL) | `12345678` |
| `SERVER_PORT` | Cổng chạy ứng dụng | `3000` |
| `JWT_SECRET` | Khóa ký JWT (chuỗi hex tối thiểu 256 bit) | Có giá trị mặc định trong `application.yaml` |
| `JWT_EXPIRATION` | Thời hạn token (mili giây) | `86400000` (24 giờ) |

### 6.3. Cách 1: Triển khai bằng Docker Compose

```bash
# Build image và khởi chạy toàn bộ hệ thống (MySQL + App)
docker compose up -d --build

# Kiểm tra trạng thái container
docker compose ps

# Xem log ứng dụng
docker compose logs -f app

# Build lại ứng dụng sau khi sửa code
docker compose up -d --build app

# Dừng hệ thống (giữ nguyên dữ liệu)
docker compose down

# Dừng hệ thống và xóa toàn bộ dữ liệu CSDL
docker compose down -v
```

Trình tự khởi động tự động:

1. Container MySQL (`ktpm-db`) khởi động, healthcheck `mysqladmin ping` được chạy định kỳ 10 giây.
2. Container ứng dụng (`ktpm-app`) chỉ khởi động khi MySQL ở trạng thái `healthy` (`depends_on: condition: service_healthy`).
3. Flyway tự động chạy các script trong `db/migration` để tạo bảng.
4. API sẵn sàng tại `http://localhost:3000/api/v1`.

| Dịch vụ | Tên container | Cổng máy host -> container | Ghi chú |
| :--- | :--- | :--- | :--- |
| MySQL | `ktpm-db` | `3308 -> 3306` | Kết nối từ máy host (DBeaver, Workbench) qua cổng 3308 |
| App | `ktpm-app` | `3000 -> 3000` | Theo biến `SERVER_PORT` |

Dữ liệu MySQL được lưu trong named volume `mysql-data`, không bị mất khi dừng hoặc tạo lại container.

### 6.4. Cách 2: Chạy trực tiếp trên máy (phục vụ phát triển, debug)

**Bước 1:** Khởi động MySQL (dùng Docker cho nhanh):

```bash
docker compose up -d mysql
```

**Bước 2:** Khai báo biến môi trường (Windows PowerShell):

```powershell
$env:SERVER_PORT="3000"
$env:SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3308/KTPM?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
$env:SPRING_DATASOURCE_USERNAME="root"
$env:SPRING_DATASOURCE_PASSWORD="12345678"
```

**Bước 3:** Chạy ứng dụng:

```bash
# Windows
.\mvnw.cmd spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```

Lưu ý: nếu gặp lỗi `release version 21 not supported`, JDK trên máy đang thấp hơn 21. Cần cài JDK 21 và trỏ biến `JAVA_HOME` tới JDK này, hoặc chuyển sang Cách 1.

### 6.5. Kiến trúc Docker image

`Dockerfile` sử dụng kỹ thuật multi-stage build:

| Giai đoạn | Base image | Công việc |
| :--- | :--- | :--- |
| Stage 1 - Builder | `maven:3.9.6-amazoncorretto-21` | Copy `pom.xml` và tải dependency trước (`mvn dependency:go-offline`) để tận dụng Docker cache, sau đó copy mã nguồn và đóng gói (`mvn clean package -DskipTests`) |
| Stage 2 - Runtime | `amazoncorretto:21-alpine` | Chỉ copy file `.jar` thành phẩm từ Stage 1 và chạy `java -jar app.jar` |

Lợi ích: image cuối nhẹ, không chứa mã nguồn và công cụ build; khi chỉ sửa code Java, Docker không phải tải lại dependency.

### 6.6. Kiểm tra sau khi triển khai

1. Mở Swagger UI: `http://localhost:3000/swagger-ui/index.html`
2. Gọi `POST /api/v1/auth/register` để tạo tài khoản, sau đó `POST /api/v1/auth/login` để lấy token.
3. Bấm nút **Authorize** trên Swagger, nhập token để gọi các API yêu cầu đăng nhập.
