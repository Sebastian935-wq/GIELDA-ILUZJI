package pl.anarchia.gieldailuzji.util;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

public final class TextUtil {
    public static final Style BASE = Style.EMPTY.withItalic(false).withColor(Formatting.GRAY);
    public static final String PREFIX = "&8[&6Giełda&8] ";

    private TextUtil() {}

    /** Parser kodów &a, &l, &#RRGGBB itd. Zwraca tekst bez domyślnej kursywy. */
    public static MutableText parse(String input) {
        MutableText root = Text.empty();
        Style style = BASE;
        StringBuilder buf = new StringBuilder();
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if ((c == '&' || c == '\u00a7') && i + 1 < input.length()) {
                char n = Character.toLowerCase(input.charAt(i + 1));
                if (n == '#' && i + 8 <= input.length()) {
                    Integer rgb = parseHex(input.substring(i + 2, i + 8));
                    if (rgb != null) {
                        flush(root, buf, style);
                        style = style.withColor(TextColor.fromRgb(rgb));
                        i += 8;
                        continue;
                    }
                }
                Formatting f = Formatting.byCode(n);
                if (f != null) {
                    flush(root, buf, style);
                    if (f == Formatting.RESET) {
                        style = BASE;
                    } else if (f.isColor()) {
                        style = Style.EMPTY.withItalic(false).withColor(f);
                    } else {
                        switch (f) {
                            case BOLD -> style = style.withBold(true);
                            case ITALIC -> style = style.withItalic(true);
                            case UNDERLINE -> style = style.withUnderline(true);
                            case STRIKETHROUGH -> style = style.withStrikethrough(true);
                            case OBFUSCATED -> style = style.withObfuscated(true);
                            default -> { }
                        }
                    }
                    i += 2;
                    continue;
                }
            }
            buf.append(c);
            i++;
        }
        flush(root, buf, style);
        return root;
    }

    /** Gradient RGB litera po literze. */
    public static MutableText gradient(String text, String fromHex, String toHex, boolean bold) {
        Integer fa = parseHex(fromHex.startsWith("#") ? fromHex.substring(1) : fromHex);
        Integer ta = parseHex(toHex.startsWith("#") ? toHex.substring(1) : toHex);
        int a = fa == null ? 0xFFFFFF : fa;
        int b = ta == null ? 0xFFFFFF : ta;
        int[] cps = text.codePoints().toArray();
        MutableText root = Text.empty();
        for (int i = 0; i < cps.length; i++) {
            float t = cps.length <= 1 ? 0f : (float) i / (cps.length - 1);
            int r = lerp((a >> 16) & 255, (b >> 16) & 255, t);
            int g = lerp((a >> 8) & 255, (b >> 8) & 255, t);
            int bl = lerp(a & 255, b & 255, t);
            Style st = Style.EMPTY.withItalic(false).withColor(TextColor.fromRgb((r << 16) | (g << 8) | bl));
            if (bold) st = st.withBold(true);
            root.append(Text.literal(new String(Character.toChars(cps[i]))).setStyle(st));
        }
        return root;
    }

    private static int lerp(int x, int y, float t) {
        return Math.round(x + (y - x) * t);
    }

    private static Integer parseHex(String s) {
        try {
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (Exception e) {
            return null;
        }
    }

    private static void flush(MutableText root, StringBuilder buf, Style style) {
        if (buf.length() == 0) return;
        root.append(Text.literal(buf.toString()).setStyle(style));
        buf.setLength(0);
    }
}
