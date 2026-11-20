package pl.anarchia.gieldailuzji.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Definicja jednej fałszywej / prawdziwej eventówki z pliku config/gielda_iluzji.json. */
public class FakeItemDef {
    public String id = "item";
    public String displayName = "Przedmiot";
    public String gradientFrom = "#FFFFFF";
    public String gradientTo = "#FFFFFF";
    public boolean bold = true;
    public String material = "minecraft:paper";
    public int customModelData = 0;
    public boolean glint = true;
    /** Format: "sharpness:5" lub "minecraft:sharpness:5". */
    public List<String> enchantments = new ArrayList<>();
    /** Kody kolorów: &a, &l, &#RRGGBB. Placeholder: {power}. */
    public List<String> lore = new ArrayList<>();
    /** Szansa (0.0 - 1.0), że oferta na rynku będzie iluzją. */
    public double illusionChance = 0.6;
    /** Realny koszt na rynku (w przedmiocie z pola priceItem). */
    public int price = 16;
    /** Wyświetlana "wartość rynkowa" - czysta ściema. */
    public String fakePrice = "25 000 000 $";
    /** Koszt w Odłamkach Prawdy w Sklepie Prawdy (0 = niedostępne). */
    public int truthPrice = 5;
    /** none | excalibur | klucz | zrzut | totem | kostium */
    public String ability = "none";
    /** Siła umiejętności (obrażenia / serca / liczba losowań / poziom prędkości). */
    public int power = 1;

    public static FakeItemDef make(String id, String name, String from, String to, String material, int cmd,
                                   String ability, int power, int price, String fake, int truth, double chance,
                                   List<String> enchants, String... lore) {
        FakeItemDef d = new FakeItemDef();
        d.id = id;
        d.displayName = name;
        d.gradientFrom = from;
        d.gradientTo = to;
        d.material = material;
        d.customModelData = cmd;
        d.ability = ability;
        d.power = power;
        d.price = price;
        d.fakePrice = fake;
        d.truthPrice = truth;
        d.illusionChance = chance;
        d.enchantments = new ArrayList<>(enchants);
        d.lore = new ArrayList<>(Arrays.asList(lore));
        return d;
    }

    public static List<FakeItemDef> defaults() {
        List<FakeItemDef> l = new ArrayList<>();
        l.add(make("excalibur", "Excalibur", "#FFD700", "#FF4500", "minecraft:netherite_sword", 1001,
                "excalibur", 6, 24, "48 500 000 $", 8, 0.65,
                List.of("sharpness:5", "unbreaking:3"),
                "&7Legendarna broń z eventu &c&lANARCHIA&7.",
                "",
                "&6Umiejętność: &eBoski Cios",
                "&7Każde trafienie zadaje dodatkowe &c{power} &7obrażeń magicznych.",
                "&7Statystyki: &cObrażenia 12 &8| &bSzybkość 1.6"));
        l.add(make("kostium_anarchisty", "Kostium Anarchisty", "#FF55FF", "#5555FF", "minecraft:golden_helmet", 1002,
                "kostium", 2, 14, "12 000 000 $", 5, 0.6,
                List.of("protection:4"),
                "&7Limitowany kostium z eventu &d&lHALLOWEEN&7.",
                "",
                "&6Umiejętność: &eDusza Imprezy",
                "&7Założony daje &bSzybkość {power} &7oraz &aSkok I&7.",
                "&7Statystyki: &9Pancerz 2 &8| &5Styl 100"));
        l.add(make("anarchiczny_klucz", "Anarchiczny Klucz", "#55FFFF", "#00AA55", "minecraft:trial_key", 1003,
                "klucz", 3, 10, "5 000 000 $", 3, 0.7,
                List.of(),
                "&7Otwiera skrzynię pełną skarbów.",
                "",
                "&6Umiejętność: &eOtwarcie Skarbca",
                "&7PPM: losujesz &a{power} &7nagród i klucz znika."));
        l.add(make("zrzut_zaopatrzenia", "Zrzut Zaopatrzenia", "#FFAA00", "#FFFF55", "minecraft:nautilus_shell", 1004,
                "zrzut", 4, 12, "8 000 000 $", 4, 0.7,
                List.of(),
                "&7Przywołuje zrzut zaopatrzenia z nieba.",
                "",
                "&6Umiejętność: &eWezwanie Zrzutu",
                "&7PPM: otrzymujesz &a{power} &7losowych zasobów."));
        l.add(make("totem_ulaskawienia", "Totem Ułaskawienia", "#FFFFFF", "#FFD700", "minecraft:nether_star", 1005,
                "totem", 5, 30, "60 000 000 $", 10, 0.75,
                List.of(),
                "&7Ostatnia szansa każdego anarchisty.",
                "",
                "&6Umiejętność: &eUłaskawienie",
                "&7Gdy masz go w ekwipunku, uratuje Cię przed śmiercią",
                "&7i przywróci &c{power} &7serc oraz Regenerację."));
        return l;
    }
}
