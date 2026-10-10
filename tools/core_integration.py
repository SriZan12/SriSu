#!/usr/bin/env python3
"""Run real loopback KMP HTTP/WebSocket traffic against a disposable Django DB."""
import argparse
import os
from pathlib import Path
import socket
import shutil
import subprocess
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--backend', type=Path, default=ROOT.parent / 'SriSu-backend')
    args = parser.parse_args()
    backend = args.backend.resolve()
    with tempfile.TemporaryDirectory(prefix='srisu-core-integration-') as tmp:
        fixture = Path(tmp) / 'fixture.json'
        with socket.socket() as sock:
            sock.bind(('127.0.0.1', 0)); port = sock.getsockname()[1]
        # Server logs remain local; never print the fixture/token.
        with (Path(tmp) / 'server.log').open('w') as log:
            server = subprocess.Popen([str(backend / '.venv/bin/python'), 'tools/core_integration_server.py', '--fixture', str(fixture), '--port', str(port)], cwd=backend, stdout=log, stderr=log, env={**os.environ, 'TMPDIR': tmp})
            try:
                ready = False
                for _ in range(100):
                    if server.poll() is not None: raise SystemExit('Disposable backend stopped; inspect its local test setup.')
                    if fixture.exists():
                        try:
                            with socket.create_connection(('127.0.0.1', port), timeout=0.1): ready = True; break
                        except OSError: pass
                    time.sleep(0.1)
                if not ready: raise SystemExit('Disposable backend did not start within 10 seconds.')
                env = {**os.environ, 'SRISU_CORE_INTEGRATION_FILE': str(fixture)}
                result = subprocess.run(['./gradlew', ':composeApp:testDebugUnitTest', '--tests', 'com.srisu.srisu.core.CoreLocalTransportIntegrationTest', '-Psrisu.coreIntegrationRun=' + str(port), '--console=plain'], cwd=ROOT, env=env)
                if result.returncode:
                    report = ROOT / 'build/reports/core-integration-server.log'
                    report.parent.mkdir(parents=True, exist_ok=True)
                    log.flush()
                    shutil.copyfile(Path(tmp) / 'server.log', report)
                    print('Disposable server diagnostic saved at build/reports/core-integration-server.log')
                    raise SystemExit(result.returncode)
                print('Paired KMP ↔ Django loopback integration passed; temporary server and credentials removed.')
            finally:
                server.terminate()
                try: server.wait(timeout=5)
                except subprocess.TimeoutExpired: server.kill(); server.wait()


if __name__ == '__main__': main()
