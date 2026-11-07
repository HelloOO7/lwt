package cz.spojenka.android.system;

import android.content.Context;
import android.view.View;

import java.util.function.Consumer;

/**
 * Union type of either a resource ID or a value of the same type.
 * This is used as a return value from methods which may use f. e. localized resource strings,
 * but can also return the actual value instead.
 *
 * @see IndString
 *
 * @param <R>
 */
public abstract class IndirectableResource<R> {

    private final boolean isIndirect;
    private final R resource;
    protected int id = 0;

    public IndirectableResource(R resource) {
        this.isIndirect = false;
        this.resource = resource;
    }

    public IndirectableResource(int id) {
        this.isIndirect = true;
        this.id = id;
        this.resource = null;
    }

    public boolean isIndirect() {
        return isIndirect;
    }

    public int getResId() {
        if (!isIndirect) {
            throw new IllegalStateException("Resource is not indirect");
        }
        return id;
    }

    protected abstract R resolve(Context context);

    private R resolveIfNeeded(Context context) {
        return isIndirect ? resolve(context) : resource;
    }

    public <T extends View> void apply(T view, Consumer<R> applyFunc) {
        applyFunc.accept(get(view.getContext()));
    }

    public R get(Context context) {
        return resolveIfNeeded(context);
    }
}
