package net.kintil.vibrancyfixblock.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.typho.vibrancy.LightManager;
import net.typho.vibrancy.block.BlockLightStorage;
import net.typho.vibrancy.block.impl.RayPointLight;
import net.typho.vibrancy.block.impl.RayPointLightStorage;
import net.typho.vibrancy.block.impl.RayPointLightType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Vibrancy's raytraced point lights (e.g. sea lanterns, {@link RayPointLight}) bake a
 * shadow-occluder mesh ({@code AsyncBlockShadowMesh}) and only mark it dirty for rebuild
 * through Vibrancy's own internal bookkeeping. In practice this can miss a rebuild: if a
 * block next to the light is destroyed, the light's occluder mesh can keep referencing
 * geometry that no longer exists, so its shadow/light keeps rendering as if the
 * neighbouring block were still there. The only previously known workaround was to
 * destroy and replace the light source itself, which forces Vibrancy to recreate it (and
 * therefore its mesh) from scratch via {@link LightManager#blockChanged}.
 * <p>
 * This mixin hooks that same {@code blockChanged} entrypoint and unconditionally calls
 * {@link RayPointLight#reload()} on every currently-loaded ray point light, sidestepping
 * whatever internal dirty-tracking missed the update. {@code reload()} just flips a flag
 * the light already polls on its own each frame, so doing this on every block change is
 * cheap and safe.
 */
@Mixin(LightManager.class)
public abstract class LightManagerBlockChangedMixin {
    @Inject(method = "blockChanged(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V", at = @At("TAIL"))
    private void vibrancyfixblock$forceRayLightRemesh(Level level, BlockPos pos, BlockState old, BlockState newState, CallbackInfo ci) {
        LightManager self = (LightManager) (Object) this;

        BlockLightStorage<?> storage = self.blockLights.get(RayPointLightType.INSTANCE);
        if (!(storage instanceof RayPointLightStorage rayStorage)) {
            return;
        }

        ConcurrentHashMap<?, ?> map = rayStorage.getMap();
        for (Object value : map.values()) {
            if (value instanceof RayPointLight light) {
                light.reload();
            }
        }
    }
}
