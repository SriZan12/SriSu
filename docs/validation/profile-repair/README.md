# Edit Profile repair — 18 November 2026

Frontend: `SriZan12/SriSu`, local `codex/navigation-flow-repair`, base
`6ed3468ff51e63509d67c241089c4e2ecd1ad39d`. Work was initially verified in
`SriSu-navigation-flow`, then transferred to the main `SriSu` checkout at the
user's request on 18 November 2026.
Backend inspected/tested: `SrizanKhadka/SriSu`, `codex/navigation-flow-audit`,
`1594c3bf1391bef131c3b353154583fe938c4952`. No backend source changes or migrations.
This follows the [navigation audit](../navigation-flow/README.md). Publication
with the navigation repairs into `dev-core-architecture` was authorized on
18 November 2026. No merge or deployment is part of this task.

## Reference and scope

The user's Android screenshot shows the two shell links colliding with system
chrome, an oversized portrait, and the old dark Edit Profile toolbar. The repair
uses SriSu's existing Satoshi typography, theme colors and 12-point field shape.
The Figma file is `LztysD1YvINX7RwZpnhhTt`; the connector rejected the live metadata
lookup because its Starter-plan quota was exhausted. No live Figma fidelity is
claimed. The supplied screenshot and existing design-system documentation are
the reference; Home, Questions and approved chat visuals are preserved.

## Changes

- One light, safe-area-aware Edit Profile header. Explore and notifications retain
  their existing destinations through labelled toolbar actions; the two extra
  shell rows are removed. The original dark shared toolbar remains unchanged.
- An 88-point portrait replaces the 180-point version. The scrollable form caps
  width at 560 points; fields grow with accessible text rather than fixed heights.
  Profile gallery thumbnails use three responsive columns with 48-point removal
  controls. The existing five photo slots and backend ownership are unchanged.
- Name → username → bio supports IME Next and Done. The existing local draft
  editor preserves selection/composition. Update dismisses the keyboard; the
  previous discard/sign-out guards remain.
- Country has an explicit empty placeholder. Country/city rows reserve space for
  their controls when labels grow. Optional flat elevation is scoped to Profile;
  other callers keep their prior default elevation.
- Photo selection launches the existing system picker directly. Android Photo
  Picker and iOS PHPicker supply access to the selected file, so Profile no longer
  waits on an unrelated blanket storage permission before opening the picker.

## Save failure and data handling

The old payload reproduced HTTP 400 with `dob: invalid` when it combined an empty
uneditable date-of-birth field with nested multipart interests. Both a text-only
edit and a photo edit failed. The same edits succeeded when only editable fields
were sent. This reproduces a concrete failure matching the report; it does not
prove which APK/backend version was installed on the user's physical phone.

The editable-only payload from the navigation repair is retained and now covered
by request-body regression tests. Empty hidden DOB/gender/zodiac/phone fields are
not sent. Existing server image URLs are not re-uploaded. New local images use the
existing bounded profile-image reader (JPEG/PNG/WebP, 5 MB, 20 megapixels) before
sending. Invalid images preserve the unsaved text and do not issue a save request.
Gallery removals remain ID-scoped; after an empty server gallery response, the
form clears old photo IDs instead of reusing a removed slot's ID.

Server field codes now produce useful username, name, location, interests and
photo messages. Arbitrary server text or private input is not copied into errors
or request logs. Failed saves retain the form. Server authorization, profile media
validation, token rotation, and duplicate-save guards remain authoritative.

## Verification

- Android unit suite: 553 tests, no failures or errors, one opt-in test skipped.
  Includes existing-image text saves, valid new-image uploads, invalid-image
  rejection without losing text, request-field coverage and structured errors.
- Installed Android app: Pixel 4a / API 35 emulator (`SriSuNavigationQA`). Both
  text and PNG photo updates returned HTTP 200 through the real Ktor repository
  and isolated Django backend. Keyboard Next/Done, keyboard Back, keep/discard
  edits, scrolling to Update, toolbar destinations and return paths passed.
  Two device test methods passed. [Installed screen](android-native.png).
- Native iOS simulator tests: six passed on iPhone 17 / iOS 26.5
  (`SriSu Couple Chat Evaluation`). Four cover native Compose layouts and focus
  actions; two cover profile/session updates. Renders cover 390×844, 320×640 and
  320×640 at 1.6× text; Update remains scroll-reachable. These are component
  tests, not installed-app photo-picker or live iOS networking verification.
- Android debug and instrumented APK builds passed, as did iOS simulator test
  compilation/linking. Theme audit and `git diff --check` passed. A final debug
  APK was rebuilt using the original checkout's configured development API
  origin, rather than the isolated QA override.
- Controlled backend reproduction: old text and photo payloads both returned
  400 (`dob: invalid`); editable-only versions both returned 200. This used the
  actual multipart profile route with a synthetic user and interest, an isolated
  SQLite test database and temporary media. No backend patch was required.

All integration writes used synthetic accounts in the isolated `srisu-home-qa`
stack on local ports 8940/8948. No personal profile or production server was
modified. The seven carried user-edit files remained unchanged during the
isolated repair. During transfer, only the repair/test/evidence files were copied;
the main checkout's newer Home edits and three design documents were preserved
byte-for-byte. Older carried Home/ChatPrivacy/iOS-project edits were not restored
over the main checkout's current versions.

Additional evidence: [Android standard](android-standard.png),
[small](android-small.png), [large text](android-large-text.png),
[iOS standard](ios-standard.png), [small](ios-small.png),
[large text](ios-large-text.png). Native software-keyboard geometry and the
system photo picker on iOS, and the user's physical Android device/server
combination, remain unverified in this repair.

## Running the repaired build

Open `/Users/srijan/studioprojects/SriSu`, on `codex/navigation-flow-repair`.
The branch is now checked out here; the old worktree is detached and retained
only as a recovery copy. The main checkout's local SDK/API-origin settings are
unchanged. Automated QA explicitly overrides the origin to the isolated loopback
backend. Credentials are not copied into source or fixtures committed to Git.

After transfer, the main checkout passed `:composeApp:testDebugUnitTest`
(553 tests, no failures/errors, one skipped), `:composeApp:assembleDebug`,
`:composeApp:compileKotlinIosSimulatorArm64`, the theme audit and
`git diff --check`. Installed-device scenarios above were run before transfer;
the profile implementation files were verified byte-identical after transfer.
