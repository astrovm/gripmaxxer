#!/usr/bin/env python3
"""Mutation testing on the app's Kotlin code: PIT changes a line a little, one change at a time, and
runs the tests that cover it. A change no test notices is a missing test.

  python3 scripts/mutants.py diff                  # the lines this branch changed from origin/main
  python3 scripts/mutants.py diff HEAD~1           # from somewhere else
  python3 scripts/mutants.py part 3 64             # the 4th of 64 equal parts of all the app's lines
  python3 scripts/mutants.py diff --out build/mutants --gradle "sh run.sh"

Uncommitted changes count in diff. Lists what lived by file and line; --out also writes it to
lived.txt and the tally ("3 of 10") to tally.txt there, for scripts/mutants-comment.sh. Exits 0
once it got to its tally, whatever lived: only a broken run fails. PIT's full report is in
app/build/reports/pitest/debug/.
"""
import argparse
import re
import shlex
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = "app/src/main/java/"
LINES = ROOT / "app/build/mutants/lines.txt"
REPORT = ROOT / "app/build/reports/pitest/debug/mutations.xml"
LIVED = ("SURVIVED", "NO_COVERAGE", "TIMED_OUT")


def changed_lines(base, root=ROOT):
    """Each changed Kotlin line, as "com/astrovm/gripmaxxer/File.kt:12", the way PIT names one."""
    since = subprocess.run(["git", "merge-base", base, "HEAD"], cwd=root, check=True, capture_output=True, text=True).stdout.strip()
    diff = subprocess.run(["git", "diff", "-U0", "--no-color", since, "--", SOURCES], cwd=root, check=True, capture_output=True, text=True).stdout
    return parse_diff(diff)


def parse_diff(diff):
    lines, file = [], None
    for line in diff.splitlines():
        if line.startswith("+++ "):
            path = line[4:].removeprefix("b/")
            file = path.removeprefix(SOURCES) if path.endswith(".kt") and path.startswith(SOURCES) else None
        elif line.startswith("@@") and file:
            start, _, count = re.match(r"@@ -\S+ \+(\d+)(,(\d+))? @@", line).groups()
            lines += [f"{file}:{n}" for n in range(int(start), int(start) + int(count or 1))]
    return lines


def all_lines(sources=ROOT / SOURCES):
    """Every line of every Kotlin file, in order, named the way PIT names one."""
    lines = []
    for file in sorted(sources.rglob("*.kt")):
        name = file.relative_to(sources).as_posix()
        count = len(file.read_text(encoding="utf-8").splitlines())
        lines += [f"{name}:{n}" for n in range(1, count + 1)]
    return lines


def part(lines, k, n):
    """The kth of n parts, 0-based, as equal as whole lines allow."""
    if not 0 <= k < n:
        raise ValueError(f"part {k} isn't one of 0 to {n - 1}")
    return lines[len(lines) * k // n : len(lines) * (k + 1) // n]


def lived(report):
    """What no test caught, "File.kt:12  survived: description", by file and line, and how many changes there were.
    No report at all means PIT found nothing to change, which is every change caught."""
    if not report.exists():
        return [], 0
    mutations = ET.parse(report).getroot()
    left = [m for m in mutations if m.get("status") in LIVED]
    left.sort(key=lambda m: (m.findtext("sourceFile"), int(m.findtext("lineNumber"))))
    rows = [
        f"{m.findtext('sourceFile')}:{m.findtext('lineNumber')}  {m.get('status').lower().replace('_', ' ')}: {m.findtext('description')}"
        for m in left
    ]
    return rows, len(mutations)


def mutate(lines, gradle):
    """Run PIT on these lines; what lived, and how many changes there were."""
    LINES.parent.mkdir(parents=True, exist_ok=True)
    LINES.write_text("\n".join(lines) + "\n")
    REPORT.unlink(missing_ok=True)
    subprocess.run([*shlex.split(gradle), ":app:pitestDebug", f"-PmutateLines={LINES}", "-PmutateVerbose=true"], cwd=ROOT, check=True)
    return lived(REPORT)


def finish(rows, total, out):
    for row in rows:
        print(row)
    tally = f"{total - len(rows)} of {total}"
    print(f"{tally} changes caught by a test.")
    if out:
        out.mkdir(parents=True, exist_ok=True)
        (out / "lived.txt").write_text("".join(f"{row}\n" for row in rows))
        (out / "tally.txt").write_text(f"{tally}\n")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = parser.add_subparsers(dest="mode", required=True)
    diff = sub.add_parser("diff", help="the lines a branch changed")
    diff.add_argument("base", nargs="?", default="origin/main")
    one = sub.add_parser("part", help="one of N equal parts of all the app's lines")
    one.add_argument("k", type=int, help="which part, 0-based")
    one.add_argument("n", type=int, help="how many parts")
    for p in (diff, one):
        p.add_argument("--gradle", default="./gradlew", help="how to run Gradle")
        p.add_argument("--out", type=Path, help="where to write lived.txt and tally.txt")
    args = parser.parse_args(argv)

    if args.mode == "diff":
        lines = changed_lines(args.base)
        print(f"{len(lines)} changed lines in {len({line.split(':')[0] for line in lines})} files." if lines else "No Kotlin lines changed in the app.")
    else:
        lines = part(all_lines(), args.k, args.n)
        print(f"Part {args.k} of {args.n}: {len(lines)} lines, {lines[0]} to {lines[-1]}." if lines else f"Part {args.k} of {args.n} is empty.")
    rows, total = mutate(lines, args.gradle) if lines else ([], 0)
    finish(rows, total, args.out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
