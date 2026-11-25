#!/bin/sh
set -eu
# Debug builds may be compiled without a local Firebase configuration.
if [ "$CONFIGURATION" != "Release" ]; then exit 0; fi
config="$TARGET_BUILD_DIR/$UNLOCALIZED_RESOURCES_FOLDER_PATH/GoogleService-Info.plist"
if [ ! -f "$config" ]; then
  echo 'error: Release requires the registered GoogleService-Info.plist.'
  exit 1
fi
if [ "$(/usr/libexec/PlistBuddy -c 'Print PROJECT_ID' "$config")" != 'srisu-ef431' ]; then
  echo 'error: Unexpected Firebase project. Review environment configuration.'
  exit 1
fi
# Actual linked app dSYM; the static Kotlin framework is included in this binary.
sdk="${SRISU_FIREBASE_IOS_SDK_PATH:-${BUILD_DIR%/Build/*}/SourcePackages/checkouts/firebase-ios-sdk}"
"$sdk/Crashlytics/upload-symbols" -gsp "$config" -p ios "$DWARF_DSYM_FOLDER_PATH/$DWARF_DSYM_FILE_NAME"
