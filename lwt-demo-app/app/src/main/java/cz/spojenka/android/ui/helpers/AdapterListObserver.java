package cz.spojenka.android.ui.helpers;

import java.util.List;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;
import cz.spojenka.android.system.livedata.LiveList;

/**
 * Helper class that observes changes in a {@link LiveList} and updates an {@link ArrayListAdapter}
 * accordingly.
 *
 * @param <T> The type of the items in the LiveList
 */
public class AdapterListObserver<T> implements LiveList.UpdateObserver<T>, Observer<List<T>> {

    private final ArrayListAdapter<T, ?> adapter;

    private LiveList<T> target;
    private LifecycleOwner lifecycleOwner;

    /**
     * Creates a new AdapterListObserver that will manage the given adapter.
     * It may be bound to a LiveList using {@link LiveList#observeUpdates(LifecycleOwner, LiveList.UpdateObserver)}
     *
     * @param adapter The adapter
     */
    public AdapterListObserver(ArrayListAdapter<T, ?> adapter) {
        this.adapter = adapter;
    }

    @Override
    public void onInserted(int start, List<T> items) {
        System.out.println("onInserted: start=" + start + ", items=" + items + "; " + this);
        adapter.addAll(start, items);
    }

    @Override
    public void onRemoved(int start, int count) {
        System.out.println("onRemoved: start=" + start + ", count=" + count + "; " + this);
        adapter.removeRange(start, count);
    }

    @Override
    public void onModified(int start, List<T> items) {
        System.out.println("onModified: start=" + start + ", items=" + items + "; " + this);
        adapter.setRange(start, items);
    }

    @Override
    public void onChanged(List<T> list) {
        unbindUpdateObserver();
        adapter.replaceAll(list);
        bindUpdateObserver();
    }

    private void unbindUpdateObserver() {
        target.removeUpdateObserver(this);
    }

    private void bindUpdateObserver() {
        if (lifecycleOwner != null) {
            target.observeUpdates(lifecycleOwner, this);
        } else {
            target.observeUpdatesForever(this);
        }
    }

    public void attach(LifecycleOwner owner, LiveList<T> liveList) {
        this.target = liveList;
        this.lifecycleOwner = owner;
        liveList.observe(owner, this);
        bindUpdateObserverIfUninitialized();
    }

    public void attach(LiveList<T> liveList) {
        this.target = liveList;
        this.lifecycleOwner = null;
        liveList.observeForever(this);
        bindUpdateObserverIfUninitialized();
    }

    private void bindUpdateObserverIfUninitialized() {
        if (!target.isInitialized()) {
            bindUpdateObserver();
        }
    }

    public void detach() {
        if (target != null) {
            target.removeObserver(this);
            target.removeUpdateObserver(this);
            target = null;
            lifecycleOwner = null;
        }
    }
}
