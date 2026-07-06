package cz.spojenka.android.ui.helpers;

import android.content.Context;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple class for recycling views. It keeps track of all views that it has created
 * and allows to reuse them once they were detached from their parents.
 */
public abstract class ViewRecycler<V extends View> {

    private final Context context;
    private final List<V> views = new ArrayList<>();

    public ViewRecycler(Context context) {
        this.context = context;
    }

    public V getView() {
        for (V view : views) {
            if (view.getParent() == null) {
                return view;
            }
        }
        V view = newView(context);
        views.add(view);
        return view;
    }

    protected abstract V newView(Context context);
}
