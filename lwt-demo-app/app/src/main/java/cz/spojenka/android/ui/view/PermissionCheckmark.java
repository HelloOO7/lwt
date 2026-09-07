package cz.spojenka.android.ui.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import com.google.android.material.color.MaterialColors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import cz.spojenka.android.ui.helpers.EdgeToEdgeSupport;
import cz.spojenka.android.util.ViewUtils;
import cz.spojenka.lwt.demoapp.R;
import cz.spojenka.lwt.demoapp.databinding.PermissionCheckmarkBinding;

public class PermissionCheckmark extends FrameLayout {

    private final PermissionCheckmarkBinding binding;

    public PermissionCheckmark(@NonNull Context context) {
        this(context, null);
    }

    public PermissionCheckmark(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PermissionCheckmark(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public PermissionCheckmark(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        binding = PermissionCheckmarkBinding.inflate(LayoutInflater.from(getContext()));
        // margin of content within the framelayout, which has the e2e ripple effect
        EdgeToEdgeSupport.installInsets(binding.getRoot(), EdgeToEdgeSupport.SIDE_HORIZONTAL);
        addView(binding.getRoot());
        //ViewUtils.enableRipple(this);

        if (attrs != null) {
            try (TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PermissionCheckmark, defStyleAttr, defStyleRes)) {
                setTitle(a.getString(R.styleable.PermissionCheckmark_title));
                setDescription(a.getString(R.styleable.PermissionCheckmark_description));
            }
        }
    }

    private boolean rippleActivated = false;

    @Override
    public void setOnClickListener(@Nullable OnClickListener l) {
        super.setOnClickListener(l);
        if (l != null) {
            if (!rippleActivated) {
                ViewUtils.enableRipple(this);
                rippleActivated = true;
            }
        } else {
            if (rippleActivated) {
                ViewUtils.disableRipple(this);
                rippleActivated = false;
            }
        }
    }

    public void setFulfilled(boolean fulfilled) {
        binding.ivCheckmark.setImageResource(R.drawable.ic_check_circle_f_24px);
        int color = MaterialColors.getColor(this, fulfilled ? android.R.attr.colorPrimary : R.attr.colorPrimaryMuted);
        binding.ivCheckmark.setImageTintList(ColorStateList.valueOf(color));
    }

    public void setUnfulfillable() {
        binding.ivCheckmark.setImageResource(R.drawable.ic_error_x_f_24px);
        binding.ivCheckmark.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(this, android.R.attr.colorError)));
    }

    public void setTitle(String title) {
        binding.tvTitle.setText(title);
    }

    public void setDescription(String description) {
        binding.tvDescription.setText(description);
    }

    public void setTitle(@StringRes int titleRes) {
        binding.tvTitle.setText(titleRes);
    }

    public void setDescription(@StringRes int descriptionRes) {
        binding.tvDescription.setText(descriptionRes);
    }
}
