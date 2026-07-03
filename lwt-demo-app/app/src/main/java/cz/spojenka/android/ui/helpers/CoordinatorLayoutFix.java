package cz.spojenka.android.ui.helpers;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;

/**
 * Fix for CoordinatorLayout which has nested scrolling children with different axes.
 * With the normal CoordinatorLayout, if a touch event is intercepted by a parent vertically
 * scrolling view, such as NestedScrollView, then, as per interception rules, a cancellation event
 * is sent to child views. If that child view is a horizontally scrolling view, such as
 * a ViewPager2, it will call onStopNestedScroll, which causes the CoordinatorLayout to
 * stop dispatching nested scroll events to that child, despite the vertical
 * scroll in progress.
 * <p>
 * The negative consequences of this are broken CollapsingToolbarLayouts in such layout
 * structures, which using this class will fix.
 * <p>
 * !! THIS USES REFLECTION AND MUST BE CAREFULLY TESTED WHEN UPDATING ANDROIDX DEPENDENCIES !!
 */
public class CoordinatorLayoutFix extends CoordinatorLayout {

    public CoordinatorLayoutFix(@NonNull Context context) {
        super(context);
    }

    public CoordinatorLayoutFix(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public CoordinatorLayoutFix(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean onStartNestedScroll(View child, View target, int axes, int type) {
        boolean handled = false;

        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View view = getChildAt(i);
            if (view.getVisibility() == View.GONE) {
                // If it's GONE, don't dispatch
                continue;
            }
            final LayoutParamsFix lp = obtainFixedLayoutParams(view);
            final Behavior viewBehavior = lp.getBehavior();
            if (viewBehavior != null) {
                final boolean accepted = viewBehavior.onStartNestedScroll(this, view, child,
                        target, axes, type);
                handled |= accepted;
                lp.startNestedScroll(target, type, axes);
            } else {
                lp.resetNestedScrollState();
            }
        }
        return handled;
    }

    @Override
    public void onStopNestedScroll(View target, int type) {
        super.onStopNestedScroll(target, type);
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            obtainFixedLayoutParams(getChildAt(i)).stopNestedScroll(target, type);
        }
    }

    private LayoutParamsFix obtainFixedLayoutParams(View view) {
        //we can not create fixed layout params directly in XML, as the superclass constructor
        //for them that takes Context and AttributeSet is package-private -_-
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp instanceof LayoutParamsFix fixed) {
            return fixed;
        } else if (lp instanceof LayoutParams clp) {
            LayoutParamsFix fixed = new LayoutParamsFix(clp);
            view.setLayoutParams(fixed);
            return fixed;
        } else {
            LayoutParamsFix fixed = new LayoutParamsFix(lp);
            view.setLayoutParams(fixed);
            return fixed;
        }
    }

    public static class LayoutParamsFix extends LayoutParams {

        private View scrollingChildTouchV;
        private View scrollingChildTouchH;
        private View scrollingChildNonTouchV;
        private View scrollingChildNonTouchH;

        private static Method setNestedScrollAcceptedMethod;

        public LayoutParamsFix(LayoutParams p) {
            super(p);
            // the superclass constructor is not implemented correctly
            cloneFrom(p);
        }

        public LayoutParamsFix(ViewGroup.LayoutParams p) {
            super(p);
        }

        private void cloneFrom(LayoutParams p) {
            for (Field field : LayoutParams.class.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    field.set(this, field.get(p));
                } catch (ReflectiveOperationException ignored) {
                    // Ignore
                }
            }
        }

        private void startNestedScroll(View target, int type, int axes) {
            if (type == ViewCompat.TYPE_TOUCH) {
                if ((axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0) {
                    scrollingChildTouchV = target;
                }
                if ((axes & ViewCompat.SCROLL_AXIS_HORIZONTAL) != 0) {
                    scrollingChildTouchH = target;
                }
            } else {
                if ((axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0) {
                    scrollingChildNonTouchV = target;
                }
                if ((axes & ViewCompat.SCROLL_AXIS_HORIZONTAL) != 0) {
                    scrollingChildNonTouchH = target;
                }
            }
            commitNestedScrollState();
        }

        private void resetNestedScrollState() {
            scrollingChildTouchV = null;
            scrollingChildTouchH = null;
            scrollingChildNonTouchV = null;
            scrollingChildNonTouchH = null;
            commitNestedScrollState();
        }

        private void stopNestedScroll(View target, int type) {
            if (type == ViewCompat.TYPE_TOUCH) {
                if (scrollingChildTouchV == target) {
                    scrollingChildTouchV = null;
                }
                if (scrollingChildTouchH == target) {
                    scrollingChildTouchH = null;
                }
            } else {
                if (scrollingChildNonTouchV == target) {
                    scrollingChildNonTouchV = null;
                }
                if (scrollingChildNonTouchH == target) {
                    scrollingChildNonTouchH = null;
                }
            }
            commitNestedScrollState();
        }

        private void commitNestedScrollState() {
            callSetNestedScrollAccepted(ViewCompat.TYPE_TOUCH, scrollingChildTouchV != null || scrollingChildTouchH != null);
            callSetNestedScrollAccepted(ViewCompat.TYPE_NON_TOUCH, scrollingChildNonTouchV != null || scrollingChildNonTouchH != null);
        }

        private void callSetNestedScrollAccepted(int type, boolean accepted) {
            try {
                if (setNestedScrollAcceptedMethod == null) {
                    setNestedScrollAcceptedMethod = LayoutParams.class
                            .getDeclaredMethod("setNestedScrollAccepted", int.class, boolean.class);
                    setNestedScrollAcceptedMethod.setAccessible(true);
                }
                setNestedScrollAcceptedMethod.invoke(this, type, accepted);
            } catch (ReflectiveOperationException ignored) {
                // Ignore
            }
        }
    }
}
