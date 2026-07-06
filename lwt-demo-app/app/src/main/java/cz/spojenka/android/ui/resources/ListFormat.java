package cz.spojenka.android.ui.resources;

import android.content.Context;

import java.util.List;

import cz.spojenka.android.system.ConfigurationValue;
import cz.spojenka.lwt.demoapp.R;

public class ListFormat {

    private static final ConfigurationValue<ConfigSet> configVal = new ConfigurationValue<>() {
        @Override
        protected ConfigSet fetch(Context context) {
            String sep = context.getString(R.string.list_format_separator);
            String lastSep = context.getString(R.string.list_format_last_separator);
            SerialCommaPolicy scp = SerialCommaPolicy.valueOf(context.getString(R.string.list_format_serial_comma_policy));
            String lastSepOxford = lastSep;
            if (scp == SerialCommaPolicy.OXFORD) {
                if (!sep.isEmpty() && Character.isWhitespace(sep.charAt(sep.length() - 1))) {
                    lastSepOxford = lastSep.stripLeading();
                }
            }
            return new ConfigSet(
                    sep,
                    lastSep,
                    lastSepOxford,
                    scp
            );
        }
    };

    public static String formatList(Context context, List<?> elements) {
        ConfigSet config = configVal.get(context);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < elements.size(); i++) {
            if (i > 0) {
                if (i == elements.size() - 1) {
                    if (config.serialCommaPolicy == SerialCommaPolicy.OXFORD && elements.size() > 2) {
                        sb.append(config.separator).append(config.lastSeparatorOxford);
                    } else {
                        sb.append(config.lastSeparator);
                    }
                } else {
                    sb.append(config.separator);
                }
            }
            sb.append(elements.get(i));
        }
        return sb.toString();
    }

    public static enum SerialCommaPolicy {
        NORMAL,
        OXFORD
    }

    private static record ConfigSet(String separator, String lastSeparator, String lastSeparatorOxford, SerialCommaPolicy serialCommaPolicy) {

    }
}
