import os
import subprocess
import tempfile
import unittest
from pathlib import Path

COMMENT = Path(__file__).resolve().parent / "mutants-comment.sh"

# A fake gh: logs each call without its body, keeps the last body, and lists comment 7 as ours
# when OLD=1.
GH = """#!/usr/bin/env bash
all="$*"
echo "${all%% -f *}" >> "$LOG"
[[ $all == *" -f body="* ]] && echo "${all#* -f body=}" > "$LOG.body"
[[ $* == *--paginate* && $OLD == 1 ]] && echo 7
exit 0
"""


class Comment(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())
        self.addCleanup(lambda: subprocess.run(["rm", "-rf", str(self.dir)]))
        gh = self.dir / "gh"
        gh.write_text(GH)
        gh.chmod(0o755)

    def comment(self, lived, old, fork=False, tally="1 of 2\n"):
        """Runs the script on a results folder; the calls it made to gh, and the body it sent."""
        results = Path(tempfile.mkdtemp(dir=self.dir))
        log = results / "log"
        log.write_text("")
        if lived is not None:
            (results / "lived.txt").write_text(lived)
        if tally is not None:
            (results / "tally.txt").write_text(tally)
        env = {
            **os.environ,
            "PATH": f"{self.dir}:{os.environ['PATH']}",
            "LOG": str(log),
            "OLD": "1" if old else "0",
            "FORK": str(fork).lower(),
            "GITHUB_REPOSITORY": "o/r",
            "PR": "5",
            "RUN": "https://run",
            "GITHUB_STEP_SUMMARY": str(results / "summary"),
        }
        run = subprocess.run(["bash", str(COMMENT), str(results)], env=env, capture_output=True, text=True)
        self.assertEqual(run.stderr, "")
        self.assertEqual(run.returncode, 0)
        body = Path(f"{log}.body")
        self.body = body.read_text() if body.exists() else ""
        self.summary = (results / "summary").read_text() if (results / "summary").exists() else ""
        return [line for line in log.read_text().split("\n") if line]

    def test_what_lived_is_posted_once_then_edited_in_place(self):
        first = self.comment("A.kt:3  survived: X\n", False)
        self.assertTrue(first[0].startswith("api --paginate repos/o/r/issues/5/comments --jq"))
        self.assertEqual(first[1:], ["api repos/o/r/issues/5/comments"])
        self.assertTrue(self.body.startswith("<!-- mutants -->\n**Mutation testing:** 1 of 2 changes caught"))
        self.assertIn("\nA.kt:3  survived: X\n", self.body)
        self.assertIn("[Full report](https://run)", self.body)
        self.assertIn("A.kt:3  survived: X", self.summary)
        self.assertEqual(self.comment("A.kt:3  survived: X\n", True)[1:], ["api -X PATCH repos/o/r/issues/comments/7"])

    def test_the_comment_goes_once_every_change_is_caught(self):
        self.assertEqual(self.comment("", True)[1:], ["api -X DELETE repos/o/r/issues/comments/7"])
        self.assertEqual(self.comment("", False)[1:], [])

    def test_no_results_at_all_counts_as_every_change_caught(self):
        self.assertEqual(self.comment(None, True, tally=None)[1:], ["api -X DELETE repos/o/r/issues/comments/7"])
        self.assertEqual(self.comment(None, False, tally=None)[1:], [])

    def test_a_forks_run_only_summarizes(self):
        self.assertEqual(self.comment("A.kt:3  survived: X\n", False, fork=True), [])
        self.assertIn("A.kt:3  survived: X", self.summary)


if __name__ == "__main__":
    unittest.main()
