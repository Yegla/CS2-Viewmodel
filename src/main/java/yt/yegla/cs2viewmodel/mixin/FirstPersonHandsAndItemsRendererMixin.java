package yt.yegla.cs2viewmodel.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import yt.yegla.cs2viewmodel.ViewmodelConfig;
import yt.yegla.cs2viewmodel.ImportedMeshes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsAndItemsRendererMixin {
    @Inject(method = "submitHandsWithItems", at = @At("HEAD"))
    private void cs2viewmodel$submitMeshes(float partialTicks, PoseStack pose, SubmitNodeCollector collector,
                                          PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state,
                                          CallbackInfo ci) {
        if (ViewmodelConfig.enabled && ImportedMeshes.hasArms() && playerState.avatarRenderState != null
                && !playerState.avatarRenderState.isInvisible) {
            ImportedMeshes.submit(pose, collector, playerState, state, partialTicks);
        }
    }

    @Inject(method = "renderPlayerArm", at = @At("HEAD"), cancellable = true)
    private void cs2viewmodel$hideVanillaArm(PoseStack pose, SubmitNodeCollector collector, int light,
                                             float inverseArmHeight, float attack, HumanoidArm arm,
                                             PlayerRenderState playerState, CallbackInfo ci) {
        if (ViewmodelConfig.enabled && ImportedMeshes.hasArms()) ci.cancel();
    }

    @Inject(method = "renderPlayerHand", at = @At("HEAD"), cancellable = true)
    private void cs2viewmodel$hideVanillaHand(PoseStack pose, SubmitNodeCollector collector, int light,
                                              HumanoidArm arm, PlayerRenderState playerState, CallbackInfo ci) {
        if (ViewmodelConfig.enabled && ImportedMeshes.hasArms()) ci.cancel();
    }

    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void cs2viewmodel$before(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state,
                                    float partialTicks, float xRot, InteractionHand hand, float attack,
                                    ItemStack itemStack, float inverseArmHeight, PoseStack pose,
                                    SubmitNodeCollector collector, int lightCoords, CallbackInfo ci) {
        if (ViewmodelConfig.enabled && ImportedMeshes.hasArms() && !state.isScoping
                && playerState.avatarRenderState != null && !playerState.avatarRenderState.isInvisible
                && !itemStack.has(DataComponents.MAP_ID)) {
            ci.cancel();
        }
    }
}
