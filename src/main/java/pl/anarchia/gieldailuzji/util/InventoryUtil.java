package pl.anarchia.gieldailuzji.util;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import pl.anarchia.gieldailuzji.item.FakeItemFactory;

import java.util.function.Predicate;

public final class InventoryUtil {
    private InventoryUtil() {}

    public static int count(PlayerInventory inv, Predicate<ItemStack> p) {
        int c = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && p.test(s)) c += s.getCount();
        }
        return c;
    }

    public static boolean remove(PlayerInventory inv, Predicate<ItemStack> p, int amount) {
        if (count(inv, p) < amount) return false;
        int left = amount;
        for (int i = 0; i < inv.size() && left > 0; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty() || !p.test(s)) continue;
            int take = Math.min(left, s.getCount());
            s.decrement(take);
            left -= take;
        }
        return true;
    }

    public static void give(ServerPlayerEntity player, ItemStack stack) {
        player.getInventory().offerOrDrop(stack);
    }

    public static void giveShards(ServerPlayerEntity player, int amount) {
        if (amount > 0) give(player, FakeItemFactory.createShard(amount));
    }

    /** Zużywa jedno użycie pierwszej Lupy z ekwipunku. */
    public static boolean useLupa(PlayerInventory inv) {
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!FakeItemFactory.isLupa(s)) continue;
            int uses = FakeItemFactory.data(s).getInt(FakeItemFactory.K_USES);
            if (uses <= 0) continue;
            if (uses - 1 <= 0) s.decrement(1);
            else FakeItemFactory.refreshLupa(s, uses - 1);
            return true;
        }
        return false;
    }
}
