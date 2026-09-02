# SriSu architecture and refactoring decisions

## Direction

SriSu is a Kotlin Multiplatform application. Shared code follows a layered, feature-oriented
structure:

- **Presentation** owns screen state and user actions. Screen-level ViewModels expose immutable
  `StateFlow` values and launch screen-bound work in `viewModelScope`.
- **Domain** defines application operations and repository contracts. This remains the next
  extraction point for features whose repository classes are currently concrete implementations.
- **Data** implements remote/local access. One-shot operations are suspending functions; changing
  data is exposed as `Flow`. Data APIs do not launch screen-bound work.
- **Core** contains lifecycle-neutral infrastructure such as coroutine dispatchers, session access,
  network configuration, transport result mapping, and logging.

Dependencies point inward: UI -> domain -> data contracts. Platform APIs stay behind
`expect`/`actual` boundaries.

## Coroutine ownership

| Work | Owner | Rule |
| --- | --- | --- |
| Screen requests and mutations | `viewModelScope` | Cancel when the ViewModel is cleared |
| Compose-only animation/scroll work | `rememberCoroutineScope` | Cancel when the composable leaves composition |
| WebSocket connection and shared socket event cache | `ApplicationCoroutineScope` | May outlive a screen; injected by DI |
| Parallel child operations | Caller's scope | Use `coroutineScope` or `supervisorScope`; do not create an orphan scope |
| CPU or blocking work | Owning data source | Be main-safe and use an injected dispatcher |

`CancellationException` is control flow, not an application error. Broad exception handlers at
suspending boundaries must rethrow it. `safeRequest`, paging, WebSocket handling, Auth, Profile,
and Chat flows now preserve cancellation.

## Key decisions implemented

1. **Explicit scope and dispatcher injection**
   - `AppCoroutineDispatchers` centralizes dispatcher selection and can be replaced in tests.
   - `ApplicationCoroutineScope` is the only process-level scope. The WebSocket client and chat
     repository receive it explicitly instead of constructing unmanaged `SupervisorJob` scopes.
   - Pagination methods are suspending APIs; the Chat ViewModel decides when to launch them.

2. **Unidirectional UI state**
   - ViewModels retain private mutable flows and expose immutable `StateFlow` values.
   - Compose screens collect flows with `collectAsStateWithLifecycle` so off-screen Android UI
     does not keep unnecessary upstream collection active.

3. **Constructor injection**
   - `SessionUtils` no longer locates Koin from inside the class. Its `SessionStorage`
     dependency is explicit and unit-testable.
   - Network settings, session access, app scope, and dispatchers are wired at the composition root.

4. **Environment-ready networking**
   - `NetworkConfig` is the single source of API and WebSocket endpoints.
   - API services no longer depend on a static LAN URL.
   - The local-development binding is isolated in DI and should be replaced by a secure
     environment-specific binding for release builds.
   - Request body logging is disabled by default. Optional header logging sanitizes
     `Authorization`; session/token payload logs were removed.

5. **Transport resilience**
   - WebSocket reconnect work has one tracked `Job`, cannot be launched repeatedly, uses a fresh
     token URL for each connection, and propagates cancellation.
   - Sending while disconnected now fails explicitly rather than silently dropping a message.
   - Paging no longer logs entire payloads and propagates cancellation to Paging.

6. **Build and regression safety**
   - The Gradle 8.14.5 wrapper launch scripts were repaired.
   - Common tests cover endpoint normalization, secure WebSocket URL generation, session parsing,
     paging null filtering, and cancellation propagation.

## Release configuration

The checked-in binding uses `NetworkConfig.localDevelopment()` to preserve current development
behavior. Before a production release, replace it in `NetworkModule.kt` with environment-provided
HTTPS/WSS values and do not store credentials in source control. Network body logging should remain
off.

## Follow-up boundaries

The refactor deliberately preserves feature behavior and API payloads. Recommended next slices are:

1. Move concrete classes currently under `domain/repository` behind interfaces, with
   implementations under `data/repository`.
2. Split the large Chat and Edit Profile ViewModels into use cases/state reducers after adding
   feature-level behavior tests.
3. Replace string error types/messages with typed domain failures and localized UI text.
4. Move build-time endpoint selection to platform release configuration and require HTTPS/WSS.
5. Add database-backed offline caching, retry policy tests, Android baseline profiles, and iOS CI
   on macOS.

## Verification

From the repository root:

```powershell
.\gradlew.bat :composeApp:compileDebugKotlinAndroid
.\gradlew.bat :composeApp:testDebugUnitTest
.\gradlew.bat :composeApp:assembleDebug
```

The design follows the official Android architecture recommendations, Android coroutine best
practices, Kotlin structured concurrency guidance, and Compose performance guidance:

- https://developer.android.com/topic/architecture/recommendations
- https://developer.android.com/kotlin/coroutines/coroutines-best-practices
- https://kotlinlang.org/docs/coroutines-basics.html
- https://developer.android.com/develop/ui/compose/performance/bestpractices
