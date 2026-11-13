package com.blockesp;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class BlockEspClient implements ClientModInitializer {
    public static KeyMapping openKey;

    @Override
    public void onInitializeClient() {
        Config.load();

        KeyMapping.Category category = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("blockesp", "main"));

        openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.blockesp.open", InputConstants.Type.KEYSYM, InputConstants.KEY_F6, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) {
                if (client.screen == null && client.level != null) {
                    client.setScreen(new EspScreen());
                }
            }
            Scanner.tick(client);
        });

        EspRenderer.init();
    }
}
