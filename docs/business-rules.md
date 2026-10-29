# Quy trình và quy tắc nghiệp vụ cơ bản

> [← Quay lại README chính](../README.md)

## a. Nghiệp vụ Xác thực & Phân quyền (Auth & Security)

- Mật khẩu người dùng phải được mã hóa một chiều khi đăng ký.
- Sử dụng cơ chế Stateless Authentication qua JWT Token cấp phát sau khi đăng nhập thành công.
- Các API quản trị (như xem danh sách tài khoản) yêu cầu quyền ADMIN.

## b. Nghiệp vụ Quản lý khách hàng

- Kiểm soát tính duy nhất của mã khách hàng và số điện thoại trên toàn hệ thống khi tạo mới cũng như khi cập nhật hồ sơ.
- Hỗ trợ tra cứu lịch sử đơn hàng theo từng khách hàng.

## c. Nghiệp vụ Quản lý Tồn kho

- **Bảo toàn lịch sử và trạng thái kinh doanh:** Sản phẩm không sử dụng cơ chế xóa khỏi CSDL.
- Mỗi sản phẩm có trạng thái hoạt động (`ACTIVE` - Đang kinh doanh, `INACTIVE` - Ngừng kinh doanh). Chỉ các sản phẩm ở trạng thái `ACTIVE` mới được phép giao dịch.
- **Cô lập nghiệp vụ sửa thông tin và điều chỉnh kho:** Việc cập nhật thông tin định danh (tên, giá bán, trạng thái) được tách rời hoàn toàn khỏi việc cập nhật số lượng tồn kho. Tồn kho chỉ biến động qua các luồng duy nhất:
  1. Khởi tạo sản phẩm ban đầu.
  2. Điều chỉnh kiểm kê/nhập hàng tăng giảm theo lượng chênh lệch.
  3. Khấu trừ tự động khi tạo đơn hàng và hoàn trả tự động khi hủy đơn.

## d. Nghiệp vụ Xử lý Giao dịch Đơn hàng

**Toàn vẹn giao dịch:** Order và OrderDetails được xử lý trong một Transaction duy nhất.

**Quy trình khởi tạo đơn và khấu trừ kho (`POST /orders`):**

1. **Chuẩn hóa giỏ hàng:** Kiểm tra đơn hàng không rỗng, số lượng mỗi sản phẩm lớn hơn 0. Tự động cộng dồn số lượng nếu có các `productId` trùng lặp trong cùng một body.
2. **Kiểm tra điều kiện ràng buộc:** Toàn bộ sản phẩm trong đơn hàng phải đang ở trạng thái `ACTIVE` và có số lượng tồn kho hiện tại lớn hơn hoặc bằng số lượng đặt mua. Nếu bất kỳ sản phẩm nào vi phạm, hủy toàn bộ giao dịch.
3. **Ghi nhận và trừ kho:** Khấu trừ tồn kho tương ứng, lưu đơn giá tại thời điểm mua vào `order_details`, tính tổng tiền và tạo đơn ở trạng thái `PENDING`.

**Quy tắc chuyển đổi trạng thái và hoàn kho:**

Đơn hàng tuân thủ nghiêm ngặt luồng trạng thái một chiều (không cho phép lùi trạng thái):

| Trạng thái hiện tại | Được phép chuyển sang | Hành vi nghiệp vụ chi tiết |
| :--- | :--- | :--- |
| `PENDING` | `SHIPPING` hoặc `CANCELLED` | Khởi tạo qua `POST /orders` (đã trừ kho & snapshot giá). Cho phép chuyển sang `SHIPPING` (Đang giao hàng) hoặc `CANCELLED` (Transaction: hoàn trả tồn kho). |
| `SHIPPING` | `COMPLETED` hoặc `CANCELLED` | Cho phép chuyển sang `COMPLETED` (giao thành công - chốt đơn) hoặc `CANCELLED` (Transaction: hoàn trả tồn kho, kể cả khi SP đã `INACTIVE`). |
| `COMPLETED` / `CANCELLED` | Khóa hoàn toàn | **TRẠNG THÁI KHÓA CỨNG (TERMINAL STATES):** Chặn tuyệt đối mọi request đổi trạng thái để bảo vệ doanh thu và chống lỗi hoàn kho 2 lần (Double Restock). |

Khi chuyển sang `CANCELLED` (từ `PENDING` hoặc `SHIPPING`), hệ thống thực thi Transaction: cập nhật trạng thái đơn sang `CANCELLED` và tự động cộng hoàn trả số lượng từng mặt hàng trong đơn về lại tồn kho của sản phẩm tương ứng (ngay cả khi sản phẩm đó hiện đã chuyển sang `INACTIVE`).

### **Sơ đồ máy trạng thái đơn hàng (Order State Machine):**

```text
       tạo đơn (POST /orders)
       [Khấu trừ kho & snapshot giá]
               |
               v
       +---------------+      xuất kho (PATCH status = SHIPPING)       +---------------+
       |               | --------------------------------------------> |               |
       |    PENDING    |                                               |   SHIPPING    |
       |               | -----\                                 -----> |               |
       +---------------+      |                                 |      +---------------+
                              |                                 |              |
                              | hủy đơn (PATCH CANCELLED)       |              | giao thành công
                              | [Cộng hoàn trả tồn kho]         |              | (PATCH COMPLETED)
                              v                                 |              v
                       +---------------+                        |      +---------------+
                       |               |                        |      |               |
                       |   CANCELLED   | <----------------------/      |   COMPLETED   |
                       |               |   hủy đơn (PATCH CANCELLED)   |               |
                       +---------------+   [Cộng hoàn trả tồn kho]     +---------------+
                               |                                               |
                               v                                               v
                        [Khóa hoàn toàn]                                [Khóa hoàn toàn]
                 Chống hoàn kho 2 lần (Double Restock)                  Bảo vệ doanh thu
```

> **Lưu ý:**
> - Chỉ có thể chuyển sang `SHIPPING` hoặc `CANCELLED` khi đơn hàng đang ở trạng thái `PENDING`.
> - Chỉ có thể chuyển sang `COMPLETED` hoặc `CANCELLED` khi đơn hàng đang ở trạng thái `SHIPPING`.
> - Khi đơn hàng đã ở trạng thái `COMPLETED` hoặc `CANCELLED`, hệ thống **khóa hoàn toàn**, chặn tuyệt đối mọi request thay đổi trạng thái tiếp theo.

