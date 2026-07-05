package net.kintil.vibrancyfixblock.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.typho.vibrancy.LightManager;
import net.typho.vibrancy.block.BlockLightStorage;
import net.typho.vibrancy.block.impl.RayPointLight;
import net.typho.vibrancy.block.impl.RayPointLightStorage;
import net.typho.vibrancy.block.impl.RayPointLightType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vibrancy's raytraced point lights (e.g. sea lanterns, {@link RayPointLight}) keep a
 * shadow mesh that's supposed to be incrementally patched via a flood-fill mesher
 * whenever a nearby block changes (see {@code RayPointLight.update()}, which watches
 * {@code LightManager.dirtyBlocks}). In some neighbour-block-removal cases this
 * incremental patch produces a wrong result: instead of the missing block's face
 * disappearing from the mesh, the light ends up rendering a solid lit cube exactly where
 * the destroyed block used to be. The only previously known workaround is to destroy and
 * replace the light source itself, which forces Vibrancy to fully discard and recreate
 * the RayPointLight (and therefore its mesh) from scratch via
 * {@link LightManager#blockChanged}.
 * <p>
 * Trying to patch the flood-fill mesher's internal state directly isn't practical from
 * an external mixin (its bookkeeping is private and non-trivial), so this mixin instead
 * automates the known-good workaround: whenever a block changes, it finds every
 * currently-loaded RayPointLight whose bounding box covers that position, and re-invokes
 * {@code blockChanged} at *that light's own block position* with its own current block
 * state as both "old" and "new". That's exactly what happens when you break and replace
 * the light block by hand - Vibrancy removes the old light data and recreates it fresh,
 * mesh included - just triggered automatically instead of manually.
 */
@Mixin(LightManager.class)
public abstract class LightManagerBlockChangedMixin {
    // Guards against infinite recursion: re-invoking blockChanged() below re-triggers
    // this same injection. Without this flag, a recreated light's own bounding box would
    // always still contain its own position, causing it to recreate itself forever.
    private static final ThreadLocal<Boolean> vibrancyfixblock$reentrant = ThreadLocal.withInitial(() -> false);

    @Inject(method = "blockChanged(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V", at = @At("TAIL"))
    private void vibrancyfixblock$forceRayLightRecreate(Level level, BlockPos pos, BlockState old, BlockState newState, CallbackInfo ci) {
        if (vibrancyfixblock$reentrant.get()) {
            return;
        }

        LightManager self = (LightManager) (Object) this;

        BlockLightStorage<?> storage = self.blockLights.get(RayPointLightType.INSTANCE);
        if (!(storage instanceof RayPointLightStorage rayStorage)) {
            return;
        }

        ConcurrentHashMap<?, ?> map = rayStorage.getMap();

        // Snapshot first: recreating a light below mutates `map` (remove + re-add), and
        // we don't want to touch it while still deciding which lights are affected.
        // We use expanded-by-1-block AABB coordinate comparisons directly (rather than
        // calling a specific AABB containment method) since it only needs public fields
        // that are stable across Minecraft/mapping versions, and we want a little slack
        // around the light's own bounding box to also catch blocks right at its edge.
        List<BlockPos> affectedLightPositions = new ArrayList<>();
        for (Object value : map.values()) {
            if (!(value instanceof RayPointLight light)) {
                continue;
            }
            AABB box = light.getBoundingBox().inflate(1.0);
            boolean overlaps = pos.getX() + 1 > box.minX && pos.getX() < box.maxX
                    && pos.getY() + 1 > box.minY && pos.getY() < box.maxY
                    && pos.getZ() + 1 > box.minZ && pos.getZ() < box.maxZ;
            if (overlaps) {
                affectedLightPositions.add(light.getBlockPos());
            }
        }

        if (affectedLightPositions.isEmpty()) {
            return;
        }

        vibrancyfixblock$reentrant.set(true);
        try {
            for (BlockPos lightPos : affectedLightPositions) {
                // Skip the block that triggered this in the first place - it already
                // just went through the normal blockChanged path above us on the stack.
                if (lightPos.equals(pos)) {
                    continue;
                }
                BlockState currentState = level.getBlockState(lightPos);
                self.blockChanged(level, lightPos, currentState, currentState);
            }
        } finally {
            vibrancyfixblock$reentrant.set(false);
        }
    }
}
