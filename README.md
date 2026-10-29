# KTPM - Hệ thống Quản lý Kho hàng, Khách hàng và Đơn hàng

## Mục lục

- [Phần 1: Mô tả bài toán và nghiệp vụ cơ bản](#phần-1-mô-tả-bài-toán-và-nghiệp-vụ-cơ-bản)
- [Phần 2: Đặc tả các API (RESTful API Specification)](#phần-2-đặc-tả-các-api-restful-api-specification)
- [Phần 3: Một số vấn đề của hệ thống (đã xử lý ở Phase 1)](#phần-3-một-số-vấn-đề-của-hệ-thống-đã-xử-lý-ở-phase-1)
- [Phần 4: Những điểm gợi ý cải tiến ở Phase 2](#phần-4-những-điểm-gợi-ý-cải-tiến-ở-phase-2)
- [Phần 5: Tổ chức mã nguồn](#phần-5-tổ-chức-mã-nguồn)
- [Phần 6: Cài đặt và triển khai (Deploy)](#phần-6-cài-đặt-và-triển-khai-deploy)
- [Phần 7: Thử nghiệm tải và đo đạc hiệu năng trên Kaggle (Benchmark & Load Testing)](#phần-7-thử-nghiệm-tải-và-đo-đạc-hiệu-năng-trên-kaggle-benchmark--load-testing)

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

Chi tiết quy trình và quy tắc nghiệp vụ của từng phân hệ (Xác thực & Phân quyền, Khách hàng, Tồn kho, Đơn hàng & Sơ đồ trạng thái):
- Xem chi tiết tại: [`docs/business-rules.md`](./docs/business-rules.md)

---

## PHẦN 2: ĐẶC TẢ CÁC API (RESTFUL API SPECIFICATION)

- **Base URL:** `http://localhost:3000/api/v1`
- **Tài liệu tương tác (Swagger UI):** `http://localhost:3000/swagger-ui/index.html`
- **File đặc tả OpenAPI (JSON):** [`docs/openapi.json`](./docs/openapi.json)
- **Xem chi tiết đặc tả các endpoint:** [`docs/api-specification.md`](./docs/api-specification.md)

---

## PHẦN 3: MỘT SỐ VẤN ĐỀ CỦA HỆ THỐNG (ĐÃ XỬ LÝ Ở PHASE 1)

Chi tiết kịch bản gây lỗi khi xử lý đồng thời (ghi đè tồn kho, đứt gãy giao dịch, bán vượt tồn kho, deadlock) và giải pháp đã áp dụng ở Phase 1:
- Xem chi tiết tại: [`docs/phase1-issues.md`](./docs/phase1-issues.md)

---

## PHẦN 4: NHỮNG ĐIỂM GỢI Ý CẢI TIẾN Ở PHASE 2

Chi tiết các điểm gợi ý cải tiến ở Phase 2 (xử lý duplicate request, thu hồi Stateless JWT, mở rộng thực thể Employee, bộ lọc nâng cao, caching và audit log):
- Xem chi tiết tại: [`docs/phase2-improvements.md`](./docs/phase2-improvements.md)

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
    │   │   ├── Request/           # Model đầu vào, kèm ràng buộc validation
    │   │   └── Response/          # Model đầu ra
    │   ├── Mapper/                # Chuyển đổi Entity <- > Model
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
| Model (DTO) | Tách biệt dữ liệu API với cấu trúc bảng | `ProductPatchRequest`, `OrderResponse` |
| Mapper | Chuyển đổi Entity và DTO | `ProductMapper.patchEntity` |
| Exceptions | Chuẩn hóa định dạng lỗi JSON trả về Client | `GlobalExceptionHandler` |

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

- **Docker Desktop** (trên Windows / macOS) hoặc **Docker Engine** kèm **Docker Compose v2** (trên Linux).

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

### 6.3. Triển khai bằng Docker Compose

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

### 6.4. Kiến trúc Docker image

`Dockerfile` sử dụng kỹ thuật multi-stage build:

| Giai đoạn | Base image | Công việc |
| :--- | :--- | :--- |
| Stage 1 - Builder | `maven:3.9.6-amazoncorretto-21` | Copy `pom.xml` và tải dependency trước (`mvn dependency:go-offline`) để tận dụng Docker cache, sau đó copy mã nguồn và đóng gói (`mvn clean package -DskipTests`) |
| Stage 2 - Runtime | `amazoncorretto:21-alpine` | Chỉ copy file `.jar` thành phẩm từ Stage 1 và chạy `java -jar app.jar` |

### 6.5. Kiểm tra sau khi triển khai

1. Mở Swagger UI: `http://localhost:3000/swagger-ui/index.html`
2. Gọi `POST /api/v1/auth/register` để tạo tài khoản, sau đó `POST /api/v1/auth/login` để lấy token.
3. Bấm nút **Authorize** trên Swagger, nhập token để gọi các API yêu cầu đăng nhập.

---

## PHẦN 7: THỬ NGHIỆM TẢI VÀ ĐO ĐẠC HIỆU NĂNG TRÊN KAGGLE (BENCHMARK & LOAD TESTING)

Kiểm thử tải được chạy tự động trên Kaggle Notebook: [`benchmark-load-testing-ktpm.ipynb`](./benchmark-load-testing-ktpm.ipynb).

### 7.1. Môi trường và tham số

| Thành phần | Giá trị |
| :--- | :--- |
| Hạ tầng | Kaggle CPU: 4 vCPU, 30 GB RAM, Ubuntu 24.04. Ứng dụng, MySQL và Locust chạy chung một máy |
| Runtime | OpenJDK 21.0.12.1, JVM `-Xms512m -Xmx1536m` |
| Cơ sở dữ liệu | MySQL 8.0.46 |
| Công cụ | Locust (headless), `psutil` đo CPU và RAM mỗi giây |
| Tải | 100 users, tăng 10 users/giây, chạy 150 giây |
| Dữ liệu seed | 1 admin, 3 khách hàng, 8 sản phẩm; sản phẩm Hot (mã 1) có tồn kho ban đầu 200 |

### 7.2. Kịch bản kiểm thử

Locust mô phỏng 3 nhóm người dùng chạy song song:

| Nhóm người dùng | Tỷ trọng | Thao tác |
| :--- | :--- | :--- |
| Shopper | 70% | Đăng ký, đăng nhập; xem sản phẩm, khách hàng, lịch sử đơn; đặt đơn sản phẩm Hot; đặt đơn nhiều sản phẩm với thứ tự xáo trộn; xem và hủy đơn; gọi thử API của Admin để kiểm tra lỗi 403 |
| Admin | 20% | Xem danh sách user; nhập thêm hàng cho sản phẩm trùng tên; sửa sản phẩm; sửa khách hàng; chuyển đơn `PENDING` -> `SHIPPING` -> `COMPLETED` |
| Spammer | 10% | Gửi liên tục (0.1 - 0.4 giây/lần) yêu cầu đặt mua và tra cứu tồn kho sản phẩm Hot để tăng tranh chấp khóa dòng |

### 7.3. Kết quả tổng hợp

| Chỉ số | Giá trị |
| :--- | :--- |
| Tổng request | 11295 |
| Request lỗi | 392 (3.5%) |
| Throughput | 75.8 RPS |
| Median / P95 / P99 | 12 ms / 170 ms / 350 ms |

Độ trễ theo nhóm thao tác:

| Nhóm thao tác | Endpoint | Median | P95 |
| :--- | :--- | :--- | :--- |
| Đọc dữ liệu | GET sản phẩm, khách hàng, chi tiết đơn, tồn kho | 7 - 10 ms | 18 - 55 ms |
| Ghi có khóa dòng | Đặt đơn Hot, đặt đơn nhiều sản phẩm, nhập hàng | 27 - 59 ms | 74 - 180 ms |
| Lịch sử đơn của khách | `GET /customers/{id}/orders` | 140 ms | 360 ms |
| Đăng ký / đăng nhập | `POST /auth/register`, `POST /auth/login` | 230 - 330 ms | 380 - 480 ms |

Số liệu của từng endpoint xem tại bảng thống kê trong Cell 10 của notebook.

### 7.4. Phân tích request lỗi

| Số lần | Thao tác | Mã lỗi | Nguyên nhân | Đánh giá |
| :--- | :--- | :--- | :--- | :--- |
| 114 | Hủy đơn (`PATCH /orders/{id}`) | 403 | API chỉ dành cho ADMIN nhưng kịch bản gửi bằng token của user thường | Phân quyền hoạt động đúng; kịch bản cần dùng token admin |
| 207 | Sửa khách hàng (`PUT /customers/{id}`) | 500 | Kịch bản gán cùng số điện thoại `0988889999` cho cả 3 khách hàng, vi phạm ràng buộc `UNIQUE` của cột `phone` | Lỗi hệ thống: cần kiểm tra trùng và trả về 400 thay vì 500 |
| 71 | Nhập hàng (49), sửa sản phẩm (22) | 409 | Hai admin cùng cập nhật một sản phẩm | Đúng thiết kế: khóa lạc quan `@Version` (mục 5.5) |

### 7.5. Kiểm tra CSDL sau bài test

- Không có sản phẩm nào bị âm tồn kho, không xảy ra bán vượt tồn kho.
- Có 2442 đơn hợp lệ: 2432 `PENDING`, 10 `COMPLETED`. Không có đơn `CANCELLED` vì toàn bộ request hủy đơn bị từ chối (mục 7.4).
- Sản phẩm Hot còn 105, đã bán 2209. Tồn kho được Admin nhập thêm trong lúc chạy.
- Notebook chưa ghi lại tổng số lượng nhập thêm, nên chưa kiểm tra được đẳng thức `tồn kho ban đầu + nhập thêm = còn lại + đã bán`.

### 7.6. Tài nguyên hệ thống

Biểu đồ CPU, RAM, throughput và độ trễ: [`kaggle_load_benchmark.png`](./kaggle_load_benchmark.png).

| Chỉ số | Nhận xét |
| :--- | :--- |
| CPU | Khoảng 90% trong 20 giây đầu (BCrypt khi đăng ký, đăng nhập), sau đó dao động 35 - 75% |
| RAM | RAM toàn máy tăng dần từ khoảng 1.96 GB lên khoảng 2.2 GB |
| Throughput | Ổn định ở mức 75 - 80 RPS sau 30 giây đầu |
| P95 theo thời gian | Khoảng 560 ms lúc bắt đầu, phần lớn thời gian ở mức 80 - 150 ms, có một đợt lên khoảng 300 ms gần cuối |

Do tải được giữ cố định 100 users, bài test chưa xác định được số users tối đa mà hệ thống chịu được.

### 7.7. Việc cần xử lý

1. Sửa kịch bản hủy đơn trong notebook để dùng token admin, sau đó chạy lại.
2. Kiểm tra trùng số điện thoại trong `CustomerService` và trả về 400.
3. Bổ sung phân trang cho `GET /customers/{id}/orders`.
4. Ghi lại số lượng nhập thêm trong notebook để kiểm tra được đẳng thức cân bằng tồn kho.
