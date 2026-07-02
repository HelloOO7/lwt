package cz.spojenka.android.system.livedata;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.WeakHashMap;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

/**
 * An observable list that extends the {@link androidx.lifecycle.LiveData} mechanism for
 * insert/delete/modify operations. It can be used with similar semantics to LiveData, for example
 * with {@link cz.spojenka.android.ui.helpers.AdapterListObserver}.
 * <p>
 * There are a couple of differences from normal LiveData, namely that it is guaranteed to always
 * have a non-null value (an empty list by default), and that a standard, non-LiveList-aware
 * observer will not be notified of this initial empty list value. Setting an empty list as
 * the value explicitly in the constructor, as well as using setValue/postValue,
 * will trigger observers as normal.
 * This is to ensure that the initial empty list does not trigger observers, but is still
 * available for use.
 * <p>
 * Updates are sequenced with revision IDs, so that observers added later do not receive
 * updates that occurred before they were added.
 *
 * @param <T>
 */
public class LiveList<T> extends AdapterLiveData<List<T>> implements Iterable<T> {

    private boolean initialized = false;

    private boolean fireUpdatesOnEmptyInserts = true;
    private long nextRevisionId = 0;
    //must be after nextRevisionId because createUpdateInfo uses nextRevisionId !!
    private final MutableLiveData<UpdateInfo> updateLiveData = new MutableLiveData<>(createUpdateInfo(UpdateType.MODIFY, 0, 0));

    private final WeakHashMap<UpdateObserver<T>, Observer<UpdateInfo>> updateObserverProxyMap = new WeakHashMap<>();

    private final Handler handler = new Handler(Looper.getMainLooper());

    public LiveList() {
        super(new ArrayList<>());
    }

    public LiveList(@NonNull List<T> value) {
        super(value);
        initialized = true;
    }

    /**
     * Enable or disable firing {@link UpdateObserver#onInserted(int, List)} events
     * when the amount of items inserted is zero.
     * By default, this is enabled.
     *
     * @param fireUpdatesOnEmptyInserts true/false
     */
    public void setFireUpdatesOnEmptyInserts(boolean fireUpdatesOnEmptyInserts) {
        this.fireUpdatesOnEmptyInserts = fireUpdatesOnEmptyInserts;
    }

    @Override
    public void setValue(List<T> value) {
        initialized = true;
        ++nextRevisionId;
        super.setValue(value);
    }

    @Override
    public void postValue(List<T> value) {
        initialized = true;
        ++nextRevisionId;
        super.postValue(value);
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    public T get(int index) {
        return getList().get(index);
    }

    public int indexOf(T item) {
        return getList().indexOf(item);
    }

    private List<T> getSubList(int start, int count) {
        return getList().subList(start, start + count);
    }

    @Override
    protected Observer<? super List<T>> adaptObserver(Observer<? super List<T>> observer) {
        return list -> {
            if (initialized) {
                observer.onChanged(list);
            }
        };
    }

    private Observer<UpdateInfo> createUpdateObserverProxy(UpdateObserver<T> updateObserver) {
        if (updateObserverProxyMap.containsKey(updateObserver)) {
            return updateObserverProxyMap.get(updateObserver);
        }

        final long startingRevisionId = nextRevisionId;
        Observer<UpdateInfo> observer = updateInfo -> {
            if (updateInfo.revisionId() < startingRevisionId) {
                return;
            }
            switch (updateInfo.type()) {
                case INSERT -> updateObserver.onInserted(updateInfo.start(), getSubList(updateInfo.start(), updateInfo.count()));
                case REMOVE -> updateObserver.onRemoved(updateInfo.start(), updateInfo.count());
                case MODIFY -> updateObserver.onModified(updateInfo.start(), getSubList(updateInfo.start(), updateInfo.count()));
            }
        };
        updateObserverProxyMap.put(updateObserver, observer);
        return observer;
    }

    /**
     * Register an observer to be notified of updates to the list. The observer will not
     * fire automatically until the next received update.
     *
     * @param lifecycleOwner Lifecycle owner
     * @param observer The observer
     */
    public void observeUpdates(LifecycleOwner lifecycleOwner, UpdateObserver<T> observer) {
        updateLiveData.observe(lifecycleOwner, createUpdateObserverProxy(observer));
    }

    public void observeUpdatesForever(UpdateObserver<T> observer) {
        updateLiveData.observeForever(createUpdateObserverProxy(observer));
    }

    public void removeUpdateObserver(UpdateObserver<T> observer) {
        var observerProxy = updateObserverProxyMap.get(observer);
        if (observerProxy == null) {
            return;
        }
        updateLiveData.removeObserver(observerProxy);
        updateObserverProxyMap.remove(observer);
    }

    public void removeRange(int start, int count) {
        List<T> list = getList();
        if (start < 0 || start >= list.size() || count <= 0) {
            return;
        }
        int end = Math.min(start + count, list.size());
        list.subList(start, end).clear();

        setUpdate(UpdateType.REMOVE, start, count);
    }

    public void remove(int index) {
        removeRange(index, 1);
    }

    public void remove(T item) {
        int index = getList().indexOf(item);
        if (index != -1) {
            remove(index);
        }
    }

    public void addAll(int start, List<T> items) {
        if (items.isEmpty() && !fireUpdatesOnEmptyInserts) {
            initialized = true; //an empty insert still counts as initialization
            return;
        }
        getList().addAll(start, items);

        setUpdate(UpdateType.INSERT, start, items.size());
    }

    public void addAll(List<T> items) {
        addAll(getList().size(), items);
    }

    public void add(T item) {
        add(size(), item);
    }

    public void add(int index, T item) {
        addAll(index, List.of(item));
    }

    public void set(int index, T item) {
        getList().set(index, item);
        setUpdate(UpdateType.MODIFY, index, 1);
    }

    public void setRange(int index, List<T> items) {
        if (items.isEmpty()) {
            return;
        }
        if (index < 0 || index + items.size() > size()) {
            throw new IndexOutOfBoundsException();
        }
        for (int i = 0; i < items.size(); i++) {
            getList().set(index + i, items.get(i));
        }
        setUpdate(UpdateType.MODIFY, index, items.size());
    }

    public void clear() {
        removeRange(0, size());
    }

    private void postUpdate(Runnable operation) {
        handler.post(operation);
    }

    public void postAddAll(int start, List<T> items) {
        postUpdate(() -> addAll(start, items));
    }

    public void postAddAll(List<T> items) {
        postUpdate(() -> addAll(items));
    }

    public void postAdd(T item) {
        postUpdate(() -> add(item));
    }

    public void postSet(int index, T item) {
        postUpdate(() -> set(index, item));
    }

    public void postSetRange(int index, List<T> items) {
        postUpdate(() -> setRange(index, items));
    }

    public void postRemoveRange(int start, int count) {
        postUpdate(() -> removeRange(start, count));
    }

    public void postRemove(int index) {
        postUpdate(() -> remove(index));
    }

    public void postRemove(T item) {
        postUpdate(() -> remove(item));
    }

    public void postClear() {
        postUpdate(this::clear);
    }

    private @NonNull List<T> getList() {
        return Objects.requireNonNull(getValue());
    }

    public List<T> asList() {
        return getValue() == null ? List.of() : getValue();
    }

    private void setUpdate(UpdateType type, int start, int count) {
        initialized = true;
        updateLiveData.setValue(createUpdateInfo(type, start, count));
    }

    private UpdateInfo createUpdateInfo(UpdateType type, int start, int count) {
        return new UpdateInfo(nextRevisionId++, type, start, count);
    }

    public int size() {
        return getList().size();
    }

    public boolean isEmpty() {
        return getList().isEmpty();
    }

    @NonNull
    @Override
    public Iterator<T> iterator() {
        return getList().iterator();
    }

    private static enum UpdateType {
        INSERT,
        REMOVE,
        MODIFY
    }

    private static record UpdateInfo(long revisionId, UpdateType type, int start, int count) {

    }

    public static interface UpdateObserver<T> {

        public void onInserted(int start, List<T> items);
        public void onRemoved(int start, int count);
        public void onModified(int start, List<T> items);
    }

    public static interface InsertOnlyUpdateObserver<T> extends UpdateObserver<T> {

        public abstract void onInserted(int start, List<T> items);

        @Override
        public default void onRemoved(int start, int count) {
            throw new UnsupportedOperationException();
        }

        @Override
        public default void onModified(int start, List<T> items) {
            throw new UnsupportedOperationException();
        }
    }
}
