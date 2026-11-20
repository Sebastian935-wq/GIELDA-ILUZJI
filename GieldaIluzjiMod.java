package pl.anarchia.gieldailuzji;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.anarchia.gieldailuzji.command.MarketCommand;
import pl.anarchia.gieldailuzji.config.ModConfig;
import pl.anarchia.gieldailuzji.event.FakeEventManager;
import pl.anarchia.gieldailuzji.item.AbilityHandler;
import pl.anarchia.gieldailuzji.market.MarketManager;
import pl.anarchia.gieldailuzji.network.OpenMarketPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class GieldaIluzjiMod implements ModInitializer {
    public static final String MOD_ID = "gielda_iluzji";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModConfig.load();
        AbilityHandler.register();
        FakeEventManager.get().register();
        MarketCommand.register();

        // Klawisz P na kliencie -> pakiet -> otwarcie GUI na serwerze
        PayloadTypeRegistry.playC2S().register(OpenMarketPayload.ID, OpenMarketPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(OpenMarketPayload.ID,
                (payload, context) -> MarketManager.open(context.player()));
        LOGGER.info("Giełda Iluzji załadowana. Uważaj na oscamy!");
    }
}
