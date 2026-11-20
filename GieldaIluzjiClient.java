package pl.anarchia.gieldailuzji.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import pl.anarchia.gieldailuzji.network.OpenMarketPayload;

/** Klawisz P (do zmiany w Opcje -> Sterowanie) otwiera Giełdę Iluzji. */
public class GieldaIluzjiClient implements ClientModInitializer {
    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.gielda_iluzji.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, "category.gielda_iluzji"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.player != null && client.currentScreen == null) {
                    ClientPlayNetworking.send(new OpenMarketPayload());
                }
            }
        });
    }
}
