package cz.spojenka.android.ui.helpers;

import android.content.Context;
import android.util.AttributeSet;

/**
 * Custom implementation of {@link OffsetLinearLayoutManager} designed to work alongside
 * {@link LoadMoreListAdapter} so that the parent {@link androidx.recyclerview.widget.RecyclerView}
 * always starts at the first content item (index 1) and not at the control button item (index 0).
 * This is effectively a {@link OffsetLinearLayoutManager} with the target start position set to 1.
 */
public class LoadMoreLinearLayoutManager extends OffsetLinearLayoutManager {
    public LoadMoreLinearLayoutManager(Context context) {
        super(context);
        init();
    }

    public LoadMoreLinearLayoutManager(Context context, int orientation, boolean reverseLayout) {
        super(context, orientation, reverseLayout);
        init();
    }

    public LoadMoreLinearLayoutManager(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        setTargetStartPos(1, 0);
    }

    public void reset() {
        init();
    }
}
