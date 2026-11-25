# ADR 001: one scoped CMP navigation owner

Date: 2026-10-01. Status: implemented; publication authorized through a feature PR targeting dev-core-architecture.

## Decision and compatibility evidence

Retain the official Compose Multiplatform Navigation 2 implementation and replace
`org.jetbrains.androidx.navigation:navigation-compose:2.8.0-alpha13` with stable
**2.9.1**. Keep Kotlin/Compose compiler **2.2.0**, CMP Gradle plugin **1.8.2**, AGP
**8.12.2**, Koin **4.1.0**, serialization **1.9.0**, coroutines **1.10.2** and Ktor
**3.2.3** unchanged. Android min/target remain 24/35; existing iOS targets remain
device arm64, simulator arm64 and simulator x64 (Xcode application deployment targets 18.x).

The [official CMP Navigation 3 guide](https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html)
specifies CMP 1.10 or newer. Its 1.1.1 Navigation artifact and 2.10.0 lifecycle
integration require a broader baseline change than this refactor. Its adaptive
example also includes a beta dependency; example versions are not our release policy.
The [Nav2 guide](https://kotlinlang.org/docs/multiplatform/compose-navigation.html)
documents typed destinations, minimal arguments, entry ownership and platform back
behavior. Its current beta example was not copied into this app.

The [2.9.1 artifact metadata](https://repo.maven.apache.org/maven2/org/jetbrains/androidx/navigation/navigation-compose/2.9.1/navigation-compose-2.9.1.pom)
uses Kotlin 2.1.21 metadata and resolves Navigation Android 2.9.4, CMP lifecycle
2.9.5 and savedstate 1.3.5. Dependency resolution also selects Compose runtime
1.9.3. This is **not** a claim that every resolved UI library remains 1.8.2.
Android compilation/tests/APK and Kotlin/Native executable tests verified this
combination. No second navigation library was added.

Also consulted the official Android [Navigation 3 overview](https://developer.android.com/guide/navigation/navigation-3),
[modularity](https://developer.android.com/guide/navigation/navigation-3/modularize),
[state saving](https://developer.android.com/guide/navigation/navigation-3/save-state),
and [results](https://developer.android.com/guide/navigation/navigation-3/return-results).
Their ownership principles apply; their APIs were not mixed into Nav2. Reconsider
Nav3 when the project deliberately upgrades CMP and validates its platform integrations.

## Ownership

```text
Android Intent / iOS onOpenURL / future notification tap adapter
    -> PlatformEntry.inbox (one bounded, minimal pending target)
    -> BaseNavigation
         StartupCoordinator + SessionCoordinator (existing auth authority)
         CoupleAccessCoordinator (projection of GET profiles/me)
         versioned account/access/couple scope + ViewModelStore
         AppNavHost (registration assembly only)
            AuthGraph -> auth-flow-owned AuthViewModel
            ChatNav -> partner-flow VM; conversation-entry VM
            HomeGraph -> shell actions/public feature contracts
            ProfileNav -> account editor + requester-scoped picker result
            coupleProfileGraph -> profile-entry VM and local editor draft
    -> entry-scoped AppNavigator -> the library NavController
```

`AppRoot` was replaced, and `App` calls `BaseNavigation`. There is one NavHost.
Controllers exist only in the owner and registration adapters. Screens use callbacks;
repositories and ViewModels do not mutate the application stack. `AppNavigator`
does not retain a shadow stack. Registration files retained in `navigation/graph`
are feature adapters, not a central switch over every business screen. Couple
Profile owns its registration and public typed contract in its feature package.

## Policy

- Native launch remains native splash; no artificial delay or second splash route.
- Startup resolves introduction/session/profile using existing persisted preferences
  and authenticated profile. Name, **required gender**, optional photo retain their
  existing order. Gender must not disappear because an older request omitted it.
- MAIN distinguishes resolving membership, ready unlinked, ready coupled and failure.
  Unlinked users retain Home, account editing, discovery and phone invitations.
  No invented requirement forces a couple before entering those features.
- Membership projection checks on initial access/foreground and every 30 seconds
  while foreground. It discards profile contents after deriving IDs. A changed couple
  or member set destroys affected navigation/drafts. Server authorization remains
  immediate and authoritative between checks. No new WebSocket subsystem.
- Temporary membership failures retain a previously verified scope and show retry;
  initial failure presents recovery. 401/403 do not keep a protected graph open.
- Logout/account generation changes clear the graph, saved tab scopes, pending links,
  results and feature VMs. Critical transitions do not depend on a transient event or
  wait for an unsaved-change dialog. Shared repository cleanup remains session-owned.

Home, Explore and Profile retain existing shell styling/icons. Crushes is removed.
The old Matches **tab** is removed, but its legitimate sent/received phone invitation
screen remains reachable from Home as Partner invitations. Explore now opens the
implemented couple feed, not dating discovery. No Sparks/Challenges feature code
was removed; no implemented destinations for those features existed in the baseline.

The bottom bar remains visible at top-level roots, as before. Tab selection derives
from the visible destination. Switching saves/restores the library's tab state;
reselection is a no-op in the bar. Detail screens hide the bar and Back returns to
its originating tab. Home's cross-tab Explore action uses the same switch operation.
`open` pushes a distinct resource entry; a duplicate current intent is ignored.
`back`/`up` pop only with an actual parent; `replace` replaces the current entry;
`finishFlow` clears child history while retaining its flow VM for confirmation;
`root` returns to a root and removes obsolete history. Android handles exit at the
root; no iOS programmatic exit. Custom fade overrides were removed to use library
platform transitions. Gesture cancellation still needs device UX verification.

Couple Profile's sections and auth's name/gender/photo steps are intentionally local
single-resource editor/wizard state. They are not parallel app back stacks. Each
profile entry owns its draft/confirmation sheet and returns within the same entry;
typed `CoupleSection` supports cross-feature section intent. Persistent saves update
repository-backed state and do not depend on a navigation result.

## Results and restoration

`ProfileNav.InterestScreen(requester, requestId)` contains only entry/request IDs.
The picker reads its originating editor VM. Its bounded `InterestPickerResult` holds
request ID plus catalogue IDs and is serialized into that entry's SavedStateHandle.
The originating editor consumes/removes it once, after initialization. It is never
broadcast to another profile. Cancel removes the request without applying a draft.
Unknown/malformed/mismatched results are discarded. Selection is a local editor
change; the existing account Save operation is still required to persist it.

Routes are concrete `@Serializable` types implementing an open marker contract;
no reflective polymorphic decoding or cross-module sealed hierarchy is needed.
Native tests encode/decode the concrete types. Resource IDs and entry IDs are distinct.

Navigation saving uses `rememberNavController` inside a versioned SaveableStateProvider
bound to session generation, account, protected state, couple ID and member IDs.
Pre-migration JSON/dating states have no matching namespace. Future route removals
must bump this version. Restoring a different access scope starts a safe root.
Configuration saving is library-managed, after startup reconciliation. Cold iOS
launch starts fresh; this is not durable cross-launch navigation storage. Pending
external intents and unsaved domain drafts are deliberately memory-only. A restored
partner preview without its authorized flow data presents recovery; it never loads
an arbitrary user's private profile. Restored pickers recover through their caller's
saved request; missing callers get an explicit recovery screen. Domain saves remain
recoverable by refetching server data.

## External entries

Only `srisu://couples/<positive-int>` and `srisu://chats/<uuid>` are accepted (512-char
limit; no query, fragment, arbitrary route name or write operation). Malformed and
retired inputs leave the current safe screen unchanged. Android initial Intent and
onNewIntent, and SwiftUI onOpenURL call the same parser/inbox. A notification provider
can call `PlatformEntry.openNotification(url, deliveryId)`; none was configured in
this repository, so provider delivery is **not** claimed as implemented or tested.
The inbox retains one target, binds it to the current account and session generation,
deduplicates delivery IDs (bounded to 32), permits a signed-out link across one login,
and discards it on logout/account/session replacement. Ordinary URL opens receive
fresh delivery IDs so a user can reopen a previously visited link; notification
providers supply stable delivery IDs. Protected navigation waits for startup and membership.
Cold entry has Home beneath the target; warm entry appends/reuses the intended
resource. APIs still authorize resource access. No link accepts an invitation.
Custom schemes are configured; verified HTTPS App Links/Universal Links are not claimed.

## Adding a feature

Follow the implemented `features/coupleprofile/navigation/CoupleProfileNavigation.kt`:

```kotlin
@Serializable data class ExampleDestination(val resourceId: Long) : Route
fun NavGraphBuilder.exampleGraph(controller: NavController) {
    composable<ExampleDestination> { entry ->
        val id = entry.toRoute<ExampleDestination>().resourceId
        val navigator = AppNavigator(controller, entry)
        // Entry-scoped VM loads id through the existing authorized repository.
        ExampleScreen(id = id, onBack = navigator::back)
    }
}
```

Register the feature's graph once in AppNavHost. Callers import its public destination,
not its screen. Validate IDs in the adapter. Add external targets to the explicit parser
only if product behavior is defined; never map URL strings directly to route names.
Use `NavigationOwnerTest` and `NavigationContractTest` as executable examples of
entry identity, lifecycle, saving and portable typed arguments.

See [migration inventory and validation](../../flows/navigation.md) for coverage and limitations.
