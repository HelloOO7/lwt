package cz.spojenka.lwt;

import java.util.HashMap;
import java.util.Map;

public class LwtTicketMetadata {

    private final Map<String, String> data;

    private LwtTicketMetadata(Map<String, String> data) {
        this.data = data;
    }

    public String getTripKey() {
        return data.get("TK");
    }

    public static LwtTicketMetadata parse(String metadata) {
        String[] kvPairs = metadata.split("\\|");
        Map<String, String> res = new HashMap<>();
        for (String kv : kvPairs) {
            String[] parts = kv.split(":", 2);
            if (parts.length == 2) {
                res.put(parts[0], parts[1]);
            }
        }
        return new LwtTicketMetadata(res);
    }
}
