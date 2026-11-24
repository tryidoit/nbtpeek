# Contributing

Thanks for helping out. NBT Peek is a small client-side Fabric mod.

## Setup
- JDK 25
- `./gradlew build` (Windows: `gradlew.bat build`); the jar lands in `build/libs/`
- `./gradlew runClient` starts a dev client

## Guidelines
- Keep code that touches Minecraft internals defensive: unstable lookups go through reflection
  (see `WindowAccess`, `ExpandKey`, `ScreenInfo`) so a rename can only disable a feature, not break the build.
- `JsonRenderer` and `Fmt` have no Minecraft dependencies; keep it that way so they stay easy to test.
- New settings: add the field to `PeekConfig` (with a clamp in `compile()`), then an entry in `PeekConfigScreen`.
- Add a line to `CHANGELOG.md` under the next version.

## Releasing
Bump `mod_version` in `gradle.properties`, update the changelog, then tag: `git tag v1.3.1 && git push --tags`.
The Release workflow builds the jar and attaches it to a GitHub release.
