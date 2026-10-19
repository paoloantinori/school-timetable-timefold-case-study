#!/usr/bin/env python3
"""TASK-634: the post-LS solution catalog.

Direttiva maintainer 20/09: "non possiamo iniziare a tenerci un catalogo di soluzioni
post-LS su cui iterare?" - extension of ITERAZIONE SU SNAPSHOT: measure on kept
boundary states instead of rebuilding them every time.

The catalog INDEX lives in the repo; the PAYLOADS stay where they are (107MB
exports cannot be committed) and are referenced by path + sha256, so a pointer
is verifiable. Every entry carries the SEED HASH (sha256 over every CSV in
src/main/resources/seed/, sorted by name): replaying an entry whose seed hash
diverges from the current seed manufactures phantom families (the H25 lesson,
20/09: pre-cure exports against post-cure quotas).

Usage:
  post_ls_catalog.py add --label m39-off --run-dir <dir> [--commit <sha>]
                         [--attempt N] [--note "..."]
  post_ls_catalog.py status [--entry LABEL]     # freshness + payload check
  post_ls_catalog.py list

Rules (see the catalog README):
  - room-faithful entries (full export.json.gz) are the replay grade; a
    lessons-only entry is for puzzles and KPI, NOT for delivery-stage replay
    (the 632b lesson: a room-less pipe kills every room gate).
  - keeping entries per residual-family BASIN, refreshed by new cycles, is the
    anti-overfit discipline (the 628 lesson: basins differ).
"""
import argparse
import hashlib
import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
SEED_DIR = REPO / "src/main/resources/seed"
CATALOG_DIR = REPO / "claudedocs/spalmone-2026-27/post-ls-catalog"
INDEX = CATALOG_DIR / "index.json"


def seed_hash(seed_commit: "str | None" = None) -> str:
    """sha256 over every seed CSV, sorted by name: the freshness coordinate.

    Default: the WORKING TREE seed (correct only for runs made on the current
    seed). For historical runs pass --seed-commit: the files are read from that
    commit, because the hash that matters is the seed THE RUN USED (an entry
    hashed against today's seed but born pre-cure would read VALID and lie -
    the exact H25 trap this guard exists for)."""
    import subprocess
    h = hashlib.sha256()
    if seed_commit:
        out = subprocess.run(
            ["git", "-C", str(REPO), "ls-tree", "-r", "--name-only", seed_commit,
             "src/main/resources/seed/"],
            capture_output=True, text=True, check=True).stdout
        names = sorted(n for n in out.splitlines() if n.endswith(".csv"))
        for name in names:
            blob = subprocess.run(
                ["git", "-C", str(REPO), "show", f"{seed_commit}:{name}"],
                capture_output=True, check=True).stdout
            h.update(name.encode())
            h.update(blob)
        return h.hexdigest()
    for csv in sorted(SEED_DIR.rglob("*.csv")):
        # review F8: recursive + repo-relative path, same domain as the
        # ls-tree branch below (a subdirectory CSV must not fork the hashes)
        h.update(csv.resolve().relative_to(REPO).as_posix().encode())
        h.update(csv.read_bytes())
    return h.hexdigest()


def file_sha256(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def load_index() -> dict:
    if INDEX.exists():
        return json.loads(INDEX.read_text())
    return {"entries": []}


def save_index(idx: dict) -> None:
    CATALOG_DIR.mkdir(parents=True, exist_ok=True)
    INDEX.write_text(json.dumps(idx, indent=2, ensure_ascii=False) + "\n")


def parse_hard(score_file: Path):
    for line in score_file.read_text().splitlines():
        if line.startswith("score:"):
            # "score: [-11/0]hard/[-1188844/1420]soft" -> -11 (FIRST bracket)
            try:
                return int(line.split("[")[1].split("/")[0])
            except (IndexError, ValueError):
                return None
    return None


def cmd_add(args) -> int:
    run_dir = Path(args.run_dir).resolve()
    if not run_dir.is_dir():
        print(f"ERR run dir not found: {run_dir}")
        return 1
    export = run_dir / "export.json.gz"
    if args.export:
        export = Path(args.export).resolve()
        if not export.exists():
            # review F1: a silent roomFaithful=false on a typo'd path is the
            # 632b trap (room-less replay kills every room gate).
            print(f"ERR --export not found: {export}")
            return 1
    lessons = run_dir / "lessons.txt"
    score_f = run_dir / "score.txt"
    if not lessons.exists():
        print(f"ERR no lessons.txt in {run_dir} (not a run capture?)")
        return 1
    entry = {
        "label": args.label,
        "seedHash": seed_hash(args.seed_commit),
        "seedCommit": args.seed_commit or "working-tree",
        "addedAt": args.date or __import__("datetime").date.today().isoformat(),
        "codeCommit": args.commit or "",
        "attempt": args.attempt,
        "roomFaithful": export.exists(),
        "payloads": {"lessonsTxt": rel(lessons)},
        "payloadSha256": {},
        "hardScore": parse_hard(score_f) if score_f.exists() else None,
        "note": args.note or "",
    }
    if export.exists():
        entry["payloads"]["exportJsonGz"] = rel(export)
    for name, relpath in list(entry["payloads"].items()):
        p = REPO / relpath
        if not p.exists():
            print(f"ERR payload missing: {relpath}")
            return 1
        entry["payloadSha256"][name] = file_sha256(p)  # type: ignore[attr-defined]
    idx = load_index()
    idx["entries"] = [e for e in idx["entries"] if e["label"] != args.label]
    idx["entries"].append(entry)
    idx["entries"].sort(key=lambda e: e["label"])
    save_index(idx)
    print(f"OK added {args.label}: roomFaithful={entry['roomFaithful']} "
          f"hard={entry['hardScore']} payloads={len(entry['payloads'])}")
    return 0


def rel(p: Path) -> str:
    """Repo-relative posix path; a payload outside the repo cannot be indexed
    (review F6: die with a clean message, not a pathlib traceback)."""
    try:
        return p.resolve().relative_to(REPO).as_posix()
    except ValueError:
        raise SystemExit(f"ERR payload outside the repo (the index is repo-relative): {p}")


def cmd_status(args) -> int:
    current = seed_hash()
    idx = load_index()
    print(f"current seedHash: {current[:16]}...")
    bad = 0
    checked = 0
    for e in idx["entries"]:
        if args.entry and e["label"] != args.entry:
            continue
        checked += 1
        seed_ok = e["seedHash"] == current
        payloads_ok = True
        for name, relpath in e.get("payloads", {}).items():
            p = REPO / relpath
            if not p.exists():
                payloads_ok = False
                print(f"  {e['label']}: MISSING {name} {relpath}")
            elif file_sha256(p) != e["payloadSha256"][name]:
                payloads_ok = False
                print(f"  {e['label']}: HASH-DIVERGED {name}")
        state = ("VALID" if seed_ok and payloads_ok
                 else "STALE-SEED" if not seed_ok
                 else "PAYLOAD-PROBLEM")
        if state != "VALID":
            bad += 1
        print(f"{state:16} {e['label']:14} hard={e.get('hardScore')} "
              f"roomFaithful={e.get('roomFaithful')} "
              f"commit={e.get('codeCommit') or '?'}")
    if args.entry and checked == 0:
        print(f"ERR no entry labeled {args.entry!r} in the catalog")
        return 1
    print(f"{checked} checked, {bad} not valid")
    print("STALE-SEED = replay against the current seed manufactures phantom"
          " families (H25 lesson): use only for what explicitly declares it.")
    # review F4: the exit code is the gate (status && replay must fail closed)
    return 1 if bad else 0


def cmd_list(_args) -> int:
    del _args
    idx = load_index()
    for e in idx["entries"]:
        print(f"{e['label']:14} hard={e.get('hardScore')} "
              f"roomFaithful={e.get('roomFaithful')} "
              f"seed={e['seedHash'][:12]} commit={e.get('codeCommit') or '?'} "
              f"attempt={e.get('attempt')} {e.get('note', '')}")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    sub = ap.add_subparsers(dest="cmd", required=True)
    a = sub.add_parser("add")
    a.add_argument("--label", required=True)
    a.add_argument("--run-dir", required=True)
    a.add_argument("--export", help="export.json.gz path if not run-dir/export.json.gz")
    a.add_argument("--commit", default="", help="code commit the run used")
    a.add_argument("--seed-commit", default=None,
                   help="commit whose SEED the run used (historical runs: default"
                        " working-tree would lie; e.g. 4f9dbc07^ for pre-18/09 runs)")
    a.add_argument("--attempt", type=int)
    a.add_argument("--date", default=None)
    a.add_argument("--note", default="")
    a.set_defaults(fn=cmd_add)
    s = sub.add_parser("status")
    s.add_argument("--entry")
    s.set_defaults(fn=cmd_status)
    l = sub.add_parser("list")
    l.set_defaults(fn=cmd_list)
    args = ap.parse_args()
    return args.fn(args)


if __name__ == "__main__":
    sys.exit(main())
