package cz.spojenka.lwt.demoapp;

import com.ncorti.slidetoact.SlideToActView;

import androidx.annotation.NonNull;

public class SlideToActController implements SlideToActView.OnSlideToActAnimationEventListener {

    private final SlideToActView view;

    private Runnable onAction;

    private boolean resetAllowed = true;
    private boolean resetPending = false;

    public SlideToActController(SlideToActView view) {
        this.view = view;
        view.setOnSlideToActAnimationEventListener(this);
    }

    public void setActionListener(Runnable onAction) {
        this.onAction = onAction;
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
        }
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
}
