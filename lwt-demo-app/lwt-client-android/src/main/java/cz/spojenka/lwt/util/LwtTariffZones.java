package cz.spojenka.lwt.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import androidx.annotation.Nullable;

public class LwtTariffZones {

    public static List<Entry> parse(String tariffZonesString) {
        List<Entry> out = new ArrayList<>();
        for (String e : tariffZonesString.split(";")) {
            String[] parts = e.split(" ", 2);
            List<String> zones = List.of(parts[parts.length - 1].split(","));
            if (parts.length == 1) {
                out.add(new Entry(null, zones));
            } else {
                out.add(new Entry(parts[0], zones));
            }
        }
        return out;
    }

    public static Entry findEntryForTariffSystem(String tariffZonesString, String tariffSystem) {
        List<Entry> entries = parse(tariffZonesString);
        Entry nullEntry = null;
        for (Entry e : entries) {
            if (Objects.equals(e.tariffSystem(), tariffSystem)) {
                return e;
            } else if (e.tariffSystem() == null) {
                nullEntry = e;
            }
        }
        return nullEntry;
    }

    public static record Entry(@Nullable String tariffSystem, List<String> zones) {

    }
}
