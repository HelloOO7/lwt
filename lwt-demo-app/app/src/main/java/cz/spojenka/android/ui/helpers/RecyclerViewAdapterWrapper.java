package cz.spojenka.android.ui.helpers;

import android.view.ViewGroup;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * A wrapper/decorator that allows to intercept calls to the wrapped adapter.
 * Unlike simply overriding the adapter methods, using a wrapper is mostly transparent
 * with regards to the original adapter.
 *
 * @param <VH> ViewHolder type
 */
public class RecyclerViewAdapterWrapper<VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<VH> {

    protected final RecyclerView.Adapter<VH> baseAdapter;

    /**
     * Create a new wrapper for the given adapter.
     *
     * @param adapter The wrapped adapter
     */
    public RecyclerViewAdapterWrapper(RecyclerView.Adapter<VH> adapter) {
        this.baseAdapter = adapter;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return baseAdapter.onCreateViewHolder(parent, viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        baseAdapter.onBindViewHolder(holder, position);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position, List<Object> payloads) {
        baseAdapter.onBindViewHolder(holder, position, payloads);
    }

    @Override
    public long getItemId(int position) {
        return baseAdapter.getItemId(position);
    }

    @Override
    public int getItemViewType(int position) {
        return baseAdapter.getItemViewType(position);
    }

    @Override
    public int getItemCount() {
        return baseAdapter.getItemCount();
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        baseAdapter.onAttachedToRecyclerView(recyclerView);
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        baseAdapter.onDetachedFromRecyclerView(recyclerView);
    }

    @Override
    public void registerAdapterDataObserver(@NonNull RecyclerView.AdapterDataObserver observer) {
        baseAdapter.registerAdapterDataObserver(observer);
    }

    /**
     * Register a {@link RecyclerView.AdapterDataObserver} to the <i>wrapper</i> adapter,
     * i. e. not the base adapter (which is what {@link #registerAdapterDataObserver(RecyclerView.AdapterDataObserver)} would do).
     *
     * @param observer The observer
     */
    protected void registerLocalAdapterDataObserver(RecyclerView.AdapterDataObserver observer) {
        super.registerAdapterDataObserver(observer);
    }

    @Override
    public void unregisterAdapterDataObserver(@NonNull RecyclerView.AdapterDataObserver observer) {
        baseAdapter.unregisterAdapterDataObserver(observer);
    }

    /**
     * Unregister a {@link RecyclerView.AdapterDataObserver} from the <i>wrapper</i> adapter,
     * i. e. not the base adapter (which is what {@link #unregisterAdapterDataObserver(RecyclerView.AdapterDataObserver)} would do).
     *
     * @param observer The observer
     */
    protected void unregisterLocalAdapterDataObserver(RecyclerView.AdapterDataObserver observer) {
        super.registerAdapterDataObserver(observer);
    }

    @Override
    public void setHasStableIds(boolean hasStableIds) {
        baseAdapter.setHasStableIds(hasStableIds);
    }

    @Override
    public void onViewRecycled(@NonNull VH holder) {
        baseAdapter.onViewRecycled(holder);
    }

    @Override
    public boolean onFailedToRecycleView(@NonNull VH holder) {
        return baseAdapter.onFailedToRecycleView(holder);
    }

    @Override
    public void onViewAttachedToWindow(@NonNull VH holder) {
        baseAdapter.onViewAttachedToWindow(holder);
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull VH holder) {
        baseAdapter.onViewDetachedFromWindow(holder);
    }
}
