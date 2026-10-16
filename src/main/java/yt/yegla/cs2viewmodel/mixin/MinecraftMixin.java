package yt.yegla.cs2viewmodel.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yt.yegla.cs2viewmodel.ImportedMeshes;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "run", at = @At("HEAD"))
    private void cs2viewmodel$preloadTextures(CallbackInfo ci) {
        ImportedMeshes.preloadTextures((Minecraft) (Object) this);
    }
}
