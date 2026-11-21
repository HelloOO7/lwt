package cz.spojenka.lwt.demoapp;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ResponsiveGridRecyclerView extends RecyclerView {

    private Integer minColumnWidth;

    public ResponsiveGridRecyclerView(@NonNull Context context) {
        super(context);
    }

    public ResponsiveGridRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ResponsiveGridRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setMinColumnWidth(int minColumnWidth) {
        this.minColumnWidth = minColumnWidth;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        if (getLayoutManager() instanceof GridLayoutManager grid) {
            int availWidth = (getMeasuredWidth() - getPaddingLeft() - getPaddingRight());
            int numColumns = Math.max(1, availWidth / minColumnWidth);
            grid.setSpanCount(numColumns);
        }
    }
}
