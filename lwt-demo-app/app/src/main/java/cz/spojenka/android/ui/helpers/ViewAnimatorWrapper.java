package cz.spojenka.android.ui.helpers;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ViewAnimatorWrapper extends FrameLayout {

    public ViewAnimatorWrapper(@NonNull Context context) {
        super(context);
    }

    public ViewAnimatorWrapper(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ViewAnimatorWrapper(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    {
        setClipChildren(false);
        setClipToPadding(false);
    }

    public static ViewAnimatorWrapper wrap(View view) {
        ViewAnimatorWrapper wrapper = new ViewAnimatorWrapper(view.getContext());
        wrapper.addView(view, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return wrapper;
    }

    public float getTranslationXRelative() {
        return getTranslationX() / getWidth();
    }

    public void setTranslationXRelative(float percent) {
        setTranslationX(percent * getWidth());
    }

    public float getTranslationYRelative() {
        return getTranslationY() / getHeight();
    }

    public void setTranslationYRelative(float percent) {
        setTranslationY(percent * getHeight());
    }
}
