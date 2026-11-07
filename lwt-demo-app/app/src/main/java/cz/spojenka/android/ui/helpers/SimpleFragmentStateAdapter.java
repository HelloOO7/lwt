package cz.spojenka.android.ui.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.viewpager2.adapter.FragmentStateAdapter;

/**
 * A {@link FragmentStateAdapter} for {@link androidx.viewpager2.widget.ViewPager2} that
 * allows specification of fragments using a list of {@link Supplier} instances. The fragments
 * will be mapped to adapter positions in the order they were added.
 * <p>
 * Example usage via subclass:
 * <pre><code>
 *     public class MyFragmentStateAdapter extends SimpleFragmentStateAdapter {
 *         {
 *             // initializer
 *             addFragment(() -> new MyFragment1());
 *             if (someCondition) {
 *                 addFragment(() -> new MyFragment2());
 *             }
 *             // ...
 *         }
 *     }
 * </code></pre>
 * <p>
 * Example usage via {@link #withFragments(Supplier[])}:
 * <pre><code>
 *     new SimpleFragmentStateAdapter(this) // FragmentActivity or Fragment
 *         .withFragments(
 *             () -> new MyFragment1(),
 *             () -> new MyFragment2(),
 *             // ...
 *         );
 * </code></pre>
 */
public class SimpleFragmentStateAdapter extends FragmentStateAdapter {

    private final List<Supplier<Fragment>> fragmentSuppliers = new ArrayList<>();

    public SimpleFragmentStateAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    public SimpleFragmentStateAdapter(@NonNull Fragment fragment) {
        super(fragment);
    }

    public SimpleFragmentStateAdapter(@NonNull FragmentManager fragmentManager, @NonNull Lifecycle lifecycle) {
        super(fragmentManager, lifecycle);
    }

    public SimpleFragmentStateAdapter(@NonNull Fragment fragment, @NonNull LifecycleOwner lifecycleOwner) {
        super(fragment.getChildFragmentManager(), lifecycleOwner.getLifecycle());
    }

    public static SimpleFragmentStateAdapter withFragmentViewLifecycle(@NonNull Fragment fragment) {
        // https://issuetracker.google.com/issues/154751401
        return new SimpleFragmentStateAdapter(fragment.getChildFragmentManager(), fragment.getViewLifecycleOwner().getLifecycle());
    }

    /**
     * Adds multiple fragment creators to the adapter. The fragments will be mapped to adapter positions
     * in the order they were added.
     *
     * @param fragments Array of fragment suppliers
     * @return this instance for method chaining
     */
    @SafeVarargs
    public final SimpleFragmentStateAdapter withFragments(Supplier<Fragment>... fragments) {
        for (var fragment : fragments) {
            addFragment(fragment);
        }
        return this;
    }

    public final SimpleFragmentStateAdapter withFragments(List<Supplier<Fragment>> fragments) {
        for (var fragment : fragments) {
            addFragment(fragment);
        }
        return this;
    }

    /**
     * Adds a fragment creator to the adapter. The method is protected and intended to be used by subclass
     * initializers.
     *
     * @param fragmentSupplier Supplier that creates the fragment instance when needed
     */
    protected final void addFragment(Supplier<Fragment> fragmentSupplier) {
        fragmentSuppliers.add(fragmentSupplier);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return fragmentSuppliers.get(position).get();
    }

    @Override
    public int getItemCount() {
        return fragmentSuppliers.size();
    }
}
