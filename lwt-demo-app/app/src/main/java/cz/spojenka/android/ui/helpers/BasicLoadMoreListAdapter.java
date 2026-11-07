package cz.spojenka.android.ui.helpers;

import androidx.annotation.NonNull;

/**
 * Specialization of a {@link LoadMoreListAdapter} that uses a {@link BasicListAdapter.BasicViewHolder} to bind items.
 *
 * @param <T> Type of items in the list
 * @param <VH> Type of view holder used to bind items
 */
public abstract class BasicLoadMoreListAdapter<T, VH extends BasicListAdapter.BasicViewHolder<T>> extends LoadMoreListAdapter<T, VH> {

    @Override
    public void onBindItemViewHolder(@NonNull VH holder, int itemPosition) {
        holder.bind(get(itemPosition));
    }
}
