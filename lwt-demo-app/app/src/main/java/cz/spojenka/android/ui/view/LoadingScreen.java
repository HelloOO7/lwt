package cz.spojenka.android.ui.view;

import android.view.View;
import android.widget.TextView;

import cz.spojenka.lwt.demoapp.R;

/**
 * Helper class for manipulating loading screens.
 * This has been superseded by {@link LoadingPlaceholderContainer}.
 */
public class LoadingScreen {

    private final View screen;
    private final View content;

    public LoadingScreen(View screen, View content) {
        this.screen = screen;
        this.content = content;
    }

    public void show() {
        screen.setVisibility(View.VISIBLE);
        if (content != null) {
            content.setVisibility(View.GONE);
        }
    }

    public void hide() {
        screen.setVisibility(View.GONE);
        if (content != null) {
            content.setVisibility(View.VISIBLE);
        }
    }

    public void setDescription(int textId) {
        setDescription(textId == 0 ? null : screen.getContext().getString(textId));
    }

    public void setDescription(String text) {
        TextView view = screen.findViewById(R.id.tvLoadingText);
        if (view != null) {
            if (text != null) {
                view.setText(text);
                view.setVisibility(View.VISIBLE);
            }
            else {
                view.setVisibility(View.GONE);
            }
        }
    }
}
