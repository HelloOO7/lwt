package cz.spojenka.android.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.ViewGroup;

import com.google.android.material.textview.MaterialTextView;

import androidx.annotation.Nullable;
import cz.spojenka.android.util.ViewUtils;
import cz.spojenka.lwt.demoapp.R;

/**
 * Generic view that can be used as a placeholder screen in activities
 * which have a state where no results are available. The view simply
 * displays a large centered text message.
 * <p>
 * Given that it is a {@link android.widget.TextView}, it can be styled and controlled from XML.
 */
public class NoResultsPlaceholder extends MaterialTextView {

    public NoResultsPlaceholder(Context context) {
        super(context);
        init();
    }

    public NoResultsPlaceholder(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public NoResultsPlaceholder(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /**
     * Create a new NoResultsPlaceholder with the given text
     *
     * @param context Context
     * @param text The text
     */
    public NoResultsPlaceholder(Context context, String text) {
        this(context);
        setText(text);
    }

    /**
     * Create a new NoResultsPlaceholder with the given text resource
     *
     * @param context Context
     * @param textRes The text resource
     */
    public NoResultsPlaceholder(Context context, int textRes) {
        this(context);
        setText(textRes);
    }

    private void init() {
        ViewUtils.setTextSize(this, R.dimen.text_title_m);
        setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setGravity(Gravity.CENTER);
    }
}
