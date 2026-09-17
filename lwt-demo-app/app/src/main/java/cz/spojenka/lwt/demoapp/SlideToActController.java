package cz.spojenka.lwt.demoapp;

import com.ncorti.slidetoact.SlideToActView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

public class SlideToActController implements SlideToActView.OnSlideToActAnimationEventListener {

    private final SlideToActView view;

    private Runnable onAction;
    private Runnable onAnimationDone;

    private boolean resetAllowed = true;
    private boolean resetPending = false;

    public SlideToActController(SlideToActView view) {
        this.view = view;
        view.setOnSlideToActAnimationEventListener(this);
    }

    public void setActionListener(Runnable onAction) {
        this.onAction = onAction;
    }

    public void runOnAnimationDone(Runnable action) {
        if (view.isCompleted()) {
            action.run();
        } else {
            onAnimationDone = action;
        }
    }

    public void reset() {
        if (resetAllowed) {
            doReset();
        } else {
            resetPending = true;
        }
    }

    private void doReset() {
        resetPending = false;
        view.setCompleted(false, true);
    }

    @Override
    public void onSlideCompleteAnimationEnded(@NonNull SlideToActView slideToActView) {
        resetAllowed = true;
        if (resetPending) {
            doReset();
        } else if (onAnimationDone != null) {
            onAnimationDone.run();
        }
        onAnimationDone = null;
    }

    @Override
    public void onSlideCompleteAnimationStarted(@NonNull SlideToActView slideToActView, float v) {
        resetAllowed = false;
        if (onAction != null) {
            onAction.run();
        }
    }

    @Override
    public void onSlideResetAnimationStarted(@NonNull SlideToActView slideToActView) {
        resetAllowed = false;
    }

    @Override
    public void onSlideResetAnimationEnded(@NonNull SlideToActView slideToActView) {
        resetAllowed = true;
    }

    public void changeCompleteIcon(@DrawableRes int icon) {
        view.setCompleteIcon(icon);
        if (view.isCompleted()) {
            view.setCompleted(true, false);
        }
    }
}
