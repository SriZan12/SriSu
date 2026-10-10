"""Relay public bundles/ciphertext between separately restarted Android/iOS tests.

Requires installed Android evaluation APKs and an iOS build-for-testing on the Mac.
This is a native protocol/storage spike, not the SriSu backend transport test.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import uuid


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--android-serial", required=True)
    parser.add_argument("--ssh", default="ssh")
    parser.add_argument("--scp", default="scp")
    parser.add_argument("--mac-host", required=True)
    parser.add_argument("--identity", required=True)
    parser.add_argument("--known-hosts", required=True, help="Previously verified Mac host key; strict checking is always enabled")
    parser.add_argument("--mac-root", required=True, help="Isolated evaluation root containing frontend/ and simulator-id")
    parser.add_argument("--ssh-option", action="append", default=[])
    args = parser.parse_args()
    run_id = str(uuid.uuid4())
    reports = Path(__file__).resolve().parent / "build" / "interop" / run_id
    reports.mkdir(parents=True)
    options = ["-F", os.devnull, "-o", "BatchMode=yes", "-o", "IdentitiesOnly=yes", "-o", "IdentityAgent=none",
               "-o", "StrictHostKeyChecking=yes", "-o", "UserKnownHostsFile=" + str(Path(args.known_hosts).resolve()),
               "-o", "GlobalKnownHostsFile=" + os.devnull, "-o", "ConnectTimeout=10",
               "-o", "ServerAliveInterval=15", "-o", "ServerAliveCountMax=3", "-i", str(Path(args.identity).resolve())]
    for option in args.ssh_option:
        options.extend(["-o", option])

    def run(command: list[str], data: bytes | None = None, timeout: int = 180) -> bytes:
        result = subprocess.run(command, input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)
        if result.returncode:
            raise RuntimeError((result.stdout + result.stderr).decode("utf-8", "replace")[-12000:])
        return result.stdout

    def remote(script: str) -> bytes:
        return run([args.ssh, *options, "-T", args.mac_host, "/bin/bash -s"], ("set -euo pipefail\n" + script + "\n").encode())

    def adb(*command: str, data: bytes | None = None) -> bytes:
        return run([args.adb, "-s", args.android_serial, *command], data)

    simulator = remote("cat " + shlex.quote(args.mac_root + "/simulator-id")).decode().strip()
    uuid.UUID(simulator)
    app = "com.srisu.evaluation.SignalSpike"
    def ios_exchange_path() -> str:
        container = remote("xcrun simctl get_app_container " + shlex.quote(simulator) + " " + app + " data").decode().strip()
        if not container.startswith("/Users/") or "\n" in container:
            raise RuntimeError("Unexpected simulator application container")
        return container + "/Documents/exchange"

    ios_exchange = ios_exchange_path()
    remote("mkdir -p " + shlex.quote(ios_exchange))
    adb("shell", "run-as", "com.srisu.spike", "mkdir", "-p", "files/exchange")

    def write_android(name: str, value: bytes) -> None:
        # Only fixed public-fixture names reach this function; no private store is read.
        script = "run-as com.srisu.spike sh -c " + shlex.quote("cat > files/exchange/" + name)
        adb("shell", "-T", script, data=value)

    def write_ios(name: str, value: bytes) -> None:
        local = reports / name
        local.write_bytes(value)
        run([args.scp, *options, str(local), args.mac_host + ":" + ios_exchange + "/" + name])

    write_android("run-id", run_id.encode())
    write_ios("run-id", run_id.encode())
    manifest = {"run_id": run_id, "library": "libsignal 0.105.0", "android": args.android_serial, "ios": simulator, "phases": []}
    ios_methods = ["test01Publish", "test02Initiate", "test03ReceiveAndReply", "test04ResumeAndSendDelayed", "test05OutOfOrderTamperAndReplay"]
    android_methods = ["phase01Publish", "phase02Initiate", "phase03ReceiveAndReply", "phase04ResumeAndSendDelayed", "phase05OutOfOrderTamperAndReplay"]
    outputs = [("android-bundle.json", "ios-bundle.json"), ("android-initial.json", "ios-initial.json"),
               ("android-reply.json", "ios-reply.json"), ("android-later.json", "ios-later.json")]

    for index, (android_method, ios_method) in enumerate(zip(android_methods, ios_methods), start=1):
        adb("shell", "am", "force-stop", "com.srisu.spike")
        android_log = adb("shell", "am", "instrument", "-w", "-r", "-e", "class",
                          "com.srisu.spike.InteropTest#" + android_method,
                          "com.srisu.spike.test/androidx.test.runner.AndroidJUnitRunner")
        (reports / f"android-{index}.log").write_bytes(android_log)
        if b"OK (1 test)" not in android_log or b"FAILURES!!!" in android_log:
            raise RuntimeError("Android phase did not pass: " + str(reports / f"android-{index}.log"))
        print(f"Phase {index}: Android passed", flush=True)
        mac_log = args.mac_root + f"/interop-{run_id}-{index}.log"
        workspace = args.mac_root + "/frontend/tools/e2ee-spike/ios"
        command = ["xcodebuild", "test-without-building", "-workspace", "SignalSpike.xcworkspace", "-scheme", "SignalSpike",
                   "-destination", "platform=iOS Simulator,id=" + simulator, "-derivedDataPath", "build",
                   "-parallel-testing-enabled", "NO", "-only-testing:SignalSpikeTests/InteropTests/" + ios_method]
        remote("export LANG=en_US.UTF-8\ncd " + shlex.quote(workspace) + "\n" + shlex.join(command) + " > " + shlex.quote(mac_log) + " 2>&1 || { tail -n 60 " + shlex.quote(mac_log) + "; exit 1; }")
        ios_log = remote("cat " + shlex.quote(mac_log))
        (reports / f"ios-{index}.log").write_bytes(ios_log)
        if not re.search(rb"\*\* TEST (?:EXECUTE )?SUCCEEDED \*\*", ios_log) or not re.search(re.escape(ios_method.encode()) + rb"[^\n]+ passed", ios_log):
            raise RuntimeError("iOS phase did not pass: " + str(reports / f"ios-{index}.log"))
        print(f"Phase {index}: iOS passed", flush=True)
        # Xcode may reinstall the host into a new container while preserving data.
        ios_exchange = ios_exchange_path()
        manifest["phases"].append({"phase": index, "android": "passed", "ios": "passed"})
        (reports / "result.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
        if index <= len(outputs):
            android_file, ios_file = outputs[index - 1]
            android_bytes = adb("exec-out", "run-as", "com.srisu.spike", "cat", "files/exchange/" + android_file)
            ios_bytes = remote("cat " + shlex.quote(ios_exchange + "/" + ios_file))
            # Decode before copying to detect command noise/truncated transfer.
            json.loads(android_bytes)
            json.loads(ios_bytes)
            write_ios(android_file, android_bytes)
            write_android(ios_file, ios_bytes)
            (reports / ios_file).write_bytes(ios_bytes)
    print("Both initiation directions, restarted protected stores, delayed messages, tamper and replay checks passed.")
    print("Evidence: " + str(reports))


if __name__ == "__main__":
    main()
