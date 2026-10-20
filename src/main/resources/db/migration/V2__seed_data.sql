INSERT INTO users (user_id, username, password, role) VALUES
(1, 'admin', '$2a$10$geTgI.iWxW8WqwQ5RAPtHOc13ofKNdJQXJWqN7UQb4Pk/hpTQcUhm', 'ROLE_ADMIN'),
(2, 'user1', '$2a$10$geTgI.iWxW8WqwQ5RAPtHOc13ofKNdJQXJWqN7UQb4Pk/hpTQcUhm', 'ROLE_USER'),
(3, 'user2', '$2a$10$geTgI.iWxW8WqwQ5RAPtHOc13ofKNdJQXJWqN7UQb4Pk/hpTQcUhm', 'ROLE_USER'),
(4, 'user3', '$2a$10$geTgI.iWxW8WqwQ5RAPtHOc13ofKNdJQXJWqN7UQb4Pk/hpTQcUhm', 'ROLE_USER');

INSERT INTO customers (customer_number, user_id, customer_name, phone) VALUES
(1, 2, 'Nguyễn Văn An', '0901234567'),
(2, 3, 'Trần Thị Bình', '0912345678'),
(3, 4, 'Lê Hoàng Cường', '0987654321'),
(4, NULL, 'Phạm Minh Đức', '0977889900'),
(5, NULL, 'Hoàng Thu Trang', '0933445566');

INSERT INTO products (product_code, product_name, quantity_in_stock, buy_price, status) VALUES
(1, 'iPhone 15 Pro Max 256GB', 50, 29990000.00, 'ACTIVE'),
(2, 'Samsung Galaxy S24 Ultra 256GB', 45, 27990000.00, 'ACTIVE'),
(3, 'MacBook Pro 14 inch M3 Pro', 30, 49990000.00, 'ACTIVE'),
(4, 'Dell XPS 15 9530 Core i7', 20, 42500000.00, 'ACTIVE'),
(5, 'Tai nghe Sony WH-1000XM5', 80, 7490000.00, 'ACTIVE'),
(6, 'iPad Air 5 M1 64GB WiFi', 60, 14500000.00, 'ACTIVE'),
(7, 'Chuột không dây Logitech MX Master 3S', 120, 2190000.00, 'ACTIVE'),
(8, 'Bàn phím cơ Keychron Q1 Pro', 40, 4390000.00, 'ACTIVE'),
(9, 'Màn hình LG 27UP850 4K UHD 27 inch', 25, 9890000.00, 'ACTIVE'),
(10, 'Apple Watch Series 9 GPS 45mm', 35, 10490000.00, 'ACTIVE');

INSERT INTO orders (order_number, order_date, customer_number, status, total_amount) VALUES
(1, '2026-03-10 10:30:00', 1, 'COMPLETED', 32180000.00),
(2, '2026-03-11 14:15:00', 2, 'SHIPPING', 54380000.00),
(3, '2026-03-12 09:00:00', 3, 'PENDING', 7490000.00);

INSERT INTO order_details (order_number, product_code, quantity_ordered, price_each) VALUES
(1, 1, 1, 29990000.00),
(1, 7, 1, 2190000.00),
(2, 3, 1, 49990000.00),
(2, 8, 1, 4390000.00),
(3, 5, 1, 7490000.00);
