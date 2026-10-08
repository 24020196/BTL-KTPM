CREATE TABLE revinfo
(
    rev      BIGINT AUTO_INCREMENT NOT NULL,
    revtstmp BIGINT NULL,
    CONSTRAINT pk_revinfo PRIMARY KEY (rev)
);

CREATE TABLE revchanges
(
    rev        BIGINT NOT NULL,
    entityname VARCHAR(255) NOT NULL,
    PRIMARY KEY (rev, entityname),
    CONSTRAINT fk_revchanges_revinfo FOREIGN KEY (rev) REFERENCES revinfo (rev) ON DELETE CASCADE
);

CREATE TABLE users
(
    user_id  BIGINT AUTO_INCREMENT NOT NULL,
    username VARCHAR(255)          NOT NULL UNIQUE,
    password VARCHAR(255)          NOT NULL,
    role     VARCHAR(20)           NOT NULL DEFAULT 'ROLE_USER',
    PRIMARY KEY (user_id)
);


CREATE TABLE employees
(
    employee_number BIGINT AUTO_INCREMENT NOT NULL,
    user_id         BIGINT UNIQUE,
    employee_name   VARCHAR(255) NOT NULL,
    reports_to      BIGINT DEFAULT NULL,
    PRIMARY KEY (employee_number),
    FOREIGN KEY (reports_to) REFERENCES employees (employee_number) ON DELETE SET NULL,
    FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE SET NULL
);


CREATE TABLE customers
(
    customer_number           BIGINT AUTO_INCREMENT NOT NULL,
    user_id                   BIGINT UNIQUE,
    customer_name             VARCHAR(255) NOT NULL,
    phone                     VARCHAR(20)  NOT NULL,
    address                   VARCHAR(255) NOT NULL,
    sales_rep_employee_number BIGINT DEFAULT NULL,
    PRIMARY KEY (customer_number),
    FOREIGN KEY (sales_rep_employee_number) REFERENCES employees (employee_number) ON DELETE SET NULL,
    FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE SET NULL
);

CREATE TABLE products
(
    product_code     BIGINT AUTO_INCREMENT NOT NULL,
    product_name     VARCHAR(255)   NOT NULL,
    quantity_in_stock INT           NOT NULL DEFAULT 0,
    buy_price        DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    PRIMARY KEY (product_code)
);

CREATE TABLE orders
(
    order_number    BIGINT AUTO_INCREMENT NOT NULL,
    order_date      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    customer_number BIGINT   NOT NULL,
    employee_number BIGINT   NOT NULL,
    PRIMARY KEY (order_number),
    FOREIGN KEY (customer_number) REFERENCES customers (customer_number),
    FOREIGN KEY (employee_number) REFERENCES employees (employee_number)
);


CREATE TABLE order_details
(
    order_number     BIGINT         NOT NULL,
    product_code     BIGINT         NOT NULL,
    quantity_ordered INT            NOT NULL,
    price_each       DECIMAL(15, 2) NOT NULL,
    PRIMARY KEY (order_number, product_code),
    FOREIGN KEY (order_number) REFERENCES orders (order_number) ON DELETE CASCADE,
    FOREIGN KEY (product_code) REFERENCES products (product_code)
);
