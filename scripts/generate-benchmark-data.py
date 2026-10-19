#!/usr/bin/env python3
"""
Generate static benchmark data TypeScript file from XML reports.
Run: python3 scripts/generate-benchmark-data.py
Output: src/main/webui/src/data/benchmarkData.ts
"""

import argparse
import os
import re
import sys
import xml.etree.ElementTree as ET

BENCHMARK_DIR = "local/benchmarkReport"
OUTPUT_FILE = "src/main/webui/src/data/benchmarkData.ts"


_SCRIPTS_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(_SCRIPTS_DIR, "analyze"))
from shared import parse_score_string  # noqa: E402 (dual-format: legacy + BendableScore bracket)


def parse_score(score_str):
    """Parse Timefold score string: legacy '-10hard/-38919medium/288278soft' or
    BendableScore bracket '[-10/0]hard/[-38919/288278]soft' (TASK-538 B; the
    suffix-regex fallback below returns 0/0/0 on bracket, so the shared parser
    runs first)."""
    parsed = parse_score_string(score_str)
    if parsed:
        return parsed
    hard = 0
    medium = 0
    soft = 0
    m = re.search(r'(-?\d+)hard', score_str)
    if m:
        hard = int(m.group(1))
    m = re.search(r'(-?\d+)medium', score_str)
    if m:
        medium = int(m.group(1))
    m = re.search(r'(-?\d+)soft', score_str)
    if m:
        soft = int(m.group(1))
    return hard, medium, soft


def format_time(millis):
    """Format milliseconds as human-readable time"""
    seconds = millis // 1000
    minutes = seconds // 60
    hours = minutes // 60
    if hours > 0:
        return f"{hours}h {minutes % 60}m"
    elif minutes > 0:
        return f"{minutes}m {seconds % 60}s"
    else:
        return f"{seconds}s"


def parse_directory_name(dir_name):
    """Parse '2026-03-18_103426' into '2026-03-18 10:34'"""
    m = re.match(r'^(\d{4}-\d{2}-\d{2})_(\d{6})$', dir_name)
    if m:
        date, time = m.groups()
        return f"{date} {time[:2]}:{time[2:4]}"
    return dir_name


def parse_benchmark_xml(filepath, dir_name):
    """Parse a single benchmark XML file and return results"""
    results = []
    try:
        tree = ET.parse(filepath)
        root = tree.getroot()

        for solver in root.findall('.//solverBenchmarkResult'):
            name = solver.findtext('name', '')
            sub_single = solver.find('.//subSingleBenchmarkResult')
            if sub_single is None:
                continue

            succeeded = sub_single.findtext('succeeded', 'false') == 'true'
            if not succeeded:
                continue

            score_str = sub_single.findtext('score', '')
            if not score_str:
                continue

            time_ms = int(sub_single.findtext('timeMillisSpent', '0'))
            hard, medium, soft = parse_score(score_str)
            date_run = parse_directory_name(dir_name)

            results.append({
                'configName': name,
                'score': {'hard': hard, 'medium': medium, 'soft': soft, 'formatted': score_str},
                'timeMillisSpent': time_ms,
                'timeFormatted': format_time(time_ms),
                'dateRun': date_run,
                'directoryName': dir_name,
            })
    except ET.ParseError as e:
        print(f"  WARNING: Failed to parse {filepath}: {e}")

    return results


def main(min_configs=0, latest=False, benchmark_dir=None):
    benchmark_dir = benchmark_dir or BENCHMARK_DIR
    if not os.path.isdir(benchmark_dir):
        print(f"ERROR: {benchmark_dir} not found")
        return

    # Find all directories with XML files
    dirs = sorted([
        d for d in os.listdir(benchmark_dir)
        if os.path.isdir(os.path.join(benchmark_dir, d))
        and os.path.isfile(os.path.join(benchmark_dir, d, 'plannerBenchmarkResult.xml'))
    ])

    if not dirs:
        print("No benchmark directories with XML files found.")
        return

    print(f"Found {len(dirs)} benchmark directories")

    # Parse all directories first
    dir_results = []
    for dir_name in dirs:
        xml_path = os.path.join(benchmark_dir, dir_name, 'plannerBenchmarkResult.xml')
        results = parse_benchmark_xml(xml_path, dir_name)
        dir_results.append((dir_name, results))
        print(f"  {dir_name}: {len(results)} configs")

    # Filter directories below minimum config count
    if min_configs > 0:
        before = len(dir_results)
        dir_results = [(d, r) for d, r in dir_results if len(r) >= min_configs]
        if before != len(dir_results):
            print(f"Filtered {before - len(dir_results)} directories with fewer than {min_configs} configs")

    # Keep only the latest qualifying directory
    if latest and dir_results:
        dir_results = [dir_results[-1]]
        print(f"Using latest directory only: {dir_results[0][0]}")

    all_results = []
    config_counter = 0

    for dir_name, results in dir_results:
        for r in results:
            config_counter += 1
            r['id'] = f"{dir_name}_{config_counter}"
            r['configNumber'] = config_counter
            all_results.append(r)

    print(f"Total: {config_counter} configurations")

    # Generate TypeScript
    ts_content = f"""// Auto-generated by scripts/generate-benchmark-data.py
// Run: python3 scripts/generate-benchmark-data.py
// DO NOT EDIT MANUALLY

import type {{ BenchmarkResult }} from '../types/benchmark';

export const benchmarkData: BenchmarkResult[] = {all_results};
"""

    os.makedirs(os.path.dirname(OUTPUT_FILE), exist_ok=True)
    with open(OUTPUT_FILE, 'w') as f:
        f.write(ts_content)

    print(f"Written {OUTPUT_FILE} ({len(all_results)} results)")


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Generate benchmark data TypeScript file from XML reports')
    parser.add_argument('--min-configs', type=int, default=0,
                        help='Minimum number of successful configs a directory must have to be included (default: 0)')
    parser.add_argument('--latest', action='store_true',
                        help='Only include the most recent qualifying directory')
    parser.add_argument('--dir', type=str, default=None,
                        help='Override benchmark report directory (default: local/benchmarkReport)')
    args = parser.parse_args()
    main(min_configs=args.min_configs, latest=args.latest, benchmark_dir=args.dir)
