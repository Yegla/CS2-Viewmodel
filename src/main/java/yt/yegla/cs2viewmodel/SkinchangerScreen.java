package yt.yegla.cs2viewmodel;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Image based, CS2 inspired inventory for viewmodel finishes. */
public final class SkinchangerScreen extends Screen {
    private static final SkinCatalog.Kind[] KINDS = SkinCatalog.Kind.values();
    private static final int GAP = 6;
    private static final int CARD_HEIGHT = 70;
    private final Screen parent;
    private SkinCatalog.Kind active = SkinCatalog.Kind.AK;
    private int page;
    private int left, top, cardWidth, columns, pageSize;

    public SkinchangerScreen(Screen parent) {
        super(Component.literal("Inventory"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        top = 53;
        int available = Math.max(180, width - 40);
        columns = Math.min(5, Math.max(2, (available + GAP) / (88 + GAP)));
        cardWidth = Math.min(132, (available - (columns - 1) * GAP) / columns);
        left = (width - columns * cardWidth - (columns - 1) * GAP) / 2;
        int rows = Math.max(1, (height - top - 35 + GAP) / (CARD_HEIGHT + GAP));
        pageSize = columns * rows;
        SkinCatalog.Skin[] skins = SkinCatalog.skins(active);
        page = Math.min(page, (skins.length - 1) / pageSize);

        int tabWidth = Math.min(84, (width - 40 - 3 * GAP) / 4);
        int tabLeft = (width - (tabWidth * 4 + 3 * GAP)) / 2;
        for (int i = 0; i < KINDS.length; i++) {
            SkinCatalog.Kind kind = KINDS[i];
            addRenderableWidget(Button.builder(Component.empty(), button -> {
                active = kind;
                page = 0;
                rebuildWidgets();
            }).bounds(tabLeft + i * (tabWidth + GAP), 27, tabWidth, 19).build());
        }

        int end = Math.min(skins.length, (page + 1) * pageSize);
        for (int i = page * pageSize; i < end; i++) {
            SkinCatalog.Skin skin = skins[i];
            int slot = i - page * pageSize;
            int x = left + (slot % columns) * (cardWidth + GAP);
            int y = top + (slot / columns) * (CARD_HEIGHT + GAP);
            addRenderableWidget(Button.builder(Component.empty(), button -> {
                select(active, skin.id());
                ViewmodelConfig.save();
                rebuildWidgets();
            }).bounds(x, y, cardWidth, CARD_HEIGHT).build());
        }

        int footer = height - 25;
        addRenderableWidget(Button.builder(Component.literal("Reset all"), button -> {
            for (SkinCatalog.Kind kind : KINDS) select(kind, "default");
            ViewmodelConfig.save();
            rebuildWidgets();
        }).bounds(left, footer, 70, 20).build());
        if (skins.length > pageSize) {
            addRenderableWidget(Button.builder(Component.literal("<"), button -> {
                page = Math.floorMod(page - 1, (skins.length - 1) / pageSize + 1);
                rebuildWidgets();
            }).bounds(width / 2 - 32, footer, 22, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), button -> {
                page = (page + 1) % ((skins.length - 1) / pageSize + 1);
                rebuildWidgets();
            }).bounds(width / 2 + 10, footer, 22, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(width - left - 70, footer, 70, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 8, 0xFFF0F2F4);
        graphics.centeredText(font, Component.literal("SELECT A FINISH"), width / 2, 18, 0xFF9BA8B4);

        int tabWidth = Math.min(84, (width - 40 - 3 * GAP) / 4);
        int tabLeft = (width - (tabWidth * 4 + 3 * GAP)) / 2;
        for (int i = 0; i < KINDS.length; i++) {
            int x = tabLeft + i * (tabWidth + GAP);
            boolean selected = active == KINDS[i];
            graphics.fill(x, 27, x + tabWidth, 46, selected ? 0xFF364552 : 0xFF202A33);
            if (selected) graphics.fill(x, 44, x + tabWidth, 46, 0xFFDAA44E);
            graphics.centeredText(font, Component.literal(label(KINDS[i])), x + tabWidth / 2,
                    32, selected ? 0xFFFFE9BD : 0xFFC0C9D0);
        }

        SkinCatalog.Skin[] skins = SkinCatalog.skins(active);
        int end = Math.min(skins.length, (page + 1) * pageSize);
        int visibleRows = Math.max(1, (end - page * pageSize + columns - 1) / columns);
        graphics.fill(left - 5, top - 5, left + columns * cardWidth + (columns - 1) * GAP + 5,
                top + visibleRows * (CARD_HEIGHT + GAP) - GAP + 5, 0xD8182029);
        for (int i = page * pageSize; i < end; i++) {
            SkinCatalog.Skin skin = skins[i];
            int slot = i - page * pageSize;
            int x = left + (slot % columns) * (cardWidth + GAP);
            int y = top + (slot / columns) * (CARD_HEIGHT + GAP);
            boolean selected = selected(active).equals(skin.id());
            boolean hovered = mouseX >= x && mouseX < x + cardWidth && mouseY >= y && mouseY < y + CARD_HEIGHT;
            graphics.fill(x, y, x + cardWidth, y + CARD_HEIGHT,
                    selected ? 0xFF8E7040 : hovered ? 0xFF506170 : 0xFF35424D);
            graphics.fill(x + 2, y + 2, x + cardWidth - 2, y + CARD_HEIGHT - 2, 0xFF202B35);
            // Minecraft 26.3 takes corners, then UVs in u0,u1,v0,v1 order.
            graphics.blit(SkinCatalog.preview(active, skin), x + 3, y + 2,
                    x + cardWidth - 3, y + CARD_HEIGHT - 18, 0f, 1f, 0f, 1f);
            graphics.fill(x + 2, y + CARD_HEIGHT - 16, x + cardWidth - 2, y + CARD_HEIGHT - 2,
                    selected ? 0xFF8E7040 : 0xFF273640);
            String fullLabel = skin.label();
            int chars = fullLabel.length();
            if (font.width(fullLabel) > cardWidth - 8) {
                while (chars > 1 && font.width(fullLabel.substring(0, chars) + "…") > cardWidth - 8) chars--;
            }
            String text = chars < fullLabel.length() ? fullLabel.substring(0, chars) + "…" : fullLabel;
            graphics.centeredText(font, Component.literal(text), x + cardWidth / 2,
                    y + CARD_HEIGHT - 14, selected ? 0xFFFFFFFF : 0xFFD3DCE3);
        }
        if (skins.length > pageSize) {
            graphics.centeredText(font, Component.literal((page + 1) + " / "
                    + ((skins.length - 1) / pageSize + 1)), width / 2, height - 19, 0xFFCAD4DC);
        }
    }

    private static String label(SkinCatalog.Kind kind) {
        return switch (kind) {
            case AK -> "AK-47";
            case KNIFE -> "KNIVES";
            case GLOVE -> "GLOVES";
            case SLEEVE -> "SLEEVES";
        };
    }

    private static String selected(SkinCatalog.Kind kind) {
        return switch (kind) {
            case AK -> ViewmodelConfig.akSkin;
            case KNIFE -> ViewmodelConfig.knifeSkin;
            case GLOVE -> ViewmodelConfig.gloveSkin;
            case SLEEVE -> ViewmodelConfig.sleeveSkin;
        };
    }

    private static void select(SkinCatalog.Kind kind, String id) {
        switch (kind) {
            case AK -> ViewmodelConfig.akSkin = id;
            case KNIFE -> ViewmodelConfig.knifeSkin = id;
            case GLOVE -> ViewmodelConfig.gloveSkin = id;
            case SLEEVE -> ViewmodelConfig.sleeveSkin = id;
        }
    }

    @Override
    public void removed() {
        ViewmodelConfig.save();
        super.removed();
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }
}
