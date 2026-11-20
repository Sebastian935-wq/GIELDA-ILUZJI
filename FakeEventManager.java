package pl.anarchia.gieldailuzji.event;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import pl.anarchia.gieldailuzji.config.ModConfig;
import pl.anarchia.gieldailuzji.util.InventoryUtil;
import pl.anarchia.gieldailuzji.util.TextUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Fałszywe eventy w świecie: fatamorgana zrzutu i najazd iluzorycznych mobów. */
public final class FakeEventManager {
    public enum Type { SUPPLY_DROP, RAID }

    private static final String ILLUSION_TAG = "gi_illusion";
    private static final FakeEventManager INSTANCE = new FakeEventManager();

    public static FakeEventManager get() {
        return INSTANCE;
    }

    private static final class ActiveEvent {
        Type type;
        RegistryKey<World> world;
        BlockPos pos;
        long endTick;
        int totalTicks;
        UUID target;
        Set<UUID> audience = new HashSet<>();
        ServerBossBar bar;
        boolean triggered;
        boolean done;
        List<UUID> mobs = new ArrayList<>();
    }

    private final Map<UUID, Integer> transactions = new HashMap<>();
    private final Map<UUID, Long> lastEvent = new HashMap<>();
    private final List<ActiveEvent> active = new ArrayList<>();
    private final Random random = new Random();

    private FakeEventManager() {}

    public void register() {
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (ActiveEvent e : active) cleanup(server, e, false);
            active.clear();
        });
        // Iluzoryczne moby pryskają po pierwszym trafieniu i nie przyjmują obrażeń.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!entity.getCommandTags().contains(ILLUSION_TAG)) return true;
            if (entity.getWorld() instanceof ServerWorld w) {
                w.spawnParticles(ParticleTypes.LARGE_SMOKE, entity.getX(), entity.getY() + 1, entity.getZ(), 25, 0.3, 0.5, 0.3, 0.02);
                w.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 1f, 1.2f);
            }
            if (source.getAttacker() instanceof ServerPlayerEntity sp) {
                sp.sendMessage(TextUtil.parse("&7Iluzja prysła..."), true);
            }
            entity.discard();
            return false;
        });
    }

    public int transactions(UUID id) {
        return transactions.getOrDefault(id, 0);
    }

    /** Wywoływane po każdym zakupie na Giełdzie Iluzji. */
    public void onTransaction(ServerPlayerEntity p) {
        ModConfig.EventsConfig ec = ModConfig.get().events;
        UUID id = p.getUuid();
        int n = transactions.merge(id, 1, Integer::sum);
        if (!ec.enabled) return;
        long now = p.getServer().getTicks();
        Long last = lastEvent.get(id);
        if (last != null && now - last < ec.cooldownSeconds * 20L) return;
        double chance = Math.min(ec.maxChance, ec.baseChance + n * ec.chancePerTransaction);
        if (random.nextDouble() < chance) {
            transactions.put(id, 0);
            start(p, random.nextBoolean() ? Type.SUPPLY_DROP : Type.RAID);
        }
    }

    /** Uruchamia fałszywy event dla gracza. Zwraca false, jeśli się nie da (np. poza Overworldem). */
    public boolean start(ServerPlayerEntity p, Type type) {
        ModConfig.EventsConfig ec = ModConfig.get().events;
        ServerWorld w = (ServerWorld) p.getWorld();
        if (w.getRegistryKey() != World.OVERWORLD) return false;
        MinecraftServer server = p.getServer();

        double ang = random.nextDouble() * Math.PI * 2;
        int span = Math.max(0, ec.maxDistance - ec.minDistance);
        double dist = ec.minDistance + random.nextDouble() * span;
        int x = (int) (p.getX() + Math.cos(ang) * dist);
        int z = (int) (p.getZ() + Math.sin(ang) * dist);
        int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);

        ActiveEvent e = new ActiveEvent();
        e.type = type;
        e.world = w.getRegistryKey();
        e.pos = new BlockPos(x, y, z);
        e.target = p.getUuid();
        e.totalTicks = Math.max(20, ec.durationSeconds * 20);
        e.endTick = server.getTicks() + e.totalTicks;

        if (ec.broadcastToAll) {
            for (ServerPlayerEntity o : server.getPlayerManager().getPlayerList()) {
                if (o.getWorld() == w) e.audience.add(o.getUuid());
            }
        }
        e.audience.add(p.getUuid());

        String coords = "&fX: " + x + " Z: " + z;
        Text chat;
        Text barName;
        BossBar.Color color;
        if (type == Type.SUPPLY_DROP) {
            chat = TextUtil.parse("&6&l[ZRZUT] &eZrzut zaopatrzenia ląduje na " + coords + " &8(~" + (int) dist + "m)");
            barName = TextUtil.parse("&6&lZrzut Zaopatrzenia &7- " + coords);
            color = BossBar.Color.YELLOW;
        } else {
            chat = TextUtil.parse("&c&l[NAJAZD] &7Banda najeźdźców oblega teren na " + coords + " &8(~" + (int) dist + "m)");
            barName = TextUtil.parse("&c&lNajazd &7- " + coords);
            color = BossBar.Color.RED;
        }
        e.bar = new ServerBossBar(barName, color, BossBar.Style.PROGRESS);
        for (UUID id : e.audience) {
            ServerPlayerEntity a = server.getPlayerManager().getPlayer(id);
            if (a == null) continue;
            a.sendMessage(chat, false);
            e.bar.addPlayer(a);
        }
        lastEvent.put(p.getUuid(), (long) server.getTicks());
        active.add(e);
        return true;
    }

    // ---------- Tick ----------

    private void tick(MinecraftServer server) {
        if (active.isEmpty()) return;
        long now = server.getTicks();
        ModConfig.EventsConfig ec = ModConfig.get().events;
        Iterator<ActiveEvent> it = active.iterator();
        while (it.hasNext()) {
            ActiveEvent e = it.next();
            ServerWorld w = server.getWorld(e.world);
            if (w == null) {
                cleanup(server, e, false);
                it.remove();
                continue;
            }
            if (now >= e.endTick) {
                cleanup(server, e, true);
                it.remove();
                continue;
            }
            e.bar.setPercent(Math.max(0f, Math.min(1f, (float) (e.endTick - now) / e.totalTicks)));

            if (now % 10 == 0) {
                double cx = e.pos.getX() + 0.5, cz = e.pos.getZ() + 0.5;
                for (int h = 0; h <= 12; h += 3) {
                    if (e.type == Type.SUPPLY_DROP) {
                        w.spawnParticles(ParticleTypes.END_ROD, cx, e.pos.getY() + h, cz, 2, 0.15, 0.2, 0.15, 0.01);
                    } else {
                        w.spawnParticles(ParticleTypes.FLAME, cx, e.pos.getY() + h * 0.3, cz, 2, 0.8, 0.2, 0.8, 0.01);
                    }
                }
            }

            if (!e.triggered) {
                double r2 = (double) ec.triggerRadius * ec.triggerRadius;
                for (UUID id : e.audience) {
                    ServerPlayerEntity pl = server.getPlayerManager().getPlayer(id);
                    if (pl == null || pl.getWorld() != w) continue;
                    if (pl.squaredDistanceTo(e.pos.getX() + 0.5, e.pos.getY(), e.pos.getZ() + 0.5) <= r2) {
                        trigger(server, w, e, pl);
                        break;
                    }
                }
            } else if (e.type == Type.RAID && !e.done) {
                boolean anyAlive = false;
                for (UUID m : e.mobs) {
                    Entity ent = w.getEntity(m);
                    if (ent != null && ent.isAlive() && !ent.isRemoved()) {
                        anyAlive = true;
                        break;
                    }
                }
                if (!anyAlive) {
                    ServerPlayerEntity pl = server.getPlayerManager().getPlayer(e.target);
                    if (pl != null) {
                        InventoryUtil.giveShards(pl, ec.shardReward);
                        pl.sendMessage(TextUtil.parse(TextUtil.PREFIX + "&a&lZdemaskowałeś najazd! &7Nagroda: &b" + ec.shardReward + " Odłamków Prawdy&7."), false);
                    }
                    e.done = true;
                }
            }

            if (e.done) {
                cleanup(server, e, false);
                it.remove();
            }
        }
    }

    private void trigger(MinecraftServer server, ServerWorld w, ActiveEvent e, ServerPlayerEntity pl) {
        ModConfig.EventsConfig ec = ModConfig.get().events;
        e.triggered = true;
        e.target = pl.getUuid();
        double cx = e.pos.getX() + 0.5, cz = e.pos.getZ() + 0.5;

        if (e.type == Type.SUPPLY_DROP) {
            w.spawnParticles(ParticleTypes.LARGE_SMOKE, cx, e.pos.getY() + 1, cz, 60, 1.0, 0.8, 1.0, 0.03);
            w.playSound(null, cx, e.pos.getY(), cz, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.AMBIENT, 2f, 0.7f);
            pl.sendMessage(TextUtil.parse("&c&l[FATAMORGANA] &7To nie był prawdziwy zrzut... Iluzja prysła!"), false);
            if (ec.trapEffects) {
                pl.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 80, 0));
                pl.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 120, 1));
            }
            InventoryUtil.giveShards(pl, ec.shardReward);
            pl.sendMessage(TextUtil.parse(TextUtil.PREFIX + "&7Zdemaskowałeś fatamorganę: &b+" + ec.shardReward + " Odłamków Prawdy&7."), false);
            e.done = true;
            return;
        }

        // RAID: iluzoryczni najeźdźcy (zerowe obrażenia, znikają po trafieniu)
        for (int i = 0; i < Math.max(1, ec.raidMobs); i++) {
            int mx = e.pos.getX() + random.nextInt(7) - 3;
            int mz = e.pos.getZ() + random.nextInt(7) - 3;
            int my = w.getTopY(Heightmap.Type.MOTION_BLOCKING, mx, mz);
            HuskEntity husk = EntityType.HUSK.spawn(w, new BlockPos(mx, my, mz), SpawnReason.EVENT);
            if (husk == null) continue;
            husk.setCustomName(TextUtil.parse("&7Najeźdźca"));
            husk.setCustomNameVisible(true);
            husk.addCommandTag(ILLUSION_TAG);
            husk.setPersistent();
            if (husk.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE) != null) {
                husk.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).setBaseValue(0.0);
            }
            e.mobs.add(husk.getUuid());
        }
        e.bar.setName(TextUtil.parse("&c&lNajazd &7- pokonaj najeźdźców!"));
        pl.sendMessage(TextUtil.parse("&c&l[NAJAZD] &7Najeźdźcy! ...czemu żaden nie zadaje obrażeń?"), false);
    }

    private void cleanup(MinecraftServer server, ActiveEvent e, boolean expired) {
        ServerWorld w = server.getWorld(e.world);
        if (w != null) {
            for (UUID m : e.mobs) {
                Entity ent = w.getEntity(m);
                if (ent != null && !ent.isRemoved()) {
                    w.spawnParticles(ParticleTypes.SMOKE, ent.getX(), ent.getY() + 1, ent.getZ(), 15, 0.3, 0.5, 0.3, 0.02);
                    ent.discard();
                }
            }
        }
        e.bar.clearPlayers();
        if (expired) {
            for (UUID id : e.audience) {
                ServerPlayerEntity a = server.getPlayerManager().getPlayer(id);
                if (a != null) a.sendMessage(TextUtil.parse("&8[&7Event&8] &7Fałszywy event zniknął jak mgła..."), false);
            }
        }
    }
}
