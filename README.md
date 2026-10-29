# KTPM - Hệ thống Quản lý Kho hàng

## Mục lục

- [Phần 1: Mô tả bài toán và nghiệp vụ cơ bản](#phần-1-mô-tả-bài-toán-và-nghiệp-vụ-cơ-bản)
- [Phần 2: Đặc tả các API (RESTful API Specification)](#phần-2-đặc-tả-các-api-restful-api-specification)
- [Phần 3: Một số vấn đề của hệ thống (đã xử lý ở Phase 1)](#phần-3-một-số-vấn-đề-của-hệ-thống-đã-xử-lý-ở-phase-1)
- [Phần 4: Những điểm gợi ý cải tiến ở Phase 2](#phần-4-những-điểm-gợi-ý-cải-tiến-ở-phase-2)
- [Phần 5: Tổ chức mã nguồn](#phần-5-tổ-chức-mã-nguồn)
- [Phần 6: Cài đặt và triển khai (Deploy)](#phần-6-cài-đặt-và-triển-khai-deploy)
- [Phần 7: Kiểm thử tải và đường cơ sở hiệu năng Phase 1](#phần-7-kiểm-thử-tải-và-đường-cơ-sở-hiệu-năng-phase-1)

---

## PHẦN 1: MÔ TẢ BÀI TOÁN VÀ NGHIỆP VỤ CƠ BẢN

### 1.1. Tổng quan bài toán
Hệ thống cung cấp nền tảng quản lý tồn kho và quan hệ khách hàng thông qua giao thức RESTful API. Mục tiêu cốt lõi là số hóa quy trình xử lý giao dịch đơn hàng, chăm sóc khách hàng và kiểm soát chặt chẽ biến động hàng hóa tồn kho.

### 1.2. Mô hình thực thể cốt lõi

| Thực thể | Vai trò trong hệ thống | Mối quan hệ nghiệp vụ |
| :--- | :--- | :--- |
| 1. User | Tài khoản định danh truy cập hệ thống. | Phân quyền người dùng (ADMIN / USER), xác thực qua JWT. |
| 2. Customer | Hồ sơ khách hàng mua sắm. | Lưu trữ thông tin liên hệ và lịch sử giao dịch các đơn hàng. |
| 3. Product | Danh mục hàng hóa thương mại. | Quản lý đơn giá và tồn kho; tham chiếu trong chi tiết đơn hàng (OrderDetails). |
| 4. Order & Order Details | Giao dịch mua bán. | Kết nối Customer và danh sách Product trong một đơn hàng duy nhất. |

### 1.3. Quy trình và quy tắc nghiệp vụ cơ bản

Chi tiết quy trình và quy tắc nghiệp vụ của từng phân hệ (Xác thực & Phân quyền, Khách hàng, Tồn kho, Đơn hàng & Sơ đồ trạng thái):
- Xem chi tiết tại: [`docs/business-rules.md`](./docs/business-rules.md)

---

## PHẦN 2: ĐẶC TẢ CÁC API (RESTFUL API SPECIFICATION)

- **Base URL:** `http://localhost:3000/api/v1`
- **Swagger UI:** `http://localhost:3000/swagger-ui/index.html`
- **Đặc tả OpenAPI (JSON):** [`docs/openapi.json`](./docs/openapi.json)
- **Chi tiết đặc tả API:** [`docs/api-specification.md`](./docs/api-specification.md)

---

## PHẦN 3: MỘT SỐ VẤN ĐỀ CỦA HỆ THỐNG (ĐÃ XỬ LÝ Ở PHASE 1)

Chi tiết kịch bản gây lỗi khi xử lý đồng thời (ghi đè tồn kho, đứt gãy giao dịch, bán vượt tồn kho, deadlock) và giải pháp đã áp dụng ở Phase 1:
- Xem chi tiết tại: [`docs/phase1-issues.md`](./docs/phase1-issues.md)

---

## PHẦN 4: NHỮNG ĐIỂM GỢI Ý CẢI TIẾN Ở PHASE 2

Chi tiết các điểm gợi ý cải tiến ở Phase 2 (xử lý duplicate request, thu hồi Stateless JWT, chặn tự cấp quyền ADMIN khi đăng ký,xử lý bất đồng bộ và cảnh báo tồn kho chạm ngưỡng an toàn  mở rộng thực thể Employee, bộ lọc nâng cao, caching và audit log):
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

Hệ thống tổ chức theo kiến trúc phân tầng (Layered Architecture). Một request đi qua các tầng theo thứ tự:

```
Client
  -> JwtAuthenticationFilter   (xác thực token)
  -> Controller                (nhận request, validate dữ liệu, phân quyền)
  -> Service                   (xử lý nghiệp vụ, transaction, khóa dữ liệu)
  -> Repository                (truy xuất CSDL)
  -> MySQL
```

Dữ liệu đầu vào và đầu ra được Mapper chuyển đổi giữa Entity và Model (`Request` / `Response`) trước khi trả về Client.

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
| Model (`Request` / `Response`) | Tách biệt dữ liệu đầu vào/đầu ra của API với cấu trúc bảng CSDL | `ProductPatchRequest`, `OrderResponse` |
| Mapper | Chuyển đổi dữ liệu giữa Entity và Model | `ProductMapper.patchEntity` |
| Exceptions | Chuẩn hóa định dạng lỗi JSON trả về Client | `GlobalExceptionHandler` |

### 5.5. Cơ sở dữ liệu

| Bảng | Khóa chính | Khóa ngoại / Ràng buộc |
| :--- | :--- | :--- |
| `users` | `user_id` | `username` UNIQUE |
| `customers` | `customer_number` | `user_id` -> `users`, `phone` UNIQUE |
| `products` | `product_code` | `version` (khóa lạc quan) |
| `orders` | `order_number` | `customer_number` -> `customers`, `version` (khóa lạc quan) |
| `order_details` | (`order_number`, `product_code`) | `order_number` -> `orders` (ON DELETE CASCADE), `product_code` -> `products` |

- Schema được quản lý hoàn toàn bằng Flyway; Hibernate đặt `ddl-auto: validate` (chỉ kiểm tra, không tự sửa bảng).
- Khi cần thay đổi schema, tạo file migration mới dạng `V{n}__mo_ta.sql`. Không sửa các file migration đã chạy vì Flyway sẽ báo lỗi checksum.
- Xem chi tiết cấu trúc bảng, kiểu dữ liệu và ràng buộc tại: [`docs/database.md`](./docs/database.md)

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

| Biến | Ý nghĩa | Ví dụ |
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

## PHẦN 7: KIỂM THỬ TẢI VÀ ĐƯỜNG CƠ SỞ HIỆU NĂNG PHASE 1

Kết quả dưới đây là **đường cơ sở (baseline)** hiệu năng của Phase 1, để Phase 2 chạy lại cùng bộ kiểm thử và đối chiếu.

- **Phiên bản được đo:** commit `808b8634c7832932fc06b495b21c0094d3939cfe`
- **Công cụ:** Locust 2.47.0, chạy trên Kaggle Notebook: [notebookf98e90cdff](https://www.kaggle.com/code/luc102t/notebookf98e90cdff)
- **Môi trường:** AMD EPYC 7B12 (4 core), 31.3 GB RAM, OpenJDK 21, MySQL 8.0.46. Ứng dụng và MySQL dùng core 0-2, Locust dùng core 3.
- **Kế hoạch kiểm thử:** [`docs/load-testing.md`](./docs/load-testing.md)
- **Báo cáo đầy đủ:** [`docs/benchmark/phase1/REPORT.md`](./docs/benchmark/phase1/REPORT.md)
- **Mã nguồn bộ kiểm thử:** [`loadtest/`](./loadtest)

### 7.1. Phương pháp

Mọi bài bắt đầu từ cùng một CSDL sinh xác định (seed `20261029`: 1000 sản phẩm, 500 khách hàng, 20000 đơn hàng) và được khôi phục trước mỗi bài. Người dùng ảo gồm 80% khách mua hàng và 20% admin.

| Bài | Cấu hình | Mục đích |
| :--- | :--- | :--- |
| Smoke | 5 người dùng ảo, 30 s | Kiểm tra kịch bản và API |
| Warmup | 50 người dùng ảo, 60 s | Làm nóng JVM và bộ đệm; không tính vào kết quả |
| Tải thường | 100 người dùng ảo, 180 s, lặp 3 lần | Đo hiệu năng ở tải dự kiến và độ dao động |
| Breakpoint | 25 → 800 người dùng ảo, mỗi bậc 60 s | Tìm năng lực tối đa và nút thắt |
| Spike | 50 → 500 → 50 người dùng ảo | Kiểm tra chịu sốc tải và hồi phục |
| Flash sale | 200 người dùng ảo mua 1 sản phẩm (tồn kho 500) | Kiểm tra khóa dòng, không bán vượt |

Tiêu chí đạt: p95 ≤ 500 ms, p99 ≤ 1000 ms, lỗi ≤ 1%. Sau mỗi bài, tồn kho được đối soát theo công thức: tồn sau = tồn trước − đã bán + hoàn kho + nhập thêm.

### 7.2. Kết quả

Cả 6 tiêu chí đều đạt; 8/8 bài có tồn kho cân bằng.

| Bài | Kết quả |
| :--- | :--- |
| Smoke | 208 request, lỗi 0% |
| Tải thường | 53.5 req/s; p50 6.0 ms, p95 48.1 ms, p99 79.6 ms; lỗi 0% |
| Breakpoint | Tối đa trong SLO: **307.4 req/s** tại 300 người dùng ảo; p95 vượt 500 ms từ 400 người dùng ảo |
| Spike | Lỗi 0%; p95 từ 43.1 ms lên 115.9 ms; hồi phục sau 10 s |
| Flash sale | Bán 500/500, bán vượt 0, deadlock 0 |

![Throughput, p95, p99 và CPU theo số người dùng ảo](./docs/benchmark/phase1/breakpoint.png)

Độ trễ theo endpoint ở tải thường xem tại [`endpoints_average_load.png`](./docs/benchmark/phase1/endpoints_average_load.png). Chậm nhất: `POST /auth/login` (p95 88 ms), `GET /customers/{id}/orders` (71 ms), `POST /orders` (50 ms), `GET /orders?page` (47 ms). Các endpoint đọc sản phẩm và khách hàng đều dưới 10 ms.

### 7.3. Nhận xét và hạn chế

- Nút thắt là CPU: ở bậc cuối, 3 core của ứng dụng và MySQL gần như dùng hết (ứng dụng 51%, MySQL 49%). Throughput dừng ở khoảng 330 req/s, độ trễ tăng nhưng lỗi vẫn bằng 0.
- Bài flash sale có p99 là 2074.9 ms (vượt 1000 ms) do 200 giao dịch xếp hàng chờ khóa dòng của cùng một sản phẩm. Tiêu chí của bài này là tính đúng đắn của dữ liệu (không bán vượt, không deadlock), và đã đạt.
- Ứng dụng, MySQL và Locust chạy chung một máy nên con số tuyệt đối không phản ánh môi trường sản xuất; giá trị của baseline nằm ở việc so sánh trên cùng phần cứng. Kaggle không đảm bảo cùng loại CPU giữa các lần chạy, vì vậy chỉ so sánh trực tiếp khi mục Môi trường của hai báo cáo trùng nhau.
- Breakpoint, spike và flash sale chỉ chạy một lần nên không có độ lệch chuẩn.

### 7.4. Chạy lại để đối chiếu ở Phase 2

Không đổi tham số nào trong `loadtest/`. Trong notebook, đặt `REFS = {"phase1": "808b8634c7832932fc06b495b21c0094d3939cfe", "phase2": "<commit Phase 2>"}`, chạy lại và so sánh với `docs/benchmark/phase1/REPORT.md`. Các bước chi tiết xem mục 8 của [`docs/load-testing.md`](./docs/load-testing.md).
