package yt.yegla.cs2viewmodel;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ViewmodelConfig {
    public static boolean enabled = true;
    public static float fov = 68.0f;
    public static float size = 1.0f;
    public static float akTilt = 3.0f;
    public static float offsetX = 2.5f;
    public static float offsetY = 0.0f;
    public static float offsetZ = -1.5f;
    public static float sway = 0.5f;
    public static float bob = 0.35f;
    public static float attackMotion = 0.35f;
    public static String akSkin = "default";
    public static String knifeSkin = "default";
    public static String gloveSkin = "default";
    public static String sleeveSkin = "default";
    public static final Placement mainItem = new Placement(0.38f, -25.0f, -20.0f, 0.04f);
    public static final Placement offhandItem = new Placement(0.38f, -25.0f, 20.0f, 0.04f);
    public static final Placement ak = new Placement(1.0f, 0.0f, 0.0f, 0.0f);
    public static final Placement knife = new Placement(1.0f, 0.0f, 0.0f, 0.0f);

    public static final class Placement {
        public float x;
        public float y;
        public float z;
        public float size;
        public float pitch;
        public float yaw;
        public float roll;
        private final float defaultYaw;
        private final float defaultPitch;
        private final float defaultSize;
        private final float defaultY;

        private Placement(float defaultSize, float defaultPitch, float defaultYaw, float defaultY) {
            this.defaultSize = defaultSize;
            this.defaultPitch = defaultPitch;
            this.defaultYaw = defaultYaw;
            this.defaultY = defaultY;
            reset();
        }

        private void reset() {
            x = 0.0f;
            y = defaultY;
            z = 0.0f;
            size = defaultSize;
            pitch = defaultPitch;
            yaw = defaultYaw;
            roll = 0.0f;
        }
    }

    private ViewmodelConfig() {}

    public static void reset() {
        resetControls();
        akSkin = "default";
        knifeSkin = "default";
        gloveSkin = "default";
        sleeveSkin = "default";
    }

    public static void resetControls() {
        enabled = true;
        fov = 68.0f;
        size = 1.0f;
        akTilt = 3.0f;
        offsetX = 2.5f;
        offsetY = 0.0f;
        offsetZ = -1.5f;
        sway = 0.5f;
        bob = 0.35f;
        attackMotion = 0.35f;
        mainItem.reset();
        offhandItem.reset();
        ak.reset();
        knife.reset();
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("cs2-viewmodel.properties");
        reset();
        Properties properties = new Properties();
        if (Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                properties.load(in);
            } catch (IOException e) {
                System.err.println("[CS2 Viewmodel] Could not read config: " + e.getMessage());
            }
        }

        enabled = Boolean.parseBoolean(properties.getProperty("enabled", "true"));
        fov = number(properties, "viewmodel_fov", 68.0f, 10.0f, 170.0f);
        size = number(properties, "viewmodel_size", 1.0f, 0.01f, 10.0f);
        akTilt = number(properties, "ak_tilt", 3.0f, -180.0f, 180.0f);
        offsetX = number(properties, "viewmodel_offset_x", 2.5f, -25.0f, 25.0f);
        offsetY = number(properties, "viewmodel_offset_y", 0.0f, -25.0f, 25.0f);
        offsetZ = number(properties, "viewmodel_offset_z", -1.5f, -25.0f, 25.0f);
        sway = number(properties, "sway", 0.5f, 0.0f, 10.0f);
        bob = number(properties, "bob", 0.35f, 0.0f, 10.0f);
        attackMotion = number(properties, "attack_motion", 0.35f, 0.0f, 10.0f);
        akSkin = SkinCatalog.find(SkinCatalog.Kind.AK, properties.getProperty("ak_skin", "default")).id();
        knifeSkin = SkinCatalog.find(SkinCatalog.Kind.KNIFE, properties.getProperty("knife_skin", "default")).id();
        gloveSkin = SkinCatalog.find(SkinCatalog.Kind.GLOVE, properties.getProperty("glove_skin", "default")).id();
        sleeveSkin = SkinCatalog.find(SkinCatalog.Kind.SLEEVE, properties.getProperty("sleeve_skin", "default")).id();
        float legacyItemSize = number(properties, "held_item_size", 0.38f, 0.01f, 10.0f);
        float legacyOffhandX = number(properties, "offhand_offset", 0.0f, -50.0f, 50.0f) * 0.1f;
        loadItem(properties, "main_item", mainItem, legacyItemSize, 0.0f);
        loadItem(properties, "offhand_item", offhandItem, legacyItemSize, legacyOffhandX);
        loadItem(properties, "ak", ak, 1.0f, 0.0f);
        loadItem(properties, "knife", knife, 1.0f, 0.0f);

        if (!Files.exists(path)) {
            save();
        }
    }

    public static void save() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("cs2-viewmodel.properties");
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        properties.setProperty("viewmodel_fov", Float.toString(fov));
        properties.setProperty("viewmodel_size", Float.toString(size));
        properties.setProperty("ak_tilt", Float.toString(akTilt));
        properties.setProperty("viewmodel_offset_x", Float.toString(offsetX));
        properties.setProperty("viewmodel_offset_y", Float.toString(offsetY));
        properties.setProperty("viewmodel_offset_z", Float.toString(offsetZ));
        properties.setProperty("sway", Float.toString(sway));
        properties.setProperty("bob", Float.toString(bob));
        properties.setProperty("attack_motion", Float.toString(attackMotion));
        properties.setProperty("ak_skin", akSkin);
        properties.setProperty("knife_skin", knifeSkin);
        properties.setProperty("glove_skin", gloveSkin);
        properties.setProperty("sleeve_skin", sleeveSkin);
        saveItem(properties, "main_item", mainItem);
        saveItem(properties, "offhand_item", offhandItem);
        saveItem(properties, "ak", ak);
        saveItem(properties, "knife", knife);
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path)) {
                properties.store(out, "CS2 viewmodel settings; editable in-game from the pause menu.");
            }
        } catch (IOException e) {
            System.err.println("[CS2 Viewmodel] Could not save config: " + e.getMessage());
        }
    }

    private static void loadItem(Properties properties, String prefix, Placement placement,
                                 float legacySize, float legacyX) {
        placement.x = number(properties, prefix + "_x", legacyX, -5.0f, 5.0f);
        placement.y = number(properties, prefix + "_y", placement.defaultY, -5.0f, 5.0f);
        placement.z = number(properties, prefix + "_z", 0.0f, -5.0f, 5.0f);
        placement.size = number(properties, prefix + "_size", legacySize, 0.01f, 10.0f);
        placement.pitch = number(properties, prefix + "_pitch", placement.defaultPitch, -360.0f, 360.0f);
        placement.yaw = number(properties, prefix + "_yaw", placement.defaultYaw, -360.0f, 360.0f);
        placement.roll = number(properties, prefix + "_roll", 0.0f, -360.0f, 360.0f);
    }

    private static void saveItem(Properties properties, String prefix, Placement placement) {
        properties.setProperty(prefix + "_x", Float.toString(placement.x));
        properties.setProperty(prefix + "_y", Float.toString(placement.y));
        properties.setProperty(prefix + "_z", Float.toString(placement.z));
        properties.setProperty(prefix + "_size", Float.toString(placement.size));
        properties.setProperty(prefix + "_pitch", Float.toString(placement.pitch));
        properties.setProperty(prefix + "_yaw", Float.toString(placement.yaw));
        properties.setProperty(prefix + "_roll", Float.toString(placement.roll));
    }

    private static float number(Properties properties, String key, float fallback, float minimum, float maximum) {
        try {
            float value = Float.parseFloat(properties.getProperty(key, Float.toString(fallback)));
            if (!Float.isFinite(value)) return fallback;
            return Math.max(minimum, Math.min(maximum, value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
