package pl.anarchia.gieldailuzji.market;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import pl.anarchia.gieldailuzji.config.ModConfig;
import pl.anarchia.gieldailuzji.event.FakeEventManager;
import pl.anarchia.gieldailuzji.item.FakeItemFactory;
import pl.anarchia.gieldailuzji.util.InventoryUtil;
import pl.anarchia.gieldailuzji.util.TextUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Serwerowe GUI 6x9. Wszystkie kliki są anulowane i obsługiwane ręcznie:
 * LPM = kup, Shift+LPM = zbadaj (Lupa lub poziom XP).
 */
public class MarketScreenHandler extends GenericContainerScreenHandler {
    public enum Mode { MARKET, TRUTH }

    private static final int OFFER_SLOTS = 45;
    private static final int SLOT_TAB_MARKET = 47;
    private static final int SLOT_TAB_TRUTH = 48;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_LUPA = 50;
    private static final int SLOT_REFRESH = 51;
    private static final int SLOT_CLOSE = 53;

    private final ServerPlayerEntity viewer;
    private final SimpleInventory view;
    private Mode mode = Mode.MARKET;
    private List<MarketOffer> offers = new ArrayList<>();
    private int lastRefreshTick = Integer.MIN_VALUE / 2;

    public MarketScreenHandler(int syncId, PlayerInventory playerInventory, ServerPlayerEntity viewer) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, new SimpleInventory(54), 6);
        this.viewer = viewer;
        this.view = (SimpleInventory) getInventory();
        this.offers = MarketManager.generate(mode);
        render();
    }

    // ---------- Blokada standardowej interakcji ----------

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex >= 0 && slotIndex < 54
                && (actionType == SlotActionType.PICKUP || actionType == SlotActionType.QUICK_MOVE)) {
            boolean closed = handleClick(slotIndex, actionType == SlotActionType.QUICK_MOVE);
            if (closed) return;
        }
        syncState();
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
        return false;
    }

    // ---------- Logika kliknięć ----------

    /** Zwraca true, jeśli GUI zostało zamknięte. */
    private boolean handleClick(int slot, boolean shift) {
        if (slot < OFFER_SLOTS) {
            if (slot >= offers.size() || offers.get(slot) == null) return false;
            if (shift) inspect(slot);
            else buy(slot);
            render();
            return false;
        }
        switch (slot) {
            case SLOT_TAB_MARKET -> switchMode(Mode.MARKET);
            case SLOT_TAB_TRUTH -> switchMode(Mode.TRUTH);
            case SLOT_LUPA -> buyLupa();
            case SLOT_REFRESH -> refresh();
            case SLOT_CLOSE -> {
                viewer.closeHandledScreen();
                return true;
            }
            default -> { }
        }
        render();
        return false;
    }

    private void switchMode(Mode m) {
        if (mode == m) return;
        mode = m;
        offers = MarketManager.generate(mode);
        play(SoundEvents.UI_BUTTON_CLICK.value());
    }

    private void refresh() {
        ModConfig cfg = ModConfig.get();
        int now = viewer.getServer().getTicks();
        int cd = cfg.refreshCooldownSeconds * 20;
        if (now - lastRefreshTick < cd) {
            int left = (cd - (now - lastRefreshTick) + 19) / 20;
            msg("&cOdświeżenie dostępne za &f" + left + "s&c.");
            return;
        }
        lastRefreshTick = now;
        offers = MarketManager.generate(mode);
        play(SoundEvents.UI_BUTTON_CLICK.value());
    }

    private void buy(int idx) {
        ModConfig cfg = ModConfig.get();
        MarketOffer o = offers.get(idx);
        PlayerInventory inv = viewer.getInventory();
        DynamicRegistryManager drm = viewer.getServer().getRegistryManager();

        if (mode == Mode.MARKET) {
            Item cur = FakeItemFactory.resolveItem(cfg.priceItem, Items.EMERALD);
            if (!InventoryUtil.remove(inv, s -> s.isOf(cur) && !FakeItemFactory.isGiItem(s), o.price)) {
                msg("&cNie stać Cię! Potrzebujesz &f" + o.price + " &c" + cfg.priceItemName + ".");
                play(SoundEvents.ENTITY_VILLAGER_NO);
                return;
            }
        } else {
            if (!InventoryUtil.remove(inv, FakeItemFactory::isShard, o.price)) {
                msg("&cPotrzebujesz &b" + o.price + " Odłamków Prawdy&c.");
                play(SoundEvents.ENTITY_VILLAGER_NO);
                return;
            }
        }

        ItemStack item = FakeItemFactory.createBase(o.def, o.illusion, drm);
        if (o.revealed) FakeItemFactory.markRevealed(item);
        Text name = item.getName();
        InventoryUtil.give(viewer, item);
        viewer.sendMessage(TextUtil.parse(TextUtil.PREFIX + "&aZakupiono: ").append(name), false);
        play(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP);

        if (mode == Mode.MARKET) {
            offers.set(idx, null);
            FakeEventManager.get().onTransaction(viewer);
        }
    }

    private void inspect(int idx) {
        ModConfig cfg = ModConfig.get();
        MarketOffer o = offers.get(idx);
        if (mode == Mode.TRUTH) {
            msg("&aKażda oferta w Sklepie Prawdy jest autentyczna.");
            return;
        }
        if (o.revealed) {
            msg("&7Ta oferta została już zbadana.");
            return;
        }
        boolean paid = false;
        if (viewer.experienceLevel >= cfg.revealMinLevel) {
            viewer.addExperienceLevels(-cfg.revealXpLevelCost);
            paid = true;
        } else if (InventoryUtil.useLupa(viewer.getInventory())) {
            paid = true;
        }
        if (!paid) {
            msg("&cPotrzebujesz &bLupy Demaskatora &club &e" + cfg.revealMinLevel + " poziomu XP&c.");
            play(SoundEvents.ENTITY_VILLAGER_NO);
            return;
        }
        o.revealed = true;
        if (o.illusion) {
            InventoryUtil.giveShards(viewer, cfg.revealRewardShards);
            msg("&c&lZdemaskowano iluzję! &7Nagroda: &b" + cfg.revealRewardShards + " Odłamków Prawdy&7.");
            play(SoundEvents.BLOCK_GLASS_BREAK);
        } else {
            msg("&aOferta jest autentyczna.");
            play(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP);
        }
    }

    private void buyLupa() {
        ModConfig cfg = ModConfig.get();
        Item cur = FakeItemFactory.resolveItem(cfg.priceItem, Items.EMERALD);
        if (!InventoryUtil.remove(viewer.getInventory(), s -> s.isOf(cur) && !FakeItemFactory.isGiItem(s), cfg.lupaPrice)) {
            msg("&cLupa kosztuje &f" + cfg.lupaPrice + " &c" + cfg.priceItemName + ".");
            play(SoundEvents.ENTITY_VILLAGER_NO);
            return;
        }
        InventoryUtil.give(viewer, FakeItemFactory.createLupa(cfg.lupaUses));
        msg("&aKupiono Lupę Demaskatora.");
        play(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP);
    }

    // ---------- Renderowanie ----------

    private void render() {
        for (int i = 0; i < 54; i++) view.setStack(i, ItemStack.EMPTY);
        for (int i = 0; i < OFFER_SLOTS; i++) {
            if (i < offers.size() && offers.get(i) != null) view.setStack(i, listing(offers.get(i)));
        }
        ItemStack pane = icon(Items.GRAY_STAINED_GLASS_PANE, false, " ");
        for (int i = OFFER_SLOTS; i < 54; i++) view.setStack(i, pane.copy());

        ModConfig cfg = ModConfig.get();
        Item cur = FakeItemFactory.resolveItem(cfg.priceItem, Items.EMERALD);
        int money = InventoryUtil.count(viewer.getInventory(), s -> s.isOf(cur) && !FakeItemFactory.isGiItem(s));
        int shards = InventoryUtil.count(viewer.getInventory(), FakeItemFactory::isShard);

        view.setStack(SLOT_TAB_MARKET, icon(Items.GOLD_INGOT, mode == Mode.MARKET,
                "&6&lGiełda Iluzji", "&7Rynek graczy. Uważaj na oscamy!"));
        view.setStack(SLOT_TAB_TRUTH, icon(Items.PRISMARINE_SHARD, mode == Mode.TRUTH,
                "&b&lSklep Prawdy", "&7Gwarantowane, prawdziwe eventówki", "&7za Odłamki Prawdy."));
        view.setStack(SLOT_INFO, icon(Items.BOOK, false, "&e&lTwoje statystyki",
                "&7" + cfg.priceItemName + ": &a" + money,
                "&7Odłamki Prawdy: &b" + shards,
                "&7Transakcje: &e" + FakeEventManager.get().transactions(viewer.getUuid())));
        view.setStack(SLOT_LUPA, icon(Items.SPYGLASS, true, "&b&lKup Lupę Demaskatora",
                "&7Cena: &a" + cfg.lupaPrice + " &7" + cfg.priceItemName,
                "&7Użycia: &e" + cfg.lupaUses));
        view.setStack(SLOT_REFRESH, icon(Items.CLOCK, false, "&a&lOdśwież oferty",
                "&7Cooldown: &f" + cfg.refreshCooldownSeconds + "s"));
        view.setStack(SLOT_CLOSE, icon(Items.BARRIER, false, "&c&lZamknij"));
    }

    private ItemStack listing(MarketOffer o) {
        ModConfig cfg = ModConfig.get();
        DynamicRegistryManager drm = viewer.getServer().getRegistryManager();
        ItemStack s = FakeItemFactory.createBase(o.def, o.illusion, drm);
        if (o.revealed) FakeItemFactory.markRevealed(s);

        List<Text> extra = new ArrayList<>();
        extra.add(Text.empty());
        extra.add(TextUtil.parse("&8&m                         "));
        if (mode == Mode.MARKET) {
            extra.add(TextUtil.parse("&7Wartość rynkowa: &6" + o.def.fakePrice));
            extra.add(TextUtil.parse("&7Sprzedawca: &f" + o.seller));
            extra.add(TextUtil.parse("&7Cena: &a" + o.price + " &7" + cfg.priceItemName));
            extra.add(Text.empty());
            extra.add(TextUtil.parse("&e\u25B6 LPM &7- kup"));
            extra.add(TextUtil.parse("&e\u25B6 Shift+LPM &7- zbadaj &8(Lupa / " + cfg.revealMinLevel + " lvl)"));
        } else {
            extra.add(TextUtil.parse("&7Sprzedawca: &bGiełda Prawdy"));
            extra.add(TextUtil.parse("&7Cena: &b" + o.price + " &7Odłamków Prawdy"));
            extra.add(TextUtil.parse("&a\u2714 Gwarantowana autentyczność"));
            extra.add(Text.empty());
            extra.add(TextUtil.parse("&e\u25B6 LPM &7- kup"));
        }
        FakeItemFactory.appendLore(s, extra);
        return s;
    }

    private static ItemStack icon(Item item, boolean glint, String name, String... lore) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponentTypes.CUSTOM_NAME, TextUtil.parse(name));
        if (lore.length > 0) {
            List<Text> l = new ArrayList<>();
            for (String x : lore) l.add(TextUtil.parse(x));
            s.set(DataComponentTypes.LORE, new LoreComponent(l));
        }
        if (glint) s.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        return s;
    }

    private void msg(String text) {
        viewer.sendMessage(TextUtil.parse(TextUtil.PREFIX + text), false);
    }

    private void play(SoundEvent sound) {
        viewer.getWorld().playSound(null, viewer.getBlockPos(), sound, SoundCategory.PLAYERS, 1f, 1f);
    }
}
