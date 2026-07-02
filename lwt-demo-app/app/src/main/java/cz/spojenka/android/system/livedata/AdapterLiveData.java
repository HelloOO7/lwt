package cz.spojenka.android.system.livedata;

import java.util.WeakHashMap;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

/**
 * LiveData that allows transparent decoration of its observers,
 * such as to omit certain values passed to them.
 *
 * @param <T> Type of data held by this LiveData
 */
public abstract class AdapterLiveData<T> extends MutableLiveData<T> {

    private final WeakHashMap<Observer<? super T>, Observer<? super T>> observerAdapterMap = new WeakHashMap<>();

    public AdapterLiveData() {
        super();
    }

    public AdapterLiveData(T value) {
        super(value);
    }

    /**
     * Override this method to create an adapter for a to-be-bound observer.
     *
     * @param observer The adaptee
     * @return The adapter. At some point, it should call the adaptee's {@link Observer#onChanged(Object)} method.
     */
    protected abstract Observer<? super T> adaptObserver(Observer<? super T> observer);

    private Observer<? super T> getObserverAdapter(Observer<? super T> observer) {
        return observerAdapterMap.computeIfAbsent(observer, this::adaptObserver);
    }

    @Override
    public void observe(@NonNull LifecycleOwner owner, @NonNull Observer<? super T> observer) {
        super.observe(owner, getObserverAdapter(observer));
    }

    @Override
    public void observeForever(@NonNull Observer<? super T> observer) {
        super.observeForever(getObserverAdapter(observer));
    }

    @Override
    public void removeObserver(@NonNull Observer<? super T> observer) {
        var adapter = observerAdapterMap.remove(observer);
        if (adapter != null) {
            super.removeObserver(adapter);
        }
    }
}
