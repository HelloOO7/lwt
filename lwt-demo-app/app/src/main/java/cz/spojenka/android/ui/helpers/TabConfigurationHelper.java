package cz.spojenka.android.ui.helpers;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.List;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import cz.spojenka.android.system.IndString;

/**
 * Implementation of {@link TabLayoutMediator.TabConfigurationStrategy}
 * that allows defining tabs with titles and icons (using the {@link Tab} record) without creating extra subclasses.
 * <p>
 * The helper can then be used like: {@code new TabLayoutMediator(..., ..., tabHelper).attach();}
 */
public class TabConfigurationHelper implements TabLayoutMediator.TabConfigurationStrategy {

    private final Tab[] tabs;

    /**
     * Create a new TabConfigurationHelper for a list of tabs.
     * The tabs will be displayed in the order they are given.
     *
     * @param tabs The tabs
     */
    public TabConfigurationHelper(Tab... tabs) {
        this.tabs = tabs;
    }

    public TabConfigurationHelper(List<Tab> tabs) {
        this.tabs = tabs.toArray(new Tab[0]);
    }

    @Override
    public void onConfigureTab(@NonNull TabLayout.Tab tab, int position) {
        tabs[position].title().apply(tab.view, tab::setText);
        if (tabs[position].iconRes() != 0) {
            tab.setIcon(tabs[position].iconRes());
        }
    }

    /**
     * Get the number of tabs registered.
     *
     * @return The number of tabs
     */
    public int getTabCount() {
        return tabs.length;
    }

    /**
     * Record representing a basic tab configuration.
     *
     * @param title The title of the tab
     * @param iconRes The icon resource of the tab, or 0 if no icon should be displayed
     */
    public record Tab(
            IndString title,
            @DrawableRes int iconRes
    ) {

        /**
         * Create a new tab with a title and an icon.
         *
         * @param title The title of the tab
         * @param iconRes The icon resource
         */
        public Tab(String title, @DrawableRes int iconRes) {
            this(new IndString(title), iconRes);
        }

        /**
         * Create a new tab with a title and no icon.
         *
         * @param title The title of the tab
         */
        public Tab(String title) {
            this(title, 0);
        }

        /**
         * Create a new tab with a title resource and an icon.
         *
         * @param titleRes The title resource
         * @param iconRes The icon resource
         */
        public Tab(@StringRes int titleRes, @DrawableRes int iconRes) {
            this(new IndString(titleRes), iconRes);
        }

        /**
         * Create a new tab with a title resource and no icon.
         *
         * @param titleRes The title resource
         */
        public Tab(@StringRes int titleRes) {
            this(titleRes, 0);
        }
    }
}
