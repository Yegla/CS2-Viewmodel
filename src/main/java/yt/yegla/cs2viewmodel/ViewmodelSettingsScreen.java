package yt.yegla.cs2viewmodel;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.Consumer;

public final class ViewmodelSettingsScreen extends Screen {
    private enum Page { VIEWMODEL, MAIN_ITEM, OFFHAND_ITEM, AK, KNIFE }

    private final Screen parent;
    private Page page;

    public ViewmodelSettingsScreen(Screen parent) {
        this(parent, Page.VIEWMODEL);
    }

    private ViewmodelSettingsScreen(Screen parent, Page page) {
        super(Component.literal("Viewmodel Settings"));
        this.parent = parent;
        this.page = page;
    }

    @Override
    protected void init() {
        int sliderWidth = Math.min(240, width - 32);
        int x = (width - sliderWidth) / 2;
        int top = Math.max(68, (height - 264) / 2 + 68);
        int tabWidth = (sliderWidth - 8) / 3;
        tab(x, top - 50, tabWidth, "Viewmodel", Page.VIEWMODEL);
        tab(x + tabWidth + 4, top - 50, tabWidth, "Main item", Page.MAIN_ITEM);
        tab(x + (tabWidth + 4) * 2, top - 50, tabWidth, "Offhand", Page.OFFHAND_ITEM);
        int weaponTabWidth = (sliderWidth - 4) / 2;
        tab(x, top - 25, weaponTabWidth, "AK-47", Page.AK);
        tab(x + weaponTabWidth + 4, top - 25, weaponTabWidth, "Knife", Page.KNIFE);

        if (page == Page.VIEWMODEL) {
            slider(x, top, sliderWidth, "Position X", -25.0f, 25.0f,
                    ViewmodelConfig.offsetX, value -> ViewmodelConfig.offsetX = value);
            slider(x, top + 20, sliderWidth, "Position Y", -25.0f, 25.0f,
                    ViewmodelConfig.offsetY, value -> ViewmodelConfig.offsetY = value);
            slider(x, top + 40, sliderWidth, "Position Z", -25.0f, 25.0f,
                    ViewmodelConfig.offsetZ, value -> ViewmodelConfig.offsetZ = value);
            slider(x, top + 60, sliderWidth, "Viewmodel size", 0.01f, 10.0f,
                    ViewmodelConfig.size, value -> ViewmodelConfig.size = value);
            slider(x, top + 80, sliderWidth, "AK tilt", -180.0f, 180.0f,
                    ViewmodelConfig.akTilt, value -> ViewmodelConfig.akTilt = value);
            slider(x, top + 100, sliderWidth, "Viewmodel FOV", 10.0f, 170.0f,
                    ViewmodelConfig.fov, value -> ViewmodelConfig.fov = value);
        } else {
            ViewmodelConfig.Placement item = switch (page) {
                case MAIN_ITEM -> ViewmodelConfig.mainItem;
                case OFFHAND_ITEM -> ViewmodelConfig.offhandItem;
                case AK -> ViewmodelConfig.ak;
                case KNIFE -> ViewmodelConfig.knife;
                default -> throw new IllegalStateException("No placement on " + page);
            };
            slider(x, top, sliderWidth, "Size", 0.01f, 10.0f,
                    item.size, value -> item.size = value);
            slider(x, top + 20, sliderWidth, "Position X", -5.0f, 5.0f,
                    item.x, value -> item.x = value);
            slider(x, top + 40, sliderWidth, "Position Y", -5.0f, 5.0f,
                    item.y, value -> item.y = value);
            slider(x, top + 60, sliderWidth, "Position Z", -5.0f, 5.0f,
                    item.z, value -> item.z = value);
            slider(x, top + 80, sliderWidth, "Pitch", -360.0f, 360.0f,
                    item.pitch, value -> item.pitch = value);
            slider(x, top + 100, sliderWidth, "Yaw", -360.0f, 360.0f,
                    item.yaw, value -> item.yaw = value);
            slider(x, top + 120, sliderWidth, "Roll", -360.0f, 360.0f,
                    item.roll, value -> item.roll = value);
        }

        int buttonWidth = (sliderWidth - 8) / 3;
        int buttonY = top + 144;
        addRenderableWidget(Button.builder(enabledLabel(), button -> {
            ViewmodelConfig.enabled = !ViewmodelConfig.enabled;
            button.setMessage(enabledLabel());
        }).bounds(x, buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset"), button -> {
            ViewmodelConfig.resetControls();
            rebuildWidgets();
        }).bounds(x + buttonWidth + 4, buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(x + (buttonWidth + 4) * 2, buttonY, buttonWidth, 20).build());
    }

    private void tab(int x, int y, int width, String label, Page target) {
        Button button = Button.builder(Component.literal(label),
                ignored -> {
                    page = target;
                    rebuildWidgets();
                })
                .bounds(x, y, width, 20).build();
        button.active = page != target;
        addRenderableWidget(button);
    }

    private void slider(int x, int y, int width, String label, float minimum, float maximum,
                        float current, Consumer<Float> setter) {
        addRenderableWidget(new ValueSlider(x, y, width, label, minimum, maximum, current, setter));
    }

    private static Component enabledLabel() {
        return Component.literal("Enabled: " + (ViewmodelConfig.enabled ? "Yes" : "No"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 8, 0xFFFFFF);
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

    private static final class ValueSlider extends AbstractSliderButton {
        private final String label;
        private final float minimum;
        private final float maximum;
        private final Consumer<Float> setter;

        private ValueSlider(int x, int y, int width, String label, float minimum, float maximum,
                            float current, Consumer<Float> setter) {
            super(x, y, width, 20, Component.empty(), encode(current, minimum, maximum));
            this.label = label;
            this.minimum = minimum;
            this.maximum = maximum;
            this.setter = setter;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            float current = decode(value, minimum, maximum);
            setMessage(Component.literal(String.format(Locale.ROOT, "%s: %.2f", label, current)));
        }

        @Override
        protected void applyValue() {
            setter.accept(decode(value, minimum, maximum));
            updateMessage();
        }

        private static double encode(float current, float minimum, float maximum) {
            if (minimum > 0.0f && maximum / minimum >= 20.0f) {
                return Math.log(current / minimum) / Math.log(maximum / minimum);
            }
            if (minimum == -maximum) {
                return 0.5 + 0.5 * Math.cbrt(current / maximum);
            }
            return (current - minimum) / (maximum - minimum);
        }

        private static float decode(double slider, float minimum, float maximum) {
            if (minimum > 0.0f && maximum / minimum >= 20.0f) {
                return (float) (minimum * Math.pow(maximum / minimum, slider));
            }
            if (minimum == -maximum) {
                double centered = slider * 2.0 - 1.0;
                return (float) (maximum * centered * centered * centered);
            }
            return minimum + (float) slider * (maximum - minimum);
        }
    }
}
