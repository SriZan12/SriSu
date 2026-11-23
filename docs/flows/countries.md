# Country and calling-code selection

Implementation: 2026-11-23, primary checkout `codex/country-picker-consistency`,
starting frontend commit `b4dbf1c280a8d03f704504738a05dadf1cd430e3`.
Backend reviewed at fetched `797b0a08406a7703ed07b93f41841fe67ae44705` on
`dev-core-architecture`; its older working checkout and all local edits are preserved.
Frontend-only publication targets `dev-core-architecture`. No backend mutation,
endpoint change or migration is required.

## Audit and decisions

| Usage | Previous implementation | Shared replacement |
| --- | --- | --- |
| Authentication phone entry | Android asset/NSBundle JSON; separate uncached platform flag conversion | Generated lazy shared catalogue and Compose drawable flag |
| Find Partner | Same catalogue, text-only prefix button; every national number truncated to 10 digits | Shared flags/picker, selected region identified by code; existing auth/backend length validation |
| Edit Profile country | Same phone picker, prefixes irrelevant to location; unknown saved names became null | Location mode without prefixes; exact saved value preserved, including legacy/unknown names |
| Country list | Flag code commented out, name-only search, no clear selected/empty state | Flags, checkmark/radio semantics, name/code/alias/accent/prefix search, clear search, honest empty state and close |
| City lookup | External countriesnow.space query using profile country name | Unchanged; this separate online city service does not block the offline country list |
| Other uses | Country fields in profile DTO/session/discovery ranking and fixtures | Existing wire/storage fields unchanged; no new public exposure of profile location |

The old set had 241 entries, including retired Netherlands Antilles; ten current
regions were missing. Several calling prefixes were wrong. The reviewed snapshot
now offers 250 current locations (249 ISO regions plus XK) and 243 metadata-backed
calling entries. AN remains resolvable for saved profiles. See the exact coverage,
calling-code exclusions/corrections, licenses and update procedure in
[`tools/countries/README.md`](../../tools/countries/README.md).

The flag bundle contains 251 images, 219,265 bytes total, shared between platforms.
It replaces two platform asset copies (296,934 bytes each) and two JSON catalogues.
Only the common bundle ships to either platform; this is source/asset accounting,
not a measured APK/IPA size reduction. No emoji fallback, per-row HTTP request,
new image-loader dependency or UI-thread JSON decoding remains.

Generated English display labels are a CLDR-based Compose string array (one cached resource read, not one per country); stable
uppercase region codes select artwork/rows. Original server-facing names remain
separate. Locale resources can translate the labels and those labels participate
in search. This task does not claim to translate the app into additional languages.

## Interaction and accessibility

Open selector → search or scroll → select region → picker closes and the existing
form callback runs. Cancel, close, Back and IME Done do not change selection. A
profile's current country can be reselected without clearing its saved city. A
new selection still runs the existing city-reset/load flow. Dismissing and reopening
clears the search while retaining the form's selected code.

Rows have 56 dp minimum height, flexible text, borderless flags in 32×24 dp slots
with original artwork aspect ratios, and a selection check. The phone-number
screen's calling-code control includes a small down arrow inside the same tap target.
These are explicit user-requested refinements to the shared picker. The 48 dp close/search-clear controls,
standard modal insets, scrollable lazy list and IME Done remain available. Headers
and country names wrap at larger text; phone prefix controls move above the input
when larger text would squeeze it. Both theme modes use existing semantic colors
and Satoshi typography. No infinite animation or motion dependency was introduced.

Figma auth page `1:2` metadata was readable; subsequent `14:220` screenshot access
hit the Starter quota. This is a derived shared picker using approved theme tokens,
not a claim of newly verified Figma pixel parity. Design-system source is `0:1`.

## Compatibility and boundaries

The backend profile serializer accepts a name in `country`, and the phone API
accepts the existing international-number representation. No server enum, country
endpoint, flag URL or code field is invented. Do not mass-migrate existing country
names: discovery/city lookup still depend on the existing values. Known saved
aliases and unknown names round-trip verbatim until the user chooses another
location; unknown artwork shows a neutral globe. Existing numbers are not rewritten.

Find Partner now reuses authentication's existing total-number bounds rather than
assuming every country has ten national digits. The backend's current maximum is
14 digits plus `+` (15-character column); this task does not expand that contract
or add full numbering-plan validation, trunk-prefix conversion or SMS delivery.

## Verification

See [country picker checks](../validation/countries/README.md) for actual results,
rendered screens and remaining device coverage limits.
