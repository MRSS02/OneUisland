package dev.flexy.dynamicisland.island;

import android.content.Context;
import android.os.Bundle;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class Metrics {
    private Metrics() {
    }

    static List<IslandItem.Metric> parse(Context ctx, Bundle extras) {
        Object raw = extras.get("android.metrics");
        if (!(raw instanceof List) || ((List<?>) raw).isEmpty()) return null;
        List<IslandItem.Metric> out = new ArrayList<>();
        for (Object o : (List<?>) raw) {
            if (!(o instanceof Bundle)) continue;
            Bundle b = (Bundle) o;
            Bundle v = b.getBundle("value");
            if (v == null) continue;
            IslandItem.Metric m = new IslandItem.Metric();
            m.label = b.getCharSequence("label");
            m.semantic = b.getInt("semanticStyle", 0);
            m.type = v.getInt("_type", 0);
            try {
                switch (m.type) {
                    case IslandItem.Metric.TIME:
                        m.countDown = v.getBoolean("countDown", false);
                        m.format = v.getInt("format", 2);
                        if (v.containsKey("pausedDuration")) m.pausedMs = number(v, "pausedDuration").longValue();
                        else m.zeroTime = number(v, "zeroTime").longValue();
                        break;
                    case IslandItem.Metric.DATE:
                        m.fixed = date(LocalDate.ofEpochDay(number(v, "value").longValue()), v.getInt("format", 0));
                        break;
                    case IslandItem.Metric.TIME_OF_DAY:
                        LocalTime t = LocalTime.ofSecondOfDay(number(v, "value").longValue() % 86400);
                        boolean h24 = android.text.format.DateFormat.is24HourFormat(ctx);
                        m.fixed = t.format(DateTimeFormatter.ofPattern(h24 ? "HH:mm" : "h:mm a", Locale.getDefault()));
                        break;
                    case IslandItem.Metric.INT:
                        m.fixed = withUnit(NumberFormat.getIntegerInstance().format(number(v, "value").longValue()),
                                v.getCharSequence("unit"));
                        break;
                    case IslandItem.Metric.FLOAT:
                        NumberFormat nf = NumberFormat.getNumberInstance();
                        nf.setMinimumFractionDigits(Math.max(0, v.getInt("minDigits", 0)));
                        nf.setMaximumFractionDigits(Math.max(nf.getMinimumFractionDigits(), v.getInt("maxDigits", 2)));
                        m.fixed = withUnit(nf.format(number(v, "value").doubleValue()), v.getCharSequence("unit"));
                        break;
                    case IslandItem.Metric.TEXT:
                        m.fixed = withUnit(String.valueOf(v.getCharSequence("value")), v.getCharSequence("unit"));
                        break;
                    default:
                        continue;
                }
            } catch (Throwable t) {
                continue;
            }
            out.add(m);
        }
        return out.isEmpty() ? null : out;
    }

    static String text(IslandItem.Metric m, long now) {
        if (m.type != IslandItem.Metric.TIME) return m.fixed == null ? "" : m.fixed.toString();
        long ms = m.pausedMs >= 0 ? m.pausedMs : m.countDown ? m.zeroTime - now : now - m.zeroTime;
        ms = Math.max(0, ms);
        if (m.format == 1) return adaptive(ms);
        return IslandView.formatTime(ms);
    }

    static int color(int semantic) {
        switch (semantic) {
            case 1: return 0xFF8AB4F8;
            case 2: return 0xFF81C995;
            case 3: return 0xFFFDD663;
            case 4: return 0xFFEE8E88;
            default: return 0xFFFFFFFF;
        }
    }

    private static String adaptive(long ms) {
        long s = ms / 1000;
        if (s < 60) return s + " s";
        long min = s / 60;
        if (min < 60) return min + " min";
        return String.format(Locale.getDefault(), "%d h %02d min", min / 60, min % 60);
    }

    private static String date(LocalDate d, int format) {
        if (format == 1) return d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG));
        String pattern = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(),
                format == 0 && d.getYear() != LocalDate.now().getYear() ? "dMMMy" : "dMMM");
        return d.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()));
    }

    private static String withUnit(String value, CharSequence unit) {
        if (unit == null || unit.length() == 0) return value;
        char c = unit.charAt(0);
        return c == '%' || c == '°' || c == '′' || c == '″' ? value + unit : value + " " + unit;
    }

    private static Number number(Bundle b, String key) {
        Object o = b.get(key);
        if (o instanceof Number) return (Number) o;
        throw new IllegalArgumentException(key);
    }
}
