package cz.spojenka.android.ui.helpers;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.widget.ActionBarContainer;
import androidx.appcompat.widget.ActionBarOverlayLayout;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;

/**
 * Hook into the AndroidX/AppCompat toolbars that fixes edge-to-edge behavior to use padding
 * on the toolbar instead of margins on the container (which insert unsightly gaps around the toolbar).
 */
public class EdgeToEdgeToolbar implements LayoutInflaterHook.ViewDecorator {

    private View actionBarOverlay;
    private View actionBarContainer;
    private View toolbar;

    @SuppressLint("RestrictedApi")
    @Override
    public void decorate(View view, String name, Context context, AttributeSet attrs) {
        if (view instanceof ActionBarOverlayLayout) {
            actionBarOverlay = view;
        } else if (view instanceof ActionBarContainer) {
            actionBarContainer = view;
        } else if (view instanceof Toolbar) {
            toolbar = view;
        }
    }

    public void onPostCreate() {
        if (toolbar == null) {
            return;
        }

        if (actionBarOverlay != null && actionBarContainer != null) {
            // must be set in onPostCreate so that it overrides AndroidX's listener
            ViewCompat.setOnApplyWindowInsetsListener(actionBarOverlay, (v, insets) -> {
                insets = ViewCompat.onApplyWindowInsets(v, insets);
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) actionBarContainer.getLayoutParams();
                toolbar.setPadding(lp.leftMargin, 0, lp.rightMargin, 0);
                //clear horizontal margin
                lp.setMarginStart(0);
                lp.setMarginEnd(0);
                actionBarContainer.setLayoutParams(lp);
                return insets;
            });
            actionBarOverlay.requestApplyInsets();
        } else {
            // custom AndroidX toolbar - not AppCompat
            EdgeToEdgeSupport.installInsets(toolbar, EdgeToEdgeSupport.SIDE_HORIZONTAL, EdgeToEdgeSupport.FLAG_APPLY_AS_PADDING);
            toolbar.requestApplyInsets();
        }
    }
}
