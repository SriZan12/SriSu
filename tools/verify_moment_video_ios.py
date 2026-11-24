#!/usr/bin/env python3
"""Run the production AVPlayer driver with a disposable synthetic loopback fixture.

First link :composeApp:linkDebugFrameworkIosSimulatorArm64 and start the backend's
tools/core_integration_server.py --moment-video --fixture <private-file> --port <port>.
This installs only com.srisu.video.qa, never the real app or real account storage.
"""
import argparse
import json
from pathlib import Path
import plistlib
import shutil
import subprocess
import tempfile
import time
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
BUNDLE = 'com.srisu.video.qa'


def run(*args, **kwargs):
    return subprocess.run(args, check=True, **kwargs)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--fixture', type=Path, required=True)
    parser.add_argument('--device', required=True, help='Booted arm64 simulator UDID')
    args = parser.parse_args()
    fixture = json.loads(args.fixture.read_text())
    if urlparse(fixture['base_url']).hostname != '127.0.0.1' or 'video_id' not in fixture:
        parser.error('Use only a disposable --moment-video loopback fixture')
    with tempfile.TemporaryDirectory(prefix='srisu-video-ios-') as temp:
        app = Path(temp)/'VideoQA.app'; app.mkdir()
        with (app/'Info.plist').open('wb') as file:
            plistlib.dump({'CFBundleIdentifier':BUNDLE,'CFBundleExecutable':'VideoQA','CFBundleName':'Video QA',
                'CFBundlePackageType':'APPL','CFBundleVersion':'1','CFBundleShortVersionString':'1.0',
                'MinimumOSVersion':'18.5','UIDeviceFamily':[1,2],'UILaunchScreen':{},
                'NSAppTransportSecurity':{'NSAllowsArbitraryLoads':True}},file)
        sdk = subprocess.check_output(['xcrun','--sdk','iphonesimulator','--show-sdk-path'],text=True).strip()
        log = ROOT/'build/reports/moments/video-ios-build.log'; log.parent.mkdir(parents=True,exist_ok=True)
        with log.open('w') as output:
            run('xcrun','--sdk','iphonesimulator','swiftc','-target','arm64-apple-ios18.5-simulator','-sdk',sdk,
                '-F',str(ROOT/'composeApp/build/bin/iosSimulatorArm64/debugFramework'),'-framework','ComposeApp',
                '-lsqlite3','-lc++','-Xlinker','-dead_strip',str(ROOT/'iosApp/iosApp/MomentVideoDriver.swift'),
                str(ROOT/'tools/qa/MomentVideoNativeQA.swift'),'-o',str(app/'VideoQA'),stdout=output,stderr=output)
        run('xcrun','simctl','install',args.device,str(app))
        try:
            container = Path(subprocess.check_output(['xcrun','simctl','get_app_container',args.device,BUNDLE,'data'],text=True).strip())
            documents = container/'Documents'; documents.mkdir(exist_ok=True)
            shutil.copyfile(args.fixture,documents/'fixture.json')
            result = documents/'result.json'; result.unlink(missing_ok=True)
            run('xcrun','simctl','launch',args.device,BUNDLE,stdout=subprocess.DEVNULL)
            deadline = time.monotonic()+35
            while not result.exists() and time.monotonic()<deadline:
                time.sleep(.25)
            report = json.loads(result.read_text()) if result.exists() else {'passed':False,'error':'timeout'}
            (log.parent/'video-ios-result.json').write_text(json.dumps(report,indent=2)+'\n')
            print(json.dumps(report))  # Only timings and fixed error codes, never credentials.
            if not report.get('passed'):
                raise SystemExit(1)
        finally:
            subprocess.run(['xcrun','simctl','terminate',args.device,BUNDLE],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
            subprocess.run(['xcrun','simctl','uninstall',args.device,BUNDLE],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)


if __name__ == '__main__':
    main()
