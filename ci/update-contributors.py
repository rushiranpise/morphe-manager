#!/usr/bin/env python3
"""Regenerate the contributor credits from an organisation's own repositories.

The wall is drawn from a JSON snapshot. Nothing in it is typed by hand, so the credits track the
organisation instead of drifting away from it: a new contributor appears, an existing one gains
commits and projects, all by running this and committing the result. The GitHub Action runs it on a
schedule, which is what keeps the wall current without anybody thinking about it.

    python ci/update-contributors.py                regenerate the snapshot
    python ci/update-contributors.py --check        fail if it is out of date (for CI)
    python ci/update-contributors.py --no-network   keep the existing snapshot, fetch nothing

What is derived, and from where:

    contributors    every maintained repository in the organisation. A person is one account across
                    all of them, so somebody who worked in the manager, the patches and the patcher
                    is credited once, with all three.
    commits         the contributions each account made, summed over the repositories.
    projects        the repositories an account contributed to, most-contributed first, so a badge
                    is a fact and not a guess.
    account         the GitHub account itself, with its own avatar; the app fetches the picture, so
                    the repository grows no binary.

A repository that is a fork contributes its project to the people who are demonstrably part of this
organisation, and nobody else. A fork tracks its upstream, so its history carries that project's
own contributors too, and no comparison can separate them: the upstream keeps committing into it.
So a fork credits only the accounts that already appear in a repository the organisation owns
outright, which is exactly the set of people who work here. Read off a fork alone, a project like
MicroG-RE would put microG's whole upstream history on the wall.

The consequence is deliberate: somebody who worked *only* on a fork has nothing to match against,
so they are not credited until they are named in `FORK_ALLOWLIST`. `EXCLUDED_REPOS` is the one
place to drop a repository outright.

Requires: `gh` (authenticated). There is no git history read: everything comes from the API, so the
snapshot covers the whole organisation rather than only the checkout this runs in.
"""

from __future__ import annotations

import argparse
import collections
import json
import os
import re
import shutil
import subprocess
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS_ROOT = os.path.join(REPO_ROOT, "app", "src", "main", "assets")
OUTPUT = os.path.join(ASSETS_ROOT, "contributors.json")

DEFAULT_ORG = "MorpheApp"

# A repository whose name carries nothing a reader would recognise as a project.
EXCLUDED_REPOS = {".github"}

# The prefix every project in this organisation shares, dropped from the badge a person is given.
PROJECT_PREFIX = "morphe-"

# Accounts that commit here but are not people: release automation, the dependency bot and the
# translation import.
EXCLUDED_LOGINS = {
    "semantic-release-bot",
    "crowdin-bot",
    "weblate",
    "anonymous",
    "github-actions",
}
BOT_LOGIN = re.compile(r"\[bot\]$|(?:^|[\s-])bot$", re.IGNORECASE)

# Handles whose profile name would be unhelpful.
NAME_OVERRIDES: dict[str, str] = {}

# A fork tracks its upstream, so its history carries that project's contributors too, and comparing
# refs cannot separate them: upstream commits keep being merged in. So a fork credits only accounts
# that already appear in a repository the organisation owns outright, which is the set of people who
# demonstrably work here. Anyone who worked *only* on a fork is listed here by hand.
FORK_ALLOWLIST: set[str] = set()

# Three badges is what fits on one row of the card.
MAX_PROJECTS = 3


def gh_json(path: str):
    """A paginated GET against the GitHub API, or None when it does not answer."""
    if shutil.which("gh") is None:
        return None
    result = subprocess.run(
        ["gh", "api", path, "--paginate"],
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    )
    if result.returncode != 0 or not result.stdout.strip():
        return None
    # --paginate concatenates the pages, so decode the values back to back.
    decoder = json.JSONDecoder()
    text = result.stdout.strip()
    index = 0
    merged: list = []
    try:
        while index < len(text):
            value, end = decoder.raw_decode(text, index)
            merged.extend(value if isinstance(value, list) else [value])
            index = end
    except ValueError:
        return None
    return merged


def is_bot(login: str) -> bool:
    return login.lower() in EXCLUDED_LOGINS or bool(BOT_LOGIN.search(login))


def project_key(repo_name: str) -> str:
    """The badge for a repository: its name without the organisation's shared prefix."""
    lowered = repo_name.lower()
    return lowered[len(PROJECT_PREFIX):] if lowered.startswith(PROJECT_PREFIX) else lowered


def org_repos(org: str) -> list[dict]:
    """Every repository of the organisation, forks and archived ones included."""
    repos = gh_json(f"orgs/{org}/repos?per_page=100") or []
    found = []
    for repo in repos:
        if not isinstance(repo, dict):
            continue
        name = repo.get("name")
        if not name or name in EXCLUDED_REPOS:
            continue
        found.append({
            "name": name,
            "fork": bool(repo.get("fork")),
            "branch": repo.get("default_branch"),
        })
    return found


def fork_parent(org: str, repo: str) -> tuple[str | None, str | None]:
    """
    The account a fork came from, and the branch its commits should be read against.

    The compare API names a base ref as `owner:branch`, not `owner/repo:branch`, so only the
    account in front of the slash is useful here.
    """
    data = gh_json(f"repos/{org}/{repo}")
    info = data[0] if isinstance(data, list) and data else None
    if not isinstance(info, dict):
        return None, None
    parent = info.get("parent") or {}
    full_name = parent.get("full_name")
    owner = full_name.split("/")[0] if isinstance(full_name, str) and "/" in full_name else None
    return owner, parent.get("default_branch")


def repo_contributions(
    org: str, repo: dict, avatars: dict[str, str | None]
) -> collections.Counter:
    """What each account contributed to one repository, as commits."""
    name = repo["name"]
    counts: collections.Counter = collections.Counter()

    if not repo["fork"]:
        for entry in gh_json(f"repos/{org}/{name}/contributors?per_page=100") or []:
            if not isinstance(entry, dict):
                continue
            login = entry.get("login")
            if not login or is_bot(login):
                continue
            counts[login] += int(entry.get("contributions") or 0)
            avatars.setdefault(login, entry.get("avatar_url"))
        return counts

    # A fork: only the commits it added on top of its parent belong to this organisation.
    parent_owner, parent_branch = fork_parent(org, name)
    head = repo.get("branch")
    if not parent_owner or not parent_branch or not head:
        return counts

    pages = gh_json(
        f"repos/{org}/{name}/compare/{parent_owner}:{parent_branch}...{head}"
    ) or []
    for page in pages:
        if not isinstance(page, dict):
            continue
        for commit in page.get("commits") or []:
            author = commit.get("author") if isinstance(commit, dict) else None
            login = author.get("login") if isinstance(author, dict) else None
            if not login or is_bot(login):
                continue
            counts[login] += 1
            if not avatars.get(login) and isinstance(author, dict):
                avatars[login] = author.get("avatar_url")
    return counts


def known_accounts() -> dict[str, dict]:
    """The snapshot as it stands, so a refresh costs nothing for accounts already named."""
    if not os.path.isfile(OUTPUT):
        return {}
    try:
        with open(OUTPUT, encoding="utf-8") as handle:
            payload = json.load(handle)
    except (OSError, ValueError):
        return {}
    found = {}
    for entry in payload.get("contributors", []):
        key = entry.get("login") or entry.get("name")
        if key:
            found[key] = entry
    return found


def profile_name(login: str) -> str | None:
    """The name an account goes by, where it has one."""
    data = gh_json(f"users/{login}")
    info = data[0] if isinstance(data, list) and data else None
    if isinstance(info, dict):
        name = info.get("name")
        if isinstance(name, str) and name.strip():
            return name.strip()
    return None


def build(org: str, network: bool) -> dict:
    known = known_accounts()

    if not network:
        # Nothing to derive without the API: the snapshot is left exactly as it is.
        people = [entry for entry in known.values() if isinstance(entry, dict)]
        people.sort(key=lambda p: (-int(p.get("commits") or 0), str(p.get("name") or "")))
        return {"org": org, "contributors": people}

    repos = org_repos(org)
    if not repos:
        raise SystemExit(f"No repositories could be read for {org}. Is `gh` authenticated?")

    totals: dict[str, int] = collections.Counter()
    projects: dict[str, collections.Counter] = collections.defaultdict(collections.Counter)
    avatars: dict[str, str | None] = {}

    def credit(counts: collections.Counter, repo: dict) -> None:
        key = project_key(repo["name"])
        for login, commits in counts.items():
            if commits <= 0:
                continue
            totals[login] += commits
            projects[login][key] += commits

    # The organisation's own repositories first: their contributors are the people who are
    # unambiguously part of this project, and they are what the forks are filtered against.
    for repo in repos:
        if not repo["fork"]:
            credit(repo_contributions(org, repo, avatars), repo)

    own_logins = set(totals)

    for repo in repos:
        if not repo["fork"]:
            continue
        counts = repo_contributions(org, repo, avatars)
        ours = collections.Counter({
            login: commits
            for login, commits in counts.items()
            if login in own_logins or login in FORK_ALLOWLIST
        })
        credit(ours, repo)

    people = []
    for login, commits in totals.items():
        ranked = sorted(projects[login], key=lambda k: (-projects[login][k], k))
        remembered = known.get(login) or {}
        # A remembered name equal to the login is not a name: it is what a lookup that failed fell
        # back to, and keeping it would stop the real name ever being found.
        remembered_name = remembered.get("name")
        if remembered_name == login:
            remembered_name = None
        name = (NAME_OVERRIDES.get(login)
                or remembered_name
                or profile_name(login)
                or login)
        people.append({
            "name": name,
            "login": login,
            "avatarUrl": avatars.get(login)
            or remembered.get("avatarUrl")
            or f"https://github.com/{login}.png?size=256",
            "commits": commits,
            "projects": ranked[:MAX_PROJECTS],
        })

    people.sort(key=lambda p: (-p["commits"], str(p["name"]).lower()))

    return {
        "generated": subprocess.run(
            ["git", "-C", REPO_ROOT, "log", "-1", "--format=%cs", "HEAD"],
            capture_output=True, text=True, encoding="utf-8", errors="replace",
        ).stdout.strip(),
        "org": org,
        "contributors": people,
    }


def render(payload: dict) -> str:
    # Indented and newline-terminated so the diff of a refresh is readable in review.
    return json.dumps(payload, ensure_ascii=False, indent=2) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true",
                        help="exit non-zero if the snapshot is out of date")
    parser.add_argument("--no-network", action="store_true",
                        help="do not ask GitHub for anything")
    parser.add_argument("--org", default=DEFAULT_ORG, help="the organisation to credit")
    parser.add_argument("--out", default=OUTPUT, help="where to write the snapshot")
    args = parser.parse_args()

    # A check has to look at the live data, or it would only ever confirm the file matches itself.
    network = not args.no_network

    if args.check:
        content = render(build(org=args.org, network=network))
        current = open(args.out, encoding="utf-8").read() if os.path.isfile(args.out) else ""
        if current != content:
            print("Contributor credits are out of date. Run: python ci/update-contributors.py",
                  file=sys.stderr)
            return 1
        print("Contributor credits are up to date.")
        return 0

    content = render(build(org=args.org, network=network))

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(content)

    total = len(json.loads(content)["contributors"])
    print(f"Wrote {os.path.relpath(args.out, REPO_ROOT)} with {total} contributors")
    return 0


if __name__ == "__main__":
    sys.exit(main())
