package cz.spojenka.lwt.demoapp;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class LitackaUtils {

    private static final List<String> PRAGUE_SORT_ORDER = List.of(
            "P", "0", "B"
    );

    public static String createCommaSeparatedList(Collection<String> zones) {
        return String.join(",", zones);
    }

    public static List<String> parseCommaSeparatedList(String zones) {
        if (zones == null || zones.isEmpty()) {
            return List.of();
        }
        return List.of(zones.split(","));
    }

    public static List<String> sortZonesForPrint(List<String> zones) {
        return zones.stream().sorted(Comparator.comparingInt(zone -> {
            int index = PRAGUE_SORT_ORDER.indexOf(zone);
            try {
                return index == -1 ? 1000 + Integer.parseInt(zone) : index;
            } catch (NumberFormatException e) {
                return 1000;
            }
        })).collect(Collectors.toList());
    }

    public static String getPreviousZone(String zone) {
        int index = PRAGUE_SORT_ORDER.indexOf(zone);
        if (index > 0) {
            return PRAGUE_SORT_ORDER.get(index - 1);
        } else if (index == 0) {
            return null;
        }
        int zoneNumber = Integer.parseInt(zone);
        if (zoneNumber > 1) {
            return String.valueOf(zoneNumber - 1);
        } else {
            return "B";
        }
    }

    public static String getNextZone(String zone) {
        int index = PRAGUE_SORT_ORDER.indexOf(zone);
        if (index != -1) {
            if (index < PRAGUE_SORT_ORDER.size() - 1) {
                return PRAGUE_SORT_ORDER.get(index + 1);
            } else {
                return "1";
            }
        }
        int zoneNumber = Integer.parseInt(zone);
        //this may return zones that do not exist in PID yet
        return String.valueOf(zoneNumber + 1);
    }

    public static List<String> ensureZonesContiguous(List<String> zones) {
        List<String> out = sortZonesForPrint(zones);
        if (out.size() <= 1) {
            return out;
        }
        String firstZone = out.get(0);
        String lastZone = out.get(out.size() - 1);

        //back to front to prevent overflow
        String current = getPreviousZone(lastZone);
        while (current != null && !current.equals(firstZone)) {
            if (!out.contains(current)) {
                out.add(current);
            }
            current = getPreviousZone(current);
        }
        return sortZonesForPrint(out);
    }

    public static String formatTicketIDForPrint(int tid) {
        final int PADDING_STEP = 3;

        String ticketString = String.valueOf(tid);
        int remainder = ticketString.length() % PADDING_STEP;
        String paddedString = (remainder != 0 ? "000".substring(remainder) : "") + ticketString;

        return IntStream.range(0, paddedString.length() / PADDING_STEP)
                .mapToObj(i -> paddedString.substring(i * PADDING_STEP, (i + 1) * PADDING_STEP))
                .collect(Collectors.joining("-"));
    }

    public static boolean isPragueZone(String zone) {
        return PRAGUE_SORT_ORDER.contains(zone);
    }

    public static boolean isInnerPragueZone(String zone) {
        return "P".equals(zone);
    }

    public static boolean isOuterZone(String zone) {
        return !isPragueZone(zone);
    }

    public static boolean isForeignRegionZone(String zone) {
        try {
            return Integer.parseInt(zone) >= 10;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
