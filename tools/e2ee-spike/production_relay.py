"""Host relay for the exact production adapters in evaluation-only test targets."""
import json
import base64
import os
from pathlib import Path
import re
import shlex
import subprocess
import uuid


class ProductionRelay:
    def __init__(self, *, adb, serial, mac_host, identity, known_hosts, mac_root, reports):
        self.adb_exe, self.serial, self.mac_host, self.mac_root = adb, serial, mac_host, mac_root
        self.reports = Path(reports)
        self.reports.mkdir(parents=True, exist_ok=True)
        self.step = 0
        ssh_root = Path(os.environ.get("WINDIR", "C:/Windows")) / "System32/OpenSSH"
        self.ssh = str(ssh_root / "ssh.exe") if os.name == "nt" else "ssh"
        self.scp = str(ssh_root / "scp.exe") if os.name == "nt" else "scp"
        self.options = ["-F", os.devnull, "-o", "BatchMode=yes", "-o", "IdentitiesOnly=yes", "-o", "IdentityAgent=none",
            "-o", "StrictHostKeyChecking=yes", "-o", "UserKnownHostsFile=" + str(Path(known_hosts).resolve()),
            "-o", "GlobalKnownHostsFile=" + os.devnull, "-o", "KexAlgorithms=curve25519-sha256", "-o", "ConnectTimeout=8",
            "-o", "ServerAliveInterval=15", "-o", "ServerAliveCountMax=3", "-i", str(Path(identity).resolve())]
        self.simulator = self.remote("cat " + shlex.quote(mac_root + "/simulator-id")).decode().strip()
        uuid.UUID(self.simulator)

    def run(self, command, data=None):
        # Bounded native stress batches deliberately reopen protected SQLite and
        # Keystore/Keychain for every operation; slow emulators need more time.
        result = subprocess.run(command, input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=1200)
        if result.returncode:
            (self.reports / f"failure-{self.step}.log").write_bytes(result.stdout + result.stderr)
            raise RuntimeError(f"Native relay command failed; inspect failure-{self.step}.log")
        return result.stdout

    def remote(self, command):
        return self.run([self.ssh, *self.options, "-T", self.mac_host, "/bin/bash -s"], ("set -euo pipefail\n" + command + "\n").encode())

    def adb(self, *args, data=None):
        return self.run([self.adb_exe,"-s",self.serial,*args],data)

    def ios_documents(self):
        container = self.remote("xcrun simctl get_app_container " + shlex.quote(self.simulator) + " com.srisu.evaluation.SignalSpike data").decode().strip()
        if not container.startswith("/Users/") or "\n" in container:
            raise RuntimeError("Unexpected simulator container")
        return container + "/Documents"

    def exchange(self, platform, commands):
        self.step += 1
        data = json.dumps(commands, separators=(",", ":"), ensure_ascii=False).encode()
        if platform == "android":
            self.adb("shell","am","force-stop","com.srisu.spike")
            self.adb("shell","run-as","com.srisu.spike","mkdir","-p","files")
            self.adb("shell","-T","run-as com.srisu.spike sh -c 'cat > files/engine-input.json'",data=data)
            log = self.adb("shell","am","instrument","-w","-r","-e","class","com.srisu.spike.ProductionRelayTest#exchange",
                "com.srisu.spike.test/androidx.test.runner.AndroidJUnitRunner")
            (self.reports / f"android-{self.step}.log").write_bytes(log)
            if b"OK (1 test)" not in log or b"FAILURES!!!" in log:
                raise RuntimeError("Android relay test failed")
            raw = self.adb("exec-out","run-as","com.srisu.spike","cat","files/engine-output.json")
        elif platform == "ios":
            folder = self.ios_documents()
            self.remote("mkdir -p " + shlex.quote(folder))
            source = self.reports / "engine-input.json"
            source.write_bytes(data)
            self.run([self.scp,*self.options,str(source),self.mac_host + ":" + folder + "/engine-input.json"])
            log_path = self.mac_root + f"/production-relay-{self.step}.log"
            command = ["xcodebuild","test-without-building","-workspace","SignalSpike.xcworkspace","-scheme","SignalSpike",
                "-destination","platform=iOS Simulator,id=" + self.simulator,"-derivedDataPath","build/production-engine",
                "-parallel-testing-enabled","NO","-only-testing:SignalSpikeTests/ProductionRelayTests/testExchange"]
            self.remote("cd " + shlex.quote(self.mac_root + "/frontend/tools/e2ee-spike/ios") + "\n" + shlex.join(command) + " > " + shlex.quote(log_path) + " 2>&1")
            log = self.remote("cat " + shlex.quote(log_path))
            (self.reports / f"ios-{self.step}.log").write_bytes(log)
            if not re.search(rb"\*\* TEST (?:EXECUTE )?SUCCEEDED \*\*", log) or not re.search(rb"testExchange[^\n]+ passed",log):
                raise RuntimeError("iOS relay test failed")
            raw = self.remote("cat " + shlex.quote(self.ios_documents() + "/engine-output.json"))
        else:
            raise ValueError("Unknown platform")
        results = json.loads(raw)
        if len(results) != len(commands):
            raise RuntimeError("Missing native result")
        return results

    def call(self, platform, account, command, value=None):
        result = self.exchange(platform,[{"account": str(account),"command":command,"input":value or {}}])[0]
        if result.get("ok") is not True:
            raise RuntimeError("Native command failed: " + result.get("code","unknown"))
        return result.get("value")

    def binary(self,platform,account,command,value,data):
        result=self.exchange(platform,[{"account":str(account),"command":command,"input":value,"binary":base64.b64encode(data).decode()}])[0]
        if result.get("ok") is not True: raise RuntimeError("Native binary command failed: "+result.get("code","unknown"))
        return result.get("value")

    def read_media(self,platform,account,descriptor,decrypt=False):
        return base64.b64decode(self.call(platform,account,"read_media",{"descriptor":descriptor,"decrypt":decrypt}),validate=True)
