package cz.spojenka.lwt.cicomock.services;

import java.util.Set;

public class PictogramStripper {

    private static final Set<Character> ALLOWED_CHARS = Set.of(
            ',', '.', '-', '(', ')', '/'
    );

    public static String stripPictograms(String lineName) {
        if (lineName == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (char c : lineName.toCharArray()) {
            if (Character.isLetterOrDigit(c) || Character.isWhitespace(c) || ALLOWED_CHARS.contains(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
