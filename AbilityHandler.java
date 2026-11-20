package pl.anarchia.gieldailuzji.item;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import pl.anarchia.gieldailuzji.config.ModConfig;
import pl.anarchia.gieldailuzji.event.FakeEventManager;
import pl.anarchia.gieldailuzji.util.InventoryUtil;
import pl.anarchia.gieldailuzji.util.TextUtil;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/** Logika użycia prawdziwych eventówek oraz pryskania iluzji. */
public final class AbilityHandler {
    private record Loot(Item item, int min, int max) {}

    private static final Loot[] KEY_LOOT = {
            new Loot(Items.DIAMOND, 1, 3), new Loot(Items.EMERALD, 4, 12), new Loot(Items.NETHERITE_SCRAP, 1, 2),
            new Loot(Items.GOLDEN_APPLE, 1, 3), new Loot(Items.ENDER_PEARL, 2, 6)};
    private static final Loot[] DROP_LOOT = {
            new Loot(Items.IRON_INGOT, 8, 24), new Loot(Items.COOKED_BEEF, 16, 32), new Loot(Items.EXPERIENCE_BOTTLE, 8, 16),
            new Loot(Items.DIAMOND, 2, 4), new Loot(Items.ARROW, 32, 64), new Loot(Items.GOLDEN_CARROT, 8, 16)};

    private AbilityHandler() {}

    public static void register() {
        // PvP / atak: iluzja pryska, Excalibur zadaje bonusowe obrażenia
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            ItemStack st = sp.getMainHandStack();
            if (!FakeItemFactory.isGiItem(st)) return ActionResult.PASS;
            if (FakeItemFactory.isIllusion(st)) {
                pop(sp, junk -> sp.setStackInHand(Hand.MAIN_HAND, junk));
                return ActionResult.FAIL;
            }
            if ("excalibur".equals(FakeItemFactory.ability(st)) && entity instanceof LivingEntity target) {
                int power = FakeItemFactory.data(st).getInt(FakeItemFactory.K_POWER);
                target.damage((ServerWorld) world, sp.getDamageSources().indirectMagic(sp, sp), power);
                target.timeUntilRegen = 0; // pozwala normalnemu uderzeniu zadać pełne obrażenia
            }
            return ActionResult.PASS;
        });

        // PPM: iluzja pryska, Lupa demaskuje, Klucz / Zrzut dają nagrody
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            ItemStack st = sp.getStackInHand(hand);
            if (st.isEmpty()) return ActionResult.PASS;

            if (FakeItemFactory.isLupa(st)) {
                Hand other = hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
                ItemStack target = sp.getStackInHand(other);
                if (!FakeItemFactory.isGiItem(target)) return ActionResult.PASS;
                useLupa(sp, st, target);
                return ActionResult.SUCCESS;
            }
            if (!FakeItemFactory.isGiItem(st)) return ActionResult.PASS;

            if (FakeItemFactory.isIllusion(st)) {
                pop(sp, junk -> sp.setStackInHand(hand, junk));
                return ActionResult.SUCCESS;
            }
            String ab = FakeItemFactory.ability(st);
            int power = Math.max(1, FakeItemFactory.data(st).getInt(FakeItemFactory.K_POWER));
            if ("klucz".equals(ab)) {
                giveLoot(sp, KEY_LOOT, power);
                st.decrement(1);
                sp.sendMessage(TextUtil.parse(TextUtil.PREFIX + "&aOtworzyłeś skarbiec!"), false);
                return ActionResult.SUCCESS;
            }
            if ("zrzut".equals(ab)) {
                giveLoot(sp, DROP_LOOT, power);
                st.decrement(1);
                sp.sendMessage(TextUtil.parse(TextUtil.PREFIX + "&6Zrzut wylądował prosto w Twoim plecaku!"), false);
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });

        // Totem Ułaskawienia: ratuje przed śmiercią; iluzja pryska i gracz ginie
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity sp)) return true;
            PlayerInventory inv = sp.getInventory();
            int fakeSlot = -1;
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.getStack(i);
                if (!FakeItemFactory.isGiItem(s) || !"totem".equals(FakeItemFactory.ability(s))) continue;
                if (FakeItemFactory.isIllusion(s)) {
                    if (fakeSlot < 0) fakeSlot = i;
                    continue;
                }
                int power = Math.max(1, FakeItemFactory.data(s).getInt(FakeItemFactory.K_POWER));
                s.decrement(1);
                sp.setHealth(Math.max(2f, power * 2f));
                sp.clearStatusEffects();
                sp.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 1));
                ServerWorld w = (ServerWorld) sp.getWorld();
                w.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, sp.getX(), sp.getY() + 1, sp.getZ(), 40, 0.4, 0.6, 0.4, 0.3);
                w.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 1f, 1f);
                sp.sendMessage(TextUtil.parse(TextUtil.PREFIX + "&6Totem Ułaskawienia uratował Ci życie!"), false);
                return false;
            }
            if (fakeSlot >= 0) {
                final int slot = fakeSlot;
                pop(sp, junk -> inv.setStack(slot, junk));
            }
            return true;
        });

        // Kostium: bonusy po założeniu, iluzja pryska przy pierwszym założeniu
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 40 != 0) return;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                ItemStack head = p.getEquippedStack(EquipmentSlot.HEAD);
                if (!FakeItemFactory.isGiItem(head) || !"kostium".equals(FakeItemFactory.ability(head))) continue;
                if (FakeItemFactory.isIllusion(head)) {
                    pop(p, junk -> p.equipStack(EquipmentSlot.HEAD, junk));
                    continue;
                }
                int power = Math.max(1, FakeItemFactory.data(head).getInt(FakeItemFactory.K_POWER));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 120, power - 1, true, false, true));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 120, 0, true, false, true));
            }
        });
    }

    /** Iluzja pryska: dym, tłuczone szkło, śmieć w miejsce przedmiotu. */
    public static void pop(ServerPlayerEntity p, Consumer<ItemStack> setter) {
        setter.accept(FakeItemFactory.createJunk());
        ServerWorld w = (ServerWorld) p.getWorld();
        w.spawnParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.4, 0.5, 0.4, 0.02);
        w.spawnParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 1.0, p.getZ(), 20, 0.4, 0.5, 0.4, 0.05);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1f, 0.8f);
        p.sendMessage(TextUtil.parse("&c&lZostałeś oscamowany! &7Iluzja prysła..."), false);
    }

    private static void useLupa(ServerPlayerEntity sp, ItemStack lupa, ItemStack target) {
        if (FakeItemFactory.data(target).getBoolean(FakeItemFactory.K_REVEALED)) {
            sp.sendMessage(TextUtil.parse(TextUtil.PREFIX + "&7Ten przedmiot został już zbadany."), true);
            return;
        }
        int uses = FakeItemFactory.data(lupa).getInt(FakeItemFactory.K_USES);
        if (uses <= 0) {
            lupa.decrement(1);
            return;
        }
        if (uses - 1 <= 0) lupa.decrement(1);
        else FakeItemFactory.refreshLupa(lupa, uses - 1);

        boolean illusion = FakeItemFactory.markRevealed(target);
        if (illusion) {
            int reward = ModConfig.get().revealRewardShards;
            InventoryUtil.giveShards(sp, reward);
            sp.sendMessage(TextUtil.parse(TextUtil.PREFIX + "&c&lZdemaskowano iluzję! &7Nagroda: &b" + reward + " Odłamków Prawdy&7."), false);
        } else {
            sp.sendMessage(TextUtil.parse(TextUtil.PREFIX + "&aTen przedmiot jest autentyczny."), false);
        }
    }

    private static void giveLoot(ServerPlayerEntity sp, Loot[] table, int rolls) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < rolls; i++) {
            Loot l = table[r.nextInt(table.length)];
            int n = l.min() + r.nextInt(l.max() - l.min() + 1);
            InventoryUtil.give(sp, new ItemStack(l.item(), n));
        }
    }
}
