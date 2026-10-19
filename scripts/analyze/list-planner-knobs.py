#!/usr/bin/env python3
"""Scan src/main/java for every read of a `planner.*` system property / config property.

Detects, per read site:
  - System.getProperty / getLong / getInteger / getBoolean("planner....", default?)
  - Boolean.getBoolean("planner....")
  - @ConfigProperty(name = "planner....", defaultValue = "....?")
Constant-referenced names (e.g. `getProperty(FLAG, "false")` where `FLAG = "planner...."`)
are resolved against string constants declared in the same file.

LIVE vs STARTUP: a read inside a method body counts as LIVE (re-read at each call, i.e.
per-build/per-solve); a read in a static field initializer or static block counts as
STARTUP (read once when the class loads, changing it later has no effect). The
classification uses the enclosing declaration of the read site (nearest preceding
`static final ... =` / `static {`), documented here because it is a heuristic on the
Java text, not a full parse.

Output: an aligned table on stdout plus a final count. Exit 0 when at least one knob
is found, 1 otherwise (so CI can catch a scan regression).
"""
import bisect
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "src" / "main" / "java"

# Args may span lines ([^)]* crosses newlines), so these run on whole-file text.
READ_RE = re.compile(
    r'(?:System\.)?(?:getProperty|getLong|getInteger|getBoolean)\s*\(\s*'
    r'(?:"(planner\.[a-zA-Z0-9._-]+)"|([A-Z][A-Z0-9_]*))'
    r'(?:\s*,\s*([^)]*))?\)'
)
# Helper readers: readCadenceKnobProperty / readM32KnobProperty / similar.
HELPER_RE = re.compile(
    r'\b(?:read\w*Property|\w*[Kk]nob\w*)\s*\(\s*"(planner\.[a-zA-Z0-9._-]+)"\s*,\s*([^,()]+)'
)
CONFIG_RE = re.compile(
    r'@ConfigProperty\s*\(\s*name\s*=\s*"(planner\.[a-zA-Z0-9._-]+)"'
    r'(?:\s*,\s*defaultValue\s*=\s*"([^"]*)")?'
)
CONST_RE = re.compile(
    r'(?:private|public|protected)?\s*(?:static\s+)?(?:final\s+)?String\s+'
    r'([A-Z][A-Z0-9_]*)\s*=\s*"(planner\.[a-zA-Z0-9._-]+)"'
)
IDENT_RE = re.compile(r'\b([a-z][a-zA-Z0-9_]*)\s*\(')


def enclosing_decl(lines, idx):
    """STARTUP if the read sits in a static field initializer or static block."""
    depth = 0
    for i in range(idx, -1, -1):
        line = lines[i]
        depth += line.count("}") - line.count("{")
        if re.match(r"\s*(private|public|protected|)\s*static\s+(final\s+)?.*=", line) and depth == 0:
            return "STARTUP"
        if re.match(r"\s*static\s*\{", line):
            return "STARTUP"
    return "LIVE"


def method_name(lines, idx):
    for i in range(idx, max(-1, idx - 200), -1):
        m = IDENT_RE.search(lines[i])
        if m and not lines[i].lstrip().startswith(("//", "*", "/*")):
            return m.group(1)
    return "?"


def default_str(raw):
    if raw is None:
        return "(none)"
    return " ".join(raw.split())[:40]


def main():
    rows = {}  # name -> dict(default, sites)
    for path in sorted(SRC.rglob("*.java")):
        text = path.read_text(encoding="utf-8", errors="replace")
        lines = text.splitlines()
        consts = {m.group(1): m.group(2) for m in CONST_RE.finditer(text)}
        rel = str(path.relative_to(ROOT))
        line_starts = [0]
        for line in lines:
            line_starts.append(line_starts[-1] + len(line) + 1)

        def lineno_of(offset):
            return bisect.bisect_right(line_starts, offset)

        hits = []  # (offset, name, default)
        for m in READ_RE.finditer(text):
            name = m.group(1) or consts.get(m.group(2))
            if name and name.startswith("planner."):
                hits.append((m.start(), name, default_str(m.group(3))))
        for m in HELPER_RE.finditer(text):
            hits.append((m.start(), m.group(1), default_str(m.group(2))))
        for offset, name, dflt in hits:
            lineno = lineno_of(offset)
            line = lines[lineno - 1]
            if line.strip().startswith(("*", "//", "/*")):
                continue  # javadoc/comment mention, not a read
            rows.setdefault(name, {"default": dflt, "sites": []})
            rows[name]["sites"].append(
                (rel, lineno, enclosing_decl(lines, lineno - 1), method_name(lines, lineno - 1))
            )
        for m in CONFIG_RE.finditer(text):
            rows.setdefault(m.group(1), {"default": m.group(2) or "(none)", "sites": []})
            rows[m.group(1)]["sites"].append((rel, lineno_of(m.start()), "STARTUP", "@ConfigProperty"))

    print(f"{'KNOB':58} {'DEFAULT':24} LIVE/STARTUP  READ-AT")
    print("-" * 140)
    for name in sorted(rows):
        info = rows[name]
        site = info["sites"][0]
        where = f"{site[0]}:{site[1]} ({site[2]}, {site[3]})"
        if len(info["sites"]) > 1:
            where += f" +{len(info['sites']) - 1} more"
        print(f"{name:58} {info['default']:24} {site[2]:12}  {where}")
    print("-" * 140)
    print(f"Total planner.* knobs: {len(rows)}")
    sys.exit(0 if rows else 1)


if __name__ == "__main__":
    main()
