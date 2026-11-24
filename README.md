# NBT Peek 1.2.1 (Fabric, client-side, Minecraft 26.3)

Adds a compact, human readable **"Item data"** section to the bottom of every item tooltip, a
**show ALL data** mode, and an in-game settings screen through **Mod Menu**.

Modern Minecraft stores item "NBT" as *data components*. NBT Peek encodes every component on the
stack and prints it as short `Label: value` lines, so it also covers anything a datapack, command
or other mod adds, not just a hand-picked list.

## Build (the Gradle wrapper is included)
Needs **JDK 25**. Then, in this folder:
```
./gradlew build          # Windows: gradlew.bat build
```
The jar is `build/libs/nbtpeek-1.2.1.jar` (not the `-sources` one). Put it in your `mods` folder.

The versions in `gradle.properties` are the ones from the official Fabric example mod for 26.3
(Minecraft 26.3, Loader 0.20.1, Loom 1.19-SNAPSHOT, Fabric API 0.165.0+26.3, Gradle 9.8.2) plus
Mod Menu 21.0.0. If Fabric publishes newer ones, update those lines (see https://fabricmc.net/develop).

**Mod Menu is optional.** It is only a compile-time dependency; players without it just don't get the
settings screen. If it ever gives you trouble at build time, set `enable_modmenu=false` in
`gradle.properties`: the settings screen is left out and everything else still builds.

## Normal view
```
Item data
Block Entity Data:
 ID: Beehive
 Flower Position: X 10, Y 64, Z -3
 Bees: 5
  Bee 1: Hive: X 12, Y 70, Z 5; Flower: X 10, Y 64, Z -3
   Status: in hive 5s of 30s; carrying nectar
Custom Data:
 Cooldown Ticks: 1m 15s (1500 ticks)
… +4 more lines (hold Shift or F3+H for more, Ctrl for ALL)
```

## Show ALL data
For when you want literally everything:
- **Hold Ctrl** (configurable) while hovering an item, or turn on **Show ALL data** in the settings
  to have it always on.
- Nothing is filtered: components normally hidden (damage, max damage, rarity, tooltip display, ...),
  vanilla-shown ones (enchantments, potions, ...), custom name, lore (as plain text), written/writable
  book title, author, generation and page text, every list element, noisy entity keys (Brain, UUID, Pos, ...),
  every stack in a shulker box or bundle with its own data, components that can't be serialized, plus
  the item id, count and a `Data: 8 components, 1.2 KB as JSON` line.
- **It can't overflow your screen.** Vanilla tooltips can't scroll, so by default ALL mode fits the lines
  to the screen height and widens lines to the screen width. If the one-per-line layout would not fit it
  switches to the compact layout automatically (`allAutoCompact`). Set **Max lines (ALL)** to a number,
  or to *Unlimited* if you really want every line (it will run off-screen).
- For a literal dump, set **Layout** to **Raw SNBT**: each component as `{key:value,...}`, wrapped.

## Everything it does
- **Readable:** Title Case labels, ids -> names, coordinates `X 10, Y 64, Z -3`, ticks `1m 15s (1500 ticks)`,
  0/1 flags -> Yes/No, colors `#FF0000`, chances `25%`, angles `90°`, potion levels `Level II · 30s`,
  `1,234,567`, shortened UUIDs, keys sorted A-Z
- **Bees:** count, hive and flower coordinates, time in hive, nectar, stung
- **Attributes:** `+7 · Main Hand`, `+10% (of base)` when hidden by the item, or when expanded
- **Effects, item stacks, books, player heads (skin id), lodestone targets, containers** (shulker/bundle summary)
- **Hold Shift** (or F3+H): expanded view with exact enchantment levels, raw potion data, bulky components
- **Three layouts:** one per line, compact (several pairs per line), raw SNBT
- **Presets:** Minimal / Balanced / Detailed / Everything in one click
- **Filters:** `ignoredItems` and `onlyItems` (ids and globs like `*_sword`), `onlyComponents`,
  `ignoredComponents`, `ignoredKeys`; **pin keys** with `highlightKeys`
- **Colors:** six configurable colors; optional component ids and a stats line

## Settings
- **With Mod Menu:** Mods -> NBT Peek -> Configure. Four pages (General, Limits, Content, Colors);
  click a button to cycle its value. Changes apply instantly and are saved when you press Done.
- **By hand:** `config/nbtpeek.json` (created on first launch, never overwritten once it exists, even
  if it has a typo; a broken file falls back to defaults). The list options (`highlightKeys`,
  `ignoredItems`, `onlyItems`, `onlyComponents`, `ignoredComponents`, `ignoredKeys`, ...) are only
  editable here.

## If it doesn't compile
Minecraft 26.x renames things between snapshots, and I can't run the real game here. Most of the code
only touches stable APIs and is checked against stand-ins for the Minecraft/Fabric classes (100+ checks).
The errors you reported from a real build (`Minecraft#setScreen` is now `Minecraft#gui.setScreen` since 26.2,
`ChatFormatting#getByName` is gone) are fixed in 1.2.1. If anything else fails, paste the compiler error: it is
usually a one-line fix. The hold-to-expand/ALL key and the screen-size auto-fit use reflection (the window is
found by type), so they can't break the build; if the window or GLFW can't be found they turn themselves off.

## Files
- `JsonRenderer.java`: component JSON -> readable entries (no Minecraft classes, easy to test)
- `Fmt.java`: naming, numbers, durations, text components, SNBT
- `TooltipPeek.java`: builds the section, layouts, ALL mode, line budget, filters
- `PeekConfig.java`, `Presets.java`, `View.java`: settings, presets, per-tooltip limits
- `ExpandKey.java`, `ScreenInfo.java`: reflection-based key and screen-size helpers
- `menu/`: Mod Menu entry point and the paged settings screen
