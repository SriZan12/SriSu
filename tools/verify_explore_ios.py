#!/usr/bin/env python3
"""Run production Explore/visitor-Moment UI in UIKit with native keyboard tests.

First link the QA-only arm64 debug controller with the opt-in, checked-in init:
  ./gradlew --init-script tools/qa/explore.init.gradle :composeApp:linkDebugFrameworkIosSimulatorArm64
Then pass a booted simulator UDID to this script. Afterwards, relink that Gradle
task without --init-script before using the framework in a normal app build.
This script never invokes Gradle or alters the production Xcode project. It
builds a temporary, dependency-free Xcode project and installs only
com.srisu.explore.qa plus its test runner.
Synthetic state only: no backend, account, credentials or production app storage.
"""
import argparse
from datetime import datetime
import json
from pathlib import Path
import plistlib
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
BUNDLE = 'com.srisu.explore.qa'
APP_ID = 'A00000000000000000000001'
TEST_ID = 'A00000000000000000000002'


def project_files(folder):
    """Small isolated native app + UI-test project, without third-party generators."""
    project = folder / 'GlobalFavesQA.xcodeproj'
    project.mkdir()
    text = r'''// !$*UTF8*$!
{
 archiveVersion = 1; classes = {}; objectVersion = 56;
 objects = {
 A00000000000000000000001 = {isa = PBXNativeTarget; buildConfigurationList = C00000000000000000000001; buildPhases = (B00000000000000000000001,B00000000000000000000002,B00000000000000000000003); buildRules = (); dependencies = (); name = GlobalFavesQA; productName = GlobalFavesQA; productReference = F00000000000000000000001; productType = "com.apple.product-type.application";};
 A00000000000000000000002 = {isa = PBXNativeTarget; buildConfigurationList = C00000000000000000000002; buildPhases = (B00000000000000000000004,B00000000000000000000005,B00000000000000000000006); buildRules = (); dependencies = (D00000000000000000000001); name = GlobalFavesUITests; productName = GlobalFavesUITests; productReference = F00000000000000000000002; productType = "com.apple.product-type.bundle.ui-testing";};
 B00000000000000000000001 = {isa = PBXSourcesBuildPhase; buildActionMask = 2147483647; files = (E00000000000000000000001,E00000000000000000000002); runOnlyForDeploymentPostprocessing = 0;};
 B00000000000000000000002 = {isa = PBXFrameworksBuildPhase; buildActionMask = 2147483647; files = (E00000000000000000000003); runOnlyForDeploymentPostprocessing = 0;};
 B00000000000000000000003 = {isa = PBXResourcesBuildPhase; buildActionMask = 2147483647; files = (E00000000000000000000004); runOnlyForDeploymentPostprocessing = 0;};
 B00000000000000000000004 = {isa = PBXSourcesBuildPhase; buildActionMask = 2147483647; files = (E00000000000000000000005); runOnlyForDeploymentPostprocessing = 0;};
 B00000000000000000000005 = {isa = PBXFrameworksBuildPhase; buildActionMask = 2147483647; files = (); runOnlyForDeploymentPostprocessing = 0;};
 B00000000000000000000006 = {isa = PBXResourcesBuildPhase; buildActionMask = 2147483647; files = (); runOnlyForDeploymentPostprocessing = 0;};
 C00000000000000000000001 = {isa = XCConfigurationList; buildConfigurations = (C10000000000000000000001); defaultConfigurationIsVisible = 0; defaultConfigurationName = Debug;};
 C00000000000000000000002 = {isa = XCConfigurationList; buildConfigurations = (C10000000000000000000002); defaultConfigurationIsVisible = 0; defaultConfigurationName = Debug;};
 C00000000000000000000003 = {isa = XCConfigurationList; buildConfigurations = (C10000000000000000000003); defaultConfigurationIsVisible = 0; defaultConfigurationName = Debug;};
 C10000000000000000000001 = {isa = XCBuildConfiguration; name = Debug; buildSettings = {PRODUCT_NAME = "$(TARGET_NAME)"; PRODUCT_BUNDLE_IDENTIFIER = "com.srisu.explore.qa"; INFOPLIST_FILE = Info.plist; FRAMEWORK_SEARCH_PATHS = ("$(inherited)","$(PROJECT_DIR)/Frameworks"); OTHER_LDFLAGS = ("$(inherited)","-lsqlite3","-lc++"); LD_RUNPATH_SEARCH_PATHS = ("$(inherited)","@executable_path/Frameworks");};};
 C10000000000000000000002 = {isa = XCBuildConfiguration; name = Debug; buildSettings = {PRODUCT_NAME = "$(TARGET_NAME)"; PRODUCT_BUNDLE_IDENTIFIER = "com.srisu.explore.qa.uitests"; GENERATE_INFOPLIST_FILE = YES; TEST_TARGET_NAME = GlobalFavesQA; LD_RUNPATH_SEARCH_PATHS = ("$(inherited)","@executable_path/Frameworks","@loader_path/Frameworks");};};
 C10000000000000000000003 = {isa = XCBuildConfiguration; name = Debug; buildSettings = {SDKROOT = iphonesimulator; SUPPORTED_PLATFORMS = iphonesimulator; IPHONEOS_DEPLOYMENT_TARGET = 18.5; TARGETED_DEVICE_FAMILY = "1,2"; SWIFT_VERSION = 5.0; SWIFT_OPTIMIZATION_LEVEL = "-Onone"; CODE_SIGNING_ALLOWED = NO; CLANG_ENABLE_MODULES = YES; ENABLE_BITCODE = NO; ONLY_ACTIVE_ARCH = YES; ARCHS = arm64; DEBUG_INFORMATION_FORMAT = dwarf;};};
 D00000000000000000000001 = {isa = PBXTargetDependency; target = A00000000000000000000001; targetProxy = D00000000000000000000002;};
 D00000000000000000000002 = {isa = PBXContainerItemProxy; containerPortal = A00000000000000000000003; proxyType = 1; remoteGlobalIDString = A00000000000000000000001; remoteInfo = GlobalFavesQA;};
 E00000000000000000000001 = {isa = PBXBuildFile; fileRef = F00000000000000000000003;};
 E00000000000000000000002 = {isa = PBXBuildFile; fileRef = F00000000000000000000004;};
 E00000000000000000000003 = {isa = PBXBuildFile; fileRef = F00000000000000000000005;};
 E00000000000000000000004 = {isa = PBXBuildFile; fileRef = F00000000000000000000006;};
 E00000000000000000000005 = {isa = PBXBuildFile; fileRef = F00000000000000000000007;};
 F00000000000000000000001 = {isa = PBXFileReference; explicitFileType = wrapper.application; path = GlobalFavesQA.app; sourceTree = BUILT_PRODUCTS_DIR;};
 F00000000000000000000002 = {isa = PBXFileReference; explicitFileType = wrapper.cfbundle; path = GlobalFavesUITests.xctest; sourceTree = BUILT_PRODUCTS_DIR;};
 F00000000000000000000003 = {isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = GlobalFavesNativeQA.swift; sourceTree = "<group>";};
 F00000000000000000000004 = {isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = MomentVideoDriver.swift; sourceTree = "<group>";};
 F00000000000000000000005 = {isa = PBXFileReference; lastKnownFileType = wrapper.framework; path = Frameworks/ComposeApp.framework; sourceTree = "<group>";};
 F00000000000000000000006 = {isa = PBXFileReference; lastKnownFileType = folder; path = "compose-resources"; sourceTree = "<group>";};
 F00000000000000000000007 = {isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = GlobalFavesUITests.swift; sourceTree = "<group>";};
 F00000000000000000000008 = {isa = PBXGroup; children = (F00000000000000000000003,F00000000000000000000004,F00000000000000000000005,F00000000000000000000006,F00000000000000000000007,F00000000000000000000009); sourceTree = "<group>";};
 F00000000000000000000009 = {isa = PBXGroup; name = Products; children = (F00000000000000000000001,F00000000000000000000002); sourceTree = "<group>";};
 A00000000000000000000003 = {isa = PBXProject; attributes = {BuildIndependentTargetsInParallel = YES; LastUpgradeCheck = 1600; TargetAttributes = {A00000000000000000000002 = {TestTargetID = A00000000000000000000001;};};}; buildConfigurationList = C00000000000000000000003; compatibilityVersion = "Xcode 14.0"; developmentRegion = en; hasScannedForEncodings = 0; knownRegions = (en,Base); mainGroup = F00000000000000000000008; productRefGroup = F00000000000000000000009; projectDirPath = ""; projectRoot = ""; targets = (A00000000000000000000001,A00000000000000000000002);};
 };
 rootObject = A00000000000000000000003;
}
'''
    (project / 'project.pbxproj').write_text(text)
    schemes = project / 'xcshareddata/xcschemes'; schemes.mkdir(parents=True)
    ref = lambda target, name: f'<BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{target}" BuildableName="{name}" BlueprintName="{name.split(".")[0]}" ReferencedContainer="container:GlobalFavesQA.xcodeproj"/>'
    app_ref, test_ref = ref(APP_ID, 'GlobalFavesQA.app'), ref(TEST_ID, 'GlobalFavesUITests.xctest')
    (schemes / 'GlobalFavesQA.xcscheme').write_text(f'''<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="1600" version="1.3">
 <BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries>
  <BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="NO" buildForArchiving="NO" buildForAnalyzing="YES">{app_ref}</BuildActionEntry>
  <BuildActionEntry buildForTesting="YES" buildForRunning="NO" buildForProfiling="NO" buildForArchiving="NO" buildForAnalyzing="YES">{test_ref}</BuildActionEntry>
 </BuildActionEntries></BuildAction>
 <TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="YES"><MacroExpansion>{app_ref}</MacroExpansion><Testables><TestableReference skipped="NO" parallelizable="NO">{test_ref}</TestableReference></Testables></TestAction>
 <LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugDocumentVersioning="YES" debugServiceExtension="internal" allowLocationSimulation="YES"><BuildableProductRunnable runnableDebuggingMode="0">{app_ref}</BuildableProductRunnable></LaunchAction>
</Scheme>
''')
    return project


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--device', required=True, help='Booted arm64 simulator UDID; never chooses a physical device')
    parser.add_argument('--framework', type=Path, default=ROOT / 'composeApp/build/bin/iosSimulatorArm64/debugFramework/ComposeApp.framework')
    parser.add_argument('--test', action='append', help='Optional XCTest method name to run; defaults to all native checks')
    args = parser.parse_args()
    if not (args.framework / 'Headers/ComposeApp.h').is_file():
        parser.error('Link the QA-only arm64 ComposeApp debug framework first')
    if 'globalFavesQaController' not in (args.framework / 'Headers/ComposeApp.h').read_text():
        parser.error('Framework does not include tools/qa/explore/GlobalFavesNativeQA.kt; use the isolated QA Gradle init script')
    devices = json.loads(subprocess.check_output(['xcrun', 'simctl', 'list', 'devices', 'booted', '--json'], text=True))
    if not any(device['udid'] == args.device and device['state'] == 'Booted' for group in devices['devices'].values() for device in group):
        parser.error('The requested simulator must already be booted')
    resources = ROOT / 'composeApp/build/processedResources/iosSimulatorArm64/main/composeResources'
    if not resources.is_dir():
        parser.error('Build the iOS Compose resources before running native tests')
    report = ROOT / 'build/reports/global-faves/ios-native' / datetime.now().strftime('%Y%m%d-%H%M%S')
    report.mkdir(parents=True)
    result = report / 'GlobalFaves.xcresult'
    with tempfile.TemporaryDirectory(prefix='srisu-explore-ios-') as temp:
        folder = Path(temp)
        project = project_files(folder)
        for source in (ROOT / 'tools/qa/GlobalFavesNativeQA.swift', ROOT / 'tools/qa/GlobalFavesUITests.swift', ROOT / 'iosApp/iosApp/MomentVideoDriver.swift'):
            shutil.copyfile(source, folder / source.name)
        shutil.copytree(args.framework, folder / 'Frameworks/ComposeApp.framework')
        shutil.copytree(resources, folder / 'compose-resources/composeResources')
        with (folder / 'Info.plist').open('wb') as file:
            plistlib.dump({'CFBundleIdentifier': BUNDLE, 'CFBundleExecutable': 'GlobalFavesQA', 'CFBundleName': 'Explore QA',
                'CFBundlePackageType': 'APPL', 'CFBundleVersion': '1', 'CFBundleShortVersionString': '1.0',
                'MinimumOSVersion': '18.5', 'CADisableMinimumFrameDurationOnPhone': True, 'UILaunchScreen': {},
                'UISupportedInterfaceOrientations': ['UIInterfaceOrientationPortrait', 'UIInterfaceOrientationLandscapeLeft', 'UIInterfaceOrientationLandscapeRight']}, file)
        command = ['xcodebuild', 'test', '-project', str(project), '-scheme', 'GlobalFavesQA',
            '-destination', f'id={args.device}', '-derivedDataPath', str(folder / 'DerivedData'),
            '-resultBundlePath', str(result), '-parallel-testing-enabled', 'NO', 'CODE_SIGNING_ALLOWED=NO']
        command += [f'-only-testing:GlobalFavesUITests/GlobalFavesUITests/{name}' for name in args.test or []]
        status = 1
        try:
            with (report / 'xcodebuild.log').open('w') as log:
                status = subprocess.run(command, stdout=log, stderr=log).returncode
            if result.exists():
                subprocess.run(['xcrun', 'xcresulttool', 'export', 'attachments', '--path', str(result),
                    '--output-path', str(report / 'attachments')], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                summary = subprocess.run(['xcrun', 'xcresulttool', 'get', 'test-results', 'summary', '--path', str(result)], capture_output=True, text=True)
                if summary.returncode == 0:
                    (report / 'summary.json').write_text(summary.stdout)
        finally:
            for bundle in (BUNDLE, BUNDLE + '.uitests.xctrunner'):
                subprocess.run(['xcrun', 'simctl', 'terminate', args.device, bundle], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                subprocess.run(['xcrun', 'simctl', 'uninstall', args.device, bundle], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        print(f'Native iOS Explore QA {"passed" if status == 0 else "failed"}; report: {report}')
        raise SystemExit(status)


if __name__ == '__main__':
    main()
