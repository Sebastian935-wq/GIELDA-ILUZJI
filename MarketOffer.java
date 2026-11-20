package pl.anarchia.gieldailuzji.market;

import pl.anarchia.gieldailuzji.config.FakeItemDef;

public final class MarketOffer {
    public final FakeItemDef def;
    public final boolean illusion;
    public final String seller;
    public final int price;
    public boolean revealed;

    public MarketOffer(FakeItemDef def, boolean illusion, String seller, int price) {
        this.def = def;
        this.illusion = illusion;
        this.seller = seller;
        this.price = price;
    }
}
