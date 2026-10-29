"""Test plan and runner. Used by the Kaggle notebook; also runnable from a shell:

    python loadtest/run_plan.py --refs phase1=HEAD --out results
    python loadtest/run_plan.py --refs phase1=<phase 1 commit> phase2=main --out results
    python loadtest/run_plan.py --refs phase1=HEAD --quick        # short dry run, about 6 minutes

Every label gets a fresh database, the same deterministic dataset, a freshly started
JVM and exactly the same sequence of stages. The database is restored to the seeded
snapshot before every stage, so stages do not influence each other.
"""

import argparse
import time
from pathlib import Path

import analyze
import bench

MIXED = ["ShopperUser", "AdminUser"]


def step_shape(levels, step_s, spawn_rate):
    return [[step_s * (i + 1), users, spawn_rate] for i, users in enumerate(levels)]


PLAN = {
    "smoke": {
        "classes": MIXED,
        "users": 5,
        "spawn_rate": 5,
        "duration": 30,
        "wait": (0.5, 1.0),
        "description": "Script and API sanity check with very few users",
    },
    "warmup": {
        "classes": MIXED,
        "users": 50,
        "spawn_rate": 10,
        "duration": 60,
        "wait": (0.5, 1.5),
        "description": "JIT, connection pool and buffer pool warm-up; not reported",
    },
    "load": {
        "classes": MIXED,
        "users": 100,
        "spawn_rate": 10,
        "duration": 180,
        "wait": (1.0, 3.0),
        "measure_from_s": 30,
        "repetitions": 3,
        "description": "Expected normal traffic, repeated to measure run-to-run variation",
    },
    "breakpoint": {
        "classes": MIXED,
        "wait": (0.5, 1.5),
        "shape": step_shape([25, 50, 100, 150, 200, 300, 400, 500, 650, 800], 60, 25),
        "stop_p95_ms": 2000,
        "stop_error_pct": 5.0,
        "description": "Stepped load until the SLO breaks, to find capacity and the bottleneck",
    },
    "spike": {
        "classes": MIXED,
        "wait": (1.0, 3.0),
        "shape": [[60, 50, 50], [120, 500, 250], [240, 50, 50]],
        "description": "Sudden tenfold surge for 60 s, then measure recovery",
    },
    "flashsale": {
        "classes": ["FlashSaleUser"],
        "users": 200,
        "spawn_rate": 100,
        "duration": 45,
        "wait": (0.0, 0.05),
        "description": "200 users buy the same product (stock 500) to check row locking",
    },
}

QUICK_OVERRIDES = {
    "smoke": {"duration": 15},
    "warmup": {"duration": 20},
    "load": {"duration": 40, "measure_from_s": 10, "repetitions": 2},
    "breakpoint": {"shape": step_shape([10, 40, 80, 160], 25, 40)},
    "spike": {"shape": [[20, 20, 20], [45, 150, 150], [80, 20, 20]]},
    "flashsale": {"duration": 20, "users": 100},
}


def build_plan(quick=False, only=None):
    plan = {name: dict(cfg) for name, cfg in PLAN.items()}
    if quick:
        for name, overrides in QUICK_OVERRIDES.items():
            plan[name].update(overrides)
    if only:
        plan = {name: cfg for name, cfg in plan.items() if name in only}
    return plan


def _stage_names(name, cfg):
    reps = cfg.get("repetitions", 1)
    return [name] if reps == 1 else [f"{name}_r{i}" for i in range(1, reps + 1)]


def _smoke_passed(stage_dir, meta):
    summary, _ = analyze.summarize_constant(stage_dir)
    return meta.get("inventory", {}).get("balanced") and summary["error_pct"] == 0


def run_label(label, ref, repo_dir, out_root, plan, log=print):
    out = Path(out_root) / label
    out.mkdir(parents=True, exist_ok=True)
    sut_cpus, _ = bench.cpu_layout()
    log(f"[{label}] building {ref} ...")
    jar = bench.build(repo_dir, ref, Path(out_root) / f"_src_{label}")
    bench.reset_database()
    bench.pin(bench.mysqld_pid(), sut_cpus)
    app = bench.App(jar, str(out / "app.log"), sut_cpus)
    log(f"[{label}] app healthy after {app.start()} s")
    try:
        data_path, counts = bench.prepare_data(out)
        log(f"[{label}] seeded {counts}")
        for name, cfg in plan.items():
            for stage in _stage_names(name, cfg):
                started = time.time()
                meta = bench.run_stage(
                    out / stage,
                    data_path,
                    app,
                    cfg["classes"],
                    users=cfg.get("users"),
                    spawn_rate=cfg.get("spawn_rate"),
                    duration=cfg.get("duration"),
                    shape=cfg.get("shape"),
                    wait=cfg["wait"],
                    stop_p95_ms=cfg.get("stop_p95_ms"),
                    stop_error_pct=cfg.get("stop_error_pct"),
                    measure_from_s=cfg.get("measure_from_s", 0),
                    description=cfg.get("description", ""),
                )
                balanced = meta.get("inventory", {}).get("balanced")
                log(f"[{label}] {stage}: {time.time() - started:.0f}s, inventory balanced={balanced}")
                if name == "smoke" and not _smoke_passed(out / stage, meta):
                    raise RuntimeError(f"Smoke test failed for {label}; fix it before running heavier stages")
    finally:
        app.stop()
    return analyze.build_report(out)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--repo", default=str(Path(__file__).resolve().parents[1]))
    parser.add_argument("--refs", nargs="+", default=["phase1=HEAD"], help="label=gitref pairs, run in this order")
    parser.add_argument("--out", default="results")
    parser.add_argument("--quick", action="store_true", help="short dry run")
    parser.add_argument("--only", nargs="*", help="run only these stages")
    args = parser.parse_args()

    plan = build_plan(args.quick, args.only)
    refs = dict(pair.split("=", 1) for pair in args.refs)
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    bench.start_mysql()
    for label, ref in refs.items():
        bench.capture_env(out / label, args.repo, {label: ref})
    bench.write_json(out / "plan.json", plan)
    for label, ref in refs.items():
        run_label(label, ref, args.repo, out, plan)
    labels = list(refs)
    if len(labels) >= 2:
        analyze.compare(out / labels[0], out / labels[-1], out / "comparison")


if __name__ == "__main__":
    main()
