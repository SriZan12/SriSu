# Moments screenshot index — 2026-10-22

Actual production Compose components rendered with synthetic names and test-only landscape artwork. No production data or replacement application fixtures are used.

Android: Robolectric API 35 native graphics, 2× density; the capture loader uses software bitmaps because hardware bitmaps are invisible to a software screenshot canvas. iOS: Compose/Skia runtime on dedicated Apple-silicon simulator `0055683D-7DBA-44C7-855D-4AA64123C522`, 2× density. These are screen-component renders; the unchanged BaseNavigation bottom bar and OS keyboard/picker/camera are outside these fixtures.

Baseline is the exact pre-task five-button Home composable copied into tests before replacement. iOS sheets include the underlying screen; Android dialog-window captures show the sheet against its scrim.

| Capture | Platform | Logical viewport | Text scale | State |
| --- | --- | --- | --- | --- |
| [android-00-baseline-home](android-00-baseline-home.png) | Android | 390 × 844 | 1× | 00 baseline home |
| [android-create-audience](android-create-audience.png) | Android | 390 × 844 | 1× | create audience |
| [android-create-failure](android-create-failure.png) | Android | 390 × 844 | 1× | create failure |
| [android-create-preview](android-create-preview.png) | Android | 390 × 844 | 1× | create preview |
| [android-create-progress](android-create-progress.png) | Android | 390 × 844 | 1× | create progress |
| [android-create-select](android-create-select.png) | Android | 390 × 844 | 1× | create select |
| [android-home-active-dark](android-home-active-dark.png) | Android | 390 × 844 | 1× | home active dark |
| [android-home-active](android-home-active.png) | Android | 390 × 844 | 1× | home active |
| [android-home-empty](android-home-empty.png) | Android | 390 × 844 | 1× | home empty |
| [android-home-long-small-large-text](android-home-long-small-large-text.png) | Android | 320 × 640 | 1.6× | home long small large text |
| [android-home-offline](android-home-offline.png) | Android | 390 × 844 | 1× | home offline |
| [android-viewer-creator-edit](android-viewer-creator-edit.png) | Android | 390 × 844 | 1× | viewer creator edit |
| [android-viewer-creator](android-viewer-creator.png) | Android | 390 × 844 | 1× | viewer creator |
| [android-viewer-expired](android-viewer-expired.png) | Android | 390 × 844 | 1× | viewer expired |
| [android-viewer-long-small-large-text](android-viewer-long-small-large-text.png) | Android | 320 × 640 | 1.6× | viewer long small large text |
| [android-viewer-partner](android-viewer-partner.png) | Android | 390 × 844 | 1× | viewer partner |
| [android-viewer-public](android-viewer-public.png) | Android | 390 × 844 | 1× | viewer public |
| [android-viewer-unavailable](android-viewer-unavailable.png) | Android | 390 × 844 | 1× | viewer unavailable |
| [ios-00-baseline-home](ios-00-baseline-home.png) | iOS simulator | 390 × 844 | 1× | 00 baseline home |
| [ios-create-audience](ios-create-audience.png) | iOS simulator | 390 × 844 | 1× | create audience |
| [ios-create-preview](ios-create-preview.png) | iOS simulator | 390 × 844 | 1× | create preview |
| [ios-home-active-dark](ios-home-active-dark.png) | iOS simulator | 390 × 844 | 1× | home active dark |
| [ios-home-active](ios-home-active.png) | iOS simulator | 390 × 844 | 1× | home active |
| [ios-home-empty](ios-home-empty.png) | iOS simulator | 390 × 844 | 1× | home empty |
| [ios-home-long-small-large-text](ios-home-long-small-large-text.png) | iOS simulator | 320 × 640 | 1.6× | home long small large text |
| [ios-viewer-creator](ios-viewer-creator.png) | iOS simulator | 390 × 844 | 1× | viewer creator |
| [ios-viewer-expired](ios-viewer-expired.png) | iOS simulator | 390 × 844 | 1× | viewer expired |
| [ios-viewer-partner](ios-viewer-partner.png) | iOS simulator | 390 × 844 | 1× | viewer partner |
| [ios-viewer-public](ios-viewer-public.png) | iOS simulator | 390 × 844 | 1× | viewer public |

Review: Home heading no longer breaks beside Add on 320dp/1.6× text; the preview is shorter so captions remain reachable; photo sizing uses Fit in the main viewer and preview, Crop for thumbnail selection only. Missing avatars use a consistent fallback. Private/public actions and creator controls reflect server capabilities.

Limits: native keyboard opening, picker/camera permission dialogs, physical devices and iOS interactive back gestures were not exercised. Synthetic artwork confirms layout/aspect handling, not the aesthetic quality of real photography.
