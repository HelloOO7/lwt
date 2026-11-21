package cz.spojenka.android.ui.helpers;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Simple {@link RecyclerView.ItemDecoration} that adds a horizontal
 * gap between RecyclerView items. Unlike {@link androidx.recyclerview.widget.DividerItemDecoration},
 * this class does not use a drawable, but only adds a blank horizontal space.
 *
 * <p>
 * See <a href="https://stackoverflow.com/questions/24618829/how-to-add-dividers-and-spaces-between-items-in-recyclerview">StackOverflow</a>
 */
public class HorizontalSpaceItemDecoration extends RecyclerView.ItemDecoration {

    private final int horizontalSpaceHeight;
    private boolean innerOnly;
    private boolean invert;

    /**
     * Creates a new instance of {@link HorizontalSpaceItemDecoration} with the given vertical space height.
     *
     * @param horizontalSpaceHeight Vertical space height in pixels.
     * @param innerOnly             If true, space will be added only between items, not before the first or after the last item.
     * @param invert                True to apply the spacing at the top of each item instead of the bottom.
     */
    public HorizontalSpaceItemDecoration(int horizontalSpaceHeight, boolean innerOnly, boolean invert) {
        this.horizontalSpaceHeight = horizontalSpaceHeight;
        this.innerOnly = innerOnly;
        this.invert = invert;
    }

    public HorizontalSpaceItemDecoration(int horizontalSpaceHeight, boolean innerOnly) {
        this(horizontalSpaceHeight, innerOnly, false);
    }

    public HorizontalSpaceItemDecoration(int horizontalSpaceHeight) {
        this(horizontalSpaceHeight, true);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        boolean rtl = parent.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        Integer start = null;
        Integer end = null;
        if (!innerOnly) {
            if (!invert) {
                if (parent.getChildAdapterPosition(view) == 0) {
                    start = horizontalSpaceHeight;
                }
                end = horizontalSpaceHeight;
            } else {
                RecyclerView.Adapter<?> adapter = parent.getAdapter();
                if (adapter != null && parent.getChildAdapterPosition(view) == adapter.getItemCount() - 1) {
                    end = horizontalSpaceHeight;
                }
                start = horizontalSpaceHeight;
            }
        } else {
            if (parent.getChildAdapterPosition(view) != 0) {
                if (!invert) {
                    start = horizontalSpaceHeight;
                } else {
                    end = horizontalSpaceHeight;
                }
            }
        }
        if (start != null) {
            if (rtl) {
                outRect.left = start;
            } else {
                outRect.right = start;
            }
        }
        if (end != null) {
            if (rtl) {
                outRect.right = end;
            } else {
                outRect.left = end;
            }
        }
    }
}