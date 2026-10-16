package yt.yegla.cs2viewmodel;

import net.fabricmc.api.ClientModInitializer;

public final class Cs2ViewmodelClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ViewmodelConfig.load();
        System.out.println("[CS2 Viewmodel] CS2 arm mesh " + (ImportedMeshes.hasArms() ? "loaded" : "not found; using vanilla arms"));
    }
}
