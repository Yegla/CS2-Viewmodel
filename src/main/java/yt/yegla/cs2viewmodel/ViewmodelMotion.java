package yt.yegla.cs2viewmodel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

public final class ViewmodelMotion {
    private static long lastSampleNanos;
    private static float lastYaw;
    private static float lastPitch;
    private static float smoothYaw;
    private static float smoothPitch;
    private static float phase;

    private ViewmodelMotion() {}

    public static void apply(PoseStack pose, FirstPersonHandsAndItemsRenderState state,
                             float attack, float partialTicks) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        long now = System.nanoTime();
        float delta = lastSampleNanos == 0 ? 0.016f : Mth.clamp((now - lastSampleNanos) / 1_000_000_000.0f, 0.0f, 0.05f);
        if (lastSampleNanos == 0 || delta >= 0.05f) {
            lastYaw = state.viewYRot;
            lastPitch = state.viewXRot;
        }
        lastSampleNanos = now;

        float yawStep = Mth.clamp(Mth.wrapDegrees(state.viewYRot - lastYaw), -12.0f, 12.0f);
        float pitchStep = Mth.clamp(state.viewXRot - lastPitch, -12.0f, 12.0f);
        lastYaw = state.viewYRot;
        lastPitch = state.viewXRot;
        float smoothing = 1.0f - (float) Math.exp(-delta * 15.0f);
        smoothYaw += (yawStep - smoothYaw) * smoothing;
        smoothPitch += (pitchStep - smoothPitch) * smoothing;

        float side = client.player.getMainArm() == HumanoidArm.LEFT ? -1.0f : 1.0f;

        // CS2 console coordinates: X is sideways, Y is depth, Z is height.
        float x = side * ((ViewmodelConfig.offsetX - 1.0f) * 0.065f);
        float y = ViewmodelConfig.offsetZ * 0.075f;
        float z = -0.08f - ViewmodelConfig.offsetY * 0.06f;

        float speed = (float) client.player.getDeltaMovement().horizontalDistance();
        phase += delta * (3.0f + Math.min(speed, 0.4f) * 32.0f);
        float movement = Math.min(speed * 4.0f, 1.0f) * ViewmodelConfig.bob;
        x += (float) Math.sin(phase) * 0.025f * movement;
        y += (float) Math.abs(Math.cos(phase)) * 0.018f * movement;
        x -= smoothYaw * ViewmodelConfig.sway * 0.012f;
        y += smoothPitch * ViewmodelConfig.sway * 0.009f;

        float hit = (float) Math.sin(Math.sqrt(Math.max(attack, 0.0f)) * Math.PI);
        y -= hit * ViewmodelConfig.attackMotion * 0.045f;
        z += hit * ViewmodelConfig.attackMotion * 0.025f;

        pose.translate(x, y, z);
        pose.rotateDegrees(Axis.XP, -smoothPitch * ViewmodelConfig.sway * 0.65f - hit * ViewmodelConfig.attackMotion * 7.0f);
        pose.rotateDegrees(Axis.YP, -smoothYaw * ViewmodelConfig.sway * 0.8f);
        pose.rotateDegrees(Axis.ZP, side * -7.0f);

        // An independent viewmodel FOV is approximated by scaling this render pass.
        float fovScale = ViewmodelConfig.size * 0.9f * (float) (Math.tan(Math.toRadians(34.0)) / Math.tan(Math.toRadians(ViewmodelConfig.fov / 2.0)));
        pose.scale(fovScale, fovScale, fovScale);
    }
}
