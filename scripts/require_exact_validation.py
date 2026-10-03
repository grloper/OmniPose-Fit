"""Read-only bounded release gate: exact commit and validate.yml success required."""
import json, os, subprocess, time

def verdict(runs, sha):
    matching = [r for r in runs if r.get("head_sha") == sha]
    if not matching:
        return "pending"
    latest = max(matching, key=lambda r: r.get("id", 0))
    if latest.get("status") != "completed":
        return "pending"
    return "success" if latest.get("conclusion") == "success" else "failed"

def main():
    sha = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    tag = os.environ.get("RELEASE_TAG", "")
    if not tag.startswith("v") or any(c not in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-" for c in tag):
        raise SystemExit("Release tag must be a safe existing v-prefixed tag")
    target = subprocess.check_output(["git", "rev-parse", f"refs/tags/{tag}^{{commit}}"], text=True).strip()
    if target != sha:
        raise SystemExit("Release tag does not identify the validated checkout commit")
    repo = os.environ["GITHUB_REPOSITORY"]
    deadline = time.monotonic() + 600
    while time.monotonic() < deadline:
        data = json.loads(subprocess.check_output(["gh", "api", f"repos/{repo}/actions/workflows/validate.yml/runs?head_sha={sha}&per_page=100"], text=True))
        state = verdict(data["workflow_runs"], sha)
        if state == "success":
            print(f"Exact commit validation succeeded: {sha}")
            return
        if state == "failed":
            raise SystemExit(f"Exact commit validation failed: {sha}")
        time.sleep(15)
    raise SystemExit(f"No successful exact commit validation within 600 seconds: {sha}")

if __name__ == "__main__": main()
