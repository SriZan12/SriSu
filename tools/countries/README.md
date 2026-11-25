# Shared country data and flags

Reviewed snapshot: 2026-11-23. No runtime country/flag network service.

`catalogue.json` is the reviewed source. `python3 tools/countries/generate.py` generates
shared Kotlin data and localizable Compose string resources. `--check` verifies both
outputs and every bundled flag against the recorded SHA-256. Do not edit generated
files, automatically fetch on app startup, or rename server-facing `name` values.

## Provenance and maintenance

- English display names/aliases: Unicode CLDR **48.0.0**, [territories.json](https://github.com/unicode-org/cldr-json/blob/48.0.0/cldr-json/cldr-localenames-full/main/en/territories.json).
  Unicode license is included. UI names are string resources; translations can use
  `country_labels` in locale-specific resource directories (retain each ISO code before the `|` separator). Search includes both
  localized labels and the English/legacy aliases. This snapshot ships English.
- Calling-code authority: Google libphonenumber **v9.0.19**,
  [PhoneNumberMetadata.xml](https://github.com/google/libphonenumber/blob/v9.0.19/resources/PhoneNumberMetadata.xml), Apache 2.0 license included.
  The old app's valid territory-specific NANP prefixes are preserved. Corrected KY
  `+345` to `+1345`, CY `+537` to `+357`, GY `+595` to `+592`, VA `+379` to `+39`.
  New SX uses `+1721`. Region identity always uses its code, never its shared prefix.
- Flags: [Flagpedia / FlagCDN](https://flagpedia.net/download/api), original aspect
  ratio PNGs at width 160, downloaded 2026-11-23; [public-domain terms](https://flagpedia.net/terms).
  Sources, bytes and hashes are in `flags-manifest.json`. The legacy AN asset is
  retained from the existing SriSu bundle solely for saved-profile presentation.
- Coverage: 249 ISO country/territory codes plus XK (Kosovo); AN (Netherlands
  Antilles) is retained as a retired lookup, not offered for a new selection.
  Added BQ, BV, CW, EH, HM, SS, SX, TF, UM and XK to the previous 241-record snapshot.
- The 243 calling entries have metadata-backed calling prefixes. AQ, BV, GS, HM,
  PN, TF and UM remain available as locations; they are absent from the phone
  picker because this snapshot has no independent calling-region metadata for
  them. Old unsupported `+672` (AQ), `+872` (PN), `+500` (GS) and retired AN are
  not offered as current telephone routes. This does not rewrite saved numbers.

To refresh, review a newer CLDR/libphonenumber release and flag sources, update
only intended records/assets, refresh their manifest hashes, and rerun generation,
coverage/search/compatibility tests and platform UI checks. Flag changes are app
updates rather than third-party changes appearing during a session. Do not add
territories or rename legacy wire values based only on flag filenames.

## Why bundled shared resources

The catalogue is small, changes infrequently, and must work before sign-in and
offline. Generated lazy Kotlin data avoids synchronous JSON/NSBundle reads entirely.
A remote catalogue would introduce loading/failure states without helping these
flows. Emoji flags vary by OS/vendor; they cannot guarantee identical artwork.
Small bundled PNGs and Compose's resource cache provide consistent flags across
Android/iOS. Preserve original aspect ratios, including Nepal; unknown codes show
a neutral globe rather than another country's flag.

The LazyColumn composes visible rows and uses country-code keys/content types.
Filtering roughly 250 entries is immediate; network searches or a debounce delay
are unnecessary. No custom image cache, image-loading dependency, permission,
endpoint or polling timer was added. Sources: [Compose resources](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-usage.html),
[lazy lists](https://developer.android.com/develop/ui/compose/lists),
[Unicode flag semantics](https://www.unicode.org/faq/emoji_dingbats.html).
