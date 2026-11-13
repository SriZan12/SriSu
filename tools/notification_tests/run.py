#!/usr/bin/env python3
"""Device-only Firebase checks using the existing Windows Docker sender credentials."""
import argparse
from datetime import datetime, timedelta, timezone
import getpass
import json
import logging
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
from uuid import uuid4

PROJECT = "srisu-ef431"
WORKER = "srisu-notification_worker-1"
REMOTE_SCRIPT = "/tmp/srisu-notification-tests.py"
CASES = {
    "audible": "Background alert on SriSu updates; sound follows the phone's channel/DND settings.",
    "quiet": "Background alert on SriSu updates - quiet; no sound.",
    "short_ttl": "Offline expiry: keep the phone offline for over 60 seconds after sending, then reconnect.",
    "data_only": "Data-only transport: no system alert or SriSu banner is expected.",
    "expired_payload": "Background alert may appear, but tapping its expired payload must not navigate to private content.",
    "unknown_delivery": "Tap while signed in: the server rejects the synthetic delivery as no longer available.",
    "invalid_schema": "An unsupported schema is ignored by the app; a background OS alert may still appear.",
}


class TestKitError(Exception):
    """Only fixed, non-sensitive messages may be passed to this exception."""


def valid_fid(value):
    return isinstance(value, str) and re.fullmatch(r"[A-Za-z0-9_-]{22}", value) is not None


def specification(case, now=None):
    if case not in CASES:
        raise TestKitError("Choose one of the listed test cases.")
    now = now or datetime.now(timezone.utc)
    data = {}
    if case == "data_only":
        data = {"qa_case": "data_only"}
    elif case in {"expired_payload", "unknown_delivery", "invalid_schema"}:
        data = {"schema": "1", "delivery_id": str(uuid4()),
                "expires_at": str(int((now + timedelta(minutes=5)).timestamp()))}
        if case == "expired_payload":
            data["expires_at"] = "1"
        elif case == "invalid_schema":
            data["schema"] = "unsupported-qa"
    return {"case": case, "data": data, "ttl": 60 if case == "short_ttl" else 300,
            "notification": case != "data_only", "audible": case != "quiet",
            "channel": "srisu_updates_quiet" if case == "quiet" else "srisu_updates"}


def make_message(spec, fid):
    from firebase_admin import messaging
    if not valid_fid(fid):
        raise TestKitError("Use the registered Firebase ID copied from SriSu Home.")
    alert = None
    android_alert = None
    if spec["notification"]:
        alert = messaging.Notification(title="SriSu", body="You have an update. Open SriSu to view it.")
        android_alert = messaging.AndroidNotification(
            channel_id=spec["channel"], sound="default" if spec["audible"] else None,
            tag="srisu-qa-" + spec["case"], visibility="private")
    return messaging.Message(fid=fid, data=spec["data"], notification=alert,
        android=messaging.AndroidConfig(priority="high", ttl=timedelta(seconds=spec["ttl"]),
            collapse_key="srisu-qa-alert" if alert else "srisu-qa-data", notification=android_alert))


def require_binding(fid):
    """Read-only check: never select an arbitrary account or create test domain records."""
    sys.path.insert(0, "/app")
    os.environ.setdefault("DJANGO_SETTINGS_MODULE", "srisu.settings")
    import django
    django.setup()
    from django.conf import settings
    from django.db import connection, transaction
    from django.utils import timezone as django_time
    from notifications.models import Installation, Preferences
    from notifications.services import digest, installation_cutoff
    if settings.FIREBASE_PROJECT_ID != PROJECT or not settings.NOTIFICATIONS_ENABLED:
        raise TestKitError("The deployed sender is not enabled for the expected Firebase project.")
    if connection.vendor != "postgresql":
        raise TestKitError("Use the Windows PostgreSQL deployment for device tests.")
    with transaction.atomic():
        with connection.cursor() as cursor:
            cursor.execute("SET TRANSACTION READ ONLY")
        now = django_time.now()
        binding = Installation.objects.filter(target_digest=digest("fid:" + fid), target_kind="fid",
            platform="android", active=True, permitted=True, user__is_active=True,
            updated_at__gte=installation_cutoff(), session__revoked_at__isnull=True,
            session__expires_at__gt=now).values("user_id").first()
        if binding is None:
            raise TestKitError("This ID has no current permitted Android registration. Sign in, allow notifications, keep SriSu open briefly, then copy its Firebase ID again.")
        prefs = Preferences.objects.filter(user_id=binding["user_id"]).first()
        if prefs and (not prefs.push or not prefs.activity):
            raise TestKitError("Enable Push notifications and Invitations & shared activity for this test account first.")


def validation_target():
    """Use an existing registered target entirely inside Docker, for dry runs only."""
    sys.path.insert(0, "/app")
    os.environ.setdefault("DJANGO_SETTINGS_MODULE", "srisu.settings")
    import django
    django.setup()
    from django.conf import settings
    from django.db import connection, transaction
    from django.utils import timezone as django_time
    from notifications.models import Installation
    from notifications.services import installation_cutoff
    if settings.FIREBASE_PROJECT_ID != PROJECT or connection.vendor != "postgresql":
        raise TestKitError("Use the current Windows Firebase/PostgreSQL deployment.")
    with transaction.atomic():
        with connection.cursor() as cursor:
            cursor.execute("SET TRANSACTION READ ONLY")
        fid = Installation.objects.filter(target_kind="fid", platform="android", active=True,
            permitted=True, user__is_active=True, updated_at__gte=installation_cutoff(),
            session__revoked_at__isnull=True, session__expires_at__gt=django_time.now()
        ).order_by("-updated_at").values_list("target", flat=True).first()
    if not fid:
        raise TestKitError("No registered Android device is available for validation. Sign in and allow notifications on a test phone first.")
    return fid


def execute_request(request, binding_check, send, choose_validation_target=None):
    """No target or provider message ID is returned, even on failure."""
    if not isinstance(request, dict) or set(request) - {"case", "fid", "send", "config_only"}:
        raise TestKitError("Invalid test request.")
    case = request.get("case")
    spec = specification(case)
    live = request.get("send", False)
    config_only = request.get("config_only", False)
    if type(live) is not bool or type(config_only) is not bool or (live and config_only):
        raise TestKitError("Configuration checks cannot send notifications.")
    if config_only and ("fid" in request or choose_validation_target is None):
        raise TestKitError("Configuration checks use an existing server registration for validation only.")
    fid = choose_validation_target() if config_only else request.get("fid")
    if not valid_fid(fid):
        raise TestKitError("Use the registered Firebase ID copied from SriSu Home.")
    if not config_only:
        binding_check(fid)
    send(make_message(spec, fid), dry_run=not live)
    return {"case": case, "result": "accepted", "mode": "send" if live else "validate_only",
            "target": "registered_validation_device" if config_only else "selected_device",
            "device_delivery": "not_verified"}


def worker_main():
    logging.disable(logging.CRITICAL)
    try:
        request = json.loads(sys.stdin.read(4097))
        import firebase_admin
        from firebase_admin import messaging
        app = firebase_admin.initialize_app(options={"projectId": PROJECT, "httpTimeout": 15})
        result = execute_request(request, require_binding,
            lambda message, dry_run: messaging.send(message, dry_run=dry_run, app=app), validation_target)
        print(json.dumps(result))
        return 0
    except TestKitError as error:
        print(json.dumps({"error": str(error)}))
    except Exception as error:
        # Never print SDK exception text, request bodies, tracebacks or registration IDs.
        print(json.dumps({"error": "Firebase validation/send failed. Check the worker's Google sign-in, network and the current Firebase ID; no raw provider error was logged.",
                          "error_type": type(error).__name__}))
    return 1


def command(args, *, data=None, label):
    try:
        result = subprocess.run(args, input=data, text=True, encoding="utf-8", errors="replace",
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=90)
    except (OSError, subprocess.TimeoutExpired):
        raise TestKitError(label) from None
    if result.returncode:
        raise TestKitError(label)
    return result.stdout


def prepare_worker():
    if shutil.which("docker") is None:
        raise TestKitError("Docker Desktop must be running and docker must be on PATH.")
    if os.name == "nt":
        os.environ.setdefault("DOCKER_HOST", "npipe:////./pipe/dockerDesktopLinuxEngine")
        if Path("D:/SriSu-tools/docker-client").is_dir():
            os.environ.setdefault("DOCKER_CONFIG", "D:/SriSu-tools/docker-client")
    command(["docker", "cp", str(Path(__file__).resolve()), f"{WORKER}:{REMOTE_SCRIPT}"],
            label="The notification worker is unavailable. Start the SriSu Docker stack first.")


def run_case(case, *, fid=None, live=False, config_only=False):
    request = {"case": case, "send": live, "config_only": config_only}
    if fid is not None:
        request["fid"] = fid
    # stdin keeps identifiers out of shell history and process command lines.
    result = subprocess.run(["docker", "exec", "-i", "-w", "/app", WORKER, "python", REMOTE_SCRIPT, "--worker"],
        input=json.dumps(request), text=True, encoding="utf-8", errors="replace",
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=60)
    try:
        report = json.loads(result.stdout)
    except (ValueError, TypeError):
        raise TestKitError("The test worker returned no valid result; raw output was withheld.") from None
    if result.returncode or "error" in report:
        # Only our worker's fixed messages are emitted; no SDK output crosses this boundary.
        if report.get("error_type") in {"UnregisteredError", "PermissionDeniedError", "UnauthenticatedError",
                "InvalidArgumentError", "ValueError", "RefreshError", "DefaultCredentialsError", "ModuleNotFoundError"}:
            print("Failure category: " + report["error_type"])
        raise TestKitError(report.get("error", "The test worker failed."))
    print(json.dumps(report))
    print(CASES[case])


def read_fid():
    fid = getpass.getpass("Paste the Firebase ID from SriSu Home (hidden; not saved): ").strip()
    if not valid_fid(fid):
        raise TestKitError("Expected a 22-character Firebase Installation ID from SriSu Home.")
    return fid


def menu():
    names = list(CASES)
    prepare_worker()
    while True:
        print("\nSriSu Android notification tests - Firebase project " + PROJECT)
        for i, case in enumerate(names, 1):
            print(f"{i}. Send {case}: {CASES[case]}")
        print("8. Validate all payloads/Google credentials using an existing registration (no notifications sent)")
        print("0. Exit")
        choice = input("Choose: ").strip()
        if choice == "0":
            return
        try:
            if choice == "8":
                for case in names:
                    run_case(case, config_only=True)
            elif choice in {str(n) for n in range(1, 8)}:
                run_case(names[int(choice) - 1], fid=read_fid(), live=True)
            else:
                print("Choose 0 through 8.")
        except TestKitError as error:
            print(str(error))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--list", action="store_true")
    parser.add_argument("--case", choices=list(CASES))
    parser.add_argument("--send", action="store_true", help="Send one chosen case to the ID entered in the hidden prompt")
    parser.add_argument("--check-config", action="store_true", help="Validate all seven payloads using an existing registered target; never sends")
    parser.add_argument("--worker", action="store_true", help=argparse.SUPPRESS)
    args = parser.parse_args()
    if args.worker:
        return worker_main()
    if args.list:
        for name, description in CASES.items():
            print(name + ": " + description)
        return 0
    if args.check_config and (args.case or args.send):
        parser.error("--check-config cannot be combined with --case or --send")
    if args.send and not args.case:
        parser.error("--send requires one --case")
    try:
        if args.check_config:
            prepare_worker()
            for case in CASES:
                run_case(case, config_only=True)
        elif args.case:
            prepare_worker()
            run_case(args.case, fid=read_fid(), live=args.send)
        else:
            menu()
        return 0
    except (EOFError, KeyboardInterrupt):
        return 0
    except TestKitError as error:
        print(str(error))
        return 1
    except Exception:
        print("Test stopped. Check Docker/network availability; raw error details were withheld.")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
