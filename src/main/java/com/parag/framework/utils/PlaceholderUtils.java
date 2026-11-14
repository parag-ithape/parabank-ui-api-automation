package com.parag.framework.utils;

/**
 * Replaces {0}, {1}… literally.
 *
 * <p>Used instead of MessageFormat, which treats single quotes as escape characters and would silently
 * break XPaths such as {@code //td[text()='{0}']} and messages containing apostrophes.</p>
 */
public final class PlaceholderUtils {

    private PlaceholderUtils() { }

    public static String fill(String template, Object... args) {
        String result = template;
        for (int i = 0; args != null && i < args.length; i++) {
            result = result.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return result;
    }
}
