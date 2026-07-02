package cz.spojenka.android.ui.helpers;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class LayoutInflaterHook implements LayoutInflater.Factory2 {

    //https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/com/android/internal/policy/PhoneLayoutInflater.java
    private static final String[] PLATFORM_VIEW_PREFIXES = {
            "android.widget.",
            "android.webkit.",
            "android.app.",
            "android.view."
    };

    private Activity activity;
    private LayoutInflaterWrapper inflaterWrapper;

    private List<ViewDecorator> viewDecorators = new ArrayList<>();


    public LayoutInflater install(Activity activity, LayoutInflater inflater) {
        this.activity = activity;
        LayoutInflater.Factory originalFactory = inflater.getFactory();
        LayoutInflater.Factory2 originalFactory2 = inflater.getFactory2();
        if (originalFactory != null) {
            inflater = inflater.cloneInContext(inflater.getContext());
        }
        inflater.setFactory2(this);
        inflaterWrapper = new LayoutInflaterWrapper(inflater, originalFactory, originalFactory2);
        return inflater;
    }

    public void release() {
        if (inflaterWrapper != null) {
            inflaterWrapper.release();
        }
        inflaterWrapper = null;
        activity = null;
    }

    public void registerViewDecorator(ViewDecorator decorator) {
        if (!viewDecorators.contains(decorator)) {
            viewDecorators.add(decorator);
        }
    }

    public void unregisterViewDecorator(ViewDecorator decorator) {
        viewDecorators.remove(decorator);
    }

    @Nullable
    @Override
    public View onCreateView(@Nullable View parent, @NonNull String name, @NonNull Context context, @NonNull AttributeSet attrs) {
        return inflaterWrapper.delegateCreateView(parent, name, context, attrs);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull String name, @NonNull Context context, @NonNull AttributeSet attrs) {
        return onCreateView(null, name, context, attrs);
    }

    public static interface ViewDecorator {

        public void decorate(View view, String name, Context context, AttributeSet attrs);
    }

    private class LayoutInflaterWrapper {

        private LayoutInflater inflater;
        private Map<Context, LayoutInflater> childLayoutInflaterCache = new ArrayMap<>();

        private LayoutInflater.Factory factory;
        private LayoutInflater.Factory2 factory2;

        public LayoutInflaterWrapper(LayoutInflater inflater, LayoutInflater.Factory factory, LayoutInflater.Factory2 factory2) {
            this.inflater = inflater;
            this.factory = factory;
            this.factory2 = factory2;
        }

        public LayoutInflater getInflater() {
            return inflater;
        }

        public void release() {
            childLayoutInflaterCache.clear();
            factory = null;
            factory2 = null;
        }

        private View platformCreateView(Context context, String name, String prefix, AttributeSet attrs) throws ClassNotFoundException {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return inflater.createView(context, name, prefix, attrs);
            } else {
                if (context != inflater.getContext()) {
                    // LayoutInflater.createViewFromTag (internal method) only stores the themed context to the
                    // mConstructorArgs field *after* calling Factory/Factory2, which means that at the point
                    // where the hook is invoked, the context is not yet set to the value we'd like.
                    // this is not a bug - the framework expects that we use the context passed as an argument,
                    // unfortunately, we can not call createView with it below Android Q.
                    // therefore, instead of using reflection hacks, we create new instances of LayoutInflater
                    // with the themed context and cache them to prevent excessive allocations.
                    // they will be cleared when the activity is destroyed.
                    inflater = childLayoutInflaterCache.computeIfAbsent(context, inflater::cloneInContext);
                }
                return inflater.createView(name, prefix, attrs);
            }
        }

        public View platformCreateView(Context context, String name, AttributeSet attrs) {
            try {
                if (name.indexOf('.') == -1) {
                    for (String prefix : PLATFORM_VIEW_PREFIXES) {
                        try {
                            return platformCreateView(context, name, prefix, attrs);
                        } catch (ClassNotFoundException e) {
                            // Ignore and try next prefix
                        }
                    }
                    return null;
                } else {
                    return platformCreateView(context, name, null, attrs);
                }
            } catch (ClassNotFoundException e) {
                return null;
            }
        }

        public View delegateCreateView(@Nullable View parent, @NonNull String name, @NonNull Context context, @NonNull AttributeSet attrs) {
            View view = null;

            if (name.equals("view")) {
                name = attrs.getAttributeValue(null, "class");
            }

            if (factory2 != null) {
                view = factory2.onCreateView(parent, name, context, attrs);
            } else if (factory != null) {
                view = factory.onCreateView(name, context, attrs);
            }

            if (view == null) {
                view = activity.onCreateView(parent, name, context, attrs);
            }

            if (view == null) {
                view = platformCreateView(context, name, attrs);
            }

            if (view != null) {
                for (ViewDecorator decorator : viewDecorators) {
                    decorator.decorate(view, name, context, attrs);
                }
            }

            return view;
        }
    }
}
