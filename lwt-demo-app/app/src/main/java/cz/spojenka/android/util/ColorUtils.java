package cz.spojenka.android.util;

import android.content.Context;
import android.graphics.Color;

import androidx.annotation.ColorInt;

/**
 * Utility class for color manipulation.
 */
public class ColorUtils {

    private static final float[] TEMP_HSV = new float[3];

    /**
     * Get the luminance of a color.
     * See <a href="https://www.w3.org/WAI/GL/wiki/Relative_luminance">Relative luminance</a>
     *
     * @param color A color in linear RGB (not sRGB!) color space.
     * @return The luminance of the color.
     */
    public static double getLuminance(@ColorInt int color) {
        return 0.2126 * Color.red(color) + 0.7152 * Color.green(color) + 0.0722 * Color.blue(color);
    }

    /**
     * Convert a color from sRGB to linear RGB color space.
     *
     * @param color Color in sRGB color space.
     * @return The color in linear RGB color space.
     */
    public static @ColorInt int sRGBToLinearColor(@ColorInt int color) {
        return Color.rgb(sRGBToLinear(Color.red(color)), sRGBToLinear(Color.green(color)), sRGBToLinear(Color.blue(color)));
    }

    private static int sRGBToLinear(int channel) {
        return (int) (sRGBToLinear(channel / 255f) * 255f);
    }

    private static float sRGBToLinear(float channel) {
        return channel <= 0.03928f ? channel / 12.92f : (float) Math.pow((channel + 0.055f) / 1.055f, 2.4f);
    }

    /**
     * Choose a color that is visible on a given background color.
     * The chosen color is either the dark or light option, depending on the luminance of the background color.
     *
     * @param backgroundColor The background color
     * @param darkOption The dark response color, a color that is visible on light backgrounds
     * @param lightOption The light response color, a color that is visible on dark backgrounds
     * @return The chosen color
     */
    public static @ColorInt int createColorOnBackground(@ColorInt int backgroundColor, @ColorInt int darkOption, @ColorInt int lightOption) {
        return getLuminance(sRGBToLinearColor(backgroundColor)) < 128 ? lightOption : darkOption;
    }

    /**
     * Adjust the brightness of a color.
     *
     * @param color The color
     * @param factor Factor to multiply the brightness by
     * @return The color with adjusted brightness
     */
    public static @ColorInt int adjustColorBrightness(@ColorInt int color, float factor) {
        int a = Color.alpha(color);
        int r = clampColorChannel((int)(Color.red(color) * factor));
        int g = clampColorChannel((int)(Color.green(color) * factor));
        int b = clampColorChannel((int)(Color.blue(color) * factor));
        return Color.argb(a, r, g, b);
    }

    /**
     * Adjust the saturation of a color.
     *
     * @param color The color
     * @param factor Factor to multiply the saturation by
     * @return The color with adjusted saturation
     */
    public static @ColorInt int adjustColorSaturation(@ColorInt int color, float factor) {
        synchronized (TEMP_HSV) {
            Color.colorToHSV(color, TEMP_HSV);
            TEMP_HSV[1] *= factor;
            return Color.HSVToColor(TEMP_HSV);
        }
    }

    /**
     * Clamp a value to the range of an 8-bit color channel, that is, 0 to 255.
     *
     * @param color The color channel value
     * @return The clamped value
     */
    public static int clampColorChannel(int color) {
        return Math.min(255, Math.max(0, color));
    }

    /**
     * Set the alpha channel of a color.
     *
     * @param color The color
     * @param alpha New alpha value, 0 to 255
     * @return The color with the new alpha value
     */
    public static int setAlpha(@ColorInt int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    public static boolean equalsIgnoreAlpha(@ColorInt int color1, @ColorInt int color2) {
        return (color1 & 0x00FFFFFF) == (color2 & 0x00FFFFFF);
    }
}
