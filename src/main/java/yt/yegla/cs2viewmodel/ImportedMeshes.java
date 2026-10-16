package yt.yegla.cs2viewmodel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public final class ImportedMeshes {
    private static final Mesh ARMS_AK = load("arms_ak");
    private static final Mesh ARMS_KNIFE = load("arms_knife");
    private static final Mesh AK47 = load("ak47");
    private static final Mesh KNIFE = load("knife");
    private static final Mesh KNIFE_KARAMBIT = load("knife_karambit");
    private static final Mesh KNIFE_BAYONET = load("knife_bayonet");
    private static final Mesh KNIFE_BUTTERFLY = load("knife_butterfly");
    private static final Mesh[][] GLOVE_ARMS = {
            { load("arms_ak_glove_fullfinger"), load("arms_knife_glove_fullfinger") },
            { load("arms_ak_glove_hardknuckle"), load("arms_knife_glove_hardknuckle") },
            { load("arms_ak_glove_specialist"), load("arms_knife_glove_specialist") },
            { load("arms_ak_glove_sporty"), load("arms_knife_glove_sporty") }
    };
    private static final Mesh[][] SLEEVES = {
            { load("sleeve_ak_fbi"), load("sleeve_knife_fbi") },
            { load("sleeve_ak_sas"), load("sleeve_knife_sas") },
            { load("sleeve_ak_balkan"), load("sleeve_knife_balkan") }
    };
    private static final Set<Identifier> REGISTERED_TEXTURES = new HashSet<>();

    private ImportedMeshes() {}

    public static boolean hasArms() {
        return ARMS_AK != null && ARMS_KNIFE != null;
    }

    public static void preloadTextures(Minecraft minecraft) {
        for (Mesh mesh : new Mesh[] { ARMS_AK, ARMS_KNIFE, AK47, KNIFE }) {
            if (mesh == null) continue;
            for (Part part : mesh.parts) registerTexture(part.texture, minecraft);
        }
        for (Mesh mesh : new Mesh[] { KNIFE_KARAMBIT, KNIFE_BAYONET, KNIFE_BUTTERFLY }) {
            if (mesh != null) for (Part part : mesh.parts) registerTexture(part.texture, minecraft);
        }
        for (Mesh[] pair : GLOVE_ARMS) for (Mesh mesh : pair) {
            if (mesh != null) for (Part part : mesh.parts) registerTexture(part.texture, minecraft);
        }
        for (Mesh[] pair : SLEEVES) for (Mesh mesh : pair) {
            if (mesh != null) for (Part part : mesh.parts) registerTexture(part.texture, minecraft);
        }
        for (SkinCatalog.Kind kind : SkinCatalog.Kind.values()) {
            for (SkinCatalog.Skin skin : SkinCatalog.skins(kind)) {
                registerTexture(skin.texture(), minecraft);
                registerTexture(SkinCatalog.preview(kind, skin), minecraft);
            }
        }
    }

    public static boolean replaces(ItemStack stack) {
        return replacement(stack) != null;
    }

    public static void submit(PoseStack pose, SubmitNodeCollector collector, PlayerRenderState playerState,
                              FirstPersonHandsAndItemsRenderState state, float partialTicks) {
        if (!hasArms() || state.isScoping) return;

        pose.pushPose();
        float attack = playerState.avatarRenderState.swingAnimation;
        ViewmodelMotion.apply(pose, state, attack, partialTicks);
        int light = playerState.avatarRenderState.lightCoords;
        Mesh mainWeapon = state.handRenderSelection.renderMainHand ? replacement(state.mainHandItem) : null;
        boolean akPose = mainWeapon == AK47;
        ViewmodelConfig.Placement weaponPlacement = akPose ? ViewmodelConfig.ak
                : isKnife(mainWeapon) ? ViewmodelConfig.knife : null;
        if (weaponPlacement != null) {
            // Rotate and size around the grip. Both CS2 arms follow the weapon,
            // and its position sliders remain useful even at large rotations.
            float gripX = akPose ? 0.32f : 0.29f;
            float gripY = akPose ? -0.27f : -0.34f;
            float gripZ = akPose ? -0.53f : -0.69f;
            pose.translate(weaponPlacement.x + gripX, weaponPlacement.y + gripY,
                    weaponPlacement.z + gripZ);
            pose.rotateDegrees(Axis.YP, weaponPlacement.yaw);
            pose.rotateDegrees(Axis.XP, weaponPlacement.pitch);
            pose.rotateDegrees(Axis.ZP, weaponPlacement.roll);
            pose.scale(weaponPlacement.size, weaponPlacement.size, weaponPlacement.size);
            pose.translate(-gripX, -gripY, -gripZ);
        }
        if (akPose && ViewmodelConfig.akTilt != 0.0f) {
            // Pivot near the pistol grip so the weapon and both hands retain
            // their contact points while the muzzle angle changes.
            pose.translate(0.32f, -0.27f, -0.53f);
            pose.rotateDegrees(Axis.ZP, ViewmodelConfig.akTilt);
            pose.translate(-0.32f, 0.27f, 0.53f);
        }
        Mesh arms = selectedArms(akPose);
        render(arms, pose, collector, light);
        Mesh sleeves = selectedSleeves(akPose);
        if (sleeves != null) render(sleeves, pose, collector, light);
        if (mainWeapon != null) {
            render(mainWeapon, pose, collector, light);
        } else if (state.handRenderSelection.renderMainHand && !state.mainHandItem.isEmpty()
                && !state.mainHandItem.has(DataComponents.MAP_ID)) {
            submitHeldItem(pose, collector, state.mainHandRenderState, InteractionHand.MAIN_HAND, akPose, light);
        }
        if (state.handRenderSelection.renderOffHand && !state.offHandItem.isEmpty()
                && !state.offHandItem.has(DataComponents.MAP_ID)) {
            submitHeldItem(pose, collector, state.offHandRenderState, InteractionHand.OFF_HAND, akPose, light);
        }
        pose.popPose();
    }

    private static void submitHeldItem(PoseStack pose, SubmitNodeCollector collector, ItemStackRenderState item,
                                       InteractionHand hand, boolean akPose, int light) {
        if (item.isEmpty()) return;
        pose.pushPose();
        ViewmodelConfig.Placement placement = hand == InteractionHand.MAIN_HAND
                ? ViewmodelConfig.mainItem : ViewmodelConfig.offhandItem;
        if (hand == InteractionHand.MAIN_HAND) {
            pose.translate(0.190f + placement.x, -0.313f + placement.y, -0.689f + placement.z);
        } else {
            pose.translate((akPose ? 0.200f : -0.097f) + placement.x,
                    (akPose ? -0.238f : -0.356f) + placement.y,
                    (akPose ? -0.814f : -0.753f) + placement.z);
        }
        pose.rotateDegrees(Axis.YP, placement.yaw);
        pose.rotateDegrees(Axis.XP, placement.pitch);
        pose.rotateDegrees(Axis.ZP, placement.roll);
        AABB bounds = item.getModelBoundingBox();
        double width = bounds.maxX - bounds.minX;
        double height = bounds.maxY - bounds.minY;
        double depth = bounds.maxZ - bounds.minZ;
        float modelScale = placement.size / (float) Math.max(1.0, Math.max(width, Math.max(height, depth)));
        pose.scale(modelScale, modelScale, modelScale);
        // Baked Minecraft item transforms shift different models in different
        // directions. Anchor their bottom center to the appropriate CS2 glove.
        pose.translate(-(bounds.minX + bounds.maxX) * 0.5, -(bounds.minY + height * 0.12),
                -(bounds.minZ + bounds.maxZ) * 0.5);
        item.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }

    private static Mesh replacement(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (AK47 != null && (stack.is(Items.BOW) || stack.is(Items.CROSSBOW))) return AK47;
        if (KNIFE != null && stack.is(ItemTags.SWORDS)) return selectedKnife();
        return null;
    }

    private static boolean isKnife(Mesh mesh) {
        return mesh == KNIFE || mesh == KNIFE_KARAMBIT || mesh == KNIFE_BAYONET || mesh == KNIFE_BUTTERFLY;
    }

    private static Mesh selectedKnife() {
        return switch (ViewmodelConfig.knifeSkin) {
            case "karambit" -> KNIFE_KARAMBIT != null ? KNIFE_KARAMBIT : KNIFE;
            case "bayonet" -> KNIFE_BAYONET != null ? KNIFE_BAYONET : KNIFE;
            case "butterfly" -> KNIFE_BUTTERFLY != null ? KNIFE_BUTTERFLY : KNIFE;
            default -> KNIFE;
        };
    }

    private static Mesh selectedArms(boolean akPose) {
        int variant = switch (ViewmodelConfig.gloveSkin) {
            case "fullfinger" -> 0;
            case "hardknuckle" -> 1;
            case "specialist" -> 2;
            case "sporty" -> 3;
            default -> -1;
        };
        if (variant >= 0 && GLOVE_ARMS[variant][akPose ? 0 : 1] != null) {
            return GLOVE_ARMS[variant][akPose ? 0 : 1];
        }
        return akPose ? ARMS_AK : ARMS_KNIFE;
    }

    private static Mesh selectedSleeves(boolean akPose) {
        int variant = switch (ViewmodelConfig.sleeveSkin) {
            case "fbi" -> 0;
            case "sas" -> 1;
            case "balkan" -> 2;
            default -> -1;
        };
        return variant < 0 ? null : SLEEVES[variant][akPose ? 0 : 1];
    }

    private static void render(Mesh mesh, PoseStack pose, SubmitNodeCollector collector, int light) {
        for (Part part : mesh.parts) {
            Identifier texture = selectedTexture(part.texture);
            if (!REGISTERED_TEXTURES.contains(texture)) continue;
            collector.submitCustomGeometry(pose, RenderTypes.entityCutout(texture), (currentPose, consumer) -> {
                float[] data = part.vertices;
                for (int i = 0; i < data.length; i += 8) {
                    consumer.addVertex(currentPose, data[i], data[i + 1], data[i + 2])
                            .setColor(255, 255, 255, 255)
                            .setUv(data[i + 3], data[i + 4])
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(light)
                            .setNormal(currentPose, data[i + 5], data[i + 6], data[i + 7]);
                }
            });
        }
    }

    private static Identifier selectedTexture(Identifier base) {
        return switch (base.getPath()) {
            case "textures/ak47_2.png" -> SkinCatalog.find(SkinCatalog.Kind.AK,
                    ViewmodelConfig.akSkin).texture();
            case "textures/knife_0.png" -> SkinCatalog.find(SkinCatalog.Kind.KNIFE,
                    ViewmodelConfig.knifeSkin).texture();
            default -> base;
        };
    }

    private static void registerTexture(Identifier id, Minecraft minecraft) {
        if (REGISTERED_TEXTURES.contains(id)) return;
        String path = "assets/" + id.getNamespace() + "/" + id.getPath();
        try (InputStream stream = ImportedMeshes.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing classpath texture " + path);
            NativeImage pixels = NativeImage.read(stream);
            minecraft.getTextureManager().register(id,
                    new DynamicTexture(() -> "CS2 " + id, pixels));
            REGISTERED_TEXTURES.add(id);
        } catch (IOException e) {
            System.err.println("[CS2 Viewmodel] Could not load texture " + id + ": " + e.getMessage());
        }
    }

    private static Mesh load(String name) {
        String path = "assets/cs2viewmodel/meshes/" + name + ".c2m";
        try (InputStream stream = ImportedMeshes.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) return null;
            DataInputStream input = new DataInputStream(stream);
            if (input.readInt() != 0x43324D31) throw new IOException("Wrong mesh magic");
            int count = input.readInt();
            if (count < 1 || count > 16) throw new IOException("Invalid part count");
            Part[] parts = new Part[count];
            for (int part = 0; part < count; part++) {
                int nameLength = input.readUnsignedShort();
                if (nameLength < 1 || nameLength > 128) throw new IOException("Invalid texture name");
                byte[] nameBytes = input.readNBytes(nameLength);
                if (nameBytes.length != nameLength) throw new IOException("Truncated texture name");
                String textureName = new String(nameBytes, StandardCharsets.UTF_8);
                int vertexCount = input.readInt();
                if (vertexCount < 4 || vertexCount > 250_000 || vertexCount % 4 != 0) {
                    throw new IOException("Invalid vertex count");
                }
                float[] vertices = new float[vertexCount * 8];
                for (int i = 0; i < vertices.length; i++) vertices[i] = input.readFloat();
                parts[part] = new Part(Identifier.fromNamespaceAndPath("cs2viewmodel", "textures/" + textureName), vertices);
            }
            return new Mesh(parts);
        } catch (IOException e) {
            System.err.println("[CS2 Viewmodel] Could not load " + path + ": " + e.getMessage());
            return null;
        }
    }

    private record Mesh(Part[] parts) {}
    private record Part(Identifier texture, float[] vertices) {}
}
