"""Deterministic benchmark dataset.

The same RNG seed always produces the same rows, so every run (Phase 1, Phase 2,
every repetition) starts from an identical database state.

Column lists follow the Phase 1 schema (V1__create.sql + V2). If Phase 2 changes
the schema, update the INSERT statements here and keep SEED unchanged.
"""

import random
from datetime import datetime, timedelta

SEED = 20261029

N_PRODUCTS = 1000
N_CUSTOMERS = 500
N_SHOPPERS = 300
N_ORDERS = 20000

HOT_PRODUCT_ID = 1
HOT_STOCK = 500
NORMAL_STOCK = (8000, 12000)

STATUS_MIX = [("COMPLETED", 0.70), ("CANCELLED", 0.10), ("SHIPPING", 0.10), ("PENDING", 0.10)]

TABLES = ["users", "customers", "products", "orders", "order_details"]


def _chunks(rows, size=1000):
    for i in range(0, len(rows), size):
        yield rows[i : i + size]


def build_dataset(password_hash):
    rng = random.Random(SEED)

    users = [(1, "bench_admin", password_hash, "ROLE_ADMIN")]
    for i in range(1, N_SHOPPERS + 1):
        users.append((i + 1, f"shopper_{i:04d}", password_hash, "ROLE_USER"))

    customers = []
    for c in range(1, N_CUSTOMERS + 1):
        user_id = c + 1 if c <= N_SHOPPERS else None
        customers.append((c, user_id, f"Customer {c:04d}", f"09{c:08d}"))

    products = [(HOT_PRODUCT_ID, "HOT-FLASH-SALE", HOT_STOCK, 199000.00, "ACTIVE", 0)]
    for p in range(2, N_PRODUCTS + 1):
        status = "INACTIVE" if p % 50 == 0 else "ACTIVE"
        price = round(rng.uniform(20, 2000)) * 1000.0
        products.append((p, f"SKU-{p:05d}", rng.randint(*NORMAL_STOCK), price, status, 0))
    price_of = {p[0]: p[3] for p in products}

    statuses, weights = zip(*STATUS_MIX, strict=True)
    start = datetime(2025, 10, 1)
    orders, details = [], []
    for order in range(1, N_ORDERS + 1):
        customer = rng.randint(1, N_CUSTOMERS)
        status = rng.choices(statuses, weights)[0]
        when = start + timedelta(seconds=rng.randint(0, 365 * 24 * 3600))
        picked = rng.sample(range(2, N_PRODUCTS + 1), rng.randint(1, 3))
        total = 0.0
        for product in picked:
            qty = rng.randint(1, 3)
            details.append((order, product, qty, price_of[product]))
            total += qty * price_of[product]
        orders.append((order, when.strftime("%Y-%m-%d %H:%M:%S"), customer, status, round(total, 2), 0))

    return {
        "users": ("(user_id, username, password, role)", users),
        "customers": ("(customer_number, user_id, customer_name, phone)", customers),
        "products": ("(product_code, product_name, quantity_in_stock, buy_price, status, version)", products),
        "orders": ("(order_number, order_date, customer_number, status, total_amount, version)", orders),
        "order_details": ("(order_number, product_code, quantity_ordered, price_each)", details),
    }


def load(conn, password_hash):
    data = build_dataset(password_hash)
    with conn.cursor() as cur:
        cur.execute("SET FOREIGN_KEY_CHECKS=0")
        for table in reversed(TABLES):
            cur.execute(f"TRUNCATE TABLE {table}")
        for table in TABLES:
            cols, rows = data[table]
            placeholders = "(" + ",".join(["%s"] * len(rows[0])) + ")"
            for chunk in _chunks(rows):
                cur.executemany(f"INSERT INTO {table} {cols} VALUES {placeholders}", chunk)
        cur.execute("SET FOREIGN_KEY_CHECKS=1")
    conn.commit()
    return {t: len(data[t][1]) for t in TABLES}


def snapshot(conn, db, snap_db):
    with conn.cursor() as cur:
        cur.execute(f"DROP DATABASE IF EXISTS {snap_db}")
        cur.execute(f"CREATE DATABASE {snap_db}")
        for table in TABLES:
            cur.execute(f"CREATE TABLE {snap_db}.{table} AS SELECT * FROM {db}.{table}")
    conn.commit()


def restore(conn, db, snap_db):
    with conn.cursor() as cur:
        cur.execute("SET FOREIGN_KEY_CHECKS=0")
        for table in reversed(TABLES):
            cur.execute(f"TRUNCATE TABLE {db}.{table}")
        for table in TABLES:
            cur.execute(f"INSERT INTO {db}.{table} SELECT * FROM {snap_db}.{table}")
        cur.execute("SET FOREIGN_KEY_CHECKS=1")
    conn.commit()
