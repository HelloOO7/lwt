package cz.spojenka.android.system.livedata;

import java.lang.reflect.Field;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

/**
 * Implementation of a custom {@link LifecycleOwner} that can be bound to pretty much any
 * app component even if it is not a lifecycle owner itself. This mainly facilitates the use of
 * {@link androidx.lifecycle.LiveData} with such components. The actual lifecycle is always
 * inherited from a parent lifecycle owner and its state changes are mirrored, however,
 * lifecycle callbacks used by LiveData are local to this class.
 */
public class ChildLifecycleOwner implements LifecycleOwner {

    private final LifecycleOwner parent;
    private final LifecycleRegistry lifecycle = new LifecycleRegistry(this);

    private static final Field stateField;

    static {
        //new version of lifecycle library does not allow reviving the lifecycle from DESTROYED,
        //so we have to hack it to the desired state manually
        try {
            stateField = LifecycleRegistry.class.getDeclaredField("state");
            stateField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public ChildLifecycleOwner(LifecycleOwner parent) {
        this.parent = parent;
        if (parent.getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED) {
            lifecycle.setCurrentState(Lifecycle.State.CREATED); //state must be at least CREATED to be able to be DESTROYED
        }
        lifecycle.setCurrentState(parent.getLifecycle().getCurrentState());
        parent.getLifecycle().addObserver(new LifecycleEventObserver() {
            @Override
            public void onStateChanged(@NonNull LifecycleOwner lifecycleOwner, @NonNull Lifecycle.Event event) {
                lifecycle.setCurrentState(event.getTargetState());
            }
        });
    }

    /**
     * Restart the lifecycle completely. This will force all {@link androidx.lifecycle.LiveData} observers
     * that depend on this lifecycle to be unregistered. Internally, the lifecycle is switched to
     * {@link Lifecycle.State#DESTROYED} and then back to the current state of the parent lifecycle.
     */
    public void reset() {
        if (lifecycle.getCurrentState() != Lifecycle.State.INITIALIZED) {
            lifecycle.setCurrentState(Lifecycle.State.DESTROYED); //unbind existing observers
        }
        Lifecycle.State nextState = parent.getLifecycle().getCurrentState();
        if (nextState != Lifecycle.State.DESTROYED) {
            try {
                stateField.set(lifecycle, Lifecycle.State.INITIALIZED); //hack to be able to revive the lifecycle
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            lifecycle.setCurrentState(nextState);
        } //else remain in either INITIALIZED or DESTROYED state
    }

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return lifecycle;
    }
}
