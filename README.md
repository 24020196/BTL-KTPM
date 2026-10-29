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

Toàn bộ quy trình kiểm thử tải (Stress / Load Testing) và giám sát tài nguyên phần cứng được thực nghiệm tự động trên môi trường **Kaggle Notebook** (File: [`benchmark-load-testing-ktpm.ipynb`](./benchmark-load-testing-ktpm.ipynb)). Kịch bản kiểm thử được thiết kế tối đa hóa độ bao phủ (Comprehensive End-to-End Testing), mô phỏng đồng thời 3 Persona người dùng chân thực và kiểm tra tính toàn vẹn CSDL sâu sau khi hoàn thành.

### 7.1. Môi trường và công cụ thử nghiệm

- **Hạ tầng thực thi:** Kaggle Linux Environment (Ubuntu 24.04 x86_64, 4 vCPU, 30GB RAM, không GPU).
- **Runtime & Database:**
  - OpenJDK 21 (`21.0.12.1-Ubuntu`).
  - MySQL Server 8.0 (`8.0.46`).
  - JVM Memory Tuning: `-Xms512m -Xmx1536m`.
- **Framework kiểm thử:** **Locust** (Headless mode) kết hợp thư viện `psutil` để thu thập tài nguyên hệ thống (CPU Total, CPU Per-core & RAM) mỗi chu kỳ 1.0 giây.
- **Tham số tải tối đa:**
  - **Số lượng Virtual Users (Concurrency):** `100 users`.
  - **Tốc độ sinh tải (Spawn rate):** `10 users/giây`.
  - **Thời gian chạy thử nghiệm:** `2 phút 30 giây` (150 giây).

### 7.2. Kịch bản kiểm thử đa Persona & Tương tranh tối đa (Multi-Persona & Stress Scenarios)

Hệ thống kiểm thử bao phủ toàn bộ 14 API trên 5 Controller thông qua 3 nhóm người dùng ảo (Personas) chạy song song:

1. **Persona 1: CustomerShopperUser (Tỷ trọng 70%) - Luồng mua sắm tuần tự:**
   - **Xác thực (`00_POST_auth_register`, `00_POST_auth_login`):** Tự động đăng ký và đăng nhập tài khoản riêng để nhận JWT Token.
   - **Tra cứu sản phẩm (`01_GET_products_page`, `02_GET_product_detail`):** Đọc danh mục phân trang và chi tiết ngẫu nhiên từng sản phẩm.
   - **Hồ sơ khách hàng (`03_GET_customer_detail`, `04_GET_customer_orders`):** Xem thông tin khách hàng và lịch sử các đơn hàng đã đặt.
   - **Tạo đơn hàng cạnh tranh (`05_POST_orders_hot_product`):** Đặt mua sản phẩm Hot (kho ban đầu 200 đơn vị) nhằm kiểm thử khóa bi quan `@Lock(PESSIMISTIC_WRITE)` (`SELECT ... FOR UPDATE`) và chống bán vượt tồn kho (Overselling). Khi hết hàng, hệ thống trả về lỗi 400 hợp lệ (`05_POST_orders_hot_out_of_stock`).
   - **Tạo đơn nhiều sản phẩm chống Deadlock (`06_POST_orders_multi_product`):** Đặt đơn chứa nhiều sản phẩm với thứ tự xáo trộn ngẫu nhiên để kiểm thử cơ chế sắp xếp mã sản phẩm tăng dần trước khi khóa dòng.
   - **Xem chi tiết đơn (`07_GET_order_detail`):** Truy vấn thông tin chi tiết đơn hàng vừa tạo.
   - **Hủy đơn hoàn kho (`08_PATCH_order_cancel`):** Hủy đơn PENDING để kiểm tra cập nhật trạng thái và transaction hoàn trả số lượng tồn kho tự động.
   - **Kiểm thử phân quyền (`09_GET_users_forbidden_test`):** User thường thử gọi API Admin (`GET /api/v1/users`) để xác nhận hệ thống chặn đúng lỗi `403 Forbidden`.

2. **Persona 2: WarehouseAdminUser (Tỷ trọng 20%) - Quản trị kho & Vận hành đơn hàng:**
   - **Quản trị người dùng (`10_ADMIN_GET_users`):** Tra cứu danh sách tài khoản người dùng với quyền `ROLE_ADMIN`.
   - **Nhập hàng cộng dồn (`11_ADMIN_POST_products_restock_merge`):** Gọi `POST /api/v1/products` với tên sản phẩm đã có sẵn để kiểm thử tính năng tự động cộng dồn số lượng tồn kho và cập nhật giá mới ngay dưới áp lực tải đồng thời.
   - **Cập nhật sản phẩm (`12_ADMIN_PATCH_product`):** Điều chỉnh giá bán và trạng thái sản phẩm bằng `PATCH`.
   - **Cập nhật khách hàng (`13_ADMIN_PUT_customer_update`):** Cập nhật thông tin liên hệ của khách hàng bằng `PUT`.
   - **Duyệt chu trình đơn hàng (`14_ADMIN_GET_orders_pipeline`, `15_ADMIN_PATCH_order_to_shipping`, `16_ADMIN_PATCH_order_to_completed`):** Quét các đơn `PENDING` chuyển sang `SHIPPING` và từ `SHIPPING` chuyển sang `COMPLETED`.

3. **Persona 3: FlashSaleSpammerUser (Tỷ trọng 10%) - Săn sale tần suất cao:**
   - Nhịp gửi yêu cầu cực nhanh (`0.1s - 0.4s`): Bắn liên tục request đặt mua Hot Product (`17_SPAMMER_POST_hot_contention`) và tra cứu tồn kho (`18_SPAMMER_GET_hot_stock_poll`) nhằm đẩy mức độ tranh chấp khóa bi quan và chịu tải của hàng đợi CSDL lên mức cực hạn.

### 7.3. Kết quả đo đạc định lượng (Locust Statistics)

Dưới áp lực thử nghiệm tối đa của 3 Persona, Locust ghi nhận kết quả chi tiết trên toàn bộ 18 tác vụ:

| Method | Endpoint / Tác vụ | Persona | Failure Count | Median (ms) | P95 (ms) | Đánh giá & Cơ chế kiểm thử |
| :--- | :--- | :---: | :---: | :---: | :---: | :--- |
| `POST` | `00_POST_auth_login` | Shopper | 0 | ~180 ms | ~410 ms | Xác thực BCrypt & sinh JWT token |
| `POST` | `00_POST_auth_register` | Shopper | 0 | ~210 ms | ~320 ms | Mã hóa mật khẩu một chiều BCrypt |
| `GET` | `01_GET_products_page` | Shopper | 0 | ~11 ms | ~45 ms | Đọc phân trang danh mục sản phẩm |
| `GET` | `02_GET_product_detail` | Shopper | 0 | ~9 ms | ~30 ms | Đọc chi tiết sản phẩm theo ID |
| `GET` | `03_GET_customer_detail` | Shopper | 0 | ~10 ms | ~32 ms | Đọc thông tin khách hàng |
| `GET` | `04_GET_customer_orders` | Shopper | 0 | ~12 ms | ~35 ms | Đọc lịch sử đơn hàng của khách |
| `POST` | `05_POST_orders_hot_product` | Shopper | 0 | ~48 ms | ~120 ms | Khóa dòng trừ kho, chống Overselling |
| `POST` | `05_POST_orders_hot_out_of_stock` | Shopper | 0 | ~12 ms | ~20 ms | Từ chối chuẩn xác khi hết hàng tồn kho |
| `POST` | `06_POST_orders_multi_product` | Shopper | 0 | ~45 ms | ~85 ms | Đơn nhiều món xáo trộn, chống Deadlock |
| `GET` | `07_GET_order_detail` | Shopper | 0 | ~13 ms | ~40 ms | Đọc chi tiết đơn & order_details |
| `PATCH` | `08_PATCH_order_cancel` | Shopper | 0 | ~44 ms | ~80 ms | Hủy đơn, tự động hoàn kho |
| `GET` | `09_GET_users_forbidden_test` | Shopper | 0 | ~8 ms | ~25 ms | Bắt chuẩn xác 403 Forbidden |
| `GET` | `10_ADMIN_GET_users` | Admin | 0 | ~12 ms | ~35 ms | Phân quyền ADMIN đọc danh sách User |
| `POST` | `11_ADMIN_POST_products_restock_merge` | Admin | 0 | ~35 ms | ~75 ms | Cộng dồn kho & update giá khi trùng tên |
| `PATCH` | `12_ADMIN_PATCH_product` | Admin | 0 | ~28 ms | ~65 ms | Cập nhật giá và trạng thái sản phẩm |
| `PUT` | `13_ADMIN_PUT_customer_update` | Admin | 0 | ~25 ms | ~60 ms | Cập nhật thông tin khách hàng |
| `PATCH` | `15_ADMIN_PATCH_order_to_shipping` | Admin | 0 | ~30 ms | ~70 ms | Chuyển đơn PENDING -> SHIPPING |
| `PATCH` | `16_ADMIN_PATCH_order_to_completed` | Admin | 0 | ~28 ms | ~68 ms | Chuyển đơn SHIPPING -> COMPLETED |
| `POST` | `17_SPAMMER_POST_hot_contention` | Spammer | 0 | ~50 ms | ~130 ms | Tranh chấp khóa bi quan tần suất cao |
| `GET` | `18_SPAMMER_GET_hot_stock_poll` | Spammer | 0 | ~8 ms | ~22 ms | Kiểm tra nhanh tồn kho |
| **Tổng** | **Aggregated Toàn Hệ Thống** | **Cả 3** | **0 (0.0%)** | **~14 ms** | **~85 ms** | **100% Request hợp lệ, không lỗi hệ thống** |

### 7.4. Xác minh tính toàn vẹn CSDL sau bài test (Deep Database Integrity Verification)

Sau khi Locust hoàn thành bài test tải, script kiểm tra CSDL độc lập (`Cell 9`) kết nối trực tiếp vào MySQL để thẩm định toàn diện:

1. **Tồn kho không âm:** Kiểm tra toàn bộ bảng `products`, xác nhận `quantity_in_stock >= 0` tuyệt đối (không có bất kỳ sản phẩm nào bị âm).
2. **Cân bằng số lượng (Stock Balancing):**
   $$\text{Tồn kho hiện tại} + \text{Số lượng đã bán (các đơn không bị hủy)} = \text{Tồn kho ban đầu} + \text{Tổng số lượng nhập thêm}$$
   - Sản phẩm Hot (kho ban đầu 200) được bán hết sạch (về đúng 0), không xảy ra tình trạng bán vượt quá số lượng (Zero Overselling).
   - Các sản phẩm trong đơn nhiều món (Multi-products) khớp chính xác 100% giữa số lượng trừ kho và số lượng trong bảng `order_details`.
3. **Tính toàn vẹn của chu trình đơn hàng:**
   - Các đơn hàng được phân bổ đầy đủ trên cả 4 trạng thái: `PENDING`, `SHIPPING`, `COMPLETED`, `CANCELLED`.
   - Các đơn bị hủy (`CANCELLED`) đều được hoàn trả tồn kho đầy đủ, không làm sai lệch số lượng hàng hóa.
4. **Kết luận kiểm định:** `DATABASE 100% CONSISTENT & INTEGRITY VERIFIED`.

### 7.5. Phân tích thống kê & Tài nguyên hệ thống (CPU, RAM, RPS, Latency)

Biểu đồ trực quan 3 bảng điều khiển (3 Subplots) được xuất ra file [kaggle_load_benchmark.png](./docs/images/benchmark_kaggle_chart.png):

1. **Bảng 1 - System Resource Utilization (CPU & RAM):**
   - **CPU:** Tăng cao trong 15-20 giây đầu khi các User đồng loạt khởi tạo và đăng ký tài khoản (do thuật toán băm BCrypt tiêu tốn CPU), sau đó ổn định ở mức 25% – 45% trong suốt quá trình sustain tải.
   - **RAM:** Tổng bộ nhớ RAM toàn máy (chạy đồng thời Spring Boot JVM, MySQL Server 8 và Locust Runner) giữ ổn định ở mức 2.0 – 2.2 GB, Garbage Collection hoạt động trơn tru không rò rỉ bộ nhớ.
2. **Bảng 2 - Throughput (RPS) vs p95 Latency (ms):**
   - Thông lượng toàn hệ thống duy trì đều đặn ở mức 35 – 45 requests/giây.
   - P95 Latency toàn hệ thống giữ vững dưới 90 ms trong phần lớn thời gian chạy thử nghiệm.
3. **Bảng 3 - Endpoint Latency Distribution (Median vs p95):**
   - Các thao tác đọc dữ liệu (GET) có độ trễ cực thấp (Median 8 – 12 ms, P95 25 – 45 ms).
   - Các thao tác ghi giao dịch có khóa dòng (POST Order, Nhập hàng) đạt độ trễ tối ưu (Median 35 – 50 ms, P95 70 – 130 ms).
   - Các thao tác bảo mật (Login/Register) có độ trễ cao hơn do chủ đích thiết kế của BCrypt để chống brute-force.
