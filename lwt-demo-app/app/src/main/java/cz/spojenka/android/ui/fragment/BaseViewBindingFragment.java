package cz.spojenka.android.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

/**
 * Base class that all fragments that use view bindings should extend.
 * <p>
 * The class provides overridable methods for better separation
 * of dependency injection, view bindings, view setup, and lifecycle handling.
 * View bindings are also automatically destroyed in {@link #onDestroyView()}
 * to prevent memory leaks.
 *
 * @param <TViewBinding> Type of the view binding class for the fragment. If the fragment does not
 *                      use view bindings, this should be set to a dummy.
 */
public abstract class BaseViewBindingFragment<TViewBinding extends ViewBinding> extends BaseFragment {

    protected TViewBinding binding;

    @Nullable
    @Override
    public View doCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = onCreateViewBinding(inflater, container);
        View view = onCreateView(binding, savedInstanceState);
        onCreateView(savedInstanceState);
        return view;
    }

    /**
     * Create the root fragment view. By default, this method returns the root view of the view binding,
     * but it may be overridden f. e. to wrap the view in a custom layout.
     *
     * @param binding The view binding of the fragment.
     * @param savedInstanceState Bundle containing the saved state of the fragment.
     * @return The root view for the fragment.
     */
    protected View onCreateView(@Nullable TViewBinding binding, @Nullable Bundle savedInstanceState) {
        return binding != null ? binding.getRoot() : null;
    }

    /**
     * Called after the view binding has been created (in {@link #onCreateViewBinding(LayoutInflater, ViewGroup)}.
     * This should be used to further initialize the view, such as setting styles.
     * <p>
     * For binding logic to the views, use {@link #onSetupView(Bundle)}.
     *
     * @param savedInstanceState Bundle containing the saved state of the fragment.
     */
    protected void onCreateView(@Nullable Bundle savedInstanceState) {
    }

    @SuppressWarnings("unchecked")
    protected TViewBinding onCreateViewBinding(LayoutInflater inflater, ViewGroup container) {
        Class<TViewBinding> viewBindingClass = getViewBindingClass();
        if (viewBindingClass == null) {
            return null;
        }
        try {
            return (TViewBinding) viewBindingClass.getMethod("inflate", LayoutInflater.class, ViewGroup.class, boolean.class)
                    .invoke(null, inflater, container, false);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get the view binding class for the fragment's main view. If the fragment does not use view bindings,
     * it may return null.
     *
     * @return The view binding class
     */
    protected abstract @Nullable Class<TViewBinding> getViewBindingClass();

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
