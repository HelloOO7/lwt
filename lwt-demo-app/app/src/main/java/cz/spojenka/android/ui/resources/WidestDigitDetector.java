package cz.spojenka.android.ui.resources;

import android.content.Context;
import android.text.TextPaint;
import android.widget.TextView;

import cz.spojenka.android.system.ConfigurationValue;

public class WidestDigitDetector {

    private static final ConfigurationValue<String> value = new ConfigurationValue<>() {
        @Override
        protected String fetch(Context context) {
            String res = "0";
            TextView textView = new TextView(context);
            textView.setText(res);
            TextPaint textPaint = textView.getPaint();
            float widest = 0;
            for (char c = '0'; c <= '9'; c++) {
                String digit = String.valueOf(c);
                float width = textPaint.measureText(digit);
                if (width > widest) {
                    widest = width;
                    res = digit;
                }
            }
            return res;
        }
    };

    public static String get(Context context) {
        return value.get(context);
    }
}
