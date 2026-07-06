package cz.spojenka.android.util;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

public class LiveDataUtils {

    /**
     * Observe a LiveData, unregistering the observer after it fires for the first time.
     *
     * @param liveData The LiveData to observe
     * @param owner Lifecycle owner
     * @param observer The Observer to be called when the LiveData changes
     * @param <T> The type of the LiveData
     */
    public static <T> void observeOnce(LiveData<T> liveData, LifecycleOwner owner, Observer<T> observer) {
        liveData.observe(owner, new Observer<T>() {
            @Override
            public void onChanged(T t) {
                liveData.removeObserver(this);
                observer.onChanged(t);
            }
        });
    }

    /**
     * Observe a LiveData, unregistering the observer after it fires for the first time.
     *
     * Unlike {@link #observeOnce(LiveData, LifecycleOwner, Observer)}, this method is not
     * lifecycle-aware, similarly to {@link LiveData#observeForever(Observer)}.
     *
     * @param liveData The LiveData to observe
     * @param observer The Observer to be called when the LiveData changes
     * @param <T> The type of the LiveData
     */
    public static <T> void observeOnce(LiveData<T> liveData, Observer<T> observer) {
        liveData.observeForever(new Observer<T>() {
            @Override
            public void onChanged(T t) {
                liveData.removeObserver(this);
                observer.onChanged(t);
            }
        });
    }

    /**
     * Set a new value to a MutableLiveData, but only if the value is different from the current one.
     *
     * This is similar to using {@link androidx.lifecycle.Transformations#distinctUntilChanged(LiveData)}.
     *
     * @param liveData The MutableLiveData to set the value to
     * @param value The new value
     * @param <T> The type of the MutableLiveData
     */
    public static <T> void setNewValue(MutableLiveData<T> liveData, T value) {
        if (!Objects.equals(liveData.getValue(), value)) {
            liveData.setValue(value);
        }
    }

    @SuppressWarnings("rawtypes")
    private static final LiveData EMPTY_LIST_LIVE_DATA = new MutableLiveData<>(List.of());

    @SuppressWarnings("unchecked")
    public static <T> LiveData<List<T>> ofEmptyList() {
        //this will NOT throw a ClassCastException, as the type is erased
        //and the list is empty
        return EMPTY_LIST_LIVE_DATA;
    }

    public static <T> LiveData<T> sinceNextValue(LiveData<T> liveData) {
        if (!liveData.isInitialized()) {
            return liveData;
        }

        MediatorLiveData<T> mediator = new MediatorLiveData<>();

        mediator.addSource(liveData, new Observer<T>() {

            private boolean acceptNext = false;

            @Override
            public void onChanged(T t) {
                if (acceptNext) {
                    mediator.setValue(t);
                } else {
                    acceptNext = true;
                }
            }
        });

        return mediator;
    }

    public static <T> LiveData<T> combine(Supplier<T> onChanged, LiveData<?>... liveData) {
        MediatorLiveData<T> result = new MediatorLiveData<>(onChanged.get());
        Observer<Object> observer = o -> setNewValue(result, onChanged.get());
        for (LiveData<?> source : liveData) {
            result.addSource(source, observer);
        }
        return result;
    }

    public static void observeAll(LifecycleOwner lifecycleOwner, Runnable onChanged, LiveData<?>... liveData) {
        Observer<Object> observer = o -> onChanged.run();
        for (LiveData<?> source : liveData) {
            source.observe(lifecycleOwner, observer);
        }
    }
}
