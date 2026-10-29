"""Workload model for the KTPM inventory and order API.

User classes:
  ShopperUser    (weight 8)  read-heavy shopping session that also places orders
  AdminUser      (weight 2)  back office: order status pipeline, restocking, edits
  FlashSaleUser              flash-sale stage only (classes are selected explicitly)

Responses are classified as:
  ok        the expected 2xx
  rejected  an expected business rejection (400 out of stock or invalid status
            transition, 409 optimistic lock conflict); not counted as an error,
            but logged with its status code so the report shows how often each rule fired
  failure   anything else (5xx, unexpected 4xx, timeouts)

Every request is appended to a raw sample log (RAW_LOG), so the analysis computes
exact percentiles per stage and per load step.

Configuration comes from environment variables set by bench.py:
  BENCH_DATA                     path to data.json (tokens, ids)
  RAW_LOG                        raw sample CSV for this stage
  RESTOCK_LOG                    restock event log, used for inventory reconciliation
  WAIT_MIN, WAIT_MAX             think time in seconds
  SHAPE_STAGES                   optional JSON list of [end_second, users, spawn_rate]
  STOP_P95_MS, STOP_ERROR_PCT    optional per-step stop thresholds (breakpoint stage)
"""

import csv
import itertools
import json
import os
import random
import threading
import time

from locust import FastHttpUser, LoadTestShape, between, events, task

with open(os.environ["BENCH_DATA"], encoding="utf-8") as _f:
    DATA = json.load(_f)

RAW_LOG = os.environ.get("RAW_LOG", "raw_requests.csv")
RESTOCK_LOG = os.environ.get("RESTOCK_LOG", "restocks.jsonl")
WAIT_MIN = float(os.environ.get("WAIT_MIN", "1.0"))
WAIT_MAX = float(os.environ.get("WAIT_MAX", "3.0"))
STAGED = bool(os.environ.get("SHAPE_STAGES"))

ADMIN_TOKEN = DATA["admin"]["token"]
SHOPPERS = DATA["shoppers"]
N_CUSTOMERS = DATA["n_customers"]
N_SEED_ORDERS = DATA["n_orders"]
HOT_PRODUCT = DATA["hot_product"]
PRODUCTS = DATA["active_products"]
PRODUCT_META = DATA["product_meta"]

RAW_LOG_HEADER = ["ts", "users", "method", "name", "status", "rt_ms", "ok"]
FLUSH_EVERY = 2000

_account_seq = itertools.count()


class _Recorder:
    """Buffers every request sample and appends it to RAW_LOG."""

    def __init__(self):
        self.lock = threading.Lock()
        self.rows = []
        self.step_samples = []
        self.runner = None

    def add(self, row, response_time, ok):
        with self.lock:
            self.rows.append(row)
            if STAGED:
                self.step_samples.append((response_time, ok))
            if len(self.rows) >= FLUSH_EVERY:
                self._flush()

    def flush(self):
        with self.lock:
            self._flush()

    def take_step_samples(self):
        with self.lock:
            samples, self.step_samples = self.step_samples, []
        return samples

    def _flush(self):
        if not self.rows:
            return
        new_file = not os.path.exists(RAW_LOG)
        with open(RAW_LOG, "a", newline="", encoding="utf-8") as f:
            writer = csv.writer(f)
            if new_file:
                writer.writerow(RAW_LOG_HEADER)
            writer.writerows(self.rows)
        self.rows.clear()


recorder = _Recorder()


@events.init.add_listener
def _on_init(environment, **_):
    recorder.runner = environment.runner


@events.request.add_listener
def _on_request(request_type, name, response_time, exception=None, response=None, **_):
    status = getattr(response, "status_code", 0) if response is not None else 0
    users = recorder.runner.user_count if recorder.runner else 0
    ok = exception is None
    row = (round(time.time(), 3), users, request_type, name, status, round(response_time, 2), int(ok))
    recorder.add(row, response_time, ok)


@events.quitting.add_listener
def _on_quit(environment, **_):
    recorder.flush()


def auth_header(token):
    return {"Authorization": f"Bearer {token}"}


def check(res, ok=(200,), rejected=()):
    if res.status_code in ok or res.status_code in rejected:
        res.success()
    else:
        res.failure(f"unexpected {res.status_code}: {(res.text or '')[:120]}")


def next_account():
    return SHOPPERS[next(_account_seq) % len(SHOPPERS)]


class ShopperUser(FastHttpUser):
    weight = 8
    wait_time = between(WAIT_MIN, WAIT_MAX)
    network_timeout = 30.0
    connection_timeout = 10.0

    def on_start(self):
        self.account = next_account()
        self.token = self.account["token"]
        self.customer_id = self.account["customer_id"]
        self.my_orders = []

    def _get(self, path, name):
        with self.client.get(path, headers=auth_header(self.token), name=name, catch_response=True) as res:
            check(res)

    @task(30)
    def browse_products(self):
        self._get(f"/api/v1/products?page={random.randint(0, 99)}&size=10", "GET /products?page")

    @task(25)
    def product_detail(self):
        self._get(f"/api/v1/products/{random.choice(PRODUCTS)}", "GET /products/{id}")

    @task(8)
    def customer_detail(self):
        self._get(f"/api/v1/customers/{self.customer_id}", "GET /customers/{id}")

    @task(5)
    def customer_orders(self):
        self._get(f"/api/v1/customers/{self.customer_id}/orders", "GET /customers/{id}/orders")

    @task(10)
    def order_detail(self):
        if self.my_orders and random.random() < 0.5:
            order_id = random.choice(self.my_orders)
        else:
            order_id = random.randint(1, N_SEED_ORDERS)
        self._get(f"/api/v1/orders/{order_id}", "GET /orders/{id}")

    @task(12)
    def place_order(self):
        items = [
            {"productCode": product, "quantityOrdered": random.randint(1, 3)}
            for product in random.sample(PRODUCTS, random.randint(1, 3))
        ]
        with self.client.post(
            "/api/v1/orders",
            json={"customerNumber": self.customer_id, "items": items},
            headers=auth_header(self.token),
            name="POST /orders",
            catch_response=True,
        ) as res:
            check(res, ok=(201,), rejected=(400,))
            if res.status_code == 201:
                self.my_orders.append(res.json()["orderNumber"])

    @task(2)
    def login(self):
        credentials = {"username": self.account["username"], "password": self.account["password"]}
        with self.client.post(
            "/api/v1/auth/login", json=credentials, name="POST /auth/login", catch_response=True
        ) as res:
            check(res)
            if res.status_code == 200:
                self.token = res.json()["accessToken"]


class AdminUser(FastHttpUser):
    weight = 2
    wait_time = between(WAIT_MIN, WAIT_MAX)
    network_timeout = 30.0
    connection_timeout = 10.0

    def on_start(self):
        self.auth = auth_header(ADMIN_TOKEN)

    @task(10)
    def list_orders(self):
        path = f"/api/v1/orders?page={random.randint(0, 50)}&size=20"
        with self.client.get(path, headers=self.auth, name="GET /orders?page", catch_response=True) as res:
            check(res)

    @task(15)
    def advance_order(self):
        path = "/api/v1/orders?page=0&size=20&sort=orderNumber,desc"
        with self.client.get(path, headers=self.auth, name="GET /orders?page", catch_response=True) as res:
            check(res)
            if res.status_code != 200:
                return
            orders = res.json().get("content", [])
        open_orders = [o for o in orders if o["status"] in ("PENDING", "SHIPPING")]
        if not open_orders:
            return
        order = random.choice(open_orders)
        if random.random() < 0.15:
            target = "CANCELLED"
        elif order["status"] == "PENDING":
            target = "SHIPPING"
        else:
            target = "COMPLETED"
        with self.client.patch(
            f"/api/v1/orders/{order['orderNumber']}",
            json={"status": target},
            headers=self.auth,
            name=f"PATCH /orders/{{id}} {target}",
            catch_response=True,
        ) as res:
            check(res, rejected=(400, 409))

    @task(5)
    def restock(self):
        product = random.choice(PRODUCTS)
        qty = random.randint(10, 50)
        name, price = PRODUCT_META[str(product)]
        payload = {"name": name, "qty": qty, "price": price}
        with self.client.post(
            "/api/v1/products", json=payload, headers=self.auth, name="POST /products (restock)", catch_response=True
        ) as res:
            check(res, ok=(200, 201), rejected=(409,))
            if res.status_code in (200, 201):
                with recorder.lock, open(RESTOCK_LOG, "a", encoding="utf-8") as f:
                    f.write(json.dumps({"product": product, "qty": qty}) + "\n")

    @task(5)
    def patch_product_price(self):
        with self.client.patch(
            f"/api/v1/products/{random.choice(PRODUCTS)}",
            json={"price": random.randint(20, 2000) * 1000, "status": "ACTIVE"},
            headers=self.auth,
            name="PATCH /products/{id}",
            catch_response=True,
        ) as res:
            check(res, rejected=(409,))

    @task(5)
    def list_customers(self):
        path = f"/api/v1/customers?page={random.randint(0, 49)}&size=10"
        with self.client.get(path, headers=self.auth, name="GET /customers?page", catch_response=True) as res:
            check(res)

    @task(3)
    def edit_customer(self):
        customer_id = random.randint(1, N_CUSTOMERS)
        path = f"/api/v1/customers/{customer_id}"
        with self.client.get(path, headers=self.auth, name="GET /customers/{id}", catch_response=True) as res:
            check(res)
            if res.status_code != 200:
                return
            customer = res.json()
        body = {
            "userId": customer.get("userId"),
            "customerName": f"Customer {customer_id:04d} v{random.randint(1, 99)}",
            "phone": customer["phone"],
        }
        with self.client.put(
            path, json=body, headers=self.auth, name="PUT /customers/{id}", catch_response=True
        ) as res:
            check(res)

    @task(2)
    def list_users(self):
        path = "/api/v1/users?page=0&size=20"
        with self.client.get(path, headers=self.auth, name="GET /users", catch_response=True) as res:
            check(res)


class FlashSaleUser(FastHttpUser):
    weight = 1
    wait_time = between(0.0, 0.05)
    network_timeout = 30.0

    def on_start(self):
        account = next_account()
        self.auth = auth_header(account["token"])
        self.customer_id = account["customer_id"]

    @task
    def buy_hot_product(self):
        with self.client.post(
            "/api/v1/orders",
            json={"customerNumber": self.customer_id, "items": [{"productCode": HOT_PRODUCT, "quantityOrdered": 1}]},
            headers=self.auth,
            name="POST /orders (hot)",
            catch_response=True,
        ) as res:
            check(res, ok=(201,), rejected=(400,))


def _p95(values):
    ordered = sorted(values)
    return ordered[int(0.95 * (len(ordered) - 1))]


if STAGED:

    class StagedShape(LoadTestShape):
        """Piecewise-constant load. Each stage is [end_second, users, spawn_rate].

        When STOP_P95_MS or STOP_ERROR_PCT is set, the run stops at the end of the
        first step that exceeds the threshold (breakpoint stage)."""

        stages = json.loads(os.environ["SHAPE_STAGES"])
        stop_p95_ms = float(os.environ.get("STOP_P95_MS", "0"))
        stop_error_pct = float(os.environ.get("STOP_ERROR_PCT", "0"))
        current = 0

        def _step_exceeds_threshold(self):
            samples = recorder.take_step_samples()
            if not samples or not (self.stop_p95_ms or self.stop_error_pct):
                return False
            p95 = _p95(s[0] for s in samples)
            error_pct = 100.0 * sum(1 for s in samples if not s[1]) / len(samples)
            users = self.stages[self.current][1]
            print(f"[shape] step users={users} n={len(samples)} p95={p95:.0f}ms err={error_pct:.2f}%", flush=True)
            too_slow = self.stop_p95_ms and p95 > self.stop_p95_ms
            too_many_errors = self.stop_error_pct and error_pct > self.stop_error_pct
            return bool(too_slow or too_many_errors)

        def tick(self):
            elapsed = self.get_run_time()
            while self.current < len(self.stages) and elapsed >= self.stages[self.current][0]:
                if self._step_exceeds_threshold():
                    print("[shape] threshold exceeded, stopping", flush=True)
                    return None
                self.current += 1
            if self.current >= len(self.stages):
                return None
            _, users, spawn_rate = self.stages[self.current]
            return users, spawn_rate
