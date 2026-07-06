package cz.spojenka.android.ui.helpers;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Drawable used to create gaps between items in {@link com.google.android.flexbox.FlexboxLayout}.
 *
 * @see #horizontal(int)
 * @see #vertical(int)
 */
public class FlexBoxGapDrawable extends Drawable {

    private final int width;
    private final int height;

    private FlexBoxGapDrawable(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /**
     * Create a gap in the horizontal item flow.
     * This should be attached with {@link com.google.android.flexbox.FlexboxLayout#setDividerDrawableVertical(Drawable)},
     * despite the name.
     *
     * @param width Width of the gap in pixels.
     * @return The drawable.
     */
    public static FlexBoxGapDrawable horizontal(int width) {
        return new FlexBoxGapDrawable(width, 1);
    }

    /**
     * Create a gap in the vertical item flow.
     * This should be attached with {@link com.google.android.flexbox.FlexboxLayout#setDividerDrawableHorizontal(Drawable)},
     * despite the name.
     *
     * @param height Height of the gap in pixels.
     * @return The drawable.
     */
    public static FlexBoxGapDrawable vertical(int height) {
        return new FlexBoxGapDrawable(1, height);
    }

    @Override
    public int getIntrinsicWidth() {
        return width;
    }

    @Override
    public int getIntrinsicHeight() {
        return height;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {

    }

    @Override
    public void setAlpha(int alpha) {

    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }
}
