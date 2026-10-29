"""Turn raw stage outputs into tables, charts and a Markdown report.

Latency percentiles are computed from the raw per-request log written by
locustfile.py, restricted to the measurement window of each stage (ramp-up
excluded), so they are exact rather than Locust's rolling-window estimates.
"""

import json
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
import numpy as np  # noqa: E402
import pandas as pd  # noqa: E402

SLO = {"p95_ms": 500, "p99_ms": 1000, "error_pct": 1.0}
LG_SATURATION_CORES = 0.85
SUT_SATURATION = 0.9
SINGLE_RUN_NOISE_PCT = 20.0
PCTS = (50, 90, 95, 99)

COLUMN_LABELS = {
    "stage": "Bài",
    "requests": "Số request",
    "rps": "Throughput (req/s)",
    "p50_ms": "p50 (ms)",
    "p95_ms": "p95 (ms)",
    "p99_ms": "p99 (ms)",
    "error_pct": "Lỗi (%)",
    "users": "Người dùng ảo",
    "app_cpu_cores_mean": "CPU ứng dụng (core)",
    "mysql_cpu_cores_mean": "CPU MySQL (core)",
    "locust_cpu_cores_p95": "CPU Locust p95 (core)",
    "littles_law_deviation_%": "Lệch so với định luật Little (%)",
    "mysql_avg_row_lock_wait_ms": "Chờ khóa dòng TB (ms)",
    "slo_pass": "Đạt SLO",
    "inventory_balanced": "Tồn kho cân bằng",
    "endpoint": "Endpoint",
    "errors": "Số lỗi",
}


def read_json(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def write_json(path, data):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, default=str, ensure_ascii=False)


def _read_optional_csv(path, t_start):
    if not path.exists():
        return pd.DataFrame()
    df = pd.read_csv(path)
    if not df.empty:
        df["t"] = df["ts"] - t_start
    return df


def load_stage(stage_dir):
    d = Path(stage_dir)
    meta = read_json(d / "meta.json")
    raw = pd.read_csv(d / "raw_requests.csv")
    raw["t"] = raw["ts"] - meta["t_start"]
    res = _read_optional_csv(d / "resources.csv", meta["t_start"])
    lg = _read_optional_csv(d / "locust_cpu.csv", meta["t_start"])
    return meta, raw, res, lg


def window(df, start, end=None):
    if df.empty:
        return df
    mask = df["t"] >= start
    if end is not None:
        mask &= df["t"] < end
    return df[mask]


def meets_slo(stats):
    return bool(
        stats.get("requests")
        and stats["p95_ms"] <= SLO["p95_ms"]
        and stats["p99_ms"] <= SLO["p99_ms"]
        and stats["error_pct"] <= SLO["error_pct"]
    )


def latency_stats(raw):
    if raw.empty:
        return {"requests": 0}
    duration = max(raw["t"].max() - raw["t"].min(), 1e-9)
    rt = raw["rt_ms"].to_numpy()
    out = {
        "requests": len(raw),
        "duration_s": round(duration, 1),
        "rps": round(len(raw) / duration, 1),
        "error_pct": round(100.0 * (1 - raw["ok"].mean()), 3),
        "mean_ms": round(float(rt.mean()), 1),
        "max_ms": round(float(rt.max()), 1),
    }
    for p in PCTS:
        out[f"p{p}_ms"] = round(float(np.percentile(rt, p)), 1)
    return out


def resource_stats(res, lg):
    out = {}
    if not res.empty:
        for role in ("app", "mysql"):
            col = f"{role}_cores"
            if col in res:
                out[f"{role}_cpu_cores_mean"] = round(float(res[col].mean()), 2)
                out[f"{role}_cpu_cores_p95"] = round(float(res[col].quantile(0.95)), 2)
                out[f"{role}_rss_mb_max"] = round(float(res[f"{role}_rss_mb"].max()), 0)
        out["machine_cpu_pct_mean"] = round(float(res["cpu_machine_pct"].mean()), 1)
        for key in ("Innodb_row_lock_waits", "Innodb_row_lock_time", "Innodb_deadlocks", "Questions"):
            if key in res:
                out[f"mysql_{key.lower()}_delta"] = int(res[key].iloc[-1] - res[key].iloc[0])
        if "Questions" in res:
            elapsed = max(res["t"].max() - res["t"].min(), 1e-9)
            out["mysql_queries_per_s"] = round(out["mysql_questions_delta"] / elapsed, 1)
        if out.get("mysql_innodb_row_lock_waits_delta"):
            waits = out["mysql_innodb_row_lock_waits_delta"]
            out["mysql_avg_row_lock_wait_ms"] = round(out["mysql_innodb_row_lock_time_delta"] / waits, 1)
    if not lg.empty:
        out["locust_cpu_cores_p95"] = round(float(lg["locust_cores"].quantile(0.95)), 2)
        out["load_generator_saturated"] = bool(out["locust_cpu_cores_p95"] > LG_SATURATION_CORES)
    return out


def endpoint_table(raw):
    rows = []
    for name, group in raw.groupby("name"):
        rt = group["rt_ms"].to_numpy()
        statuses = group["status"].value_counts().to_dict()
        rows.append(
            {
                "endpoint": name,
                "requests": len(group),
                "share_%": round(100 * len(group) / len(raw), 1),
                "p50_ms": round(np.percentile(rt, 50), 1),
                "p95_ms": round(np.percentile(rt, 95), 1),
                "p99_ms": round(np.percentile(rt, 99), 1),
                "errors": int((group["ok"] == 0).sum()),
                "status_codes": ", ".join(f"{k}:{v}" for k, v in sorted(statuses.items())),
            }
        )
    return pd.DataFrame(rows).sort_values("requests", ascending=False)


def summarize_constant(stage_dir):
    meta, raw, res, lg = load_stage(stage_dir)
    start = meta.get("measure_from_s", 0)
    raw_w = window(raw, start)
    lat = latency_stats(raw_w)
    summary = {"stage": Path(stage_dir).name, **lat, **resource_stats(window(res, start), window(lg, start))}
    if meta.get("users") and meta.get("wait_s") and lat.get("requests"):
        think_time = sum(meta["wait_s"]) / 2.0
        predicted = meta["users"] / (think_time + lat["mean_ms"] / 1000.0)
        summary["littles_law_predicted_rps"] = round(predicted, 1)
        summary["littles_law_deviation_%"] = round(100 * (lat["rps"] - predicted) / predicted, 1)
    summary["slo_pass"] = meets_slo(lat)
    if "inventory" in meta:
        summary["inventory_balanced"] = meta["inventory"]["balanced"]
    return summary, endpoint_table(raw_w)


def step_table(stage_dir, settle_s=10):
    """One row per load step of a staged run; the first `settle_s` seconds of each step are dropped."""
    meta, raw, res, lg = load_stage(stage_dir)
    rows, prev_end = [], 0
    for end, users, _ in meta["shape"]:
        start = prev_end + settle_s
        prev_end = end
        samples = window(raw, start, end)
        if len(samples) < 20:
            continue
        row = {
            "users": users,
            **latency_stats(samples),
            **resource_stats(window(res, start, end), window(lg, start, end)),
        }
        row["slo_pass"] = meets_slo(row)
        rows.append(row)
    return pd.DataFrame(rows)


def _throughput_knee(steps):
    """First step after which throughput grows by less than a quarter of the relative load increase."""
    for i in range(1, len(steps)):
        prev, cur = steps.iloc[i - 1], steps.iloc[i]
        rps_gain = (cur["rps"] - prev["rps"]) / prev["rps"]
        load_gain = (cur["users"] - prev["users"]) / prev["users"]
        if rps_gain < 0.25 * load_gain:
            return int(prev["users"])
    return None


def capacity(steps, sut_cores=None):
    if steps.empty:
        return {}
    within_slo = steps[steps["slo_pass"]]
    best = within_slo.loc[within_slo["rps"].idxmax()] if not within_slo.empty else None
    violations = steps.loc[~steps["slo_pass"], "users"]

    last = steps.iloc[-1]
    busiest, utilization, split = None, None, None
    if "app_cpu_cores_mean" in steps:
        app, db = float(last.get("app_cpu_cores_mean", 0)), float(last.get("mysql_cpu_cores_mean", 0))
        busiest = "app (JVM)" if app >= db else "mysql"
        if sut_cores:
            utilization = round((app + db) / sut_cores, 2)
        if app + db:
            split = f"ứng dụng {100 * app / (app + db):.0f}% / MySQL {100 * db / (app + db):.0f}%"

    return {
        "sut_cpu_cores_allotted": sut_cores,
        "sut_cpu_utilization_at_last_step": utilization,
        "sut_cpu_saturated": None if utilization is None else utilization >= SUT_SATURATION,
        "cpu_split_at_last_step": split,
        "max_rps_within_slo": None if best is None else float(best["rps"]),
        "users_at_max_rps_within_slo": None if best is None else int(best["users"]),
        "p95_at_that_point_ms": None if best is None else float(best["p95_ms"]),
        "throughput_knee_users": _throughput_knee(steps),
        "first_slo_violation_users": None if violations.empty else int(violations.iloc[0]),
        "busiest_process_at_last_step": busiest,
    }


def _bin_seconds(df, bin_s):
    df = df.copy()
    df["bin"] = (df["t"] // bin_s) * bin_s
    return df


def timeline(stage_dir, bin_s=5):
    _, raw, res, _ = load_stage(stage_dir)
    grouped = _bin_seconds(raw, bin_s).groupby("bin")
    tl = (
        pd.DataFrame(
            {
                "users": grouped["users"].max(),
                "rps": grouped.size() / bin_s,
                "p50_ms": grouped["rt_ms"].median(),
                "p95_ms": grouped["rt_ms"].quantile(0.95),
                "error_pct": 100 * (1 - grouped["ok"].mean()),
            }
        )
        .reset_index()
        .rename(columns={"bin": "t"})
    )
    if not res.empty:
        cpu = _bin_seconds(res, bin_s).groupby("bin")[["app_cores", "mysql_cores"]].mean()
        tl = tl.merge(cpu.reset_index().rename(columns={"bin": "t"}), on="t", how="left")
    return tl


def spike_recovery(tl, raw, baseline_end_s, spike_end_s, tolerance=1.2):
    baseline = tl[tl["t"] < baseline_end_s]["p95_ms"].median()
    during = window(raw, baseline_end_s, spike_end_s)
    after = tl[tl["t"] >= spike_end_s]
    recovered = after[after["p95_ms"] <= baseline * tolerance]
    peak = tl[(tl["t"] >= baseline_end_s) & (tl["t"] < spike_end_s)]
    has_samples = not during.empty
    return {
        "baseline_p95_ms": round(float(baseline), 1),
        "spike_p95_ms": round(float(np.percentile(during["rt_ms"], 95)), 1) if has_samples else None,
        "spike_p99_ms": round(float(np.percentile(during["rt_ms"], 99)), 1) if has_samples else None,
        "worst_5s_bin_p95_ms": round(float(peak["p95_ms"].max()), 1) if not peak.empty else None,
        "spike_error_pct": round(100 * (1 - float(during["ok"].mean())), 2) if has_samples else None,
        "recovery_s": None if recovered.empty else round(float(recovered["t"].iloc[0] - spike_end_s), 1),
    }


def _save(fig, path):
    fig.tight_layout()
    fig.savefig(path, dpi=130)
    plt.close(fig)


def plot_steps(steps, path, title):
    fig, (top, bottom) = plt.subplots(2, 1, figsize=(10, 8), sharex=True)
    top.plot(steps["users"], steps["rps"], "o-", color="#2a7d4f", label="Throughput (req/s)")
    top.set_ylabel("Throughput (req/s)")
    latency = top.twinx()
    latency.plot(steps["users"], steps["p95_ms"], "s--", color="#c0392b", label="p95 (ms)")
    latency.plot(steps["users"], steps["p99_ms"], "^:", color="#8e44ad", label="p99 (ms)")
    latency.axhline(SLO["p95_ms"], color="#c0392b", alpha=0.3, lw=1)
    latency.set_ylabel("Độ trễ (ms)")
    handles, labels = top.get_legend_handles_labels()
    handles2, labels2 = latency.get_legend_handles_labels()
    top.legend(handles + handles2, labels + labels2, loc="upper left")
    top.set_title(title)
    top.grid(alpha=0.3)

    cpu_series = {
        "app_cpu_cores_mean": "Ứng dụng (JVM)",
        "mysql_cpu_cores_mean": "MySQL",
        "locust_cpu_cores_p95": "Locust (p95)",
    }
    for col, label in cpu_series.items():
        if col in steps:
            bottom.plot(steps["users"], steps[col], "o-", label=label)
    bottom.set_xlabel("Người dùng ảo")
    bottom.set_ylabel("CPU (core)")
    bottom.legend()
    bottom.grid(alpha=0.3)
    _save(fig, path)


def plot_timeline(tl, path, title, marks=()):
    fig, (top, bottom) = plt.subplots(2, 1, figsize=(10, 7), sharex=True)
    top.plot(tl["t"], tl["rps"], color="#2a7d4f", label="req/s")
    top.set_ylabel("req/s")
    users = top.twinx()
    users.plot(tl["t"], tl["users"], color="#7f8c8d", ls=":", label="Người dùng ảo")
    users.set_ylabel("Người dùng ảo")
    top.set_title(title)
    top.grid(alpha=0.3)
    bottom.plot(tl["t"], tl["p50_ms"], label="p50")
    bottom.plot(tl["t"], tl["p95_ms"], label="p95")
    bottom.set_ylabel("Độ trễ (ms)")
    bottom.set_xlabel("Thời gian từ đầu bài (s)")
    bottom.legend()
    bottom.grid(alpha=0.3)
    for mark in marks:
        for ax in (top, bottom):
            ax.axvline(mark, color="k", alpha=0.2)
    _save(fig, path)


def plot_endpoints(ep, path, title):
    ep = ep.sort_values("p95_ms")
    fig, ax = plt.subplots(figsize=(10, 0.4 * len(ep) + 1.5))
    y = np.arange(len(ep))
    ax.barh(y - 0.2, ep["p50_ms"], 0.4, label="p50")
    ax.barh(y + 0.2, ep["p95_ms"], 0.4, label="p95")
    ax.set_yticks(y)
    ax.set_yticklabels(ep["endpoint"], fontsize=8)
    ax.set_xlabel("Độ trễ (ms)")
    ax.set_title(title)
    ax.legend()
    ax.grid(axis="x", alpha=0.3)
    _save(fig, path)


def _format_cell(value):
    if isinstance(value, (bool, np.bool_)):
        return "Có" if value else "Không"
    if value is None or (isinstance(value, float) and np.isnan(value)):
        return "-"
    return value


def md_table(df, cols=None):
    df = (df[cols] if cols else df).rename(columns=COLUMN_LABELS)
    header = "| " + " | ".join(df.columns) + " |\n|" + " :--- |" * len(df.columns) + "\n"
    rows = ("| " + " | ".join(str(_format_cell(v)) for v in row) + " |" for row in df.itertuples(index=False))
    return header + "\n".join(rows)


def _kv_table(pairs, key_title, value_title):
    return md_table(pd.DataFrame([{key_title: k, value_title: v} for k, v in pairs]))


def _sut_cores(env):
    pinned = env.get("cpu_pinning", {}).get("app+mysql", "")
    if "-" in pinned:
        lo, hi = pinned.split("-")
        return int(hi) - int(lo) + 1
    return 1 if pinned else None


def _verdict(passed):
    return "Đạt" if passed else "Không đạt"


def _present(columns, df):
    return [c for c in columns if c in df]


# Each _section_* function returns (summary entry, verdict row, Markdown text).
# A verdict row is (stage, criterion, observed result, passed).


def _section_smoke(stage_dir):
    s, _ = summarize_constant(stage_dir)
    passed = s["error_pct"] == 0 and s.get("inventory_balanced")
    verdict = ("smoke", "Không lỗi, tồn kho cân bằng", f"{s['requests']} request, lỗi {s['error_pct']}%", passed)
    balance = "cân bằng" if s.get("inventory_balanced") else "lệch"
    text = (
        "### 3.{n}. Smoke\n\n"
        f"Bài smoke gửi {s['requests']} request với tỉ lệ lỗi {s['error_pct']}%. Tồn kho sau bài: {balance}.\n"
    )
    return s, verdict, text


def _variation(df):
    keys = ("rps", "p50_ms", "p95_ms", "p99_ms", "error_pct", "app_cpu_cores_mean", "mysql_cpu_cores_mean")
    out = {}
    for key in (k for k in keys if k in df):
        mean = df[key].mean()
        std = df[key].std(ddof=1) if len(df) > 1 else 0.0
        out[key] = {
            "mean": round(mean, 2),
            "std": round(std, 2),
            "cv_%": round(100 * std / mean, 1) if len(df) > 1 and mean else 0.0,
        }
    return out


def _section_load(label_dir, stage_dirs):
    runs, endpoints = zip(*(summarize_constant(p) for p in stage_dirs), strict=True)
    df = pd.DataFrame(runs)
    agg = _variation(df)
    summary = {"runs": list(runs), "aggregate": agg}

    criterion = f"p95 ≤ {SLO['p95_ms']} ms, p99 ≤ {SLO['p99_ms']} ms, lỗi ≤ {SLO['error_pct']}% ở mọi lần chạy"
    observed = f"p95 {agg['p95_ms']['mean']} ± {agg['p95_ms']['std']} ms, {agg['rps']['mean']} req/s"
    verdict = ("load", criterion, observed, bool(df["slo_pass"].all()))

    per_endpoint = (
        pd.concat(endpoints)
        .groupby("endpoint")
        .agg(
            requests=("requests", "sum"),
            p50_ms=("p50_ms", "mean"),
            p95_ms=("p95_ms", "mean"),
            p99_ms=("p99_ms", "mean"),
            errors=("errors", "sum"),
        )
        .round(1)
        .reset_index()
        .sort_values("requests", ascending=False)
    )
    per_endpoint.to_csv(label_dir / "endpoints_average_load.csv", index=False)
    plot_endpoints(
        per_endpoint,
        label_dir / "endpoints_average_load.png",
        "Độ trễ theo endpoint ở tải thường (trung bình các lần chạy)",
    )

    run_cols = _present(
        (
            "stage",
            "requests",
            "rps",
            "p50_ms",
            "p95_ms",
            "p99_ms",
            "error_pct",
            "app_cpu_cores_mean",
            "mysql_cpu_cores_mean",
            "locust_cpu_cores_p95",
            "littles_law_deviation_%",
            "slo_pass",
            "inventory_balanced",
        ),
        df,
    )
    variation = pd.DataFrame(
        [
            {
                "Chỉ số": COLUMN_LABELS.get(k, k),
                "Trung bình": v["mean"],
                "Độ lệch chuẩn": v["std"],
                "Hệ số biến thiên (%)": v["cv_%"],
            }
            for k, v in agg.items()
        ]
    )
    text = (
        f"### 3.{{n}}. Tải thường\n\nBài tải thường chạy {len(runs)} lần. "
        f"Throughput trung bình là {agg['rps']['mean']} req/s và p95 trung bình là {agg['p95_ms']['mean']} ms.\n\n"
        + md_table(df, run_cols)
        + "\n\n**Độ dao động giữa các lần chạy**\n\n"
        + md_table(variation)
        + "\n\n**Độ trễ theo endpoint** (trung bình các lần chạy)\n\n"
        + md_table(per_endpoint)
        + "\n\n![Độ trễ p50 và p95 theo endpoint ở tải thường](endpoints_average_load.png)\n"
    )
    return summary, verdict, text


def _section_breakpoint(label_dir, env, deviations):
    stage_dir = label_dir / "breakpoint"
    meta = read_json(stage_dir / "meta.json")
    steps = step_table(stage_dir)
    steps.to_csv(label_dir / "breakpoint_steps.csv", index=False)
    cap = capacity(steps, _sut_cores(env))
    plot_steps(steps, label_dir / "breakpoint.png", "Tải theo bậc: throughput, độ trễ đuôi và CPU theo tiến trình")

    planned_max = meta["shape"][-1][1]
    if not steps.empty and int(steps["users"].iloc[-1]) < planned_max:
        deviations.append(
            f"Bài breakpoint dừng ở bậc {int(steps['users'].iloc[-1])} người dùng ảo thay vì {planned_max} "
            f"như kế hoạch, do vượt ngưỡng dừng (p95 > {meta.get('stop_p95_ms')} ms hoặc lỗi > "
            f"{meta.get('stop_error_pct')}%). Đây là hành vi dự kiến."
        )

    max_rps, at_users = cap.get("max_rps_within_slo"), cap.get("users_at_max_rps_within_slo")
    verdict = (
        "breakpoint",
        "Xác định được năng lực tối đa trong SLO",
        f"{max_rps} req/s tại {at_users} người dùng ảo",
        max_rps is not None,
    )
    if cap.get("sut_cpu_saturated"):
        bottleneck = (
            f"CPU cấp cho ứng dụng và MySQL đã dùng hết ở bậc cuối; phân chia: {cap.get('cpu_split_at_last_step')}."
        )
    else:
        bottleneck = (
            "CPU cấp cho ứng dụng và MySQL chưa dùng hết ở bậc cuối; điểm nghẽn nằm ngoài CPU "
            "(ví dụ chờ khóa, connection pool hoặc I/O)."
        )
    step_cols = _present(
        (
            "users",
            "requests",
            "rps",
            "p50_ms",
            "p95_ms",
            "p99_ms",
            "error_pct",
            "app_cpu_cores_mean",
            "mysql_cpu_cores_mean",
            "locust_cpu_cores_p95",
            "mysql_avg_row_lock_wait_ms",
            "slo_pass",
        ),
        steps,
    )
    capacity_table = _kv_table(
        [
            ("Năng lực tối đa trong SLO", f"{max_rps} req/s"),
            ("Số người dùng ảo tại năng lực tối đa", at_users),
            ("Bậc đầu tiên vi phạm SLO", cap.get("first_slo_violation_users")),
            ("Bậc throughput ngừng tăng tương xứng", cap.get("throughput_knee_users")),
            ("Số core cấp cho ứng dụng và MySQL", cap.get("sut_cpu_cores_allotted")),
            ("Tỉ lệ dùng số core đó ở bậc cuối", cap.get("sut_cpu_utilization_at_last_step")),
            ("Phân chia CPU ở bậc cuối", cap.get("cpu_split_at_last_step")),
        ],
        "Chỉ số",
        "Giá trị",
    )
    text = (
        "### 3.{n}. Breakpoint\n\n"
        f"Năng lực tối đa trong SLO là {max_rps} req/s tại {at_users} người dùng ảo. {bottleneck}\n\n"
        + md_table(steps, step_cols)
        + "\n\n"
        + capacity_table
        + "\n\n![Throughput, p95, p99 và CPU theo số người dùng ảo](breakpoint.png)\n"
    )
    return {"steps": steps.to_dict("records"), "capacity": cap}, verdict, text


def _section_spike(label_dir):
    stage_dir = label_dir / "spike"
    meta, raw, _, _ = load_stage(stage_dir)
    tl = timeline(stage_dir)
    baseline_end, spike_end = meta["shape"][0][0], meta["shape"][1][0]
    rec = spike_recovery(tl, raw, baseline_end, spike_end)
    plot_timeline(tl, label_dir / "spike.png", "Bài spike theo thời gian (khung 5 s)", marks=(baseline_end, spike_end))

    passed = (
        rec["spike_error_pct"] is not None
        and rec["spike_error_pct"] <= SLO["error_pct"]
        and rec["recovery_s"] is not None
    )
    verdict = (
        "spike",
        "Không lỗi trong lúc sốc tải và hồi phục được",
        f"lỗi {rec['spike_error_pct']}%, hồi phục sau {rec['recovery_s']} s",
        passed,
    )
    table = md_table(
        pd.DataFrame(
            [
                {
                    "p95 nền (ms)": rec["baseline_p95_ms"],
                    "p95 khi sốc tải (ms)": rec["spike_p95_ms"],
                    "p99 khi sốc tải (ms)": rec["spike_p99_ms"],
                    "Lỗi khi sốc tải (%)": rec["spike_error_pct"],
                    "Thời gian hồi phục (s)": rec["recovery_s"],
                }
            ]
        )
    )
    text = (
        "### 3.{n}. Spike\n\n"
        f"Khi tải tăng đột ngột, p95 tăng từ {rec['baseline_p95_ms']} ms lên {rec['spike_p95_ms']} ms, "
        f"tỉ lệ lỗi {rec['spike_error_pct']}%. Sau khi tải giảm, p95 về lại mức nền sau {rec['recovery_s']} s.\n\n"
        + table
        + "\n\n![Throughput và độ trễ theo thời gian trong bài spike](spike.png)\n"
    )
    return rec, verdict, text


def _section_flashsale(label_dir):
    meta, raw, res, lg = load_stage(label_dir / "flashsale")
    inventory = meta["inventory"]
    hot = inventory["hot_product"]
    hot_requests = raw["name"] == "POST /orders (hot)"
    created = int((hot_requests & (raw["status"] == 201)).sum())
    rejected = int((hot_requests & (raw["status"] == 400)).sum())
    fs = {
        "requests": len(raw),
        "orders_created_201": created,
        "rejected_out_of_stock_400": rejected,
        "stock_before": hot["stock_before"],
        "units_sold_in_db": hot["sold"],
        "stock_after": hot["stock_after"],
        "oversold_units": max(0, hot["sold"] - hot["stock_before"]),
        "client_vs_db_match": created == hot["sold"],
        **latency_stats(raw),
        **resource_stats(res, lg),
        "inventory_balanced": inventory["balanced"],
    }
    passed = fs["oversold_units"] == 0 and fs["client_vs_db_match"] and fs["inventory_balanced"]
    verdict = (
        "flashsale",
        "Bán vượt = 0, số đơn client nhận khớp CSDL",
        f"bán {fs['units_sold_in_db']}/{fs['stock_before']}, bán vượt {fs['oversold_units']}",
        passed,
    )
    table = md_table(
        pd.DataFrame(
            [
                {
                    "Tồn kho ban đầu": fs["stock_before"],
                    "Đơn tạo thành công (201)": created,
                    "Từ chối hết hàng (400)": rejected,
                    "Đã bán trong CSDL": fs["units_sold_in_db"],
                    "Tồn kho sau": fs["stock_after"],
                    "Bán vượt": fs["oversold_units"],
                    "Client khớp CSDL": fs["client_vs_db_match"],
                    "p95 (ms)": fs["p95_ms"],
                    "p99 (ms)": fs["p99_ms"],
                    "Số lần chờ khóa dòng": fs.get("mysql_innodb_row_lock_waits_delta"),
                    "Chờ khóa dòng TB (ms)": fs.get("mysql_avg_row_lock_wait_ms"),
                    "Deadlock": fs.get("mysql_innodb_deadlocks_delta"),
                }
            ]
        )
    )
    text = (
        "### 3.{n}. Flash sale\n\n"
        f"{created} đơn được tạo và {rejected} request bị từ chối do hết hàng. "
        f"Số bán vượt là {fs['oversold_units']}.\n\n" + table + "\n"
    )
    return fs, verdict, text


def _integrity_rows(label_dir, plan):
    order = {name: i for i, name in enumerate(plan)}
    stage_dirs = sorted(label_dir.iterdir(), key=lambda p: (order.get(p.name.split("_r")[0], 99), p.name))
    rows = []
    for stage_dir in stage_dirs:
        meta_path = stage_dir / "meta.json"
        if not meta_path.exists():
            continue
        inv = read_json(meta_path).get("inventory")
        if inv:
            rows.append(
                {
                    "stage": stage_dir.name,
                    "balanced": inv["balanced"],
                    "mismatched_products": inv["mismatch_count"],
                    "negative_stock": inv["negative_stock_products"],
                    "wrong_order_totals": inv["orders_with_wrong_total"],
                    "units_sold": inv["units_sold"],
                    "units_restocked": inv["units_restocked"],
                    "units_returned": inv["units_returned_from_seeded"],
                    "new_orders": json.dumps(inv["new_orders_by_status"]),
                }
            )
    return rows


def _validity_warnings(summary):
    warnings = [
        f"Bài {r['stage']}: p95 CPU của Locust vượt {LG_SATURATION_CORES} core, "
        "kết quả có thể bị giới hạn bởi bộ sinh tải."
        for r in summary.get("average_load", {}).get("runs", [])
        if r.get("load_generator_saturated")
    ]
    warnings += [
        f"Bậc {r['users']} người dùng ảo: p95 CPU của Locust vượt {LG_SATURATION_CORES} core."
        for r in summary.get("breakpoint", {}).get("steps", [])
        if r.get("load_generator_saturated")
    ]
    return warnings


def _missing_stages(label_dir, plan):
    missing = []
    for stage, cfg in plan.items():
        reps = cfg.get("repetitions", 1)
        names = [stage] if reps == 1 else [f"{stage}_r{i}" for i in range(1, reps + 1)]
        missing += [n for n in names if not (label_dir / n / "meta.json").exists()]
    return [f"Không có kết quả của: {', '.join(missing)}."] if missing else []


def _render(label, env, verdicts, sections, integrity, deviations, warnings):
    commit = env.get("refs", {}).get(label, "")
    md = [f"# Báo cáo hoàn thành kiểm thử tải: `{label}`\n"]
    md.append(
        _kv_table(
            [
                ("Loại tài liệu", "Báo cáo hoàn thành kiểm thử (test completion report)"),
                ("Kế hoạch kiểm thử", "`docs/load-testing.md`"),
                ("Phiên bản được kiểm thử", f"`{label}`, commit `{commit}`"),
                ("Thời điểm chạy", env.get("captured_at", "")),
            ],
            "Thông tin",
            "Giá trị",
        )
        + "\n"
    )

    verdict_rows = [{"Bài": s, "Tiêu chí": c, "Kết quả": r, "Đánh giá": _verdict(p)} for s, c, r, p in verdicts]
    if integrity:
        balanced = sum(r["balanced"] for r in integrity)
        verdict_rows.append(
            {
                "Bài": "Mọi bài",
                "Tiêu chí": "Tồn kho cân bằng, không âm kho, tổng tiền đơn đúng",
                "Kết quả": f"{balanced}/{len(integrity)} bài cân bằng",
                "Đánh giá": _verdict(balanced == len(integrity)),
            }
        )
    md.append("## 1. Tóm tắt\n")
    md.append(md_table(pd.DataFrame(verdict_rows)) + "\n")

    if env:
        pinning = env.get("cpu_pinning", {})
        md.append("## 2. Môi trường kiểm thử\n")
        md.append(
            _kv_table(
                [
                    ("CPU", f"{env.get('cpu_model')}, {env.get('logical_cpus')} core logic"),
                    ("RAM", f"{env.get('ram_gb')} GB"),
                    (
                        "Phân bổ CPU",
                        f"ứng dụng và MySQL: core {pinning.get('app+mysql')}; Locust: core {pinning.get('locust')}",
                    ),
                    ("Java", f"{env.get('java')}; `{' '.join(env.get('jvm_opts', []))}`"),
                    ("MySQL", env.get("mysql")),
                    ("Locust", env.get("locust")),
                    ("Python", env.get("python")),
                ],
                "Thành phần",
                "Cấu hình",
            )
            + "\n"
        )

    md.append("## 3. Kết quả theo bài kiểm thử\n")
    md.append(
        f"Tiêu chí hiệu năng: p95 ≤ {SLO['p95_ms']} ms, p99 ≤ {SLO['p99_ms']} ms, tỉ lệ lỗi ≤ {SLO['error_pct']}%.\n"
    )
    md.extend(text.replace("3.{n}.", f"3.{i}.", 1) for i, text in enumerate(sections, 1))

    md.append("## 4. Đúng đắn dữ liệu\n")
    if integrity:
        md.append("Bảng sau là kết quả đối soát tồn kho sau từng bài kiểm thử.\n")
        md.append(
            md_table(
                pd.DataFrame(
                    [
                        {
                            "Bài": r["stage"],
                            "Cân bằng": r["balanced"],
                            "Sản phẩm lệch": r["mismatched_products"],
                            "Sản phẩm âm kho": r["negative_stock"],
                            "Đơn sai tổng tiền": r["wrong_order_totals"],
                            "Đã bán": r["units_sold"],
                            "Nhập thêm": r["units_restocked"],
                            "Hoàn kho": r["units_returned"],
                            "Đơn mới theo trạng thái": r["new_orders"],
                        }
                        for r in integrity
                    ]
                )
            )
            + "\n"
        )
    md.append("## 5. Sai khác so với kế hoạch\n")
    md.append("\n".join(f"- {x}" for x in deviations) + "\n" if deviations else "Không có.\n")
    md.append("## 6. Cảnh báo về tính hợp lệ của phép đo\n")
    md.append(
        "\n".join(f"- {w}" for w in warnings) + "\n"
        if warnings
        else "Không có. Bộ sinh tải không bị nghẽn CPU ở bài nào.\n"
    )
    return "\n".join(md)


def build_report(label_dir):
    """Write summary.json and REPORT.md, structured as an ISO/IEC/IEEE 29119-3 test completion report."""
    d = Path(label_dir)
    env = read_json(d / "environment.json") if (d / "environment.json").exists() else {}
    plan = read_json(d.parent / "plan.json") if (d.parent / "plan.json").exists() else {}
    summary = {"label": d.name, "slo": SLO, "environment": env}
    verdicts, sections, deviations = [], [], []

    def add(key, result):
        entry, verdict, text = result
        summary[key] = entry
        verdicts.append(verdict)
        sections.append(text)

    if (d / "smoke").exists():
        add("smoke", _section_smoke(d / "smoke"))
    load_runs = sorted(p for p in d.glob("load_r*") if (p / "meta.json").exists())
    if load_runs:
        add("average_load", _section_load(d, load_runs))
    if (d / "breakpoint").exists():
        add("breakpoint", _section_breakpoint(d, env, deviations))
    if (d / "spike").exists():
        add("spike", _section_spike(d))
    if (d / "flashsale").exists():
        add("flashsale", _section_flashsale(d))

    summary["integrity"] = _integrity_rows(d, plan)
    summary["warnings"] = _validity_warnings(summary)
    deviations += _missing_stages(d, plan)

    write_json(d / "summary.json", summary)
    report = _render(d.name, env, verdicts, sections, summary["integrity"], deviations, summary["warnings"])
    (d / "REPORT.md").write_text(report, encoding="utf-8")
    return summary


KEY_METRICS = [
    ("average_load", "rps", "Throughput ở tải thường (req/s)", +1),
    ("average_load", "p95_ms", "p95 ở tải thường (ms)", -1),
    ("average_load", "p99_ms", "p99 ở tải thường (ms)", -1),
    ("average_load", "error_pct", "Tỉ lệ lỗi ở tải thường (%)", -1),
    ("breakpoint", "max_rps_within_slo", "Năng lực tối đa trong SLO (req/s)", +1),
    ("breakpoint", "users_at_max_rps_within_slo", "Người dùng ảo tại năng lực tối đa", +1),
    ("spike", "spike_p95_ms", "p95 khi sốc tải (ms)", -1),
    ("spike", "recovery_s", "Thời gian hồi phục sau sốc tải (s)", -1),
    ("flashsale", "p95_ms", "p95 trong flash sale (ms)", -1),
    ("flashsale", "oversold_units", "Số đơn vị bán vượt (phải bằng 0)", -1),
]


def _metric(summary, section, key):
    s = summary.get(section, {})
    if section == "average_load":
        return s.get("aggregate", {}).get(key, {}).get("mean")
    if section == "breakpoint":
        return s.get("capacity", {}).get(key)
    return s.get(key)


def _noise_pct(a, b, section, key):
    """Changes smaller than this are treated as measurement noise.

    For average-load metrics the threshold is twice the larger coefficient of
    variation observed across the repeated runs of either version (min 5%).
    Single-run metrics fall back to a fixed threshold."""
    if section == "average_load":
        cvs = [s.get("average_load", {}).get("aggregate", {}).get(key, {}).get("cv_%") for s in (a, b)]
        cvs = [c for c in cvs if isinstance(c, (int, float))]
        return max(5.0, 2 * max(cvs)) if cvs else SINGLE_RUN_NOISE_PCT
    return SINGLE_RUN_NOISE_PCT


def _compare_verdict(before, after, change, noise, better):
    if change is None:
        return "Không đổi" if before == after else "-"
    if abs(change) < noise:
        return "Trong ngưỡng nhiễu"
    return "Tốt hơn" if change * better > 0 else "Kém hơn"


def _plot_comparison(before_steps, after_steps, labels, path):
    fig, (left, right) = plt.subplots(1, 2, figsize=(12, 4.5))
    for steps, label in zip((before_steps, after_steps), labels, strict=True):
        left.plot(steps["users"], steps["rps"], "o-", label=label)
        right.plot(steps["users"], steps["p95_ms"], "o-", label=label)
    right.axhline(SLO["p95_ms"], color="r", alpha=0.3)
    left.set(title="Throughput theo số người dùng ảo", xlabel="Người dùng ảo", ylabel="req/s")
    right.set(title="p95 theo số người dùng ảo", xlabel="Người dùng ảo", ylabel="ms")
    for ax in (left, right):
        ax.grid(alpha=0.3)
        ax.legend()
    _save(fig, path)


def compare(before_dir, after_dir, out_dir):
    a = read_json(Path(before_dir) / "summary.json")
    b = read_json(Path(after_dir) / "summary.json")
    rows = []
    for section, key, label, better in KEY_METRICS:
        va, vb = _metric(a, section, key), _metric(b, section, key)
        noise = _noise_pct(a, b, section, key)
        change = None
        if isinstance(va, (int, float)) and isinstance(vb, (int, float)) and va:
            change = round(100 * (vb - va) / abs(va), 1)
        rows.append(
            {
                "Chỉ số": label,
                a["label"]: va,
                b["label"]: vb,
                "Thay đổi (%)": "-" if change is None else change,
                "Ngưỡng nhiễu (%)": round(noise, 1),
                "Kết luận": _compare_verdict(va, vb, change, noise, better),
            }
        )
    df = pd.DataFrame(rows)
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    df.to_csv(out / "comparison.csv", index=False)
    md = [
        f"# So sánh `{a['label']}` và `{b['label']}`\n",
        "Hai phiên bản được đo trong cùng một phiên Kaggle, với cùng dữ liệu, cùng kịch bản tải và cùng tiêu chí. "
        "Một thay đổi chỉ được kết luận là tốt hơn hoặc kém hơn khi vượt ngưỡng nhiễu: 2 lần hệ số biến thiên lớn "
        "nhất của các lần chạy tải thường (tối thiểu 5%), hoặc 20% với chỉ số chỉ đo một lần.\n",
        md_table(df) + "\n",
    ]
    steps_a, steps_b = Path(before_dir) / "breakpoint_steps.csv", Path(after_dir) / "breakpoint_steps.csv"
    if steps_a.exists() and steps_b.exists():
        _plot_comparison(
            pd.read_csv(steps_a),
            pd.read_csv(steps_b),
            (a["label"], b["label"]),
            out / "comparison_breakpoint.png",
        )
        md.append("![Throughput và p95 của hai phiên bản theo số người dùng ảo](comparison_breakpoint.png)\n")
    (out / "COMPARISON.md").write_text("\n".join(md), encoding="utf-8")
    return df
