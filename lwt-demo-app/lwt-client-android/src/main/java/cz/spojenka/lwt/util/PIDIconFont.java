package cz.spojenka.lwt.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PIDIconFont {

    private static final String[] CHAR_CODES = {
            "c_RequestStop", "\uD83D\uDD14",
            "c_Train", "Ⓢ",
            "c_SBahn", "Ⓢ",
            "c_Air", "✈",
            "c_Ferry", "⚓",
            "c_UndergroundA", "\uD83C\uDD70",
            "c_UndergroundB", "\uD83C\uDD71",
            "c_UndergroundC", "\uD83C\uDD72",
            "c_UndergroundD", "\uD83C\uDD73",
    };

    private static final Set<String> TINTABLE_ICONS = Set.of(
            "c_RequestStop", "c_Train", "c_SBahn"
    );

    private static final Map<String, String> ICON_MAP = new HashMap<>();

    static {
        for (int i = 0; i < CHAR_CODES.length; i += 2) {
            ICON_MAP.put(CHAR_CODES[i], CHAR_CODES[i + 1]);
        }
    }

    public static String getCharCodeForIcon(String iconId) {
        return ICON_MAP.getOrDefault(iconId, "");
    }

    public static boolean isIconTintable(String iconId) {
        return TINTABLE_ICONS.contains(iconId);
    }
}
