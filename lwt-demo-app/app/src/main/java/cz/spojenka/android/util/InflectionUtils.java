package cz.spojenka.android.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Class for transforming inflection templates.
 * <p>
 * An inflection template is a string resource that contains comma-separated options for each form.
 * Given that some languages do not inflect certain terms, the localized version may also simply
 * contain one option. {@link #inflectFromTemplate(String, int)} can then be used to get
 * the inflected version of a string, or the default if the current language does not inflect it.
 */
public class InflectionUtils {

    private static final Map<String, String[]> templateCache = new HashMap<>();

    /**
     * Get the inflected form of a string from a template.
     * The separated template is cached between runs.
     *
     * @param template The template
     * @param templateIndex Index of the inflected form
     * @return The inflected form, or the default form if there is only one.
     */
    public static String inflectFromTemplate(String template, int templateIndex) {
        String[] options = templateCache.computeIfAbsent(template, key -> key.split(","));
        String selOption = options[0];
        if (templateIndex < options.length) {
            selOption = options[templateIndex];
        }
        return selOption;
    }
}
