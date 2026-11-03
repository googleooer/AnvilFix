# Changelog

## 1.1.0

AnvilFix now runs on **Fabric, Forge and NeoForge**, on every Minecraft version from **1.19 to 26.3** that the loader has a release for.

### Fixes

- **Crash with other anvil mods** (#1): AnvilFix replaced the whole anvil method, which made mods like AdventureZ crash on launch. It now hooks in without replacing anything.
- **Couldn't load on Fabric Loader 0.14.21** (#2): AnvilFix no longer asks for a newer loader than it needs.
- **Incompatible enchantments could be combined** (#3): Protection + Fire Protection, Silk Touch + Fortune, Infinity + Mending and the rest now follow vanilla's rules again.
- **"Too Expensive!" still showed** for costs of 40 or more, even though the item could be taken. The real cost is shown now.
- **The second item was consumed for nothing**: renaming an undamaged tool with its repair material in the second slot used up the whole stack. So did an unusable book or item. The anvil now shows no result in these cases, like vanilla.
- **Upgrades were undercharged**: Sharpness I + a Sharpness III book gave Sharpness III but charged for level II. Enchantments now cost base × the level you end up with.
- **Book merging lost levels**: Sharpness I + Sharpness III books gave Sharpness II. Books now merge like book + item.
- **Combining two damaged tools did nothing**: their durability now adds up (plus vanilla's 12% bonus), for the per-material cost of 2 levels.
- **Multiplayer cost display**:
  - The client no longer guesses results the server would reject. For example, with `mendingWorksWithUnbreaking` turned on, it used to show Mending + Unbreaking as impossible.
  - On servers, clients without AnvilFix now see the real cost instead of the vanilla one.

### Changes

- From Minecraft 1.21.11, game rules have namespaced ids, so the gamerule is `/gamerule anvilfix:mending_works_with_unbreaking`. Earlier versions keep `mendingWorksWithUnbreaking`.
- AnvilFix is now licensed under LGPL-3.0 (the license text is included in the jar).

### Known limitations

- On Forge and NeoForge, AnvilFix replaces the anvil's result calculation, so the loaders' anvil events don't fire. Mods that add anvil recipes through those events won't work alongside it.
- A client without AnvilFix still shows "Too Expensive!" for costs of 40 or more; the item can still be taken.
