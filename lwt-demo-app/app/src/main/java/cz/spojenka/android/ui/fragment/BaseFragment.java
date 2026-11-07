package cz.spojenka.android.ui.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import cz.spojenka.android.ui.helpers.ViewAnimatorWrapper;
import cz.spojenka.android.util.AsyncUtils;

/**
 * Base class that all fragments in the application should extend.
 * <p>
 * The class provides overridable methods for better separation
 * of dependency injection, view setup, and lifecycle handling.
 */
public abstract class BaseFragment extends Fragment {

    protected boolean onCreateViewCalled = false;

    private List<PendingViewModel<?>> pendingViewModels = new ArrayList<>(2);

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        //"Any restored child fragments will be created before the base Fragment.onCreate method returns" - this must be set up before that happens
        onInstallChildFragmentCallbacks(savedInstanceState);
        super.onCreate(savedInstanceState);
    }

    protected void onInstallChildFragmentCallbacks(Bundle savedInstanceState) {

    }

    @Nullable
    @Override
    public final View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        onCreateViewCalled = true;
        View view = doCreateView(inflater, container, savedInstanceState);
        if (view == null) {
            return null;
        }
        // support relative view translation - animators have to be used instead of view animations for predictive back navigation
        view = ViewAnimatorWrapper.wrap(view);
        return view;
    }

    protected View doCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (!onCreateViewCalled) {
            //bug: if two fragment transactions are done right after one another, onViewCreated
            //may be called without an onCreateView before it
            return;
        }
        onSetupView(savedInstanceState);
        MenuProvider menuProvider = getMenuProvider();
        if (menuProvider != null) {
            requireActivity().addMenuProvider(menuProvider, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
        }
    }

    /**
     * Called after the view has been created to set up listeners and callbacks.
     *
     * @param savedInstanceState Bundle containing the saved state of the fragment.
     */
    protected void onSetupView(@Nullable Bundle savedInstanceState) {

    }

    protected <T extends ViewModel> void createViewModelInActivity(Class<T> clazz, Consumer<T> onCreated) {
        FragmentActivity activity = getActivity();
        if (activity != null) {
            createViewModelNow(activity, clazz, onCreated);
        } else {
            // Activity not yet attached - delay creation
            pendingViewModels.add(new PendingViewModel<>(PendingViewModelType.ACTIVITY, clazz, onCreated));
        }
    }

    private static <T extends ViewModel> void createViewModelNow(ViewModelStoreOwner owner, Class<T> clazz, Consumer<T> onCreated) {
        T viewModel = new ViewModelProvider(owner).get(clazz);
        onCreated.accept(viewModel);
    }

    private void processPendingViewModels(PendingViewModelType type, ViewModelStoreOwner owner) {
        for (PendingViewModel<?> pending : pendingViewModels) {
            if (pending.type() == type) {
                pending.complete(owner);
            }
        }
        pendingViewModels.removeIf(pending -> pending.type() == type);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        onCreateViewCalled = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        pendingViewModels.clear();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        onRegisterResultContracts(context);
        if (context instanceof ComponentActivity activity) {
            processPendingViewModels(PendingViewModelType.ACTIVITY, activity);
        }
    }

    /**
     * Called to register result contracts for the fragment. This method is called in {@link #onAttach(Context)}
     *
     * @param context The context of the fragment
     */
    protected void onRegisterResultContracts(Context context) {

    }

    /**
     * Get a {@link MenuProvider} to be used in the activity when this fragment is on-screen.
     * By default, if the fragment itself implements {@link MenuProvider}, it is returned and used.
     *
     * @return The menu provider for the fragment, or null if the fragment does not provide a menu.
     */
    protected MenuProvider getMenuProvider() {
        if (this instanceof MenuProvider mp) {
            return mp;
        }
        return null;
    }

    /**
     * Get an executor that runs on the main UI thread.
     *
     * @return
     * @see ContextCompat#getMainExecutor(Context)
     */
    public Executor getMainThreadExecutor() {
        return ContextCompat.getMainExecutor(requireContext());
    }

    /**
     * Get an executor that only runs during the lifecycle of the fragment.
     *
     * @return
     * @see AsyncUtils#getLifecycleExecutor(Fragment)
     */
    public Executor getLifecycleExecutor() {
        return AsyncUtils.getLifecycleExecutor(this);
    }

    public boolean isUIDestroyed() {
        FragmentActivity activity = getActivity();
        return activity == null || activity.isFinishing() || activity.isDestroyed() || isRemoving() || isDetached();
    }

    public void navigateBack() {
        FragmentActivity activity = getActivity();
        if (activity != null) {
            activity.getOnBackPressedDispatcher().onBackPressed();
        }
    }

    private record PendingViewModel<T extends ViewModel>(PendingViewModelType type, Class<T> clazz,
                                                         Consumer<T> onCreated) {

        public void complete(ViewModelStoreOwner owner) {
            createViewModelNow(owner, clazz(), onCreated());
        }
    }

    private static enum PendingViewModelType {
        ACTIVITY,
        NAVIGATOR
    }
}
