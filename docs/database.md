# Thiết kế Cơ sở dữ liệu

> [← Quay lại README chính](../README.md)

## 1. Tổng quan các bảng

| Bảng | Mô tả | Khóa chính | Khóa ngoại & Ràng buộc chính |
| :--- | :--- | :--- | :--- |
| `users` | Tài khoản đăng nhập và phân quyền | `user_id` | `username` `UNIQUE` |
| `customers` | Hồ sơ thông tin khách hàng | `customer_number` | `user_id` -> `users(user_id)` (`UNIQUE`, `ON DELETE SET NULL`), `phone` `UNIQUE` |
| `products` | Danh mục hàng hóa và tồn kho | `product_code` | `version` (khóa lạc quan `@Version`) |
| `orders` | Thông tin chung của đơn hàng | `order_number` | `customer_number` -> `customers(customer_number)`, `version` (khóa lạc quan `@Version`) |
| `order_details` | Chi tiết từng mặt hàng trong đơn | (`order_number`, `product_code`) | `order_number` -> `orders(order_number)` (`ON DELETE CASCADE`), `product_code` -> `products(product_code)` |

---

## 2. Chi tiết cấu trúc bảng và kiểu dữ liệu

### 2.1. Bảng `users`

| Cột | Kiểu dữ liệu | Ràng buộc / Mặc định | Ý nghĩa |
| :--- | :--- | :--- | :--- |
| `user_id` | `BIGINT` | `PK`, `AUTO_INCREMENT`, `NOT NULL` | Mã định danh tài khoản |
| `username` | `VARCHAR(255)` | `NOT NULL`, `UNIQUE` | Tên đăng nhập duy nhất |
| `password` | `VARCHAR(255)` | `NOT NULL` | Mật khẩu mã hóa BCrypt |
| `role` | `VARCHAR(20)` | `NOT NULL`, `DEFAULT 'ROLE_USER'` | Phân quyền (`ROLE_USER`, `ROLE_ADMIN`) |

### 2.2. Bảng `customers`

| Cột | Kiểu dữ liệu | Ràng buộc / Mặc định | Ý nghĩa |
| :--- | :--- | :--- | :--- |
| `customer_number` | `BIGINT` | `PK`, `AUTO_INCREMENT`, `NOT NULL` | Mã khách hàng |
| `user_id` | `BIGINT` | `FK -> users(user_id)`, `UNIQUE`, `NULL`, `ON DELETE SET NULL` | Tài khoản gắn với khách hàng (quan hệ 1-1) |
| `customer_name` | `VARCHAR(255)` | `NOT NULL` | Họ và tên khách hàng |
| `phone` | `VARCHAR(20)` | `NOT NULL`, `UNIQUE` | Số điện thoại liên hệ duy nhất |

### 2.3. Bảng `products`

| Cột | Kiểu dữ liệu | Ràng buộc / Mặc định | Ý nghĩa |
| :--- | :--- | :--- | :--- |
| `product_code` | `BIGINT` | `PK`, `AUTO_INCREMENT`, `NOT NULL` | Mã sản phẩm |
| `product_name` | `VARCHAR(255)` | `NOT NULL` | Tên sản phẩm |
| `quantity_in_stock` | `INT` | `NOT NULL`, `DEFAULT 0` | Số lượng tồn kho hiện có |
| `buy_price` | `DECIMAL(15, 2)` | `NOT NULL`, `DEFAULT 0.00` | Đơn giá sản phẩm |
| `status` | `VARCHAR(20)` | `NOT NULL`, `DEFAULT 'ACTIVE'` | Trạng thái (`ACTIVE`, `INACTIVE`) |
| `version` | `BIGINT` | `NOT NULL`, `DEFAULT 0` | Phiên bản phục vụ khóa lạc quan (`@Version`) |

### 2.4. Bảng `orders`

| Cột | Kiểu dữ liệu | Ràng buộc / Mặc định | Ý nghĩa |
| :--- | :--- | :--- | :--- |
| `order_number` | `BIGINT` | `PK`, `AUTO_INCREMENT`, `NOT NULL` | Mã đơn hàng |
| `order_date` | `DATETIME` | `NOT NULL`, `DEFAULT CURRENT_TIMESTAMP` | Thời điểm tạo đơn hàng |
| `customer_number` | `BIGINT` | `FK -> customers(customer_number)`, `NOT NULL` | Khách hàng sở hữu đơn hàng |
| `status` | `VARCHAR(20)` | `NOT NULL`, `DEFAULT 'PENDING'` | Trạng thái đơn (`PENDING`, `SHIPPING`, `COMPLETED`, `CANCELLED`) |
| `total_amount` | `DECIMAL(15, 2)` | `NOT NULL`, `DEFAULT 0.00` | Tổng tiền của đơn hàng |
| `version` | `BIGINT` | `NOT NULL`, `DEFAULT 0` | Phiên bản phục vụ khóa lạc quan (`@Version`) |

### 2.5. Bảng `order_details`

| Cột | Kiểu dữ liệu | Ràng buộc / Mặc định | Ý nghĩa |
| :--- | :--- | :--- | :--- |
| `order_number` | `BIGINT` | `PK`, `FK -> orders(order_number)`, `ON DELETE CASCADE`, `NOT NULL` | Mã đơn hàng (thuộc khóa chính phức hợp) |
| `product_code` | `BIGINT` | `PK`, `FK -> products(product_code)`, `NOT NULL` | Mã sản phẩm (thuộc khóa chính phức hợp) |
| `quantity_ordered` | `INT` | `NOT NULL` | Số lượng đặt mua của sản phẩm |
| `price_each` | `DECIMAL(15, 2)` | `NOT NULL` | Đơn giá lưu tại thời điểm đặt hàng |

---

## 3. Ràng buộc toàn vẹn và quản lý schema

- **Ràng buộc tham chiếu (Foreign Key):**
  - `customers.user_id` dùng `ON DELETE SET NULL` để giữ lại hồ sơ khách hàng và lịch sử đơn hàng khi tài khoản đăng nhập bị xóa.
  - `order_details.order_number` dùng `ON DELETE CASCADE` để tự động xóa chi tiết đơn khi đơn hàng cha bị xóa; trong khi `order_details.product_code` và `orders.customer_number` không cho phép xóa sản phẩm hoặc khách hàng đã phát sinh giao dịch.
- **Khóa chính phức hợp:** Bảng `order_details` dùng cặp (`order_number`, `product_code`) làm khóa chính (`OrderDetailId`), đảm bảo mỗi sản phẩm chỉ xuất hiện một dòng duy nhất trong cùng một đơn hàng.
- **Kiểm soát truy cập đồng thời:**
  - Cột `version` trên `products` và `orders` kết hợp `@Version` của JPA để phát hiện xung đột cập nhật đồng thời (Optimistic Locking).
  - Các giao dịch trừ/hoàn tồn kho trong `OrderService` sử dụng thêm khóa dòng `SELECT ... FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)`) trên bảng `products`.
- **Quản lý migration:** Schema được quản lý hoàn toàn bằng Flyway (`V1__create.sql`, `V2__add_version_to_products_and_orders.sql`); Hibernate đặt `ddl-auto: validate` (chỉ kiểm tra khớp schema, không tự ý sửa bảng).

