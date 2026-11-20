package pl.anarchia.gieldailuzji.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import pl.anarchia.gieldailuzji.config.FakeItemDef;
import pl.anarchia.gieldailuzji.util.TextUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/** Tworzenie przedmiotów wyłącznie na Data Components (1.21.4). */
public final class FakeItemFactory {
    public static final String K_ID = "gi_id";
    public static final String K_ILLUSION = "is_illusion";
    public static final String K_REVEALED = "gi_revealed";
    public static final String K_ABILITY = "gi_ability";
    public static final String K_POWER = "gi_power";
    public static final String K_SHARD = "gi_shard";
    public static final String K_LUPA = "gi_lupa";
    public static final String K_USES = "gi_uses";

    private FakeItemFactory() {}

    // ---------- CUSTOM_DATA helpers ----------

    public static NbtCompound data(ItemStack s) {
        return s.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
    }

    public static void editData(ItemStack s, Consumer<NbtCompound> f) {
        NbtCompound n = data(s);
        f.accept(n);
        s.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n));
    }

    public static boolean isGiItem(ItemStack s) {
        return !s.isEmpty() && data(s).contains(K_ID);
    }

    public static boolean isIllusion(ItemStack s) {
        return !s.isEmpty() && data(s).getBoolean(K_ILLUSION);
    }

    public static boolean isShard(ItemStack s) {
        return !s.isEmpty() && data(s).getBoolean(K_SHARD);
    }

    public static boolean isLupa(ItemStack s) {
        return !s.isEmpty() && data(s).getBoolean(K_LUPA);
    }

    public static String ability(ItemStack s) {
        return data(s).getString(K_ABILITY);
    }

    public static Item resolveItem(String id, Item fallback) {
        Identifier i = Identifier.tryParse(id == null ? "" : id);
        if (i != null && Registries.ITEM.containsId(i)) return Registries.ITEM.get(i);
        return fallback;
    }

    // ---------- Eventówki ----------

    /** Czysty przedmiot (bez linii rynkowych). Iluzja i oryginał wyglądają identycznie. */
    public static ItemStack createBase(FakeItemDef def, boolean illusion, DynamicRegistryManager drm) {
        ItemStack st = new ItemStack(resolveItem(def.material, Items.PAPER));
        st.set(DataComponentTypes.CUSTOM_NAME, TextUtil.gradient(def.displayName, def.gradientFrom, def.gradientTo, def.bold));

        List<Text> lore = new ArrayList<>();
        for (String line : def.lore) {
            lore.add(TextUtil.parse(line.replace("{power}", String.valueOf(def.power))));
        }
        st.set(DataComponentTypes.LORE, new LoreComponent(lore));

        if (def.glint) st.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        if (def.customModelData > 0) {
            st.set(DataComponentTypes.CUSTOM_MODEL_DATA,
                    new CustomModelDataComponent(List.of((float) def.customModelData), List.of(), List.of(), List.of()));
        }
        applyEnchantments(st, def, drm);

        editData(st, d -> {
            d.putString(K_ID, def.id);
            d.putBoolean(K_ILLUSION, illusion);
            d.putString(K_ABILITY, def.ability);
            d.putInt(K_POWER, def.power);
        });
        return st;
    }

    private static void applyEnchantments(ItemStack st, FakeItemDef def, DynamicRegistryManager drm) {
        if (def.enchantments.isEmpty()) return;
        Registry<Enchantment> reg = drm.getOrThrow(RegistryKeys.ENCHANTMENT);
        ItemEnchantmentsComponent.Builder b = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
        for (String e : def.enchantments) {
            int idx = e.lastIndexOf(':');
            if (idx < 0) continue;
            int lvl;
            try {
                lvl = Integer.parseInt(e.substring(idx + 1).trim());
            } catch (NumberFormatException ex) {
                continue;
            }
            Identifier id = Identifier.tryParse(e.substring(0, idx).trim());
            if (id == null) continue;
            Optional<RegistryEntry.Reference<Enchantment>> opt = reg.getOptional(RegistryKey.of(RegistryKeys.ENCHANTMENT, id));
            if (opt.isPresent()) b.add(opt.get(), lvl);
        }
        st.set(DataComponentTypes.ENCHANTMENTS, b.build());
    }

    public static void appendLore(ItemStack s, List<Text> extra) {
        LoreComponent old = s.getOrDefault(DataComponentTypes.LORE, LoreComponent.DEFAULT);
        List<Text> l = new ArrayList<>(old.lines());
        l.addAll(extra);
        s.set(DataComponentTypes.LORE, new LoreComponent(l));
    }

    /** Oznacza przedmiot jako zbadany i dopisuje ostrzeżenie / potwierdzenie. Zwraca true, jeśli to iluzja. */
    public static boolean markRevealed(ItemStack s) {
        boolean illusion = isIllusion(s);
        editData(s, d -> d.putBoolean(K_REVEALED, true));
        Text line = illusion
                ? TextUtil.parse("&c&l[\u26A0 UWAGA: PRZEDMIOT ILUZORYCZNY]")
                : TextUtil.parse("&a&l[\u2714 Przedmiot autentyczny]");
        appendLore(s, List.of(Text.empty(), line));
        return illusion;
    }

    // ---------- Przedmioty pomocnicze ----------

    public static ItemStack createShard(int count) {
        ItemStack s = new ItemStack(Items.PRISMARINE_SHARD, count);
        s.set(DataComponentTypes.CUSTOM_NAME, TextUtil.gradient("Odłamek Prawdy", "#55FFFF", "#FFFFFF", true));
        s.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                TextUtil.parse("&7Waluta Giełdy Iluzji."),
                TextUtil.parse("&7Wymień na prawdziwe eventówki"),
                TextUtil.parse("&7w &bSklepie Prawdy&7."))));
        s.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        editData(s, d -> d.putBoolean(K_SHARD, true));
        return s;
    }

    public static ItemStack createLupa(int uses) {
        ItemStack s = new ItemStack(Items.SPYGLASS);
        s.set(DataComponentTypes.CUSTOM_NAME, TextUtil.gradient("Lupa Demaskatora", "#55FFFF", "#FFFFFF", true));
        s.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        editData(s, d -> d.putBoolean(K_LUPA, true));
        refreshLupa(s, uses);
        return s;
    }

    public static void refreshLupa(ItemStack s, int uses) {
        editData(s, d -> d.putInt(K_USES, uses));
        s.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                TextUtil.parse("&7Demaskuje iluzoryczne eventówki."),
                TextUtil.parse("&7Pozostałe użycia: &e" + uses),
                Text.empty(),
                TextUtil.parse("&8Giełda: &7Shift+LPM na ofertę"),
                TextUtil.parse("&8Poza giełdą: &7Lupa w jednej ręce,"),
                TextUtil.parse("&7eventówka w drugiej, PPM"))));
    }

    /** Bezwartościowy śmieć po prysnięciu iluzji. */
    public static ItemStack createJunk() {
        boolean gold = ThreadLocalRandom.current().nextBoolean();
        ItemStack s = new ItemStack(gold ? Items.GOLD_NUGGET : Items.ROTTEN_FLESH);
        s.set(DataComponentTypes.CUSTOM_NAME, TextUtil.parse(gold ? "&6Złoto Głupców" : "&2Zgnite Mięso"));
        s.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                TextUtil.parse("&c&lZostałeś oscamowany."),
                TextUtil.parse("&8Iluzja prysła jak bańka mydlana..."))));
        return s;
    }
}
