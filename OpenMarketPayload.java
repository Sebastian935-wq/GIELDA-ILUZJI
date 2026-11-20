package pl.anarchia.gieldailuzji.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import pl.anarchia.gieldailuzji.GieldaIluzjiMod;

/** Pakiet klient -> serwer: "otwórz Giełdę Iluzji" (wysyłany po wciśnięciu klawisza P). */
public record OpenMarketPayload() implements CustomPayload {
    public static final CustomPayload.Id<OpenMarketPayload> ID =
            new CustomPayload.Id<>(Identifier.of(GieldaIluzjiMod.MOD_ID, "open_market"));
    public static final PacketCodec<RegistryByteBuf, OpenMarketPayload> CODEC =
            PacketCodec.unit(new OpenMarketPayload());

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
