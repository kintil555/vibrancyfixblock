package net.kintil.vibrancyfixblock.mixin;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.typho.big_shot_lib.api.math.vec.IVec3;
import net.typho.vibrancy.LightManager;
import net.typho.vibrancy.block.BlockLightStorage;
import net.typho.vibrancy.block.impl.RayPointLight;
import net.typho.vibrancy.block.impl.RayPointLightStorage;
import net.typho.vibrancy.block.impl.RayPointLightType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vibrancy's raytraced point lights (e.g. sea lanterns) bake a static shadow-occluder
 * mesh for every light and only rebuild it when {@link LightManager#dirtySections} says
 * a nearby chunk section was re-meshed by Sodium AND that section happens to be one the
 * light already considers relevant ({@code RayPointLightStorage#shouldCollectMeshGeometry}).
 * <p>
 * In practice this chain can miss a rebuild: if the section holding the destroyed
 * neighbour block doesn't get re-polled into {@code sectionMeshCaches} in the same frame
 * (or Sodium's rebuild doesn't trip Vibrancy's mesh-cache mixin for that section), the
 * light's occluder mesh keeps referencing the block that no longer exists, and the sea
 * lantern's shadow/light keeps rendering as if the block were still there. The only
 * previously known workaround was to destroy and replace the light source itself, which
 * forces {@code LightManager#blockChanged} to recreate the RayPointLight (and therefore
 * its mesh) from scratch.
 * <p>
 * This mixin hooks the same {@code blockChanged} entrypoint Vibrancy itself uses and
 * unconditionally re-queues the occluder mesh for every currently loaded RayPointLight,
 * sidestepping the fragile dirty-section bookkeeping entirely. {@code queueMesh()} is
 * cheap (it just flips a boolean the light already polls every frame), so doing this on
 * every block change is safe even though it's broader than strictly necessary.
 */
@Mixin(LightManager.class)
public abstract class LightManagerBlockChangedMixin extends LightManager {
    @Inject(
            method = "blockChanged(Lnet/minecraft/world/level/Level;Lnet/typho/big_shot_lib/api/math/vec/IVec3;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At("TAIL")
    )
    @SuppressWarnings("rawtypes")
    private void vibrancyfixblock$forceRayLightRemesh(Level level, IVec3 pos, BlockState old, BlockState newState, CallbackInfo ci) {
        BlockLightStorage<?> storage = this.blockLights.get(RayPointLightType.INSTANCE);
        if (!(storage instanceof RayPointLightStorage rayStorage)) {
            return;
        }

        synchronized (rayStorage.getMap()) {
            for (RayPointLight light : rayStorage.getMap().values()) {
                light.reload();
            }
        }
    }
}
