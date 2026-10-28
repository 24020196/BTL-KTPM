# Một số vấn đề của hệ thống (Đã xử lý ở Phase 1)

> [← Quay lại README chính](../README.md)

Khi Backend Server xử lý đồng thời nhiều HTTP Request (Concurrent Requests) và tương tác bất đồng bộ xuống Persistence Database, độ trễ đọc/ghi dễ gây sai lệch dữ liệu.

## 3.1. Lỗi ghi đè dữ liệu khi cập nhật tồn kho

**Kịch bản gây lỗi:**

1. Thực thể Product (mã A) đang có tồn kho `qty = 10`.
2. Lúc 09:00: Admin gọi `GET /api/v1/products/A`, nhận về Response có `qty = 10` và chuẩn bị nhập thêm 5 sản phẩm (tính thành 15).
3. Lúc 09:01: Customer gửi Request `POST /api/v1/orders` với `OrderRequest(customerId, employeeId, List<Items>)` đặt mua 3 sản phẩm A. Server xử lý và trừ kho trong Database xuống còn `qty = 7`, trả về 201 Created.
4. Lúc 09:02: Admin gửi Request `PUT` mang toàn bộ dữ liệu cũ kèm `ProductRequest(name, qty = 15, price)` lên Server. Database bị ghi đè ngược thành `qty = 15` (lệch 3 sản phẩm so với thực tế là 7 + 5 = 12).

**Cách hệ thống Phase 1 đã xử lý:**

- Trong vận hành thực tế, mọi biến động `qty` bắt buộc đi qua các Request giao dịch `POST` (như `POST /api/v1/orders`) hoặc điều chỉnh theo lượng chênh lệch.
- API cập nhật thông tin sản phẩm được chuyển thành `PATCH /api/v1/products/{id}` và chỉ nhận `name`, `price`, `status`, không cho phép ghi đè trực tiếp `qty`.

## 3.2. Đứt gãy giao dịch giữa chừng

**Kịch bản gây lỗi:**

- Client gửi `POST /api/v1/orders` với `List<Items>` gồm 5 sản phẩm. Server phải ghi liên tiếp vào bảng `orders`, `order_details` và trừ `qty` trong bảng `products`.
- Khi Server vừa trừ kho xong 3 sản phẩm đầu trong Database, đến sản phẩm thứ 4 thì phát hiện `qty` không đủ (hoặc sản phẩm đang `INACTIVE`, hoặc ngắt kết nối Database đột ngột). Nếu các lệnh chạy độc lập, Server trả Response lỗi nhưng 3 sản phẩm đầu đã bị trừ kho vô lý trong khi đơn hàng không được tạo.

**Cách hệ thống Phase 1 đã xử lý:**

- Bọc toàn bộ quá trình kiểm tra trạng thái `ACTIVE`, đối chiếu và trừ/hoàn trả `qty`, lưu bảng `orders` + `order_details` (trong cả `POST /api/v1/orders` và `PATCH /api/v1/orders/{id}`) vào một Database Transaction (ACID) duy nhất.
- Tuân thủ nguyên tắc All-or-Nothing: bất kỳ bước nào lỗi sẽ tự động kích hoạt ROLLBACK, khôi phục nguyên trạng `qty` của các dòng trước đó.

## 3.3. Sai số trong Database khi có Request đồng thời

**Kịch bản gây lỗi:**

- Sản phẩm A còn tồn kho `qty = 5`. Cùng một thời điểm, 2 khách hàng gửi đồng thời 2 Request `POST /api/v1/orders` cùng đặt mua sản phẩm A với số lượng lần lượt là 4 và 3.
- Hai luồng xử lý trên Server cùng đọc Database lúc `qty = 5`, cùng kiểm tra thấy hợp lệ (5 >= 4 và 5 >= 3) và cùng phát lệnh cập nhật trừ kho xuống Database, dẫn đến bán vượt tồn kho (Overselling) hoặc ghi đè sai số dư `qty`.

**Cách hệ thống Phase 1 đã xử lý:**

- Áp dụng khóa dòng mức cơ sở dữ liệu (Row-level Locking - `SELECT ... FOR UPDATE`) trên bảng `products` bên trong Transaction.
- Khi Request 1 đang kiểm tra và trừ `qty` của sản phẩm A, dòng dữ liệu này bị khóa độc quyền. Request 2 phải chờ ở hàng đợi Database cho đến khi Request 1 COMMIT xong (lúc này `qty` còn 1), nhờ đó Request 2 đọc được giá trị mới nhất và báo lỗi hết hàng chính xác.

## 3.4. Bế tắc hệ thống do khóa chéo (Database Deadlock)

**Kịch bản gây lỗi:** Khi đã áp dụng khóa dòng (Row-level Locking) để chống âm kho cho `POST /api/v1/orders`:

- Request 1 gửi `List<Items>` theo thứ tự [Sản phẩm A, Sản phẩm B]: Luồng 1 khóa dòng A trước, rồi chờ khóa dòng B.
- Cùng lúc đó, Request 2 gửi `List<Items>` theo thứ tự [Sản phẩm B, Sản phẩm A]: Luồng 2 khóa dòng B trước, rồi chờ khóa dòng A.
- Hệ quả: Luồng 1 chờ Luồng 2 nhả B, Luồng 2 chờ Luồng 1 nhả A. Cả hai treo cứng (Deadlock), làm nghẽn kết nối Database.

**Cách hệ thống Phase 1 đã phòng ngừa:**

- Ngay ở bước chuẩn hóa `OrderRequest`, hệ thống áp dụng quy tắc: "Gộp và sắp xếp `productId` trong `List<Items>` theo thứ tự tăng dần trước khi kiểm tra/trừ kho".
- Nhờ đó, cả Request 1 và Request 2 đều luôn khóa dòng Sản phẩm A trước rồi mới đến Sản phẩm B, triệt tiêu hoàn toàn nguy cơ Deadlock.

