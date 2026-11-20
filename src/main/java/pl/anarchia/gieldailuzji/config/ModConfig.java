package pl.anarchia.gieldailuzji.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import pl.anarchia.gieldailuzji.GieldaIluzjiMod;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Konfiguracja JSON: config/gielda_iluzji.json */
public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static ModConfig instance = new ModConfig();

    // --- Giełda ---
    public int offersInMarket = 28;
    public int refreshCooldownSeconds = 15;
    public String priceItem = "minecraft:emerald";
    public String priceItemName = "szmaragdów";
    public int lupaPrice = 8;
    public int lupaUses = 5;
    /** Od tego poziomu XP można badać oferty bez Lupy (kosztem revealXpLevelCost poziomów). */
    public int revealMinLevel = 20;
    public int revealXpLevelCost = 1;
    public int revealRewardShards = 1;
    public List<String> sellers = new ArrayList<>(List.of(
            "xX_Handlarz_Xx", "OscamPro", "KrolRynku", "Anarchista2137", "TrustedSeller_PL",
            "NieOszukuje", "LegitShop", "DiamentowyJanusz", "SprzedamEventowke", "Cwaniak_GG"));

    // --- Fałszywe eventy ---
    public EventsConfig events = new EventsConfig();

    // --- Eventówki ---
    public List<FakeItemDef> items = new ArrayList<>();

    public static class EventsConfig {
        public boolean enabled = true;
        public double baseChance = 0.02;
        public double chancePerTransaction = 0.03;
        public double maxChance = 0.5;
        public int cooldownSeconds = 300;
        public int durationSeconds = 120;
        public int minDistance = 30;
        public int maxDistance = 60;
        public int triggerRadius = 10;
        public int raidMobs = 3;
        public boolean trapEffects = true;
        public int shardReward = 2;
        public boolean broadcastToAll = false;
    }

    public static ModConfig get() {
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("gielda_iluzji.json");
    }

    public static void load() {
        Path p = path();
        ModConfig cfg = null;
        boolean existed = Files.exists(p);
        if (existed) {
            try (Reader r = Files.newBufferedReader(p)) {
                cfg = GSON.fromJson(r, ModConfig.class);
            } catch (Exception e) {
                GieldaIluzjiMod.LOGGER.error("Błąd w gielda_iluzji.json - używam wartości domyślnych (plik nie zostanie nadpisany).", e);
                ModConfig fallback = new ModConfig();
                fallback.items = FakeItemDef.defaults();
                instance = fallback;
                return;
            }
        }
        if (cfg == null) cfg = new ModConfig();
        if (cfg.items == null || cfg.items.isEmpty()) cfg.items = FakeItemDef.defaults();
        if (cfg.events == null) cfg.events = new EventsConfig();
        if (cfg.sellers == null || cfg.sellers.isEmpty()) cfg.sellers = new ArrayList<>(List.of("Anonim"));
        for (FakeItemDef d : cfg.items) {
            if (d.enchantments == null) d.enchantments = new ArrayList<>();
            if (d.lore == null) d.lore = new ArrayList<>();
            if (d.ability == null) d.ability = "none";
            if (d.displayName == null) d.displayName = d.id;
        }
        instance = cfg;
        if (!existed) save();
    }

    public static void save() {
        Path p = path();
        try {
            Files.createDirectories(p.getParent());
            try (Writer w = Files.newBufferedWriter(p)) {
                GSON.toJson(instance, w);
            }
        } catch (IOException e) {
            GieldaIluzjiMod.LOGGER.error("Nie udało się zapisać konfiguracji", e);
        }
    }
}
