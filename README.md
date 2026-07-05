# Vibrancy Light Fix

A tiny client-side Fabric add-on for [Vibrancy](https://modrinth.com/mod/vibrancy) that fixes a
stale-shadow bug on raytraced block lights (e.g. sea lanterns) for **Minecraft 1.21.11**.

## The bug

If you place a sea lantern next to another block, then destroy that neighbouring block, the
shadow/light Vibrancy casts for the sea lantern doesn't update — it keeps rendering as if the
neighbouring block were still there. The only previously known workaround was to destroy and
replace the sea lantern itself, which forces Vibrancy to fully recreate its light data.

## Root cause

Vibrancy's raytraced point lights (`RayPointLight`) bake a shadow-occluder mesh
(`AsyncBlockShadowMesh`) and only mark it dirty for rebuild through Vibrancy's own internal
tracking. That tracking can miss a rebuild in some neighbour-block-removal cases, leaving the
occluder mesh referencing geometry that no longer exists — so the shadow/light keeps rendering
the old state. Destroying and replacing the light source works around this because it goes
through a different path (`LightManager.blockChanged` → remove + recreate the `RayPointLight`
from scratch, building a brand new mesh).

## The fix

This mod adds a single mixin into `LightManager.blockChanged` — the exact same entrypoint
Vibrancy itself already uses to react to block changes — and unconditionally calls `.reload()`
on every currently-loaded `RayPointLight`. `reload()` just flips a flag the light already polls
once per frame on its own, so doing this on every block change is cheap and safe, and it
sidesteps whatever internal bookkeeping missed the update.

See [`LightManagerBlockChangedMixin.java`](src/main/java/net/kintil/vibrancyfixblock/mixin/LightManagerBlockChangedMixin.java)
for the implementation.

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

Vibrancy and Big Shot Lib's 1.21.11 Fabric builds aren't (yet) published on Modrinth's public
Maven, and Sodium is under the Polyform Shield license, which makes casually redistributing it
from a third-party Maven something this repo intentionally avoids. So instead of resolving
these automatically, the build expects you to provide the exact jars you already run in-game.

### Local build

1. Create a `libs/` folder in the project root (it's git-ignored, so this is safe).
2. Copy in the three jars you already use in your `mods` folder:
   - `vibrancy-fabric-*.jar`
   - `sodium-fabric-*.jar`
   - `big_shot_lib-*.jar` (also referred to as "Big Shot Rendering Library")

   (Matching is by filename prefix, so exact version numbers in the filename don't matter.)
3. Build:

   ```bash
   ./gradlew build
   ```

The output jar will be in `build/libs/`.

### CI (GitHub Actions)

Since these jars can't be committed to the repo or pulled from a public Maven, CI downloads
them from a GitHub Release *in this repo* that you create and maintain yourself:

1. Create a release in this repo tagged `vendor-libs-v1` (any non-published/draft-free release
   works; it doesn't need to be marked "latest").
2. Attach the same three jars (`vibrancy-fabric-*.jar`, `sodium-fabric-*.jar`,
   `big_shot_lib-*.jar`) as release assets.
3. Push — the `Build` workflow (`.github/workflows/build.yml`) downloads them from that release
   into `libs/` before compiling.

If you ever update to a newer Vibrancy/Sodium/Big Shot Lib build, just re-upload updated assets
to that same `vendor-libs-v1` release (or bump the `VENDOR_LIBS_TAG` env var in the workflow if
you'd rather create a new tag).

**Note on licensing:** you are responsible for making sure you're allowed to re-upload these
jars under their respective licenses (Vibrancy and Big Shot Lib are MIT; Sodium is Polyform
Shield 1.0.0). This repo never mirrors them itself — only you, by attaching them to your own
release, so please double check Polyform Shield's terms before doing so if you're unsure.

## Installing

Drop the built jar into your `mods` folder alongside Fabric API, Vibrancy, Sodium, and Big Shot
Lib. No configuration needed — it works automatically as soon as it's present.

## License

MIT, see [LICENSE](LICENSE).
