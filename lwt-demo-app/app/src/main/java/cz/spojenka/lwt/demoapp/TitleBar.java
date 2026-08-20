package cz.spojenka.lwt.demoapp;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.divider.MaterialDivider;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import cz.spojenka.android.util.ViewUtils;

public class TitleBar extends LinearLayout {

    private MaterialDivider divider;
    private ImageView icon;
    private TextView text;

    public TitleBar(Context context) {
        super(context);
        init(null);
    }

    public TitleBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public TitleBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    public TitleBar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        setOrientation(VERTICAL);

        LinearLayout inner = new LinearLayout(getContext());
        inner.setOrientation(HORIZONTAL);

        icon = new ImageView(getContext());
        text = new TextView(getContext());
        text.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);

        var iconLP = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
        iconLP.setMarginEnd(getResources().getDimensionPixelSize(R.dimen.item_margin_normal));
        inner.addView(icon, iconLP);
        var textLP = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT);
        textLP.weight = 1;
        inner.addView(text, textLP);

        divider = new MaterialDivider(getContext());
        addView(inner, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams dividerLP = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dividerLP.setMargins(0, getResources().getDimensionPixelSize(R.dimen.item_margin_normal), 0, 0);
        addView(divider, dividerLP);

        if (attrs != null) {
            try (var a = getContext().obtainStyledAttributes(attrs, R.styleable.TitleBar)) {
                setText(a.getText(R.styleable.TitleBar_android_text));
                setTextSize(a.getDimension(R.styleable.TitleBar_android_textSize, getResources().getDimension(R.dimen.text_title_l)));
                setIcon(a.getResourceId(R.styleable.TitleBar_android_icon, Resources.ID_NULL));
                setDividerVisible(a.getBoolean(R.styleable.TitleBar_dividerVisible, true));
                if (a.hasValue(R.styleable.TitleBar_dividerThickness)) {
                    setDividerThickness(a.getDimensionPixelSize(R.styleable.TitleBar_dividerThickness, 0));
                }
            }
        } else {
            setIcon(Resources.ID_NULL);
            setTextSize(getResources().getDimension(R.dimen.text_title_l));
        }
    }

    public void setDividerVisible(boolean visible) {
        divider.setVisibility(visible ? VISIBLE : GONE);
    }

    public void setDividerThickness(int thickness) {
        divider.setDividerThickness(thickness);
    }

    public void setText(CharSequence text) {
        this.text.setText(text);
    }

    public void setText(@StringRes int resId) {
        this.text.setText(resId);
    }

    public void setTextSize(float size) {
        this.text.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
    }

    public void setIcon(@DrawableRes int resId) {
        if (resId == Resources.ID_NULL) {
            icon.setVisibility(GONE);
            icon.setImageDrawable(null);
        } else {
            icon.setVisibility(VISIBLE);
            icon.setImageResource(resId);
        }
    }
}
