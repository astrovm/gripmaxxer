import contextlib
import io
import subprocess
import tempfile
import unittest
from pathlib import Path

import mutants

PACKAGE = "com/astrovm/gripmaxxer"


class Parts(unittest.TestCase):
    def test_every_line_is_in_exactly_one_part(self):
        with tempfile.TemporaryDirectory() as root:
            sources = Path(root)
            (sources / PACKAGE / "ui").mkdir(parents=True)
            (sources / PACKAGE / "B.kt").write_text("b1\nb2\n")
            (sources / PACKAGE / "A.kt").write_text("a1\na2\na3\n")
            (sources / PACKAGE / "ui/C.kt").write_text("c1\n")
            (sources / PACKAGE / "notes.txt").write_text("not code\n")
            lines = mutants.all_lines(sources)
        self.assertEqual(
            lines,
            [
                f"{PACKAGE}/A.kt:1",
                f"{PACKAGE}/A.kt:2",
                f"{PACKAGE}/A.kt:3",
                f"{PACKAGE}/B.kt:1",
                f"{PACKAGE}/B.kt:2",
                f"{PACKAGE}/ui/C.kt:1",
            ],
        )
        for n in (1, 4, 6, 9, 64):
            parts = [mutants.part(lines, k, n) for k in range(n)]
            self.assertEqual(sum(parts, []), lines, n)
            self.assertLessEqual(max(map(len, parts)) - min(map(len, parts)), 1, n)

    def test_the_real_sources_split_into_64_parts_that_cover_every_line_once(self):
        lines = mutants.all_lines()
        self.assertGreater(len(lines), 64)
        parts = [mutants.part(lines, k, 64) for k in range(64)]
        self.assertEqual(sum(parts, []), lines)
        self.assertLessEqual(max(map(len, parts)) - min(map(len, parts)), 1)

    def test_a_part_out_of_range_is_refused(self):
        for k in (-1, 4):
            with self.assertRaises(ValueError):
                mutants.part(["a"], k, 4)


class Diff(unittest.TestCase):
    def test_changed_lines_come_from_the_new_side_of_each_hunk(self):
        diff = f"""diff --git a/app/src/main/java/{PACKAGE}/A.kt b/app/src/main/java/{PACKAGE}/A.kt
--- a/app/src/main/java/{PACKAGE}/A.kt
+++ b/app/src/main/java/{PACKAGE}/A.kt
@@ -3 +3,2 @@ fun f()
-old
+new
+newer
@@ -10,2 +11,0 @@ fun g()
-gone
-gone too
@@ -20,0 +20 @@ fun h()
+added
diff --git a/app/src/main/java/{PACKAGE}/gone.kt b/app/src/main/java/{PACKAGE}/gone.kt
--- a/app/src/main/java/{PACKAGE}/gone.kt
+++ /dev/null
@@ -1,2 +0,0 @@
-x
-y
diff --git a/app/src/main/java/{PACKAGE}/notes.md b/app/src/main/java/{PACKAGE}/notes.md
--- a/app/src/main/java/{PACKAGE}/notes.md
+++ b/app/src/main/java/{PACKAGE}/notes.md
@@ -1 +1 @@
-a
+b
diff --git a/app/src/main/java/{PACKAGE}/ui/B.kt b/app/src/main/java/{PACKAGE}/ui/B.kt
--- a/app/src/main/java/{PACKAGE}/ui/B.kt
+++ b/app/src/main/java/{PACKAGE}/ui/B.kt
@@ -0,0 +1,2 @@
+one
+two
"""
        self.assertEqual(
            mutants.parse_diff(diff),
            [f"{PACKAGE}/A.kt:3", f"{PACKAGE}/A.kt:4", f"{PACKAGE}/A.kt:20", f"{PACKAGE}/ui/B.kt:1", f"{PACKAGE}/ui/B.kt:2"],
        )

    def test_a_branch_is_diffed_from_where_it_left_its_base(self):
        with tempfile.TemporaryDirectory() as root:
            repo = Path(root)
            source = repo / mutants.SOURCES / PACKAGE
            source.mkdir(parents=True)

            def git(*args):
                subprocess.run(["git", "-c", "user.name=t", "-c", "user.email=t@t", *args], cwd=repo, check=True, capture_output=True)

            git("init", "-q", "-b", "main")
            (source / "A.kt").write_text("a1\na2\na3\n")
            (source / "Other.kt").write_text("o1\n")
            git("add", ".")
            git("commit", "-q", "-m", "base")
            git("checkout", "-q", "-b", "branch")
            (source / "A.kt").write_text("a1\nchanged\na3\nadded\n")
            git("commit", "-qam", "branch")
            git("checkout", "-q", "main")
            (source / "Other.kt").write_text("o1\no2\n")
            git("commit", "-qam", "main moved on")
            git("checkout", "-q", "branch")
            self.assertEqual(mutants.changed_lines("main", repo), [f"{PACKAGE}/A.kt:2", f"{PACKAGE}/A.kt:4"])


class Lived(unittest.TestCase):
    def report(self, xml):
        file = tempfile.NamedTemporaryFile("w", suffix=".xml", delete=False)
        self.addCleanup(Path(file.name).unlink)
        file.write(xml)
        file.close()
        return Path(file.name)

    def test_what_no_test_caught_is_listed_by_file_and_line(self):
        report = self.report(
            """<mutations>
          <mutation status='KILLED'><sourceFile>A.kt</sourceFile><lineNumber>3</lineNumber><description>killed</description></mutation>
          <mutation status='SURVIVED'><sourceFile>B.kt</sourceFile><lineNumber>10</lineNumber><description>negated conditional</description></mutation>
          <mutation status='NO_COVERAGE'><sourceFile>A.kt</sourceFile><lineNumber>12</lineNumber><description>removed call</description></mutation>
          <mutation status='TIMED_OUT'><sourceFile>A.kt</sourceFile><lineNumber>2</lineNumber><description>loop</description></mutation>
          <mutation status='MEMORY_ERROR'><sourceFile>A.kt</sourceFile><lineNumber>1</lineNumber><description>oom</description></mutation>
        </mutations>"""
        )
        rows, total = mutants.lived(report)
        self.assertEqual(total, 5)
        self.assertEqual(
            rows,
            [
                "A.kt:2  timed out: loop",
                "A.kt:12  no coverage: removed call",
                "B.kt:10  survived: negated conditional",
            ],
        )

    def test_lines_are_sorted_as_numbers(self):
        report = self.report(
            """<mutations>
          <mutation status='SURVIVED'><sourceFile>A.kt</sourceFile><lineNumber>10</lineNumber><description>b</description></mutation>
          <mutation status='SURVIVED'><sourceFile>A.kt</sourceFile><lineNumber>9</lineNumber><description>a</description></mutation>
        </mutations>"""
        )
        self.assertEqual(mutants.lived(report)[0], ["A.kt:9  survived: a", "A.kt:10  survived: b"])

    def test_no_report_is_nothing_to_change(self):
        self.assertEqual(mutants.lived(Path("/nonexistent/mutations.xml")), ([], 0))


class Finish(unittest.TestCase):
    def test_the_tally_and_what_lived_are_written_for_the_comment(self):
        with tempfile.TemporaryDirectory() as root:
            out = Path(root) / "nested"
            with contextlib.redirect_stdout(io.StringIO()):
                mutants.finish(["A.kt:1  survived: x", "B.kt:2  timed out: y"], 5, out)
            self.assertEqual((out / "lived.txt").read_text(), "A.kt:1  survived: x\nB.kt:2  timed out: y\n")
            self.assertEqual((out / "tally.txt").read_text(), "3 of 5\n")
            with contextlib.redirect_stdout(io.StringIO()):
                mutants.finish([], 0, out)
            self.assertEqual((out / "lived.txt").read_text(), "")
            self.assertEqual((out / "tally.txt").read_text(), "0 of 0\n")


class Run(unittest.TestCase):
    """main() end to end, with a fake Gradle that writes the report PIT would."""

    def run_main(self, argv, report):
        root = Path(tempfile.mkdtemp())
        self.addCleanup(lambda: subprocess.run(["rm", "-rf", str(root)]))
        fake = root / "gradle.sh"
        saved = (mutants.LINES, mutants.REPORT)
        mutants.LINES, mutants.REPORT = root / "build/lines.txt", root / "build/reports/mutations.xml"
        self.addCleanup(lambda: setattr(mutants, "LINES", saved[0]) or setattr(mutants, "REPORT", saved[1]))
        fake.write_text(
            f'echo "$@" > {root}/args\n'
            f'cp "${{2#-PmutateLines=}}" {root}/lines-seen\n'
            + (f'mkdir -p {mutants.REPORT.parent}\ncat > {mutants.REPORT} <<EOF\n{report}\nEOF\n' if report else "")
        )
        with contextlib.redirect_stdout(io.StringIO()):
            status = mutants.main([*argv, "--gradle", f"sh {fake}", "--out", str(root / "out")])
        return root, status

    def test_a_part_runs_pit_on_its_lines_and_writes_the_results(self):
        report = "<mutations><mutation status='SURVIVED'><sourceFile>A.kt</sourceFile><lineNumber>3</lineNumber><description>d</description></mutation><mutation status='KILLED'><sourceFile>A.kt</sourceFile><lineNumber>4</lineNumber><description>e</description></mutation></mutations>"
        root, status = self.run_main(["part", "0", "64"], report)
        self.assertEqual(status, 0)
        self.assertEqual((root / "args").read_text().split()[:2], [":app:pitestDebug", f"-PmutateLines={root}/build/lines.txt"])
        self.assertIn("-PmutateVerbose=true", (root / "args").read_text())
        self.assertEqual((root / "lines-seen").read_text().splitlines(), mutants.part(mutants.all_lines(), 0, 64))
        self.assertEqual((root / "out/lived.txt").read_text(), "A.kt:3  survived: d\n")
        self.assertEqual((root / "out/tally.txt").read_text(), "1 of 2\n")

    def test_pit_finding_nothing_to_change_counts_as_all_caught(self):
        root, status = self.run_main(["part", "1", "64"], "")
        self.assertEqual(status, 0)
        self.assertEqual((root / "out/lived.txt").read_text(), "")
        self.assertEqual((root / "out/tally.txt").read_text(), "0 of 0\n")

    def test_a_broken_run_fails(self):
        root = Path(tempfile.mkdtemp())
        self.addCleanup(lambda: subprocess.run(["rm", "-rf", str(root)]))
        saved = (mutants.LINES, mutants.REPORT)
        mutants.LINES, mutants.REPORT = root / "lines.txt", root / "mutations.xml"
        self.addCleanup(lambda: setattr(mutants, "LINES", saved[0]) or setattr(mutants, "REPORT", saved[1]))
        with contextlib.redirect_stdout(io.StringIO()), self.assertRaises(subprocess.CalledProcessError):
            mutants.main(["part", "0", "64", "--gradle", "false"])


if __name__ == "__main__":
    unittest.main()
