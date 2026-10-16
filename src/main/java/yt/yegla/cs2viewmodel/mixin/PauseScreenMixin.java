package yt.yegla.cs2viewmodel.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yt.yegla.cs2viewmodel.ViewmodelSettingsScreen;
import yt.yegla.cs2viewmodel.SkinchangerScreen;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin() {
        super(Component.empty());
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void cs2viewmodel$addSettingsButton(CallbackInfo ci) {
        addRenderableWidget(Button.builder(Component.literal("Viewmodel"),
                button -> minecraft.setScreenAndShow(new ViewmodelSettingsScreen(this)))
                .bounds(width - 158, 8, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Skinchanger"),
                button -> minecraft.setScreenAndShow(new SkinchangerScreen(this)))
                .bounds(width - 158, 32, 150, 20).build());
    }
}
