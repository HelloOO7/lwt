package cz.spojenka.android.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import com.google.android.material.button.MaterialButton;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.viewpager2.widget.ViewPager2;
import cz.spojenka.android.ui.helpers.InfiniteViewPagerMediator;
import cz.spojenka.lwt.demoapp.R;

/**
 * Abstract class for a swipe-able view with previous/next buttons on the sides.
 * It is implemented using a ViewPager2 and two buttons.
 *
 * @see DateSwiper
 * @see TimeSwiper
 *
 * @param <T> Type of the value controlled by the swiper.
 */
public abstract class AbstractSwiper<T> extends LinearLayout {

    protected final MutableLiveData<T> highlightedValue = new MutableLiveData<>();

    private ViewPager2 viewPager;
    private Button leftButton;
    private Button rightButton;

    public AbstractSwiper(Context context) {
        this(context, null);
    }

    public AbstractSwiper(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AbstractSwiper(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public AbstractSwiper(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    /**
     * Get the LiveData that holds the value currently in the center of the swiper.
     *
     * @return
     */
    public LiveData<T> getHighlightedValue() {
        return highlightedValue;
    }

    /**
     * Set the value that should be highlighted in the center of the swiper.
     * The swiper will immediately jump to the value without animation.
     * Subclasses must properly handle the case when the value is null, which should
     * reset the swiper to the value at pivot index 0.
     *
     * @param value The value to highlight.
     */
    public void setValue(T value) {
        viewPager.setCurrentItem(InfiniteViewPagerMediator.pivotToItem(valueToPivot(value)), false);
        highlightedValue.setValue(value);
    }

    /**
     * Reset the swiper to its initial state.
     * This is the same as setting the value to null.
     */
    public void reset() {
        setValue(null);
    }

    /**
     * Reload the UI of all views in the swiper.
     * This is useful when all views need to be updated, for example when the reference date changes.
     */
    protected void reloadViews() {
        viewPager.getAdapter().notifyDataSetChanged();
    }

    /**
     * Get the pivot index of a value in the swiper's range.
     * The pivot index is the positive or negative offset in number of elements from the initial swiper value.
     *
     * @param value The value
     * @return The pivot index
     */
    public abstract int valueToPivot(T value);

    /**
     * Get the value at a pivot index in the swiper's range.
     * The pivot index is the positive or negative offset in number of elements from the initial swiper value.
     *
     * @param pivot The pivot index
     * @return The value
     */
    public abstract T pivotToValue(int pivot);

    /**
     * Get the icon resource to be used for the left/previous button.
     * @return
     */
    public @DrawableRes int getLeftButtonIcon() {
        return R.drawable.ic_chevron_left_24px;
    }

    /**
     * Get the icon resource to be used for the right/next button.
     * @return
     */
    public @DrawableRes int getRightButtonIcon() {
        return R.drawable.ic_chevron_right_24px;
    }

    /**
     * Setup the ViewPager2 of the center element with an adapter and possibly more configuration.
     * This must be implemented by subclasses.
     *
     * @param viewPager The ViewPager2 to setup
     */
    protected abstract void setupViewPager(ViewPager2 viewPager);

    private Button createButton(Context context, int iconId) {
        MaterialButton b = new MaterialButton(context, null, com.google.android.material.R.attr.materialIconButtonOutlinedStyle);
        b.setInsetBottom(0);
        b.setInsetTop(0);
        b.setIconResource(iconId);
        var lp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.weight = 0f;
        b.setLayoutParams(lp);
        return b;
    }

    private ViewPager2 createViewPager(Context context) {
        ViewPager2 viewPager = new ViewPager2(context);
        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.weight = 1f;
        viewPager.setLayoutParams(lp);
        setupViewPager(viewPager);
        return viewPager;
    }

    protected void init(Context context) {
        setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setOrientation(HORIZONTAL);
        setBackgroundResource(R.drawable.swiper_rails);

        leftButton = createButton(context, getLeftButtonIcon());
        viewPager = createViewPager(context);
        rightButton = createButton(context, getRightButtonIcon());

        addView(leftButton);
        addView(viewPager);
        addView(rightButton);

        leftButton.setOnClickListener(v -> viewPager.setCurrentItem(viewPager.getCurrentItem() - 1, true));
        rightButton.setOnClickListener(v -> viewPager.setCurrentItem(viewPager.getCurrentItem() + 1, true));

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                leftButton.setEnabled(position > 0);
                rightButton.setEnabled(position < viewPager.getAdapter().getItemCount() - 1);
                onValueSelected(pivotToValue(InfiniteViewPagerMediator.itemToPivot(position)));
            }
        });
    }

    /**
     * Function to be called whenever a value is selected.
     * This does not update the ViewPager - in case ViewPager should scroll to the selected value,
     * use {@link #setValue(Object)} instead.
     *
     * @param value The new value
     */
    protected void onValueSelected(T value) {
        highlightedValue.setValue(value);
    }
}
