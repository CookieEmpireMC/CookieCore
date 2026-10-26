#!/usr/bin/env python3
"""Merge-time ownership check: did every commit on a branch stay inside its author's files?

Usage::

    mise run ownership-check <branch> [--base <feature-branch>] [--human-approved]
    python tools/ownership/check.py <branch> [--base <feature-branch>] [--human-approved]

Looks at the commits in ``<base>..<branch>`` (``--base`` defaults to the
current branch, i.e. the feature branch the orchestrator is on):

* every **non-merge** commit is checked by its **author email** against that
  author's globs in ``.claude/ownership.json``, so a tester commit on an
  agent's branch is judged by the tester's rules. Unknown authors fail.
* **merge** commits are not author-checked; files that differ from every
  parent (conflict resolutions, or both sides edited) are listed so the
  orchestrator can mention them in the PR.
* human-controlled files: a violation for agents; for the orchestrator (and
  for anyone with ``--human-approved``, when the user explicitly approved the
  change) they're listed under "needs human review" instead.

Exit code: 0 clean, 1 violations, 2 usage/git error.

Authorship is self-declared (``git -c user.email=…``), so this guards against
mistakes, not tampering. The map and matcher are read from
``$CLAUDE_PROJECT_DIR``, else the main checkout, else this checkout (the first
that has them); the source is printed. Standard library only.
"""

from __future__ import annotations

import argparse
import importlib.util
import os
import subprocess
import sys

LIB_REL = os.path.join(".claude", "hooks", "ownership_lib.py")
MAP_REL = os.path.join(".claude", "ownership.json")


def git(*args: str) -> str:
    result = subprocess.run(["git", *args], capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)} failed: {result.stderr.strip()}")
    return result.stdout


def candidate_roots() -> list:
    roots = []
    env = os.environ.get("CLAUDE_PROJECT_DIR")
    if env:
        roots.append(env)
    try:
        common = git("rev-parse", "--path-format=absolute", "--git-common-dir").strip()
        roots.append(os.path.dirname(common))  # main checkout (parent of the shared .git)
    except RuntimeError:
        pass
    try:
        roots.append(git("rev-parse", "--show-toplevel").strip())
    except RuntimeError:
        pass
    return roots


def load_rules():
    """Return ``(lib_module, ownership_map, root)`` from the first root that has both files."""
    for root in candidate_roots():
        lib_path, map_path = os.path.join(root, LIB_REL), os.path.join(root, MAP_REL)
        if os.path.isfile(lib_path) and os.path.isfile(map_path):
            spec = importlib.util.spec_from_file_location("ownership_lib", lib_path)
            lib = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(lib)
            return lib, lib.load_map(map_path), root
    raise RuntimeError(f"no {MAP_REL} + {LIB_REL} found in: {', '.join(candidate_roots())}")


def list_commits(base: str, branch: str) -> list:
    """Commits in ``base..branch``, oldest first, as dicts."""
    out = git("log", "--reverse", "--format=%H%x1f%P%x1f%an%x1f%ae%x1f%s%x1e", f"{base}..{branch}")
    commits = []
    for record in out.split("\x1e"):
        record = record.strip("\n")
        if not record:
            continue
        sha, parents, name, email, subject = record.split("\x1f")
        commits.append({"sha": sha, "parents": parents.split(), "name": name,
                        "email": email, "subject": subject})
    return commits


def _names(out: str) -> list:
    return [p for p in out.split("\0") if p]


def changed_files(sha: str) -> list:
    """Paths a non-merge commit touched (renames count as delete + add)."""
    return sorted(set(_names(git("diff-tree", "--root", "--no-commit-id", "-r",
                                 "--no-renames", "--name-only", "-z", sha))))


def merge_resolution_files(sha: str, parents: list) -> list:
    """Paths where a merge commit differs from every parent."""
    common = None
    for parent in parents:
        files = set(_names(git("diff", "--no-renames", "--name-only", "-z", parent, sha)))
        common = files if common is None else common & files
    return sorted(common or [])


def check(lib, data: dict, commits: list, human_approved: bool = False) -> dict:
    """Classify every commit's files. Returns a result dict.

    A commit dict may carry a precomputed ``files`` list (handy in tests);
    otherwise the files are read from git.
    """
    result = {"violations": [], "human_review": [], "merges": [], "checked": 0}
    for commit in commits:
        if len(commit["parents"]) > 1:
            files = commit["files"] if "files" in commit else \
                merge_resolution_files(commit["sha"], commit["parents"])
            result["merges"].append((commit, files))
            continue
        result["checked"] += 1
        files = commit["files"] if "files" in commit else changed_files(commit["sha"])
        evaluate_commit(lib, data, commit, files, human_approved, result)
    return result


def evaluate_commit(lib, data, commit, files, human_approved, result) -> None:
    ident = lib.identity_for_email(data, commit["email"])
    for path in files:
        owner_text = lib.describe_owners(data, path)
        if ident is None:
            result["violations"].append((commit, path, "unknown author", owner_text))
            continue
        who, entry = ident
        if lib.is_human_controlled(data, path):
            if who == lib.ORCHESTRATOR or human_approved:
                result["human_review"].append((commit, path))
            else:
                result["violations"].append((commit, path, who, owner_text))
        elif not lib.matches(entry["paths"], path):
            result["violations"].append((commit, path, who, owner_text))


def short(commit) -> str:
    return f"{commit['sha'][:9]} {commit['subject'][:60]}"


def report(result: dict, base: str, branch: str, root: str) -> int:
    print(f"ownership-check: {base}..{branch} (rules from {root})")
    print(f"  {result['checked']} commit(s) checked, {len(result['merges'])} merge commit(s) skipped")

    if result["merges"]:
        print("\nMerge commits (author check skipped). Files that differ from every parent"
              " (conflict resolutions or both-side edits); list them in the PR:")
        for commit, files in result["merges"]:
            print(f"  {short(commit)} ({commit['name']} <{commit['email']}>)")
            for path in files or ["(none)"]:
                print(f"      {path}")

    if result["human_review"]:
        print("\nHuman-controlled files changed (put them under 'Needs human review' in the PR):")
        for commit, path in result["human_review"]:
            print(f"  {short(commit)}  {commit['name']} <{commit['email']}>  {path}")

    if result["violations"]:
        print(f"\nFAIL: {len(result['violations'])} ownership violation(s):")
        for commit, path, who, owner_text in result["violations"]:
            print(f"  commit {short(commit)}")
            print(f"    author: {commit['name']} <{commit['email']}> ({who})")
            print(f"    file:   {path}")
            print(f"    owner:  {owner_text}")
        print("\nSend each violation back to the agent that made it (see docs/workflow.md).")
        return 1
    print("\nOK: every commit stays inside its author's files.")
    return 0


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("branch", help="branch to check, e.g. <feature>/plugin-coder")
    parser.add_argument("--base", help="branch it merges into (default: the current branch)")
    parser.add_argument("--human-approved", action="store_true",
                        help="the user explicitly approved changes to human-controlled files: "
                             "list them for review instead of failing")
    args = parser.parse_args(argv)
    try:
        base = args.base or git("rev-parse", "--abbrev-ref", "HEAD").strip()
        if base == "HEAD":
            raise RuntimeError("detached HEAD; pass --base <feature-branch>")
        for ref in (base, args.branch):
            git("rev-parse", "--verify", "--quiet", f"{ref}^{{commit}}")
        lib, data, root = load_rules()
        result = check(lib, data, list_commits(base, args.branch), args.human_approved)
    except (RuntimeError, ValueError, OSError) as exc:
        print(f"ownership-check: {exc}", file=sys.stderr)
        return 2
    return report(result, base, args.branch, root)


if __name__ == "__main__":
    sys.exit(main())
