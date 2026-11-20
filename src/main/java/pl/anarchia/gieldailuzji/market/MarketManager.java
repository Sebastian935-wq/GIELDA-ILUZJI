package pl.anarchia.gieldailuzji.market;

import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import pl.anarchia.gieldailuzji.config.FakeItemDef;
import pl.anarchia.gieldailuzji.config.ModConfig;
import pl.anarchia.gieldailuzji.util.TextUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class MarketManager {
    private MarketManager() {}

    public static void open(ServerPlayerEntity player) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inv, p) -> new MarketScreenHandler(syncId, inv, player),
                TextUtil.gradient("Giełda Iluzji", "#FFAA00", "#FF5555", true)));
    }

    public static List<MarketOffer> generate(MarketScreenHandler.Mode mode) {
        ModConfig cfg = ModConfig.get();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<MarketOffer> out = new ArrayList<>();
        if (mode == MarketScreenHandler.Mode.TRUTH) {
            for (FakeItemDef d : cfg.items) {
                if (d.truthPrice > 0) out.add(new MarketOffer(d, false, "Giełda Prawdy", d.truthPrice));
            }
            return out;
        }
        if (cfg.items.isEmpty()) return out;
        int n = Math.max(1, Math.min(45, cfg.offersInMarket));
        for (int i = 0; i < n; i++) {
            FakeItemDef d = cfg.items.get(r.nextInt(cfg.items.size()));
            boolean illusion = r.nextDouble() < d.illusionChance;
            String seller = cfg.sellers.get(r.nextInt(cfg.sellers.size()));
            out.add(new MarketOffer(d, illusion, seller, d.price));
        }
        return out;
    }
}
