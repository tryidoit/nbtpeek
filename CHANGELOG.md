# Changelog

## 1.2.1
- Fixed build errors on Minecraft 26.3: screens are opened with `Minecraft.gui.setScreen` (26.2+), color names
  no longer use the removed `ChatFormatting.getByName`
- Fixed `fabric.mod.json` failing to parse: the source file is now valid JSON; the Mod Menu entry point is
  removed at build time when `enable_modmenu=false`
- Window lookup for the hold-key / auto-fit features now searches by type instead of one method name

## 1.2.0
- **Show ALL data mode** (always on, or hold Ctrl): no filtering, full lists, custom name, lore, books, every
  container stack with its data, non-serializable components, item id/count and a data-size line;
  fits the screen automatically and switches to compact layout when needed
- Raw SNBT layout; Presets (Minimal / Balanced / Detailed / Everything)
- Effects (`Speed: Level II · 30s`), item stacks (`Arrow ×3`), books, colors `#FF0000`, chances, angles,
  potion levels, thousands separators, keys sorted A-Z
- `onlyItems`, `onlyComponents`, optional component ids and a stats line
- Paged settings screen (General / Limits / Content / Colors)
- Gradle wrapper included; versions updated to the official 26.3 template (Loader 0.20.1, Loom 1.19-SNAPSHOT,
  Fabric API 0.165.0+26.3, Mod Menu 21.0.0)

## 1.1.0
- Mod Menu support with an in-game settings screen (optional dependency, `enable_modmenu` build switch)
- Hold Shift / Ctrl / Alt to expand the tooltip (F3+H still works); hint line says which
- Compact layout (several pairs per line)
- Container summaries (shulker boxes, bundles), player head skin info
- `highlightKeys`, `ignoredItems` (globs), configurable colors, `requireAdvancedTooltips`, `showHeader`
- Exact enchantment levels and raw potion data in the expanded view
- Mod icon, license, Mod Menu metadata
- Fixed: highlighted keys were not colored when their parent object was packed onto one line

## 1.0.0
- Human readable item data tooltip: custom data, bees with hive/flower coordinates, attributes, lodestone
  targets, entity data; line cap with "+N more"; F3+H expanded view
- Fixed: long bee lines were clipped, tiny floats shown as 0, config file overwritten when it had a typo
