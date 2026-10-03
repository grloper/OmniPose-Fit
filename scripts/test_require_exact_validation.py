import unittest
from require_exact_validation import verdict

class ReleaseGateTest(unittest.TestCase):
    def test_wrong_sha_cannot_release(self):
        self.assertEqual("pending", verdict([dict(id=1, head_sha="other", status="completed", conclusion="success")], "wanted"))
    def test_pending_and_failed_cannot_release(self):
        for status, conclusion, expected in [("in_progress", None, "pending"), ("completed", "failure", "failed"), ("completed", "cancelled", "failed")]:
            self.assertEqual(expected, verdict([dict(id=1, head_sha="wanted", status=status, conclusion=conclusion)], "wanted"))
    def test_exact_success_and_newer_failure(self):
        success = dict(id=1, head_sha="wanted", status="completed", conclusion="success")
        self.assertEqual("success", verdict([success], "wanted"))
        self.assertEqual("failed", verdict([success, dict(id=2, head_sha="wanted", status="completed", conclusion="failure")], "wanted"))

if __name__ == "__main__": unittest.main()
