package cz.spojenka.lwt.demoapp;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;

import com.google.android.material.textview.MaterialTextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;
import cz.spojenka.lwt.CICOPresenceAdvertiser;

public class BeOutNotice extends MaterialTextView {

    {
        init();
    }

    public BeOutNotice(@NonNull Context context) {
        super(context);
    }

    public BeOutNotice(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public BeOutNotice(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private void init() {
        setCompoundDrawablePadding(getResources().getDimensionPixelSize(R.dimen.item_margin_normal));
        int tint;
        int icon;
        if (CICOPresenceAdvertiser.isSupported(getContext())) {
            icon = R.drawable.ic_check_24px;
            setText(R.string.beout_notice_supported);
            tint = R.color.delay_ok;
        } else {
            icon = R.drawable.ic_warning_f_24px;
            setText(R.string.beout_notice_not_supported);
            tint = R.color.delay_mid;
        }
        setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0);
        TextViewCompat.setCompoundDrawableTintList(this, ColorStateList.valueOf(getContext().getColor(tint)));
    }
}
