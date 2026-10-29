"""Benchmark orchestration used by the Kaggle notebook (also runnable locally).

Responsibilities:
  * record the environment (hardware, software versions, git commit, JVM flags)
  * build a given git ref of the application
  * start MySQL and the Spring Boot app on their own CPU cores and run Locust
    on a separate core, so the load generator does not take CPU from the system under test
  * seed a deterministic dataset, snapshot it, and restore it before every stage
  * run one stage of the test plan while sampling per-process resources every second
  * reconcile inventory after each stage (stock must balance exactly)
"""

import contextlib
import csv
import json
import os
import platform
import shutil
import subprocess
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import psutil
import pymysql
import requests

import seed

HERE = Path(__file__).resolve().parent
DB = "KTPM"
SNAP_DB = "KTPM_SNAPSHOT"
DB_USER = "root"
DB_PASS = "12345678"
PORT = 3000
BASE_URL = f"http://127.0.0.1:{PORT}"
SHOPPER_PASSWORD = "Bench#2026"

JVM_OPTS = ["-Xms1g", "-Xmx1g", "-XX:+UseG1GC"]
APP_ENV = {
    "SERVER_PORT": str(PORT),
    "SPRING_DATASOURCE_URL": (
        f"jdbc:mysql://127.0.0.1:3306/{DB}?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
    ),
    "SPRING_DATASOURCE_USERNAME": DB_USER,
    "SPRING_DATASOURCE_PASSWORD": DB_PASS,
    "JWT_SECRET": "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
    "JWT_EXPIRATION": "86400000",
    "SPRING_JPA_SHOW_SQL": "false",
    "LOGGING_LEVEL_ROOT": "WARN",
    "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_BOOT_STARTUPINFOLOGGER": "INFO",
}


def run(cmd, check=True, **kwargs):
    return subprocess.run(cmd, shell=isinstance(cmd, str), check=check, text=True, capture_output=True, **kwargs)


def write_json(path, data):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, default=str)


def cpu_layout():
    """Return (cores for app + MySQL, core for Locust) as taskset CPU lists."""
    n = os.cpu_count() or 1
    if n >= 4:
        return f"0-{n - 2}", str(n - 1)
    if n >= 2:
        return "0", "1"
    return "0", "0"


def db_conn(database=None):
    return pymysql.connect(
        host="127.0.0.1", user=DB_USER, password=DB_PASS, database=database, autocommit=False, charset="utf8mb4"
    )


@contextlib.contextmanager
def db_cursor(database=None):
    """Cursor on a fresh connection; commits on success and always closes the connection."""
    conn = db_conn(database)
    try:
        with conn.cursor() as cur:
            yield cur
        conn.commit()
    finally:
        conn.close()


def _cpu_model():
    with open("/proc/cpuinfo", encoding="utf-8") as f:
        for line in f:
            if line.startswith("model name"):
                return line.split(":", 1)[1].strip()
    return "?"


def _first_line_containing(text, word):
    return next((line for line in text.splitlines() if word in line), "?")


def capture_env(out_dir, repo_dir, refs):
    sut_cpus, lg_cpus = cpu_layout()
    mysqld_words = run("mysqld --version", check=False).stdout.split()
    info = {
        "captured_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "platform": platform.platform(),
        "cpu_model": _cpu_model(),
        "logical_cpus": os.cpu_count(),
        "ram_gb": round(psutil.virtual_memory().total / 2**30, 1),
        "java": _first_line_containing(run("java -version 2>&1", check=False).stdout, "version"),
        "mysql": "MySQL " + (mysqld_words[2] if len(mysqld_words) > 2 else "?"),
        "locust": " ".join(run("locust --version", check=False).stdout.split()[:2]),
        "python": platform.python_version(),
        "jvm_opts": JVM_OPTS,
        "cpu_pinning": {"app+mysql": sut_cpus, "locust": lg_cpus},
        "refs": {
            label: run(["git", "-C", str(repo_dir), "rev-parse", ref]).stdout.strip() for label, ref in refs.items()
        },
    }
    Path(out_dir).mkdir(parents=True, exist_ok=True)
    write_json(Path(out_dir) / "environment.json", info)
    return info


def start_mysql():
    run("service mysql start", check=False)
    for _ in range(30):
        if run("mysqladmin ping", check=False).returncode == 0:
            break
        time.sleep(1)
    run(
        [
            "mysql",
            "-u",
            "root",
            "-e",
            f"ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '{DB_PASS}'; FLUSH PRIVILEGES;",
        ],
        check=False,
    )
    with db_cursor() as cur:
        cur.execute("SELECT @@version, @@innodb_buffer_pool_size, @@max_connections, @@transaction_isolation")
        version, pool, max_conn, isolation = cur.fetchone()
    return {
        "version": version,
        "innodb_buffer_pool_mb": int(pool) // 2**20,
        "max_connections": max_conn,
        "isolation": isolation,
    }


def mysqld_pid():
    for p in psutil.process_iter(["name"]):
        if p.info["name"] == "mysqld":
            return p.pid
    return None


def pin(pid, cpus):
    if pid and shutil.which("taskset"):
        run(["taskset", "-a", "-cp", cpus, str(pid)], check=False)


def reset_database():
    with db_cursor() as cur:
        cur.execute(f"DROP DATABASE IF EXISTS {DB}")
        cur.execute(f"DROP DATABASE IF EXISTS {SNAP_DB}")
        cur.execute(f"CREATE DATABASE {DB} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")


def build(repo_dir, ref, work_dir):
    """Check out `ref` into its own worktree and build the jar. Returns the jar path."""
    work_dir = Path(work_dir)
    if work_dir.exists():
        run(["git", "-C", str(repo_dir), "worktree", "remove", "--force", str(work_dir)], check=False)
        shutil.rmtree(work_dir, ignore_errors=True)
    run(["git", "-C", str(repo_dir), "worktree", "add", "--detach", str(work_dir), ref])

    mvnw = work_dir / "mvnw"
    if mvnw.exists():
        run(f"sed -i 's/\\r$//' {mvnw} && chmod +x {mvnw}", check=False)
    goals = ["-q", "-B", "clean", "package", "-DskipTests"]
    res = run(["./mvnw" if mvnw.exists() else "mvn", *goals], check=False, cwd=work_dir)
    if res.returncode != 0 and mvnw.exists() and shutil.which("mvn"):
        res = run(["mvn", *goals], check=False, cwd=work_dir)
    if res.returncode != 0:
        raise RuntimeError("Build failed:\n" + res.stdout[-3000:] + res.stderr[-2000:])
    jars = [j for j in (work_dir / "target").glob("*.jar") if not j.name.endswith(("-plain.jar", ".original"))]
    return str(jars[0])


class App:
    def __init__(self, jar, log_path, cpus):
        self.jar, self.log_path, self.cpus = jar, log_path, cpus
        self.proc = None
        self.log = None

    def start(self, timeout=180):
        cmd = ["java", *JVM_OPTS, "-jar", self.jar]
        if shutil.which("taskset"):
            cmd = ["taskset", "-c", self.cpus, *cmd]
        self.log = open(self.log_path, "w", encoding="utf-8")  # noqa: SIM115 - closed in stop()
        self.proc = subprocess.Popen(cmd, stdout=self.log, stderr=subprocess.STDOUT, env={**os.environ, **APP_ENV})
        started = time.time()
        while time.time() - started < timeout:
            if self.proc.poll() is not None:
                raise RuntimeError(f"App exited early, see {self.log_path}")
            with contextlib.suppress(requests.RequestException):
                if requests.get(f"{BASE_URL}/v3/api-docs", timeout=2).status_code == 200:
                    return round(time.time() - started, 1)
            time.sleep(1)
        raise RuntimeError(f"App did not become healthy in {timeout}s, see {self.log_path}")

    @property
    def pid(self):
        """PID of the JVM (taskset execs java, so this is normally the Popen PID itself)."""
        if not self.proc:
            return None
        try:
            children = psutil.Process(self.proc.pid).children(recursive=True)
        except psutil.Error:
            return self.proc.pid
        java = [c for c in children if c.name() == "java"]
        return java[0].pid if java else self.proc.pid

    def stop(self):
        if self.proc and self.proc.poll() is None:
            self.proc.terminate()
            try:
                self.proc.wait(timeout=20)
            except subprocess.TimeoutExpired:
                self.proc.kill()
        if self.log:
            self.log.close()


def _login(username):
    res = requests.post(
        f"{BASE_URL}/api/v1/auth/login", json={"username": username, "password": SHOPPER_PASSWORD}, timeout=30
    )
    res.raise_for_status()
    return res.json()["accessToken"]


def prepare_data(out_dir):
    """Seed the deterministic dataset, snapshot it and issue a JWT for every account in advance."""
    template_user = f"hash_template_{int(time.time())}"
    requests.post(
        f"{BASE_URL}/api/v1/auth/register",
        json={"username": template_user, "password": SHOPPER_PASSWORD},
        timeout=10,
    ).raise_for_status()

    conn = db_conn(DB)
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT password FROM users WHERE username=%s", (template_user,))
            password_hash = cur.fetchone()[0]
        counts = seed.load(conn, password_hash)
        seed.snapshot(conn, DB, SNAP_DB)
        with conn.cursor() as cur:
            cur.execute(
                "SELECT product_code, product_name, buy_price FROM products "
                "WHERE status='ACTIVE' AND product_code <> %s",
                (seed.HOT_PRODUCT_ID,),
            )
            active = cur.fetchall()
    finally:
        conn.close()

    names = [f"shopper_{i:04d}" for i in range(1, seed.N_SHOPPERS + 1)]
    with ThreadPoolExecutor(4) as pool:
        tokens = list(pool.map(_login, names))
    data = {
        "admin": {"username": "bench_admin", "token": _login("bench_admin")},
        "shoppers": [
            {"username": name, "password": SHOPPER_PASSWORD, "token": token, "customer_id": i + 1}
            for i, (name, token) in enumerate(zip(names, tokens, strict=True))
        ],
        "n_customers": seed.N_CUSTOMERS,
        "n_orders": seed.N_ORDERS,
        "hot_product": seed.HOT_PRODUCT_ID,
        "hot_stock": seed.HOT_STOCK,
        "active_products": [p[0] for p in active],
        "product_meta": {str(p[0]): [p[1], float(p[2])] for p in active},
        "row_counts": counts,
    }
    path = Path(out_dir) / "data.json"
    write_json(path, data)
    return str(path), counts


def restore_database():
    conn = db_conn()
    try:
        seed.restore(conn, DB, SNAP_DB)
    finally:
        conn.close()


class Monitor(threading.Thread):
    """Samples CPU and RSS of the app and MySQL once per second, plus MySQL status counters."""

    STATUS_VARS = ("Innodb_row_lock_waits", "Innodb_row_lock_time", "Threads_running", "Threads_connected", "Questions")

    def __init__(self, out_csv, pids, interval=1.0):
        super().__init__(daemon=True)
        self.out_csv, self.pids, self.interval = out_csv, pids, interval
        self.stop_event = threading.Event()
        self.rows = []

    def _processes(self):
        procs = {}
        for role, pid in self.pids.items():
            try:
                root = psutil.Process(pid)
                procs[role] = [root, *root.children(recursive=True)]
                for p in procs[role]:
                    p.cpu_percent(None)
            except (psutil.Error, TypeError):
                procs[role] = []
        return procs

    def _mysql_status(self, cur):
        placeholders = ",".join(["%s"] * len(self.STATUS_VARS))
        cur.execute(f"SHOW GLOBAL STATUS WHERE Variable_name IN ({placeholders})", self.STATUS_VARS)
        out = {name: int(value) for name, value in cur.fetchall()}
        cur.execute("SELECT COUNT FROM information_schema.INNODB_METRICS WHERE NAME='lock_deadlocks'")
        row = cur.fetchone()
        out["Innodb_deadlocks"] = int(row[0]) if row else 0
        return out

    def _sample(self, procs, cur):
        cores = psutil.cpu_percent(None, percpu=True)
        row = {"ts": round(time.time(), 3), "cpu_machine_pct": round(sum(cores) / len(cores), 1)}
        row.update({f"core{i}_pct": c for i, c in enumerate(cores)})
        for role, plist in procs.items():
            cpu, rss = 0.0, 0
            for p in plist:
                with contextlib.suppress(psutil.Error):
                    cpu += p.cpu_percent(None)
                    rss += p.memory_info().rss
            row[f"{role}_cores"] = round(cpu / 100.0, 3)
            row[f"{role}_rss_mb"] = round(rss / 2**20, 1)
        with contextlib.suppress(pymysql.Error):
            row.update(self._mysql_status(cur))
        row["mem_used_mb"] = round(psutil.virtual_memory().used / 2**20, 1)
        row["ncpu"] = os.cpu_count() or 1
        return row

    def run(self):
        procs = self._processes()
        psutil.cpu_percent(None, percpu=True)
        conn = db_conn()
        try:
            with conn.cursor() as cur:
                while not self.stop_event.wait(self.interval):
                    self.rows.append(self._sample(procs, cur))
        finally:
            conn.close()
        if self.rows:
            with open(self.out_csv, "w", newline="", encoding="utf-8") as f:
                writer = csv.DictWriter(f, fieldnames=list(self.rows[-1].keys()), extrasaction="ignore")
                writer.writeheader()
                writer.writerows(self.rows)

    def stop(self):
        self.stop_event.set()
        self.join(timeout=10)


def _locust_command(stage_dir, user_classes, users, spawn_rate, duration, shape):
    cmd = [
        "locust",
        "-f",
        str(HERE / "locustfile.py"),
        "--headless",
        "--host",
        BASE_URL,
        "--html",
        str(stage_dir / "locust_report.html"),
        "--only-summary",
        "--stop-timeout",
        "10",
        "--loglevel",
        "WARNING",
    ]
    if not shape:
        cmd += ["-u", str(users), "-r", str(spawn_rate), "-t", f"{duration}s"]
    cmd += list(user_classes)
    if shutil.which("taskset"):
        cmd = ["taskset", "-c", cpu_layout()[1], *cmd]
    return cmd


def _locust_env(stage_dir, data_path, wait, shape, stop_p95_ms, stop_error_pct):
    env = {
        **os.environ,
        "BENCH_DATA": str(data_path),
        "RAW_LOG": str(stage_dir / "raw_requests.csv"),
        "RESTOCK_LOG": str(stage_dir / "restocks.jsonl"),
        "WAIT_MIN": str(wait[0]),
        "WAIT_MAX": str(wait[1]),
    }
    if shape:
        env["SHAPE_STAGES"] = json.dumps(shape)
        if stop_p95_ms:
            env["STOP_P95_MS"] = str(stop_p95_ms)
        if stop_error_pct:
            env["STOP_ERROR_PCT"] = str(stop_error_pct)
    return env


def _max_order_number():
    with db_cursor(DB) as cur:
        cur.execute("SELECT COALESCE(MAX(order_number), 0) FROM orders")
        return cur.fetchone()[0]


def _wait_and_sample_cpu(proc):
    """Wait for the Locust process to exit, sampling its CPU use once per second."""
    samples = []
    try:
        locust = psutil.Process(proc.pid)
        locust.cpu_percent(None)
    except psutil.Error:
        locust = None
    while proc.poll() is None:
        time.sleep(1)
        if locust:
            with contextlib.suppress(psutil.Error):
                samples.append((round(time.time(), 3), round(locust.cpu_percent(None) / 100.0, 3)))
    return samples


def run_stage(
    stage_dir,
    data_path,
    app,
    user_classes,
    users=None,
    spawn_rate=None,
    duration=None,
    shape=None,
    wait=(1.0, 3.0),
    stop_p95_ms=None,
    stop_error_pct=None,
    restore=True,
    measure_from_s=0,
    description="",
):
    """Run one Locust stage and collect raw samples, resource usage and an inventory reconciliation."""
    stage_dir = Path(stage_dir)
    if stage_dir.exists():
        shutil.rmtree(stage_dir)
    stage_dir.mkdir(parents=True)
    if restore:
        restore_database()

    cmd = _locust_command(stage_dir, user_classes, users, spawn_rate, duration, shape)
    env = _locust_env(stage_dir, data_path, wait, shape, stop_p95_ms, stop_error_pct)
    max_order_before = _max_order_number()

    monitor = Monitor(stage_dir / "resources.csv", {"app": app.pid, "mysql": mysqld_pid()})
    monitor.start()
    t_start = time.time()
    stdout_path = stage_dir / "locust_stdout.txt"
    with open(stdout_path, "w", encoding="utf-8") as out:
        proc = subprocess.Popen(cmd, env=env, stdout=out, stderr=subprocess.STDOUT, text=True)
        cpu_samples = _wait_and_sample_cpu(proc)
    t_end = time.time()
    monitor.stop()

    if not (stage_dir / "raw_requests.csv").exists():
        output = stdout_path.read_text(encoding="utf-8")
        raise RuntimeError(f"Locust produced no samples (exit {proc.returncode}):\n{output[-2000:]}")
    with open(stage_dir / "locust_cpu.csv", "w", encoding="utf-8") as f:
        f.write("ts,locust_cores\n" + "\n".join(f"{t},{c}" for t, c in cpu_samples))

    meta = {
        "description": description,
        "user_classes": list(user_classes),
        "users": users,
        "spawn_rate": spawn_rate,
        "duration_s": duration,
        "shape": shape,
        "wait_s": list(wait),
        "stop_p95_ms": stop_p95_ms,
        "stop_error_pct": stop_error_pct,
        "restored_db": restore,
        "t_start": t_start,
        "t_end": t_end,
        "measure_from_s": measure_from_s,
        "max_order_before": max_order_before,
        "locust_exit_code": proc.returncode,
    }
    if restore:
        meta["inventory"] = reconcile_inventory(stage_dir, max_order_before)
    write_json(stage_dir / "meta.json", meta)
    return meta


def _restocked_units(stage_dir):
    totals = {}
    log = Path(stage_dir) / "restocks.jsonl"
    if log.exists():
        for line in log.read_text(encoding="utf-8").splitlines():
            event = json.loads(line)
            totals[event["product"]] = totals.get(event["product"], 0) + event["qty"]
    return totals


def reconcile_inventory(stage_dir, max_order_before):
    """Check stock_after == stock_before - sold + returned + restocked for every product.

    sold:      units in orders created during the stage that are not cancelled
    returned:  units in seeded orders that were cancelled during the stage
    restocked: units added through POST /products, logged by the load generator
    """
    restocked = _restocked_units(stage_dir)
    with db_cursor(DB) as cur:
        cur.execute(f"SELECT product_code, quantity_in_stock FROM {SNAP_DB}.products")
        before = dict(cur.fetchall())
        cur.execute("SELECT product_code, quantity_in_stock FROM products")
        after = dict(cur.fetchall())
        cur.execute(
            """SELECT d.product_code, SUM(d.quantity_ordered) FROM order_details d
               JOIN orders o ON o.order_number = d.order_number
               WHERE o.order_number > %s AND o.status <> 'CANCELLED' GROUP BY d.product_code""",
            (max_order_before,),
        )
        sold = {k: int(v) for k, v in cur.fetchall()}
        cur.execute(
            f"""SELECT d.product_code, SUM(d.quantity_ordered) FROM order_details d
                JOIN orders o ON o.order_number = d.order_number
                JOIN {SNAP_DB}.orders s ON s.order_number = o.order_number
                WHERE o.status = 'CANCELLED' AND s.status <> 'CANCELLED' GROUP BY d.product_code"""
        )
        returned = {k: int(v) for k, v in cur.fetchall()}
        cur.execute("SELECT status, COUNT(*) FROM orders WHERE order_number > %s GROUP BY status", (max_order_before,))
        new_orders = dict(cur.fetchall())
        cur.execute(
            """SELECT COUNT(*) FROM orders o
               JOIN (SELECT order_number, SUM(quantity_ordered * price_each) total
                     FROM order_details GROUP BY order_number) d ON d.order_number = o.order_number
               WHERE o.order_number > %s AND ABS(o.total_amount - d.total) > 0.001""",
            (max_order_before,),
        )
        wrong_totals = cur.fetchone()[0]
        cur.execute("SELECT COUNT(*) FROM products WHERE quantity_in_stock < 0")
        negative = cur.fetchone()[0]

    mismatches = []
    for product, stock_before in before.items():
        expected = stock_before - sold.get(product, 0) + returned.get(product, 0) + restocked.get(product, 0)
        if after.get(product) != expected:
            mismatches.append(
                {
                    "product": product,
                    "before": stock_before,
                    "sold": sold.get(product, 0),
                    "returned": returned.get(product, 0),
                    "restocked": restocked.get(product, 0),
                    "expected": expected,
                    "actual": after.get(product),
                }
            )
    hot = seed.HOT_PRODUCT_ID
    return {
        "products_checked": len(before),
        "mismatches": mismatches[:20],
        "mismatch_count": len(mismatches),
        "negative_stock_products": negative,
        "orders_with_wrong_total": wrong_totals,
        "new_orders_by_status": new_orders,
        "units_sold": sum(sold.values()),
        "units_returned_from_seeded": sum(returned.values()),
        "units_restocked": sum(restocked.values()),
        "hot_product": {"stock_before": before.get(hot), "sold": sold.get(hot, 0), "stock_after": after.get(hot)},
        "balanced": not mismatches and negative == 0 and wrong_totals == 0,
    }
