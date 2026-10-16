package yt.yegla.cs2viewmodel;

import net.minecraft.resources.Identifier;

public final class SkinCatalog {
    public enum Kind { AK, KNIFE, GLOVE, SLEEVE }

    public record Skin(String id, String label, Identifier texture) {
        private Skin(String id, String label, String path) {
            this(id, label, Identifier.fromNamespaceAndPath("cs2viewmodel", "textures/" + path));
        }
    }

    private static final Skin[] AK = {
            new Skin("default", "Default", "ak47_2.png"),
            new Skin("porcelain", "Inheritance", "skins/ak_porcelain.png"),
            new Skin("bus", "Outsiders", "skins/ak_bus.png"),
            new Skin("explosive", "Searing Rage", "skins/ak_explosive.png"),
            new Skin("crane", "Crane Flight", "skins/ak_crane.png")
    };
    private static final Skin[] KNIFE = {
            new Skin("default", "Default", "knife_0.png"),
            new Skin("karambit", "Karambit | Vanilla", "variants/knife_karambit_0.png"),
            new Skin("bayonet", "Bayonet | Vanilla", "variants/knife_bayonet_0.png"),
            new Skin("butterfly", "Butterfly | Vanilla", "variants/knife_butterfly_0.png")
    };
    private static final Skin[] GLOVE = {
            new Skin("default", "T", "arms_ak_1.png"),
            new Skin("fullfinger", "Alt T", "variants/glove_fullfinger_0.png"),
            new Skin("hardknuckle", "CT", "variants/glove_hardknuckle_0.png"),
            new Skin("specialist", "Specialist | Crimson Kimono", "variants/glove_specialist_0.png"),
            new Skin("sporty", "Sport | Blaze", "variants/glove_sporty_0.png")
    };
    private static final Skin[] SLEEVE = {
            new Skin("default", "T Default", "arms_ak_0.png"),
            new Skin("fbi", "Vertigo T", "variants/sleeve_fbi.png"),
            new Skin("sas", "SAS", "variants/sleeve_sas.png"),
            new Skin("balkan", "Balkan", "variants/sleeve_balkan.png")
    };

    private SkinCatalog() {}

    public static Skin[] skins(Kind kind) {
        return switch (kind) {
            case AK -> AK;
            case KNIFE -> KNIFE;
            case GLOVE -> GLOVE;
            case SLEEVE -> SLEEVE;
        };
    }

    public static Skin find(Kind kind, String id) {
        for (Skin skin : skins(kind)) {
            if (skin.id().equals(id)) return skin;
        }
        return skins(kind)[0];
    }

    public static Identifier preview(Kind kind, Skin skin) {
        return Identifier.fromNamespaceAndPath("cs2viewmodel",
                "textures/gui/skins/" + kind.name().toLowerCase(java.util.Locale.ROOT)
                        + "_" + skin.id() + ".png");
    }

    public static String step(Kind kind, String current, int direction) {
        Skin[] options = skins(kind);
        for (int i = 0; i < options.length; i++) {
            if (options[i].id().equals(current)) {
                return options[Math.floorMod(i + direction, options.length)].id();
            }
        }
        return options[0].id();
    }
}
