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
- **Xem chi tiết đặc tả các endpoint & mã lỗi tại:** [`docs/api-specification.md`](./docs/api-specification.md)

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

### 5.5. Ánh xạ cơ chế xử lý (Phần 3) vào mã nguồn

| Vấn đề | Cơ chế | Vị trí trong mã nguồn |
| :--- | :--- | :--- |
| Ghi đè tồn kho | Tách API cập nhật thông tin (`PATCH`) khỏi tồn kho | `ProductController`, `ProductPatchRequest` |
| Đứt gãy giao dịch | `@Transactional` bao toàn bộ nghiệp vụ đơn hàng | `OrderService` |
| Overselling | `@Lock(PESSIMISTIC_WRITE)` sinh `SELECT ... FOR UPDATE` | `ProductRepository.findAllByIdWithLock` |
| Deadlock | Lọc trùng và sắp xếp `productCode` tăng dần trước khi khóa | `OrderService.createOrder`, `OrderService.patchOrder` |
| Cập nhật đồng thời cùng bản ghi | Khóa lạc quan `@Version`, trả về 409 Conflict | `Order.java`, `Product.java`, `GlobalExceptionHandler` |
| Nhập hàng trùng tên | Tự động cộng dồn `quantityInStock`, cập nhật `buyPrice` nếu khác giá | `ProductService.createProduct`, `ProductRepository.findFirstByProductName` |

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

Lợi ích: image cuối nhẹ, không chứa mã nguồn và công cụ build; khi chỉ sửa code Java, Docker không phải tải lại dependency.

### 6.5. Kiểm tra sau khi triển khai

1. Mở Swagger UI: `http://localhost:3000/swagger-ui/index.html`
2. Gọi `POST /api/v1/auth/register` để tạo tài khoản, sau đó `POST /api/v1/auth/login` để lấy token.
3. Bấm nút **Authorize** trên Swagger, nhập token để gọi các API yêu cầu đăng nhập.

---

## PHẦN 7: THỬ NGHIỆM TẢI VÀ ĐO ĐẠC HIỆU NĂNG TRÊN KAGGLE (BENCHMARK & LOAD TESTING)

### 7.1. Môi trường và cấu hình thử nghiệm

| Thành phần | Cấu hình |
| :--- | :--- |
| Hạ tầng | Kaggle Linux (Ubuntu 24.04 x86_64, 4 vCPU, 30 GB RAM, không GPU); App, MySQL và Locust chạy chung một máy |
| Runtime | OpenJDK 21 (`21.0.12.1-Ubuntu`), cấu hình JVM `-Xms512m -Xmx1536m` |
| Cơ sở dữ liệu | MySQL Server 8.0 (`8.0.46`) |
| Công cụ kiểm thử | Locust (headless mode) kết hợp `psutil` thu thập CPU và RAM mỗi 1 giây |
| Tham số tải | 100 Virtual Users, tốc độ sinh tải 10 users/giây, thời gian chạy 150 giây |
| Dữ liệu ban đầu | 1 admin, 3 khách hàng, 8 sản phẩm (sản phẩm Hot mã 1 có tồn kho ban đầu 200) |
| Notebook thực thi | [`benchmark-load-testing-ktpm.ipynb`](./benchmark-load-testing-ktpm.ipynb) |

### 7.2. Kịch bản kiểm thử

Hệ thống kiểm thử gọi 14 API trên 5 Controller thông qua 3 nhóm người dùng ảo (Persona) chạy song song:

| Persona | Tỷ trọng | Luồng tác vụ | Mục tiêu kiểm thử |
| :--- | :---: | :--- | :--- |
| `CustomerShopperUser` | 70% | Đăng ký, đăng nhập (`00`) -> xem danh sách, chi tiết sản phẩm (`01`, `02`) -> xem hồ sơ, lịch sử đơn (`03`, `04`) -> đặt sản phẩm Hot (`05`), đặt đơn nhiều sản phẩm (`06`) -> xem chi tiết đơn (`07`), hủy đơn (`08`) -> gọi thử API admin (`09`) | Luồng mua sắm tuần tự; khóa bi quan chống bán vượt tồn kho (`05`); sắp xếp mã sản phẩm chống deadlock (`06`); kiểm tra phân quyền `403` (`09`) |
| `WarehouseAdminUser` | 20% | Xem danh sách user (`10`) -> nhập hàng trùng tên cộng dồn tồn kho (`11`) -> cập nhật sản phẩm (`12`) -> cập nhật khách hàng (`13`) -> duyệt đơn `PENDING` -> `SHIPPING` -> `COMPLETED` (`14`, `15`, `16`) | Nghiệp vụ quản trị kho và đơn hàng; kiểm tra khóa lạc quan `@Version` khi cập nhật đồng thời (`11`, `12`) |
| `FlashSaleSpammerUser` | 10% | Gửi liên tục mỗi 0.1 - 0.4 giây: đặt mua sản phẩm Hot (`17`) và truy vấn tồn kho (`18`) | Tạo tranh chấp khóa dòng (`SELECT ... FOR UPDATE`) ở tần suất cao |

### 7.3. Kết quả đo đạc

Tổng cộng 11.295 request được xử lý trong 150 giây (trung bình 75,8 RPS), ghi nhận 392 request lỗi (3,5%):

| Method | Endpoint / Tác vụ | Persona | Requests | Lỗi | Median (ms) | P95 (ms) | P99 (ms) |
| :--- | :--- | :---: | ---: | ---: | ---: | ---: | ---: |
| `POST` | `00_POST_auth_login` | Shopper | 70 | 0 | 230 | 380 | 520 |
| `POST` | `00_POST_auth_register` | Shopper | 70 | 0 | 330 | 480 | 560 |
| `GET` | `01_GET_products_page` | Shopper | 742 | 0 | 9 | 55 | 160 |
| `GET` | `02_GET_product_detail` | Shopper | 729 | 0 | 8 | 43 | 220 |
| `GET` | `03_GET_customer_detail` | Shopper | 720 | 0 | 7 | 27 | 140 |
| `GET` | `04_GET_customer_orders` | Shopper | 710 | 0 | 140 | 360 | 420 |
| `POST` | `05_POST_orders_hot_product` | Shopper | 241 | 0 | 59 | 170 | 350 |
| `POST` | `05_POST_orders_hot_out_of_stock` | Shopper | 250 | 0 | 10 | 20 | 48 |
| `POST` | `06_POST_orders_multi_product` | Shopper | 339 | 0 | 36 | 74 | 210 |
| `GET` | `07_GET_order_detail` | Shopper | 463 | 0 | 10 | 18 | 41 |
| `PATCH` | `08_PATCH_order_cancel` | Shopper | 114 | 114 (403) | 6 | 14 | 18 |
| `GET` | `09_GET_users_forbidden_test` | Shopper | 149 | 0 | 7 | 14 | 24 |
| `GET` | `10_ADMIN_GET_users` | Admin | 324 | 0 | 9 | 92 | 230 |
| `POST` | `11_ADMIN_POST_products_restock_merge` | Admin | 321 | 49 (409) | 27 | 77 | 280 |
| `PATCH` | `12_ADMIN_PATCH_product` | Admin | 314 | 22 (409) | 26 | 51 | 170 |
| `PUT` | `13_ADMIN_PUT_customer_update` | Admin | 309 | 207 (500) | 18 | 55 | 140 |
| `GET` | `14_ADMIN_GET_orders_pipeline` | Admin | 304 | 0 | 17 | 29 | 49 |
| `PATCH` | `15_ADMIN_PATCH_order_to_shipping` | Admin | 10 | 0 | 59 | 190 | 190 |
| `PATCH` | `16_ADMIN_PATCH_order_to_completed` | Admin | 10 | 0 | 38 | 49 | 49 |
| `POST` | `17_SPAMMER_POST_hot_contention` | Spammer | 1.849 | 0 | 50 | 180 | 360 |
| `POST` | `17_SPAMMER_POST_hot_out_of_stock` | Spammer | 2.330 | 0 | 10 | 20 | 50 |
| `GET` | `18_SPAMMER_GET_hot_stock_poll` | Spammer | 927 | 0 | 8 | 19 | 78 |
| **Tổng** | **Aggregated** | **Cả 3** | **11.295** | **392 (3,5%)** | **12** | **170** | **350** |

Phân tích các request lỗi:

| Tác vụ | Số lần | Mã lỗi | Nguyên nhân | Đánh giá |
| :--- | ---: | :---: | :--- | :--- |
| `08_PATCH_order_cancel` | 114 | 403 | `PATCH /orders/{id}` yêu cầu quyền `ADMIN` (`OrderController`), nhưng kịch bản gửi bằng token user thường | Phân quyền đúng; cần đổi sang token admin trong kịch bản để test luồng hủy đơn |
| `13_ADMIN_PUT_customer_update` | 207 | 500 | Kịch bản gán cùng số điện thoại `0988889999` cho cả 3 khách hàng, vi phạm ràng buộc `UNIQUE` cột `phone` | Lỗi xử lý: cần kiểm tra trùng số điện thoại và trả `400` thay vì `500` |
| `11_ADMIN_POST_products_restock_merge` | 49 | 409 | Hai admin cùng cập nhật một sản phẩm, khóa lạc quan `@Version` phát hiện xung đột | Đúng thiết kế (Phần 3): trả `409 Conflict` để chống ghi đè dữ liệu |
| `12_ADMIN_PATCH_product` | 22 | 409 | Tương tự mục trên khi cập nhật thông tin sản phẩm đồng thời | Đúng thiết kế |

### 7.4. Kiểm tra toàn vẹn dữ liệu

Kết quả đối soát trực tiếp trên MySQL sau khi kết thúc bài test:

| Hạng mục | Kết quả ghi nhận |
| :--- | :--- |
| Sản phẩm có tồn kho âm (`quantity_in_stock < 0`) | `0` (không xảy ra bán vượt tồn kho) |
| Tổng số đơn hàng hợp lệ | 2.442 đơn (`COMPLETED`: 10, `PENDING`: 2.432, `SHIPPING`: 0, `CANCELLED`: 0) |
| Sản phẩm mã 1 (Hot) | Còn lại: 105 - Đã bán: 2.209 |
| Sản phẩm mã 2 | Còn lại: 52.353 - Đã bán: 520 |
| Sản phẩm mã 3 | Còn lại: 52.809 - Đã bán: 508 |
| Sản phẩm mã 4 | Còn lại: 49.499 - Đã bán: 501 |

- Không có đơn ở trạng thái `CANCELLED` do toàn bộ request hủy đơn trong kịch bản bị từ chối với mã `403`.
- Tồn kho cuối lớn hơn tồn kho ban đầu trừ số lượng đã bán do Admin liên tục nhập thêm hàng (`11_ADMIN_POST_products_restock_merge`). Kịch bản hiện chưa lưu tổng lượng nhập thêm nên chưa đối chiếu đẳng thức `tồn đầu + nhập thêm = còn lại + đã bán`.

### 7.5. Tài nguyên hệ thống và độ trễ

Biểu đồ chi tiết được xuất tại file [`kaggle_load_benchmark.png`](./kaggle_load_benchmark.png):

| Chỉ số | Kết quả ghi nhận | Nguyên nhân / Nhận xét |
| :--- | :--- | :--- |
| CPU | Đạt ~90% trong 20 giây đầu, sau đó dao động 35% - 75% (đỉnh ~80%) | Tăng cao ở giai đoạn đầu do 70 user đồng loạt đăng ký và đăng nhập (BCrypt tốn CPU) |
| RAM | Tăng dần từ ~1,96 GB lên ~2,20 GB | Tính tổng trên toàn máy (JVM, MySQL 8.0 và Locust) |
| Thông lượng (RPS) | Ổn định ở mức 75 - 80 requests/giây sau 30 giây đầu | P95 chung phần lớn duy trì ở mức 80 - 150 ms sau pha khởi tạo |
| Độ trễ đọc cơ bản (`GET` sản phẩm, khách hàng, chi tiết đơn) | Median 7 - 10 ms, P95 < 60 ms | Truy vấn theo khóa chính hoặc có phân trang |
| Độ trễ ghi có khóa (`POST` đơn hàng, nhập kho) | Median 27 - 59 ms, P95 74 - 180 ms | Chịu thời gian chờ khóa dòng `SELECT ... FOR UPDATE` khi tranh chấp |
| Độ trễ `GET /customers/{id}/orders` (`04`) | Median 140 ms, P95 360 ms | Endpoint chưa phân trang, trả về toàn bộ lịch sử đơn hàng khi tổng số đơn vượt 2.400 |
| Độ trễ xác thực (`register`, `login`) | Median 230 - 330 ms, P95 380 - 480 ms | Chi phí tính toán của thuật toán băm mật khẩu BCrypt |

Các điểm cần khắc phục rút ra từ bài test:
- Đổi sang dùng token admin cho tác vụ `08_PATCH_order_cancel` trong kịch bản để kiểm thử luồng hủy đơn và hoàn kho.
- Kiểm tra trùng số điện thoại trong `CustomerService` và trả về mã lỗi `400` thay vì `500`.
- Bổ sung phân trang cho endpoint `GET /api/v1/customers/{id}/orders`.
- Ghi nhận tổng số lượng nhập thêm trong kịch bản test để kiểm tra đẳng thức cân bằng tồn kho.
