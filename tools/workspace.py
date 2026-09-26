#!/usr/bin/env python3
"""Inspect the SriSu workspace or run explicitly selected local checks.

Never switches branches, fetches into a checkout, pushes, or starts app servers.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
MAP = json.loads((ROOT / "docs/integration/project-map.json").read_text())


def repo_path(name):
    override = os.environ.get("SRISU_BACKEND_DIR") if name == "backend" else None
    return Path(override or ROOT / MAP["repositories"][name]["path"]).expanduser().resolve()


def git(path, *args):
    return subprocess.check_output(
        ["git", "-C", str(path), *args], text=True, stderr=subprocess.PIPE, timeout=30
    ).strip()


def matches_remote(url, repository):
    # Accept normal GitHub HTTPS and SSH forms without printing credentials.
    return url.removesuffix(".git").lower() in {
        "https://github.com/" + repository.lower(),
        "git@github.com:" + repository.lower(),
        "ssh://git@github.com/" + repository.lower(),
    }


def status(remote=False):
    valid = True
    for name, spec in MAP["repositories"].items():
        path = repo_path(name)
        print(f"{name}: {path}", flush=True)
        try:
            if Path(git(path, "rev-parse", "--show-toplevel")).resolve() != path:
                raise ValueError("path is not a repository root")
            if not matches_remote(git(path, "remote", "get-url", "origin"), spec["github"]):
                raise ValueError("origin does not match " + spec["github"])
            branch = git(path, "branch", "--show-current") or "DETACHED"
            head = git(path, "rev-parse", "HEAD")
            changes = git(path, "status", "--porcelain")
            print(f"  {spec['github']} | working branch: {branch} | HEAD: {head}")
            print(f"  integration: {spec['integration_branch']} | "
                  f"working tree: {'modified' if changes else 'clean'}")
            if not (path / "AGENTS.md").is_file():
                raise ValueError("missing AGENTS.md")
            if remote:
                ref = "refs/heads/" + spec["integration_branch"]
                result = git(path, "ls-remote", "--exit-code", "origin", ref)
                upstream = result.split()[0]
                print(f"  upstream now: {upstream} | "
                      f"{'same commit' if upstream == head else 'different from working HEAD; review before integrating'}")
        except (OSError, ValueError, subprocess.SubprocessError) as error:
            detail = str(error) if isinstance(error, ValueError) else type(error).__name__
            print(f"  ERROR: {detail}. Check folder, repository identity, and Git access.")
            valid = False
    print("Figma reference: https://www.figma.com/design/" + MAP["figma"]["file_key"])
    print("Figma live access is verified through the connector, not this script.")
    return valid


def run(command, cwd, env=None):
    print("Running: " + " ".join(map(str, command)), flush=True)
    subprocess.run(command, cwd=cwd, env=env, check=True)


def verify(target):
    if not status():
        return False
    if target in {"frontend", "all"}:
        run([sys.executable, "tools/theme/check_theme.py"], ROOT)
        env = os.environ.copy()
        # The system JDK can be newer than the version supported by Gradle.
        studio_jdk = Path("/Applications/Android Studio.app/Contents/jbr/Contents/Home")
        if env.get("SRISU_JAVA_HOME"):
            env["JAVA_HOME"] = env["SRISU_JAVA_HOME"]
        elif studio_jdk.is_dir():
            env["JAVA_HOME"] = str(studio_jdk)
        tasks = ["./gradlew", ":composeApp:testDebugUnitTest", ":composeApp:compileDebugKotlinAndroid"]
        if sys.platform == "darwin":
            tasks.append(":composeApp:compileKotlinIosSimulatorArm64")
        else:
            print("iOS compilation requires macOS; not checked on this host.")
        run(tasks + ["--console=plain"], ROOT, env)
    if target in {"backend", "all"}:
        backend = repo_path("backend")
        python = backend / ".venv/bin/python"
        if not python.is_file():
            raise ValueError("Backend .venv missing; follow its docs/workspace.md")
        run([str(python), "tools/workspace.py", "check"], backend)
        run([str(python), "tools/workspace.py", "test"], backend)
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    inspect = commands.add_parser("status")
    inspect.add_argument("--remote", action="store_true", help="Read live integration refs without fetching or changing branches")
    check = commands.add_parser("verify")
    check.add_argument("target", choices=["frontend", "backend", "all"])
    args = parser.parse_args()
    try:
        ok = status(args.remote) if args.command == "status" else verify(args.target)
        return 0 if ok else 1
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        print(f"Verification failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
