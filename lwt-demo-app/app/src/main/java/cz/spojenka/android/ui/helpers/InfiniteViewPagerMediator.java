package cz.spojenka.android.ui.helpers;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

/**
 * A mediator helper class that simulates infinite left/right scrolling in a {@link ViewPager2}.
 * This is done by wrapping an original adapter and modifying all relevant calls so that the
 * requested item indices are in the interval (-∞, ∞) instead of [0, ∞).
 * <p>
 * As a result, your original adapter will receive {@link RecyclerView.Adapter#onBindViewHolder(RecyclerView.ViewHolder, int)}
 * calls that use negative indices for pages left of the initial ("pivot") page and positive indices for
 * pages right of the pivot page.
 * <p>
 * The actual number of pages is theoretically only limited by the width of the 32-bit integer.
 * Since the infiniteness is simulated through regular {@link RecyclerView} adapter calls, the
 * initial page will be set to {@link Integer#MAX_VALUE} / 2 when attached, which translates
 * to index 0 (the "pivot", i. e. the center page) in the original adapter.
 * <p>
 * Page limit: 8 pages <br>
 * *rotate* <br>
 * INFINITE PAGES <br>
 * Cool and good <br>
 * *nyoom*
 */
public class InfiniteViewPagerMediator {

    private static final int FAKE_PAGE_COUNT = Integer.MAX_VALUE;
    private static final int FAKE_PAGE_PIVOT = FAKE_PAGE_COUNT / 2;

    private final ViewPager2 viewPager;
    private final AdapterBridge<?> adapterBridge;

    /**
     * Create a new mediator for the given {@link ViewPager2} and adapter.
     *
     * @param viewPager The target ViewPager
     * @param adapter The adapter that provides the infinite pages
     */
    public InfiniteViewPagerMediator(ViewPager2 viewPager, RecyclerView.Adapter<?> adapter) {
        this.viewPager = viewPager;
        adapterBridge = new AdapterBridge<>(adapter);
    }

    /**
     * Attach the mediator to the ViewPager. This will attach the wrapped adapter to the ViewPager
     * and set the initial page to the pivot page.
     */
    public void attach() {
        viewPager.setAdapter(adapterBridge);
        viewPager.setCurrentItem(FAKE_PAGE_PIVOT, false);
    }

    /**
     * Get the minimum theoretical value of the pivot-based page index.
     * This value is always a constant.
     *
     * @return The minimum pivot-based page index, which is almost the lowest negative 32-bit integer.
     */
    public static int minPivot() {
        return itemToPivot(0);
    }

    /**
     * Get the maximum theoretical value of the pivot-based page index.
     * This value is always a constant.
     *
     * @return The maximum pivot-based page index, which is the highest positive 32-bit integer.
     */
    public static int maxPivot() {
        return itemToPivot(FAKE_PAGE_COUNT - 1);
    }

    /**
     * Convert a pivot-based page index to zero-based index in a real adapter.
     *
     * @param pivot The pivot-based (-∞, ∞) page index
     * @return The zero-based [0, ∞) page index
     */
    public static int pivotToItem(int pivot) {
        return pivot + FAKE_PAGE_PIVOT;
    }

    /**
     * Convert a zero-based index in a real adapter to a pivot-based page index.
     *
     * @param item The zero-based [0, ∞) page index
     * @return The pivot-based (-∞, ∞) page index
     */
    public static int itemToPivot(int item) {
        return item - FAKE_PAGE_PIVOT;
    }

    private static class AdapterBridge<VH extends RecyclerView.ViewHolder> extends RecyclerViewAdapterWrapper<VH> {

        private final Map<RecyclerView.AdapterDataObserver, AdapterDataObserverBridge> observerBridges = new WeakHashMap<>();

        public AdapterBridge(RecyclerView.Adapter<VH> adapter) {
            super(adapter);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            super.onBindViewHolder(holder, itemToPivot(position));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position, List<Object> payloads) {
            super.onBindViewHolder(holder, itemToPivot(position), payloads);
        }

        @Override
        public long getItemId(int position) {
            return super.getItemId(itemToPivot(position));
        }

        @Override
        public int getItemViewType(int position) {
            return super.getItemViewType(itemToPivot(position));
        }

        @Override
        public int getItemCount() {
            return FAKE_PAGE_COUNT;
        }

        @Override
        public void registerAdapterDataObserver(@NonNull RecyclerView.AdapterDataObserver observer) {
            AdapterDataObserverBridge ado = new AdapterDataObserverBridge(observer);
            observerBridges.put(observer, ado);
            registerLocalAdapterDataObserver(observer);
            super.registerAdapterDataObserver(ado);
        }

        @Override
        public void unregisterAdapterDataObserver(@NonNull RecyclerView.AdapterDataObserver observer) {
            unregisterLocalAdapterDataObserver(observer);
            super.unregisterAdapterDataObserver(Objects.requireNonNull(observerBridges.get(observer)));
        }
    }

    private static class AdapterDataObserverBridge extends RecyclerView.AdapterDataObserver {

        private final RecyclerView.AdapterDataObserver baseObserver;

        public AdapterDataObserverBridge(RecyclerView.AdapterDataObserver baseObserver) {
            this.baseObserver = baseObserver;
        }

        @Override
        public void onChanged() {
            baseObserver.onChanged();
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount) {
            baseObserver.onItemRangeChanged(pivotToItem(positionStart), itemCount);
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount, @Nullable Object payload) {
            baseObserver.onItemRangeChanged(pivotToItem(positionStart), itemCount, payload);
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            baseObserver.onItemRangeInserted(pivotToItem(positionStart), itemCount);
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            baseObserver.onItemRangeRemoved(pivotToItem(positionStart), itemCount);
        }

        @Override
        public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
            baseObserver.onItemRangeMoved(pivotToItem(fromPosition), pivotToItem(fromPosition), itemCount);
        }

        @Override
        public void onStateRestorationPolicyChanged() {
            baseObserver.onStateRestorationPolicyChanged();
        }
    }
}
