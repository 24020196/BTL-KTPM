# ByteMe — Hệ thống Đặt đồ ăn (Restaurant Ordering Backend)

Backend REST API đặt món cho nhà hàng phục vụ một cơ sở duy nhất, hỗ trợ ăn tại chỗ (`DINE_IN`) và giao hàng tận nơi (`DELIVERY`).

- **Ngôn ngữ & Nền tảng:** Java 21, Spring Boot 3.5.16, MySQL 8.4, Spring Data JPA, Spring Security (JWT RS256).
- **Tài liệu API:**
  - Swagger UI: http://localhost:8080/swagger-ui/index.html
  - Đặc tả OpenAPI 3.0: http://localhost:8080/openapi.yaml
- **Tài liệu thiết kế chi tiết:** Xem thư mục [docs/](docs/).

---

## 1. Tính năng chính

- **Xác thực & Phân quyền (RBAC):** Đăng ký tài khoản, đăng nhập cấp token JWT bất đối xứng (RS256), in-memory rate limiting tại Filter chống brute-force. Ba vai trò độc lập:
  - `CUSTOMER`: Duyệt thực đơn, quản lý giỏ hàng, đặt đơn, xem và hủy đơn của mình (khi ở `PENDING`).
  - `EMPLOYEE`: Tra cứu danh sách đơn cần xử lý, xác nhận đơn và cập nhật tiến độ (`CONFIRMED`, `PROCESSING`, `SHIPPING`, `COMPLETED`, `CANCELLED`).
  - `ADMIN`: Quản trị thực đơn (thêm, cập nhật giá/tồn kho, ngừng kinh doanh món ăn).
- **Thực đơn & Giỏ hàng:** Xem danh sách món ăn, lọc theo danh mục, tìm kiếm theo từ khóa; giỏ hàng lưu theo từng tài khoản khách hàng.
- **Tạo đơn hàng (Checkout):** Trừ tồn kho và làm rỗng giỏ hàng đồng bộ trong **một database transaction duy nhất**. Lưu snapshot cố định tên và đơn giá món ăn tại thời điểm mua.
- **Vòng đời đơn hàng:** Tự động hoàn lại tồn kho khi đơn chuyển sang `CANCELLED` (áp dụng cả khi khách hủy hoặc nhân viên hủy).

---

## 2. Kiến trúc & Cấu trúc mã nguồn

Mã nguồn được tổ chức theo **kiến trúc phân tầng kỹ thuật (Package-by-Layer)** với ranh giới trách nhiệm rõ ràng (API → Nghiệp vụ → Truy cập dữ liệu):

- [**`controller`**](file:///src/main/java/org/example/controller): Tiếp nhận HTTP/JSON, kiểm tra ràng buộc đầu vào (`@Valid`), bóc tách `CurrentUser`.
- [**`service`**](file:///src/main/java/org/example/service): Xử lý quy tắc nghiệp vụ, quản lý giao dịch (`@Transactional`). Tầng nghiệp vụ **độc lập hoàn toàn với web framework và thư viện DB** (được kiểm tra tự động bởi ArchUnit).
- [**`repository`**](file:///src/main/java/org/example/repository): Truy cập dữ liệu thông qua Spring Data JPA.
- [**`entity`**](file:///src/main/java/org/example/entity): Các JPA entity (`User`, `Food`, `Cart`, `Order`...) và enums nghiệp vụ (`OrderStatus`, `OrderType`, `PaymentMethod`).
- [**`dto`**](file:///src/main/java/org/example/dto): Request / Response DTOs.
- [**`config`** & **`platform`**](file:///src/main/java/org/example/platform): Cấu hình Spring Security, JWT RS256, BCrypt, bộ lọc IP rate limiter.
- [**`common`** & **`shared`**](file:///src/main/java/org/example/common): Xử lý ngoại lệ tập trung (`GlobalErrors`, `BusinessException`), tiện ích xác thực người dùng.

```text
src/main/java/org/example/
├── controller/       # AuthController, FoodController, CartController, OrderController
├── service/          # RegisterService, LoginService, FoodService, CartService, OrderService
├── repository/       # AccountRepository, FoodRepository, CartRepository, OrderRepository
├── entity/           # User, Food, Category, Cart, CartItem, Order, OrderItem, enums
├── dto/              # Request / Response DTOs
├── config/           # SecurityConfiguration
├── platform/         # BCryptPasswordHasher, LocalRsaTokenIssuer, LoginIpFilter...
├── common/           # BusinessException, GlobalErrors, CurrentUser, ApiError
└── shared/api/       # AuthenticatedUser, Role
```

---

## 3. Cài đặt và khởi chạy

### Yêu cầu môi trường
- Java 21 trở lên
- Docker & Docker Compose
- Python 3 (thư viện `cryptography==50.0.2` để sinh cặp khóa RSA cho JWT)

### Bước 1: Chuẩn bị cặp khóa RSA cho JWT
```sh
python3 -m venv .venv

# Linux / macOS:
.venv/bin/pip install cryptography==50.0.2
.venv/bin/python scripts/generate-keys.py

# Windows (PowerShell):
.\.venv\Scripts\pip install cryptography==50.0.2
.\.venv\Scripts\python scripts/generate-keys.py
```

### Bước 2: Khởi chạy hệ thống

#### Cách 1: Sử dụng Docker Compose (Khuyên dùng)
```sh
# Chuẩn bị bản sao khóa phân quyền cho container Docker
python scripts/prepare-docker-keys.py

# Khởi chạy MySQL và Backend
docker compose up --build -d

# Nạp dữ liệu mẫu vào MySQL (chạy một lần)
docker compose exec mysql sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" < /opt/byteme/seed.sql'
```

#### Cách 2: Chạy trực tiếp trên máy host (Local JVM)
1. Chạy riêng container MySQL:
   ```sh
   docker compose up mysql -d
   ```
2. Khởi chạy backend qua Maven Wrapper:
   - **Linux / macOS**:
     ```sh
     DB_URL=jdbc:mysql://localhost:3306/starter DB_USERNAME=starter DB_PASSWORD=local-development-only ./mvnw spring-boot:run
     ```
   - **Windows (PowerShell)**:
     ```powershell
     $env:DB_URL="jdbc:mysql://localhost:3306/starter"; $env:DB_USERNAME="starter"; $env:DB_PASSWORD="local-development-only"; .\mvnw.cmd spring-boot:run
     ```

### Bước 3: Tài khoản thử nghiệm (Seed Accounts)
Sau khi nạp `seed.sql`, hệ thống có sẵn các tài khoản với mật khẩu chung là **`ByteMeDemo!2026`**:
- **ADMIN:** `admin@byteme.example` (thêm, sửa, ngừng kinh doanh món)
- **EMPLOYEE:** `huong.employee@byteme.example` hoặc `nam.employee@byteme.example` (tiếp nhận & xử lý đơn)
- **CUSTOMER:** `an.customer@byteme.example`, `binh.customer@byteme.example` (đặt món, quản lý giỏ hàng)

Sinh nhanh token JWT fixture để kiểm tra phân quyền:
```sh
bash scripts/issue-local-tokens.sh
```

---

## 4. Danh sách API (18 Use Cases)

| UC | Method | Endpoint | Quyền hạn | Mô tả tóm tắt |
| :--- | :--- | :--- | :--- | :--- |
| **UC01** | POST | `/api/auth/register` | Public | Đăng ký tài khoản khách hàng |
| **UC02** | POST | `/api/auth/token` | Public | Đăng nhập và nhận accessToken JWT |
| **UC03** | GET | `/api/foods?categoryId=` | Public | Xem danh sách món ăn đang bán |
| **UC04** | GET | `/api/foods/{id}` | Public | Xem chi tiết thông tin một món ăn |
| **UC05** | GET | `/api/foods/search?q=&categoryId=` | Public | Tìm kiếm món ăn theo từ khóa và danh mục |
| **UC06** | POST | `/api/foods` | ADMIN | Thêm món ăn mới vào thực đơn |
| **UC07** | PUT | `/api/foods/{id}` | ADMIN | Cập nhật thông tin món ăn, giá bán, tồn kho |
| **UC08** | DELETE | `/api/foods/{id}` | ADMIN | Ngừng kinh doanh món ăn (Soft delete) |
| **UC09** | GET | `/api/cart` | CUSTOMER | Xem giỏ hàng hiện tại của khách |
| **UC10** | POST | `/api/cart/items` | CUSTOMER | Thêm món ăn vào giỏ hàng |
| **UC11** | PUT | `/api/cart/items/{cartItemId}` | CUSTOMER | Cập nhật số lượng món trong giỏ |
| **UC12** | DELETE | `/api/cart/items/{cartItemId}` | CUSTOMER | Xóa món khỏi giỏ hàng |
| **UC13** | POST | `/api/orders` | CUSTOMER | Tạo đơn hàng mới từ giỏ hàng hiện tại |
| **UC14** | GET | `/api/orders/{orderId}` | CUSTOMER / EMPLOYEE | Xem chi tiết đơn hàng (khách chỉ xem đơn của mình) |
| **UC15** | DELETE | `/api/orders/{orderId}` | CUSTOMER | Khách hủy đơn hàng của mình (khi ở PENDING) |
| **UC16** | POST | `/api/orders/{orderId}/confirm` | EMPLOYEE | Tiếp nhận và xác nhận đơn hàng |
| **UC17** | PUT | `/api/orders/{orderId}/status` | EMPLOYEE | Cập nhật trạng thái đơn (chế biến, giao hàng, hoàn thành, hủy) |
| **UC18** | GET | `/api/orders?status=&orderType=&page=&size=` | EMPLOYEE | Tra cứu danh sách đơn hàng cần xử lý có phân trang |

---

## 5. Quy trình Đặt hàng & Vòng đời đơn

### Tạo đơn hàng (`POST /api/orders`)
Client không gửi danh sách món hay đơn giá; hệ thống tự đọc từ giỏ hàng hiện tại và tính toán tổng tiền.

- **Đơn ăn tại chỗ (`DINE_IN`):**
  ```json
  {
    "orderType": "DINE_IN",
    "tableNumber": "B05",
    "paymentMethod": "CASH",
    "note": "Ít cay"
  }
  ```
  *Phương thức thanh toán:* `CASH` hoặc `BANK_TRANSFER`.

- **Đơn giao hàng (`DELIVERY`):**
  ```json
  {
    "orderType": "DELIVERY",
    "recipientName": "Nguyễn Văn A",
    "shippingPhone": "0900000004",
    "shippingAddress": "123 Đường ABC, Phường 1, Quận 1",
    "paymentMethod": "COD"
  }
  ```
  *Phương thức thanh toán:* `COD` hoặc `BANK_TRANSFER`.

### Vòng đời trạng thái đơn hàng
```
[PENDING] ──► [CONFIRMED] ──► [PROCESSING] ──(DINE_IN)──────────► [COMPLETED]
    │              │                │
    │              │                └──(DELIVERY)──► [SHIPPING] ──► [COMPLETED]
    │              │                                     │
    └──────────────┴─────────────────────────────────────┴──────► [CANCELLED]
```
- Khách hàng chỉ được hủy đơn khi ở trạng thái `PENDING`.
- Nhân viên có quyền hủy đơn ở `PENDING`, `CONFIRMED` hoặc `PROCESSING`.
- Khi đơn chuyển sang `CANCELLED`, hệ thống tự động hoàn lại tồn kho trong cùng database transaction.

---

## 6. Kiểm thử

- **Unit & Architecture Test (không yêu cầu Docker):**
  ```sh
  ./mvnw test -Dtest="!*IntegrationTest,!DatabaseDesignTests"
  ```
  *Kiểm tra nghiệp vụ, bảo mật MockMvc và kiểm tra quy tắc phân tầng bằng ArchUnit ([`ArchitectureTest.java`](file:///src/test/java/org/example/ArchitectureTest.java)).*

- **Toàn bộ Test Suites (yêu cầu Docker daemon cho Testcontainers):**
  ```sh
  ./mvnw test
  ```

- **Kiểm thử kịch bản API tự động (Black-box Test):**
  ```sh
  python -X utf8 scripts/test-endpoints.py
  ```

- **Đo tải & Báo cáo bảo mật:**
  - Kịch bản k6 trên Kaggle CPU: [kaggle/results/phase1-baseline/README.md](kaggle/results/phase1-baseline/README.md).
  - Đánh giá OWASP ZAP & kiểm tra phân quyền BOLA: [docs/security-report.md](docs/security-report.md).

---

## 7. Tài liệu kỹ thuật chi tiết

| Tài liệu | Đường dẫn | Nội dung chính |
| :--- | :--- | :--- |
| **Kiến trúc hệ thống** | [docs/architecture-single-restaurant.md](docs/architecture-single-restaurant.md) | Phân tầng theo nhiệm vụ, sequence diagrams tạo đơn & hủy đơn, quy tắc transaction |
| **Thiết kế CSDL** | [docs/database-design.md](docs/database-design.md) | Schema 7 bảng MySQL, khóa ngoại, chỉ mục, snapshot giá và múi giờ UTC |
| **Đặc tả Use Cases** | [docs/Project KTPM.md](<docs/Project KTPM.md>) | Đặc tả chi tiết 18 use cases (UC01–UC18), actors, điều kiện tiên quyết và mã lỗi |
| **Báo cáo Bảo mật** | [docs/security-report.md](docs/security-report.md) | Kết quả quét lỗ hổng OWASP ZAP và kiểm thử phân quyền BOLA |
