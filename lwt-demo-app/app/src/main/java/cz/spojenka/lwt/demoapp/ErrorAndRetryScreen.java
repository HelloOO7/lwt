package cz.spojenka.lwt.demoapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.lifecycle.LifecycleOwner;
import cz.spojenka.android.system.SubscreenSwitcher;
import cz.spojenka.android.system.livedata.LiveErrorSignal;
import cz.spojenka.lwt.demoapp.databinding.ErrorAndRetryScreenBinding;

public class ErrorAndRetryScreen {

    private final FrameLayout root;

    private final ErrorAndRetryScreenBinding binding;
    private final View contentRoot;

    private final SubscreenSwitcher switcher;

    public ErrorAndRetryScreen(Context context, View mainContentRoot) {
        root = new FrameLayout(context);
        binding = ErrorAndRetryScreenBinding.inflate(LayoutInflater.from(context), root, true);
        contentRoot = mainContentRoot;
        root.addView(mainContentRoot);
        switcher = new SubscreenSwitcher(binding.getRoot(), mainContentRoot);
        switcher.setSubscreen(mainContentRoot);
    }

    public FrameLayout getRoot() {
        return root;
    }

    public void showError(Throwable error) {
        binding.tvErrorText.setText(error.getMessage());
        switcher.setSubscreen(binding.getRoot());
    }

    public void showContent() {
        switcher.setSubscreen(contentRoot);
    }

    public void setOnRetryListener(Runnable callback) {
        binding.btnRetry.setOnClickListener(v -> {
            showContent();
            callback.run();
        });
    }

    public void bindErrorSignal(LifecycleOwner lifecycleOwner, LiveErrorSignal signal, Runnable retryCallback) {
        signal.observe(lifecycleOwner, error -> {
            if (error != null) {
                showError(error);
            } else {
                showContent();
            }
        });
        setOnRetryListener(() -> {
            signal.ack();
            retryCallback.run();
        });
    }
}
