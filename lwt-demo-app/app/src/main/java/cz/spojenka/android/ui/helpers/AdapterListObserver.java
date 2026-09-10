package cz.spojenka.android.ui.helpers;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
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
        adapter.addAll(start, items);
    }

    @Override
    public void onRemoved(int start, int count) {
        adapter.removeRange(start, count);
    }

    @Override
    public void onModified(int start, List<T> items) {
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
        target.observeUpdatesForever(this);
    }

    public void attach(LifecycleOwner owner, LiveList<T> liveList) {
        this.target = liveList;
        liveList.observeForever(this);
        // bugfix: the list can be updated even in the background (such as BLE scans).
        // in that case, we must not pause updates if the activity is in STOPPED state,
        // as it could miss out on inserts/removals
        owner.getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onDestroy(@NonNull LifecycleOwner owner) {
                detach();
                owner.getLifecycle().removeObserver(this);
            }
        });
        bindUpdateObserverIfUninitialized();
    }

    public void attach(LiveList<T> liveList) {
        this.target = liveList;
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
        }
    }
}
