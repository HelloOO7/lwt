package cz.spojenka.android.ui.activity;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import cz.spojenka.android.ui.helpers.EdgeToEdgeAttrs;
import cz.spojenka.android.ui.helpers.EdgeToEdgeSupport;
import cz.spojenka.android.ui.helpers.EdgeToEdgeToolbar;
import cz.spojenka.android.ui.helpers.LayoutInflaterHook;
import cz.spojenka.android.util.AsyncUtils;

public class BaseActivity extends AppCompatActivity {

    private final LayoutInflaterHook layoutInflaterHook = new LayoutInflaterHook();
    private LayoutInflater hookedLayoutInflater = null;
    private final EdgeToEdgeToolbar toolbarE2e = new EdgeToEdgeToolbar();
    private OnBackInvokedDispatcher compatBackInvokedDispatcher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hookedLayoutInflater = layoutInflaterHook.install(this, getLayoutInflater());
        layoutInflaterHook.registerViewDecorator(EdgeToEdgeAttrs.getInstance());
        layoutInflaterHook.registerViewDecorator(toolbarE2e);
        if (!hasCustomEdgeToEdgeSetup()) {
            EdgeToEdgeSupport.enable(this);
        }
        workaroundActivityLabelLocalization();
        setupCompatBackNavigationObserver();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        layoutInflaterHook.release();
    }

    protected final LayoutInflaterHook getLayoutInflaterHook() {
        return layoutInflaterHook;
    }

    @NonNull
    @Override
    public LayoutInflater getLayoutInflater() {
        if (hookedLayoutInflater != null) {
            return hookedLayoutInflater;
        }
        return super.getLayoutInflater();
    }

    @Override
    public Object getSystemService(@NonNull String name) {
        if (name.equals(LAYOUT_INFLATER_SERVICE) && hookedLayoutInflater != null) {
            return hookedLayoutInflater;
        }
        return super.getSystemService(name);
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        EdgeToEdgeSupport.registerCompatInsetsFixups(this);
        toolbarE2e.onPostCreate();
    }

    protected boolean hasCustomEdgeToEdgeSetup() {
        return false;
    }

    private void setupCompatBackNavigationObserver() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.BAKLAVA) {
            // on Android 16 only, there can be exactly one PRIORITY_SYSTEM_NAVIGATION_OBSERVER callback registered
            OnBackInvokedDispatcher superDispatcher = super.getOnBackInvokedDispatcher();
            compatBackInvokedDispatcher = new OnBackInvokedDispatcher() {

                private final List<OnBackInvokedCallback> navigationObserverCallbacks = new ArrayList<>();

                private final OnBackInvokedCallback dispatchNavigationObserverCallbacks =
                        () -> new ArrayList<>(navigationObserverCallbacks).forEach(OnBackInvokedCallback::onBackInvoked);

                @Override
                public void registerOnBackInvokedCallback(int priority, @NonNull OnBackInvokedCallback callback) {
                    // "If the callback instance has been already registered, the existing instance (no matter its priority) will be unregistered and registered again."
                    navigationObserverCallbacks.remove(callback);
                    if (priority == PRIORITY_SYSTEM_NAVIGATION_OBSERVER) {
                        navigationObserverCallbacks.add(callback);
                        if (navigationObserverCallbacks.size() == 1) {
                            superDispatcher.registerOnBackInvokedCallback(PRIORITY_SYSTEM_NAVIGATION_OBSERVER, dispatchNavigationObserverCallbacks);
                        }
                    } else {
                        BaseActivity.super.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(priority, callback);
                    }
                }

                @Override
                public void unregisterOnBackInvokedCallback(@NonNull OnBackInvokedCallback callback) {
                    if (navigationObserverCallbacks.remove(callback)) {
                        if (navigationObserverCallbacks.isEmpty()) {
                            superDispatcher.unregisterOnBackInvokedCallback(dispatchNavigationObserverCallbacks);
                        }
                    } else {
                        BaseActivity.super.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(callback);
                    }
                }
            };
        }
    }

    @NonNull
    @Override
    public OnBackInvokedDispatcher getOnBackInvokedDispatcher() {
        if (compatBackInvokedDispatcher != null) {
            return compatBackInvokedDispatcher;
        }
        return super.getOnBackInvokedDispatcher();
    }

    public void addOnBackPressedCallback(OnBackPressedCallback callback) {
        getOnBackPressedDispatcher().addCallback(this, callback);
    }

    /**
     * On older Android versions, changing the app language will not affect the action bar title
     * until the app is restarted, see <a href="https://stackoverflow.com/questions/22884068/troubles-with-activity-title-language">StackOverflow</a>.
     * This method is a workaround for the issue.
     */
    private void workaroundActivityLabelLocalization() {
        try {
            int labelRes = getPackageManager().getActivityInfo(getComponentName(), 0).labelRes;
            if (labelRes != 0) {
                setTitle(labelRes);
            }
        } catch (PackageManager.NameNotFoundException ignored) {

        }
    }

    public Executor getMainThreadExecutor() {
        return ContextCompat.getMainExecutor(this);
    }

    public Executor getLifecycleExecutor() {
        return AsyncUtils.getLifecycleExecutor(this);
    }
}
