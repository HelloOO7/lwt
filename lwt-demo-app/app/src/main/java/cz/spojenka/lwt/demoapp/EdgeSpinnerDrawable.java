package cz.spojenka.lwt.demoapp;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.shape.ShapeAppearancePathProvider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class EdgeSpinnerDrawable extends Drawable {

    // written mostly by Gemini

    private final ShapeAppearanceModel shape;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shapePath = new Path();
    private final Path segmentPath = new Path();
    private final PathMeasure pathMeasure = new PathMeasure();
    private final RectF rectF = new RectF();
    private final ShapeAppearancePathProvider pathProvider = new ShapeAppearancePathProvider();

    private float progress = 0f;
    private float segmentLength = 0.2f;
    private long duration = 2000;
    private long startTime = SystemClock.uptimeMillis();

    private boolean indeterminate = true;
    private float progressRatio = 0f;
    private float startOffset = 0f;

    private ColorStateList tintList;
    private PorterDuff.Mode tintMode = PorterDuff.Mode.SRC_IN;
    private ColorFilter tintFilter;

    public EdgeSpinnerDrawable(ShapeAppearanceModel shape) {
        this.shape = shape;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(8f);
    }

    public void setStrokeWidth(float width) {
        paint.setStrokeWidth(width);
        updatePath(getBounds());
        invalidateSelf();
    }

    public void setSegmentLength(float length) {
        this.segmentLength = length;
        invalidateSelf();
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public void setIndeterminate(boolean indeterminate) {
        if (this.indeterminate != indeterminate) {
            this.indeterminate = indeterminate;
            invalidateSelf();
        }
    }

    public boolean isIndeterminate() {
        return indeterminate;
    }

    public void setProgress(float progress) {
        this.progressRatio = Math.max(0f, Math.min(1f, progress));
        if (!indeterminate) {
            invalidateSelf();
        }
    }

    public void setStartOffset(float offset) {
        this.startOffset = offset;
        invalidateSelf();
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);
        updatePath(bounds);
    }

    private void updatePath(Rect bounds) {
        rectF.set(bounds);
        float inset = paint.getStrokeWidth() / 2f;
        rectF.inset(inset, inset);
        pathProvider.calculatePath(shape, 1f, rectF, shapePath);
        pathMeasure.setPath(shapePath, false);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        float totalLength = pathMeasure.getLength();
        if (totalLength <= 0) return;

        float start;
        float end;

        if (indeterminate) {
            long now = SystemClock.uptimeMillis();
            progress = ((now - startTime) % duration) / (float) duration;
            start = totalLength * (startOffset + progress);
            end = start + totalLength * segmentLength;
        } else {
            start = totalLength * startOffset;
            end = start + totalLength * progressRatio;
        }

        segmentPath.reset();
        float s = start % totalLength;
        float e = end % totalLength;

        if (s < 0) s += totalLength;
        if (e < 0) e += totalLength;

        if (start != end && s == e) {
            // Full circle
            pathMeasure.getSegment(0, totalLength, segmentPath, true);
        } else if (e > s) {
            pathMeasure.getSegment(s, e, segmentPath, true);
        } else if (e < s) {
            pathMeasure.getSegment(s, totalLength, segmentPath, true);
            pathMeasure.getSegment(0, e, segmentPath, true);
        }

        paint.setColorFilter(tintFilter != null ? tintFilter : null);
        canvas.drawPath(segmentPath, paint);

        if (indeterminate) {
            invalidateSelf();
        }
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public void setTintList(@Nullable ColorStateList tint) {
        this.tintList = tint;
        this.tintFilter = updateTintFilter(tint, tintMode);
        invalidateSelf();
    }

    @Override
    public void setTintMode(@Nullable PorterDuff.Mode tintMode) {
        this.tintMode = tintMode;
        this.tintFilter = updateTintFilter(tintList, tintMode);
        invalidateSelf();
    }

    @Override
    protected boolean onStateChange(@NonNull int[] state) {
        if (tintList != null && tintMode != null) {
            tintFilter = updateTintFilter(tintList, tintMode);
            invalidateSelf();
            return true;
        }
        return false;
    }

    @Override
    public boolean isStateful() {
        return tintList != null && tintList.isStateful();
    }

    private ColorFilter updateTintFilter(ColorStateList tint, PorterDuff.Mode tintMode) {
        if (tint == null || tintMode == null) {
            return null;
        }
        int color = tint.getColorForState(getState(), Color.TRANSPARENT);
        return new PorterDuffColorFilter(color, tintMode);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
