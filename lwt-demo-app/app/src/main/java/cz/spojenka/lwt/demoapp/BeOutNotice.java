package cz.spojenka.lwt.demoapp;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.util.AttributeSet;

import com.google.android.material.textview.MaterialTextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;
import cz.spojenka.lwt.CICOPresenceAdvertiser;

public class BeOutNotice extends MaterialTextView {

    {
        update();
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

    private void update() {
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

    private final BroadcastReceiver btStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            update();
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // if bt is off, isSupported often returns false even if the hardware does actually support it
        getContext().registerReceiver(btStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        update();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getContext().unregisterReceiver(btStateReceiver);
    }
}
