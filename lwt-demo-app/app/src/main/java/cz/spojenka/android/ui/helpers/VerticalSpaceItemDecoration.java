package cz.spojenka.android.ui.helpers;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Simple {@link RecyclerView.ItemDecoration} that adds a vertical
 * gap between RecyclerView items. Unlike {@link androidx.recyclerview.widget.DividerItemDecoration},
 * this class does not use a drawable, but only adds a blank vertical space.
 *
 * <p>
 * See <a href="https://stackoverflow.com/questions/24618829/how-to-add-dividers-and-spaces-between-items-in-recyclerview">StackOverflow</a>
 */
public class VerticalSpaceItemDecoration extends RecyclerView.ItemDecoration {

    private final int verticalSpaceHeight;
    private boolean innerOnly;
    private boolean invert;

    /**
     * Creates a new instance of {@link VerticalSpaceItemDecoration} with the given vertical space height.
     *
     * @param verticalSpaceHeight Vertical space height in pixels.
     * @param innerOnly           If true, space will be added only between items, not before the first or after the last item.
     * @param invert              True to apply the spacing at the top of each item instead of the bottom.
     */
    public VerticalSpaceItemDecoration(int verticalSpaceHeight, boolean innerOnly, boolean invert) {
        this.verticalSpaceHeight = verticalSpaceHeight;
        this.innerOnly = innerOnly;
        this.invert = invert;
    }

    public VerticalSpaceItemDecoration(int verticalSpaceHeight, boolean innerOnly) {
        this(verticalSpaceHeight, innerOnly, false);
    }

    public VerticalSpaceItemDecoration(int verticalSpaceHeight) {
        this(verticalSpaceHeight, true);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (!innerOnly) {
            if (!invert) {
                if (parent.getChildAdapterPosition(view) == 0) {
                    outRect.top = verticalSpaceHeight;
                }
                outRect.bottom = verticalSpaceHeight;
            } else {
                RecyclerView.Adapter<?> adapter = parent.getAdapter();
                if (adapter != null && parent.getChildAdapterPosition(view) == adapter.getItemCount() - 1) {
                    outRect.bottom = verticalSpaceHeight;
                }
                outRect.top = verticalSpaceHeight;
            }
        } else {
            if (parent.getChildAdapterPosition(view) != 0) {
                if (!invert) {
                    outRect.top = verticalSpaceHeight;
                } else {
                    outRect.bottom = verticalSpaceHeight;
                }
            }
        }
    }
}