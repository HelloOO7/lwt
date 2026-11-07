package cz.spojenka.android.ui.helpers;

import android.graphics.Rect;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

/**
 * Abstract class for an adapter for RecyclerViews that supports loading more items at the beginning and end of the list.
 * The adapter will automatically manage buttons to load more items when necessary.
 * <p>
 * The class has to be overridden first in order to provide implementations of the GUI elements.
 *
 * @param <T>  Type of items in the list
 * @param <VH> Type of ViewHolder for the items
 * @see AutoLoadMoreAdapter
 */
public abstract class LoadMoreListAdapter<T, VH extends RecyclerView.ViewHolder> extends ArrayListAdapter<T, RecyclerView.ViewHolder> {

    /**
     * View type for the content items in the list.
     */
    protected static final int VIEW_TYPE_ITEM = 0;
    /**
     * View type for the "load previous" button.
     */
    protected static final int VIEW_TYPE_LOAD_PREV = 1;
    /**
     * View type for the "load next" button.
     */
    protected static final int VIEW_TYPE_LOAD_NEXT = 2;

    private boolean loadPrevEnabled = false;
    private boolean loadNextEnabled = false;

    private boolean prevIsLoading = false;
    private boolean nextIsLoading = false;

    public LoadMoreListAdapter() {
        super();
        if (loadPrevEnabled = isDefaultShowLoadPrev()) {
            super.add(null);
        }
        if (loadNextEnabled = isDefaultShowLoadNext()) {
            super.add(null);
        }
    }

    /**
     * Returns whether the previous button should be shown by default.
     *
     * @return
     */
    protected boolean isDefaultShowLoadPrev() {
        return true;
    }

    /**
     * Returns whether the next button should be shown by default.
     *
     * @return
     */
    protected boolean isDefaultShowLoadNext() {
        return true;
    }

    /**
     * Checks whether the previous button should be shown.
     *
     * @return
     */
    protected boolean shouldDisplayLoadPrev() {
        return loadPrevEnabled;
    }

    /**
     * Checks whether the next button should be shown.
     *
     * @return
     */
    protected boolean shouldDisplayLoadNext() {
        return loadNextEnabled;
    }

    /**
     * Returns the index of the first content item in the adapter.
     *
     * @return 1 if the previous button is shown, 0 otherwise
     */
    public int innerStartIndex() {
        return shouldDisplayLoadPrev() ? 1 : 0;
    }

    /**
     * Returns the index after the last content item in the adapter.
     *
     * @return size() + 1 if the next button is shown, size() otherwise
     */
    public int innerEndIndex() {
        return innerStartIndex() + size();
    }

    /**
     * Sets whether the previous button should be shown.
     * The button will be added to/removed from the list as necessary.
     *
     * @param enabled true if the previous button should be shown
     */
    public void setLoadPrevButtonEnabled(boolean enabled) {
        if (loadPrevEnabled != enabled) {
            loadPrevEnabled = enabled;
            if (enabled) {
                super.add(0, null);
            } else {
                setPrevIsLoading(false);
                super.remove(0);
            }
        }
    }

    /**
     * Sets whether the next button should be shown.
     * The button will be added to/removed from the list as necessary.
     *
     * @param enabled true if the next button should be shown
     */
    public void setLoadNextButtonEnabled(boolean enabled) {
        if (loadNextEnabled != enabled) {
            loadNextEnabled = enabled;
            if (enabled) {
                super.add(null);
            } else {
                setNextIsLoading(false);
                super.remove(super.size() - 1);
            }
        }
    }

    /**
     * Sets whether the previous button should show a loading indicator.
     * Subclasses should override this method to update the UI accordingly.
     *
     * @param prevIsLoading true if the previous button should show a loading indicator
     */
    public void setPrevIsLoading(boolean prevIsLoading) {
        this.prevIsLoading = prevIsLoading;
    }

    /**
     * Sets whether the next button should show a loading indicator.
     * Subclasses should override this method to update the UI accordingly.
     *
     * @param nextIsLoading true if the next button should show a loading indicator
     */
    public void setNextIsLoading(boolean nextIsLoading) {
        this.nextIsLoading = nextIsLoading;
    }

    /**
     * Notifies the adapter that "previous" items have finished loading.
     * This will hide the loading indicator on the previous button.
     *
     * @see #setPrevIsLoading(boolean)
     */
    public void notifyLoadPrevFinished() {
        setPrevIsLoading(false);
    }

    /**
     * Notifies the adapter that loading "previous" items has been cancelled.
     * This behaves the same as {@link #notifyLoadPrevFinished()},
     * but may be overriden in subclasses to update the UI differently if necessary.
     */
    public void notifyLoadPrevCancelled() {
        setPrevIsLoading(false);
    }

    /**
     * Notifies the adapter that "next" items have finished loading.
     * This will hide the loading indicator on the next button.
     *
     * @see #setNextIsLoading(boolean)
     */
    public void notifyLoadNextFinished() {
        setNextIsLoading(false);
    }

    /**
     * Notifies the adapter that loading "next" items has been cancelled.
     * This behaves the same as {@link #notifyLoadPrevFinished()},
     * but may be overriden in subclasses to update the UI differently if necessary.
     */
    public void notifyLoadNextCancelled() {
        setNextIsLoading(false);
    }

    /**
     * Returns whether the previous button is currently showing a loading indicator.
     *
     * @return true if the previous button is in loading state (as a result of calling {@link #setPrevIsLoading(boolean)})
     */
    public boolean isPrevLoading() {
        return prevIsLoading;
    }

    /**
     * Returns whether the next button is currently showing a loading indicator.
     *
     * @return true if the next button is in loading state (as a result of calling {@link #setNextIsLoading(boolean)})
     */
    public boolean isNextLoading() {
        return nextIsLoading;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == innerStartIndex() - 1) {
            return VIEW_TYPE_LOAD_PREV;
        } else if (position == innerEndIndex()) {
            return VIEW_TYPE_LOAD_NEXT;
        } else {
            return VIEW_TYPE_ITEM;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (position >= innerStartIndex() && position < innerEndIndex()) {
            onBindItemViewHolder((VH) holder, position - innerStartIndex());
        }
        //others have nothing to bind
    }

    /**
     * Called when a ViewHolder is bound to a real item. This is not the same as regular
     * {@link RecyclerView.Adapter#onBindViewHolder(RecyclerView.ViewHolder, int)}, as that method
     * also takes care of creating view holders for the load more buttons, whereas this method
     * is only called for the content items.
     *
     * @param holder       ViewHolder to bind
     * @param itemPosition Position of the item in the list (in a sensible range, i. e. without regard for the control buttons).
     */
    public abstract void onBindItemViewHolder(@NonNull VH holder, int itemPosition);

    private int getControlButtonCount() {
        int count = 0;
        if (shouldDisplayLoadPrev()) {
            count++;
        }
        if (shouldDisplayLoadNext()) {
            count++;
        }
        return count;
    }

    @Override
    public int size() {
        return getItemCount() - getControlButtonCount();
    }

    @Override
    public boolean isEmpty() {
        return getItemCount() == getControlButtonCount();
    }

    @NonNull
    @Override
    public Iterator<T> iterator() {
        return items.subList(innerStartIndex(), innerEndIndex()).iterator();
    }

    @Override
    public boolean add(T t) {
        super.add(innerEndIndex(), t);
        return true;
    }

    @Override
    public boolean addAll(@NonNull Collection<? extends T> c) {
        return super.addAll(innerEndIndex(), c);
    }

    @Override
    public boolean addAll(int index, @NonNull Collection<? extends T> c) {
        return super.addAll(innerStartIndex() + index, c);
    }

    private final Rect keepScrollVhBounds = new Rect();

    /**
     * Same as {@link #addAll(int, Collection)} but the scroll position of a parent RecyclerView
     * is adjusted so that it is visually the same as before the items were added.
     *
     * @param index        Index at which to insert the first element from the specified collection
     * @param c            Collection containing elements to be added to this list
     * @param recyclerView RecyclerView to adjust the scroll position of
     * @return true if this list changed as a result of the call
     */
    public boolean addAllKeepScroll(int index, @NonNull Collection<? extends T> c, RecyclerView recyclerView) {
        int topOffset = 0;
        boolean changeScroll = false;
        LinearLayoutManager llm = null;
        if (recyclerView.getLayoutManager() instanceof LinearLayoutManager) {
            llm = (LinearLayoutManager) recyclerView.getLayoutManager();
            changeScroll = llm.findFirstVisibleItemPosition() < innerStartIndex() + index;
            RecyclerView.ViewHolder vh = recyclerView.findViewHolderForAdapterPosition(innerStartIndex() + index);
            if (vh != null) {
                recyclerView.getDecoratedBoundsWithMargins(vh.itemView, keepScrollVhBounds);
                topOffset = keepScrollVhBounds.top;
            }
        }
        if (addAll(index, c)) {
            if (changeScroll) {
                llm.scrollToPositionWithOffset(innerStartIndex() + index + c.size(), topOffset);
            }
            return true;
        }
        return false;
    }

    /**
     * Same as {@link #addAll(int, Collection)} but the scroll position of a parent ViewPager2
     * is adjusted so that it is visually the same as before the items were added.
     *
     * @param index     Index at which to insert the first element from the specified collection
     * @param c         Collection containing elements to be added to this list
     * @param viewPager ViewPager2 to adjust the scroll position of
     * @return true if this list changed as a result of the call
     */
    public boolean addAllKeepScroll(int index, @NonNull Collection<? extends T> c, ViewPager2 viewPager) {
        int oldPos = viewPager.getCurrentItem();
        if (addAll(index, c)) {
            if (oldPos <= innerStartIndex() + index) {
                viewPager.setCurrentItem(oldPos + c.size(), false);
            }
            return true;
        }
        return false;
    }

    @Override
    public void removeRange(int fromIndex, int count) {
        super.removeRange(innerStartIndex() + fromIndex, count);
    }

    private void doClear() {
        Object first = shouldDisplayLoadPrev() ? items.get(0) : null;
        Object last = shouldDisplayLoadNext() ? items.get(innerEndIndex()) : null;
        items.removeIf(t -> t != first && t != last);
    }

    @Override
    public void clear() {
        int oldStart = innerStartIndex();
        int oldEnd = innerEndIndex();
        doClear();
        notifyItemRangeRemoved(oldStart, oldEnd);
    }

    @Override
    public void replaceAll(Collection<T> newItems) {
        doClear();
        items.addAll(innerStartIndex(), newItems);
        notifyDataSetChanged();
    }

    @Override
    public T get(int index) {
        return super.get(innerStartIndex() + index);
    }

    @Override
    public T set(int index, T element) {
        return super.set(innerStartIndex() + index, element);
    }

    @Override
    public void setRange(int start, List<T> newItems) {
        super.setRange(innerStartIndex() + start, newItems);
    }

    @Override
    public void add(int index, T element) {
        super.add(innerStartIndex() + index, element);
    }

    @Override
    public T remove(int index) {
        return super.remove(innerStartIndex() + index);
    }

    @Override
    public int indexOf(@Nullable Object o) {
        return items.indexOf(o) - innerStartIndex();
    }

    @Override
    public int lastIndexOf(@Nullable Object o) {
        return items.lastIndexOf(o) - innerStartIndex();
    }
}
