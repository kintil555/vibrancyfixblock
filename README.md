# Vibrancy Light Fix

A tiny client-side Fabric add-on for [Vibrancy](https://modrinth.com/mod/vibrancy) that fixes a
stale-shadow bug on raytraced block lights (e.g. sea lanterns) for **Minecraft 1.21.11**.

## The bug

If you place a sea lantern next to another block, then destroy that neighbouring block, the
shadow/light Vibrancy casts for the sea lantern doesn't update — it keeps rendering as if the
neighbouring block were still there. The only previously known workaround was to destroy and
replace the sea lantern itself, which forces Vibrancy to fully recreate its light data.

## Root cause

Vibrancy's raytraced point lights (`RayPointLight`) bake a **static** shadow-occluder mesh once,
and only queue a rebuild (`queueMesh()`) when `LightManager.dirtySections` reports that a nearby
chunk section was re-meshed by Sodium **and** that light already considered that section relevant.
That chain depends on Sodium's mesh-rebuild mixin hook firing for the right section in the right
frame — in some cases (e.g. certain neighbour-block removals) it doesn't, so the occluder mesh
never gets marked dirty and the old geometry keeps being used for shadow casting.

Destroying and replacing the light source works around this because it goes through a completely
different path (`LightManager.blockChanged` → remove + recreate the `RayPointLight` from scratch,
building a brand new mesh).

## The fix

This mod adds a single mixin into `LightManager.blockChanged` — the exact same entrypoint Vibrancy
itself already uses to react to block changes — and unconditionally calls `.reload()` (which just
calls `queueMesh()`) on every currently-loaded `RayPointLight`. This sidesteps the fragile
dirty-section bookkeeping entirely. `queueMesh()` is cheap (it just flips a boolean the light
already polls once per frame on its own thread pool), so doing this on every block change is safe.

See [`LightManagerBlockChangedMixin.java`](src/main/java/net/kintil/vibrancyfixblock/mixin/LightManagerBlockChangedMixin.java)
for the implementation and a longer explanation in the class javadoc.

## Requirements

- Minecraft **1.21.11**
- Fabric Loader 0.19.2+
- Fabric API
- [Vibrancy](https://modrinth.com/mod/vibrancy) (built for 1.21.11)
- Sodium
- Big Shot Lib (Vibrancy's own rendering library dependency)

This mod does not replace or bundle any of the above — it only patches Vibrancy's own behaviour
at runtime, so all of them must already be installed.

## Building it yourself

This repo intentionally does **not** hardcode exact dependency version numbers for Vibrancy,
Sodium, and Big Shot Lib, because those get updated independently of Minecraft/loader versions
and a stale pin would silently start failing to resolve. Before your first build:

1. Open `gradle.properties`.
2. For each of `vibrancy_version`, `sodium_version`, and `big_shot_lib_version`, visit the
   corresponding Modrinth page filtered to Fabric + 1.21.11, open the newest listed version, and
   copy the **version number** shown on that version's page (not the display title):
   - https://modrinth.com/mod/vibrancy/versions?l=fabric&g=1.21.11
   - https://modrinth.com/mod/sodium/versions?l=fabric&g=1.21.11
   - https://modrinth.com/mod/big-shot-lib/versions?l=fabric&g=1.21.11
3. Save the file, then build:

```bash
./gradlew build
```

The output jar will be in `build/libs/`.

## Installing

Drop the built jar into your `mods` folder alongside Fabric API, Vibrancy, Sodium, and Big Shot
Lib. No configuration needed — it works automatically as soon as it's present.

## License

MIT, see [LICENSE](LICENSE).
