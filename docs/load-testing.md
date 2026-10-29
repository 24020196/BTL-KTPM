# Kế hoạch kiểm thử tải Phase 1

| Thông tin | Giá trị |
| :--- | :--- |
| Loại tài liệu | Kế hoạch kiểm thử (test plan) |
| Phiên bản | 1.0 |
| Ngày cập nhật | 2026-10-29 |
| Tác giả | Lực (`Luc102T`) |
| Phạm vi | Đo đường cơ sở (baseline) của Phase 1; các nhóm làm Phase 2 dùng lại để đối chiếu |
| Phiên bản được đo | Commit cuối của Phase 1 trên nhánh `main`; mã commit được ghi trong báo cáo `docs/benchmark/phase1/REPORT.md` |
| Mã nguồn bộ đo | [Thư mục `loadtest/`](../loadtest) |

Tài liệu này mô tả cách kiểm thử tải hệ thống ở Phase 1: kiểm thử cái gì, bằng phương pháp nào, theo tiêu chí đạt nào và trong môi trường nào. Kết quả là đường cơ sở để các nhóm làm Phase 2 chạy lại cùng bộ đo và đối chiếu sau khi cải tiến. Cấu trúc tài liệu theo mẫu kế hoạch kiểm thử của chuẩn ISO/IEC/IEEE 29119-3. Kết quả của mỗi lần chạy được ghi trong báo cáo hoàn thành kiểm thử `REPORT.md`, do bộ đo sinh tự động.

## Mục lục

1. [Bối cảnh kiểm thử](#1-bối-cảnh-kiểm-thử)
2. [Giả định và ràng buộc](#2-giả-định-và-ràng-buộc)
3. [Rủi ro và biện pháp giảm thiểu](#3-rủi-ro-và-biện-pháp-giảm-thiểu)
4. [Chiến lược kiểm thử](#4-chiến-lược-kiểm-thử)
5. [Tiêu chí đạt](#5-tiêu-chí-đạt)
6. [Môi trường kiểm thử](#6-môi-trường-kiểm-thử)
7. [Sản phẩm bàn giao](#7-sản-phẩm-bàn-giao)
8. [Hướng dẫn đối chiếu cho nhóm làm Phase 2](#8-hướng-dẫn-đối-chiếu-cho-nhóm-làm-phase-2)
9. [Hướng dẫn chạy](#9-hướng-dẫn-chạy)
10. [Hạn chế đã biết](#10-hạn-chế-đã-biết)
11. [Thuật ngữ](#11-thuật-ngữ)
12. [Tài liệu tham khảo](#12-tài-liệu-tham-khảo)

## 1. Bối cảnh kiểm thử

### 1.1. Đối tượng kiểm thử

Đối tượng kiểm thử (SUT) là backend Spring Boot 4 kết nối MySQL 8, cung cấp REST API tại `/api/v1`. Bài kiểm thử bao phủ các nhóm endpoint sau:

| Nhóm | Endpoint |
| :--- | :--- |
| Xác thực | `POST /auth/login` |
| Sản phẩm | `GET /products`, `GET /products/{id}`, `POST /products`, `PATCH /products/{id}` |
| Khách hàng | `GET /customers`, `GET /customers/{id}`, `PUT /customers/{id}`, `GET /customers/{id}/orders` |
| Đơn hàng | `GET /orders`, `GET /orders/{id}`, `POST /orders`, `PATCH /orders/{id}` |
| Người dùng | `GET /users` |

### 1.2. Mục tiêu kiểm thử

Bài kiểm thử phải trả lời năm câu hỏi:

1. Ở tải bình thường, độ trễ và tỉ lệ lỗi là bao nhiêu, và kết quả có ổn định giữa các lần chạy không?
2. Hệ thống phục vụ tối đa bao nhiêu request mỗi giây mà vẫn đạt tiêu chí, và thành phần nào nghẽn trước?
3. Khi tải tăng đột ngột, hệ thống có lỗi không và mất bao lâu để hồi phục?
4. Dưới tải đồng thời cao, dữ liệu có còn đúng không?
5. Các chỉ số trên được ghi lại đủ chi tiết để nhóm làm Phase 2 đo lại trong cùng điều kiện và biết chúng thay đổi bao nhiêu?

### 1.3. Đối tượng sử dụng tài liệu

| Người đọc | Dùng tài liệu để |
| :--- | :--- |
| Nhóm thực hiện Phase 1 | Chạy bài đo đường cơ sở và công bố báo cáo |
| Các nhóm làm Phase 2 | Chạy lại đúng bộ đo trên phiên bản đã cải tiến và đối chiếu với Phase 1 (mục 8) |
| Giảng viên | Kiểm tra phương pháp đo, tiêu chí đạt và tính tái lập của kết quả |

## 2. Giả định và ràng buộc

- Đề bài yêu cầu đánh giá trên một cấu hình phần cứng duy nhất. Mọi số liệu chính thức được đo trên Kaggle CPU.
- Kaggle cấp một máy. Bộ sinh tải, ứng dụng và CSDL chạy chung máy, được tách bằng cách ghim CPU (mục 6).
- Một phiên Kaggle có giới hạn thời gian. Thời lượng các bài kiểm thử được chọn để một phiên bản chạy xong trong khoảng 35 phút.
- Nhóm tự đặt tiêu chí độ trễ (mục 5.1), vì đề bài không quy định.
- Kịch bản tải giả định tỉ lệ đọc/ghi khoảng 80/20, phổ biến với hệ thống quản lý bán hàng.

## 3. Rủi ro và biện pháp giảm thiểu

| Rủi ro | Ảnh hưởng | Biện pháp |
| :--- | :--- | :--- |
| Bộ sinh tải thiếu CPU | Throughput đo được thấp hơn năng lực thật | Ghim Locust vào core riêng; cảnh báo khi Locust dùng quá 0.85 core |
| JIT và bộ đệm chưa nóng | Độ trễ đầu bài cao bất thường | Bài warmup; bỏ 30 s đầu của bài tải thường và 10 s đầu mỗi bậc |
| Dữ liệu thay đổi giữa các bài | Bài sau chịu ảnh hưởng của bài trước | Khôi phục CSDL về snapshot trước mỗi bài |
| Nhiễu giữa các lần chạy | Kết luận sai rằng Phase 2 tốt hơn | Lặp 3 lần; dùng ngưỡng nhiễu khi so sánh; chạy A/A test (mục 5.3) |
| Máy Kaggle khác nhau giữa các phiên | Số liệu Phase 2 đo trên máy khác với máy đo Phase 1 | Nhóm làm Phase 2 đo lại commit Phase 1 cùng phiên bản của mình trong cùng một phiên Kaggle (mục 8) |
| Nhóm làm Phase 2 sửa bộ đo | Hai phiên bản bị đo bằng hai kịch bản khác nhau | Dùng bộ đo tại đúng commit Phase 1; quy định rõ phần được và không được thay đổi (mục 8.1) |
| Request nhập kho bị timeout phía client nhưng server vẫn ghi | Đối soát tồn kho báo lệch giả | Timeout client 30 s; báo cáo liệt kê chi tiết từng sản phẩm lệch để kiểm tra |

## 4. Chiến lược kiểm thử

### 4.1. Phương pháp tham chiếu

| Nguồn | Nội dung áp dụng |
| :--- | :--- |
| [Phân loại bài test tải của Grafana k6](https://grafana.com/docs/k6/latest/testing-guides/test-types/) | Chia thành smoke, tải thường, breakpoint, spike; chạy smoke trước; tăng dần tải |
| [TechEmpower Framework Benchmarks](https://github.com/TechEmpower/FrameworkBenchmarks) | Loại kết quả warmup; đo nhiều mức đồng thời; tách bộ sinh tải khỏi máy chủ; công bố cấu hình và dữ liệu kết quả |
| [Tài liệu Locust](https://docs.locust.io/) | Tải theo bậc bằng `LoadTestShape`; phân loại phản hồi bằng `catch_response` |

### 4.2. Mô hình tải

| Nhóm người dùng | Tỉ trọng | Thao tác (trọng số) |
| :--- | :--- | :--- |
| Shopper | 80% | Xem trang sản phẩm (30), xem chi tiết sản phẩm (25), đặt đơn 1–3 sản phẩm (12), xem chi tiết đơn (10), xem hồ sơ (8), xem lịch sử đơn (5), đăng nhập lại (2) |
| Admin | 20% | Chuyển trạng thái đơn, trong đó 15% là hủy đơn (15); xem danh sách đơn (10); nhập kho (5); sửa giá (5); xem danh sách khách hàng (5); sửa khách hàng (3); xem danh sách người dùng (2) |
| FlashSale | Chỉ trong bài flashsale | Liên tục mua 1 đơn vị sản phẩm HOT có tồn kho 500 |

Mỗi Shopper đăng nhập bằng một tài khoản riêng và đặt đơn cho khách hàng gắn với tài khoản đó.

### 4.3. Dữ liệu kiểm thử

`loadtest/seed.py` sinh dữ liệu từ một giá trị `SEED` cố định, nên mọi lần chạy có cùng dữ liệu ban đầu.

| Bảng | Số bản ghi | Ghi chú |
| :--- | ---: | :--- |
| `users` | 301 | 1 ADMIN và 300 USER |
| `customers` | 500 | 300 khách hàng gắn với tài khoản USER |
| `products` | 1000 | Sản phẩm mã 1 là HOT, tồn kho 500; 2% sản phẩm INACTIVE |
| `orders` | 20000 | 70% COMPLETED, 10% CANCELLED, 10% SHIPPING, 10% PENDING |
| `order_details` | Khoảng 40000 | 1–3 sản phẩm mỗi đơn |

Sau khi nạp, bộ đo chụp snapshot CSDL và khôi phục snapshot trước mỗi bài kiểm thử.

### 4.4. Các bài kiểm thử

| Bài | Người dùng ảo | Thời gian nghỉ (s) | Thời lượng | Mục đích |
| :--- | :--- | :--- | :--- | :--- |
| smoke | 5 | 0.5–1.0 | 30 s | Xác nhận kịch bản và API chạy đúng |
| warmup | 50 | 0.5–1.5 | 60 s | Làm nóng JIT, connection pool, buffer pool; không báo cáo |
| load (×3) | 100 | 1.0–3.0 | 180 s, bỏ 30 s đầu | Đo hiệu năng ở tải bình thường và độ dao động |
| breakpoint | 25, 50, 100, 150, 200, 300, 400, 500, 650, 800 | 0.5–1.5 | 60 s mỗi bậc, bỏ 10 s đầu mỗi bậc | Tìm năng lực tối đa và thành phần nghẽn; dừng khi p95 > 2000 ms hoặc lỗi > 5% |
| spike | 50 → 500 → 50 | 1.0–3.0 | 60 s + 60 s + 120 s | Đo độ trễ khi tải tăng đột ngột và thời gian hồi phục |
| flashsale | 200 | 0–0.05 | 45 s | Kiểm tra khóa dòng khi nhiều người cùng mua một sản phẩm |

Các bài chạy theo đúng thứ tự trong bảng. Nếu smoke không đạt, các bài sau không chạy.

### 4.5. Phân loại phản hồi

| Nhóm | Mã trạng thái | Tính vào tỉ lệ lỗi |
| :--- | :--- | :--- |
| Thành công | 200, 201 theo từng endpoint | Không |
| Từ chối nghiệp vụ hợp lệ | 400 khi hết hàng hoặc sai luồng trạng thái; 409 khi xung đột khóa lạc quan | Không, nhưng được đếm riêng |
| Lỗi | 5xx, mã không mong đợi, timeout, lỗi kết nối | Có |

### 4.6. Số liệu thu thập

| Nhóm | Chỉ số | Nguồn |
| :--- | :--- | :--- |
| Phía client | Số request, throughput (req/s), p50/p90/p95/p99/max (ms), tỉ lệ lỗi (%), phân bố mã trạng thái theo endpoint | Log thô của từng request |
| Phía server | CPU (core) và RSS (MB) của ứng dụng, MySQL và Locust; số truy vấn mỗi giây; số lần chờ khóa dòng, thời gian chờ trung bình (ms) và số deadlock của InnoDB | `psutil` và `SHOW GLOBAL STATUS`, lấy mẫu mỗi giây |
| Đúng đắn | Kết quả đối soát tồn kho, số sản phẩm âm kho, số đơn sai tổng tiền | Truy vấn CSDL sau mỗi bài |

Percentile được tính trực tiếp từ log thô của từng request, không dùng giá trị ước lượng của Locust.

## 5. Tiêu chí đạt

### 5.1. Tiêu chí hiệu năng

| Chỉ số | Ngưỡng |
| :--- | :--- |
| p95 | ≤ 500 ms |
| p99 | ≤ 1000 ms |
| Tỉ lệ lỗi | ≤ 1% |

Ngưỡng được khai báo trong biến `SLO` của `loadtest/analyze.py`. Năng lực tối đa được định nghĩa là throughput cao nhất của một bậc breakpoint mà vẫn đạt cả ba ngưỡng.

### 5.2. Tiêu chí đúng đắn dữ liệu

Sau mỗi bài kiểm thử, mọi sản phẩm phải thỏa phương trình:

```text
tồn kho sau = tồn kho trước − số đã bán (đơn mới, chưa hủy)
                            + số hoàn kho (đơn có sẵn bị hủy trong bài)
                            + số nhập thêm
```

Ngoài ra, không sản phẩm nào có tồn kho âm, và tổng tiền mỗi đơn mới bằng tổng `quantity × price_each` của các dòng chi tiết. Ở bài flashsale, số đơn client nhận mã 201 phải bằng số đơn vị đã bán trong CSDL, và số đơn vị bán vượt phải bằng 0.

### 5.3. Tiêu chí hợp lệ của phép đo

| Kiểm tra | Điều kiện | Khi không đạt |
| :--- | :--- | :--- |
| Bộ sinh tải không nghẽn | p95 CPU của Locust ≤ 0.85 core | Báo cáo ghi cảnh báo; không dùng bậc đó để kết luận |
| Định luật Little | Throughput đo được gần với `users / (thời gian nghỉ + thời gian phản hồi)` | Kiểm tra lại kịch bản và bộ sinh tải |
| Độ dao động | Hệ số biến thiên của 3 lần chạy tải thường | Chênh lệch nhỏ hơn ngưỡng nhiễu không được kết luận |
| Ngưỡng nhiễu khi so sánh | 2 × hệ số biến thiên lớn nhất, tối thiểu 5%; 20% với chỉ số chỉ đo một lần | Kết luận là "Trong ngưỡng nhiễu" |
| A/A test | So sánh một phiên bản với chính nó cho ra "Trong ngưỡng nhiễu" hoặc "Không đổi" ở mọi chỉ số | Không tin vào kết quả so sánh |

## 6. Môi trường kiểm thử

| Thành phần | Cấu hình |
| :--- | :--- |
| Phần cứng | Kaggle CPU; model CPU, số core và RAM được ghi lại khi chạy |
| Phân bổ CPU | Ứng dụng và MySQL dùng core `0` đến `n-2`; Locust dùng core `n-1` |
| Runtime | OpenJDK 21, `-Xms1g -Xmx1g -XX:+UseG1GC` |
| Cơ sở dữ liệu | MySQL 8.0, cấu hình mặc định của gói Ubuntu |
| Bộ sinh tải | Locust, `FastHttpUser` |
| Cấu hình ứng dụng khi đo | `SPRING_JPA_SHOW_SQL=false`, `LOGGING_LEVEL_ROOT=WARN` |

Mỗi lần chạy ghi `environment.json` gồm commit Git, model CPU, số core, RAM, phiên bản Java, MySQL, Locust, Python, cờ JVM và cách phân bổ CPU.

## 7. Sản phẩm bàn giao

| Sản phẩm | Vị trí | Lưu vào repo |
| :--- | :--- | :--- |
| Kế hoạch kiểm thử | `docs/load-testing.md` | Có |
| Bộ đo và notebook | `loadtest/` | Có |
| Báo cáo đường cơ sở Phase 1 | `REPORT.md` trong output của notebook | Có, tại `docs/benchmark/phase1/` |
| Biểu đồ | `breakpoint.png`, `spike.png`, `endpoints_average_load.png` | Có, cùng thư mục với báo cáo |
| Notebook Kaggle công khai | Link ghi trong README | Có, dưới dạng link |
| Bảng so sánh hai phiên bản | `comparison/COMPARISON.md` | Do nhóm làm Phase 2 tạo và lưu trong repo của họ |
| Dữ liệu thô và dữ liệu trung gian | `raw_requests.csv`, `resources.csv`, `summary.json`, `meta.json` | Không; lưu trong output của notebook Kaggle công khai |

## 8. Hướng dẫn đối chiếu cho nhóm làm Phase 2

### 8.1. Phần giữ nguyên và phần được thay đổi

Để kết quả Phase 2 so sánh được với Phase 1, bộ đo phải giữ nguyên các phần dưới đây.

| Thành phần | Quy định |
| :--- | :--- |
| Môi trường | Kaggle CPU, cấu hình notebook như mục 9.1 |
| Phiên bản bộ đo | `HARNESS_REF` = mã commit Phase 1 |
| Kế hoạch các bài kiểm thử | Biến `PLAN` trong `loadtest/run_plan.py`, không đổi |
| Tiêu chí đạt | Biến `SLO` trong `loadtest/analyze.py`, không đổi |
| Dữ liệu kiểm thử | Biến `SEED` và số bản ghi trong `loadtest/seed.py`, không đổi |
| Mô hình tải | Các thao tác và trọng số trong `loadtest/locustfile.py`, không đổi |

Nếu Phase 2 thay đổi API hoặc schema (ví dụ thêm trường `employeeId` vào đơn hàng), nhóm được sửa `locustfile.py` và `seed.py` ở mức tối thiểu để gọi đúng API mới, với điều kiện:

1. Không đổi thao tác, trọng số, số bản ghi và giá trị `SEED`.
2. Bản sửa vẫn chạy được với commit Phase 1, để đo cả hai phiên bản bằng cùng một bộ đo.
3. Ghi rõ từng thay đổi và lý do trong báo cáo Phase 2.

### 8.2. Quy trình đối chiếu

1. Lấy mã commit Phase 1 ở bảng thông tin đầu file `docs/benchmark/phase1/REPORT.md` (dòng "Phiên bản được kiểm thử"). Các bước dưới đây gọi mã này là `<commit Phase 1>`. Commit này có sẵn trong mọi bản clone hoặc fork của repo.
2. Chạy A/A test để đo nhiễu của môi trường: `HARNESS_REF = "<commit Phase 1>"`, `REFS = {"phase1": "<commit Phase 1>", "phase1_again": "<commit Phase 1>"}`. Mọi dòng trong `COMPARISON.md` phải có kết luận "Trong ngưỡng nhiễu" hoặc "Không đổi".
3. Chạy đối chiếu: `REFS = {"phase1": "<commit Phase 1>", "phase2": "<nhánh Phase 2>"}`, `QUICK = False`. Hai phiên bản được đo trong cùng một phiên Kaggle, với cùng dữ liệu, cùng kịch bản và cùng tiêu chí.
4. Đọc `COMPARISON.md` và biểu đồ `comparison_breakpoint.png`. Chỉ kết luận "Tốt hơn" hoặc "Kém hơn" ở các chỉ số vượt ngưỡng nhiễu.
5. Đối chiếu thêm với báo cáo đường cơ sở `docs/benchmark/phase1/REPORT.md`. So sánh trực tiếp con số chỉ hợp lệ khi model CPU và số core trong mục Môi trường của hai báo cáo trùng nhau. Nếu khác, chỉ dùng kết quả của bước 3.

### 8.3. Chỉ số nên xem theo loại cải tiến

| Cải tiến | Chỉ số cần xem |
| :--- | :--- |
| Cache cho `GET /products` | p95 của `GET /products?page` và `GET /products/{id}`; CPU của MySQL; năng lực tối đa |
| Chống gửi lặp request ở `POST /orders` | Số đơn trùng khi gửi lặp request (cần bổ sung bài kiểm thử riêng, không thay bài có sẵn); p95 của `POST /orders` |
| Thu hồi token (access token ngắn hạn, blacklist) | p50 của các request có xác thực |
| Phân trang và tối ưu truy vấn ở `GET /customers/{id}/orders` | p95 của endpoint này; số truy vấn mỗi giây của MySQL |
| Bảng lịch sử biến động kho | p95 của các thao tác ghi; kết quả đối soát tồn kho |

## 9. Hướng dẫn chạy

### 9.1. Chạy trên Kaggle

1. Trên Kaggle, chọn **Create** > **New Notebook** > **File** > **Import Notebook**, rồi chọn `loadtest/kaggle_load_test.ipynb`.
2. Trong **Session options**, đặt **Accelerator** là **None** và bật **Internet**.
3. Trong ô **1. Tham số**, đặt `HARNESS_REF`, `REFS` và `QUICK`. Khi đo đường cơ sở Phase 1, giữ mặc định `HARNESS_REF = "main"` và `REFS = {"phase1": "HEAD"}`; báo cáo tự ghi lại mã commit đã đo.
4. Để chạy thử, đặt `QUICK = True` và chọn **Run All**.
5. Để chạy chính thức, đặt `QUICK = False` và chọn **Save Version** > **Save & Run All (Commit)**.
6. Khi phiên bản chạy xong, tải `results.zip` trong tab **Output**.

### 9.2. Chạy trên máy cá nhân

Nếu cần chạy thử trên máy cá nhân, cài Java 21, MySQL 8 (tài khoản `root`, mật khẩu `12345678`) và các gói Python, rồi chạy:

```bash
pip install locust psutil pymysql requests pandas matplotlib
python loadtest/run_plan.py --refs phase1=HEAD --out results --quick
```

Kết quả trên máy cá nhân chỉ dùng để kiểm tra bộ đo, không dùng làm số liệu chính thức.

## 10. Hạn chế đã biết

- Ghim CPU tách được thời gian CPU, nhưng không tách được bộ nhớ đệm L3, băng thông bộ nhớ và đĩa giữa ứng dụng, MySQL và Locust.
- Locust sinh tải theo mô hình đóng: mỗi người dùng ảo chờ phản hồi rồi mới gửi request tiếp theo. Khi hệ thống chậm, tải tự giảm, nên độ trễ đuôi có thể thấp hơn thực tế (coordinated omission).
- Mỗi bậc breakpoint dài 60 s và bài tải thường dài 3 phút để vừa một phiên Kaggle. Kế hoạch chưa có bài soak test kéo dài nhiều giờ, nên chưa phát hiện được rò rỉ bộ nhớ.
- Ngưỡng 20% cho chỉ số chỉ đo một lần là ngưỡng thận trọng; chênh lệch nhỏ hơn ở các chỉ số này cần thêm lần chạy để kết luận.
- Kaggle không cam kết cùng một model CPU giữa các phiên, nên con số tuyệt đối của đường cơ sở chỉ là tham chiếu; đối chiếu chính xác phải đo hai phiên bản trong cùng một phiên (mục 8.2).

## 11. Thuật ngữ

| Thuật ngữ | Định nghĩa |
| :--- | :--- |
| SUT | System under test, hệ thống được kiểm thử: ứng dụng Spring Boot và MySQL |
| Người dùng ảo | Một phiên người dùng do Locust mô phỏng, gửi request tuần tự và nghỉ giữa các request |
| Throughput | Số request hệ thống xử lý xong mỗi giây (req/s) |
| p50, p95, p99 | Độ trễ mà 50%, 95%, 99% số request không vượt quá |
| SLO | Service level objective, ngưỡng hiệu năng dùng để kết luận đạt hay không đạt |
| Hệ số biến thiên (CV) | Độ lệch chuẩn chia cho giá trị trung bình, tính theo phần trăm |
| A/A test | So sánh một phiên bản với chính nó để đo nhiễu của phép đo |
| Snapshot | Bản sao CSDL ngay sau khi nạp dữ liệu, dùng để khôi phục trước mỗi bài |
| Đường cơ sở (baseline) | Kết quả đo của phiên bản Phase 1, làm mốc để đối chiếu sau cải tiến |

## 12. Tài liệu tham khảo

- ISO/IEC/IEEE 29119-3:2021, *Software and systems engineering — Software testing — Part 3: Test documentation*.
- [Phân loại bài test tải của Grafana k6](https://grafana.com/docs/k6/latest/testing-guides/test-types/)
- [Kho mã nguồn TechEmpower Framework Benchmarks](https://github.com/TechEmpower/FrameworkBenchmarks)
- [Tài liệu Locust](https://docs.locust.io/)
