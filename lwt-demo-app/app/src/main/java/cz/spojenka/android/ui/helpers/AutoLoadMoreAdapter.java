package cz.spojenka.android.ui.helpers;

import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.google.android.material.progressindicator.CircularProgressIndicator;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import cz.spojenka.lwt.demoapp.R;

/**
 * Implementation of {@link LoadMoreListAdapter} that automatically loads the previous/next data
 * as the user scrolls through the list.
 *
 * @param <T> Type of the items in the list
 * @param <V> Type of the view holder for the items
 */
public abstract class AutoLoadMoreAdapter<T, V extends BasicListAdapter.BasicViewHolder<T>> extends BasicLoadMoreListAdapter<T, V> {

    private boolean loadPrevCalled = false;
    private boolean loadTriggerEnabled = true;

    private boolean loadPrevEnabled = true;
    private boolean loadNextEnabled = true;

    protected abstract RecyclerView.ViewHolder onCreateItemViewHolder(@NonNull ViewGroup parent);

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return switch (viewType) {
            case VIEW_TYPE_ITEM -> onCreateItemViewHolder(parent);
            case VIEW_TYPE_LOAD_NEXT ->
                    new LoadMoreViewHolder(createLoadMoreView(parent));
            case VIEW_TYPE_LOAD_PREV ->
                    new LoadMoreViewHolder(createLoadMoreView(parent));
            default -> throw new IllegalArgumentException("Unknown view type: " + viewType);
        };
    }

    private void callLoadNext() {
        if (isAnyLoading()) {
            return;
        }
        setNextIsLoading(true);
        onLoadNextRequested();
    }

    private void callLoadPrev() {
        if (isAnyLoading()) {
            return;
        }
        loadPrevCalled = true;
        setPrevIsLoading(true);
        onLoadPrevRequested();
    }

    @Override
    public void setLoadPrevButtonEnabled(boolean enabled) {
        super.setLoadPrevButtonEnabled(enabled);
        loadPrevEnabled = enabled;
    }

    @Override
    public void setLoadNextButtonEnabled(boolean enabled) {
        super.setLoadNextButtonEnabled(enabled);
        loadNextEnabled = enabled;
    }

    public void setLoadTriggerEnabled(boolean loadTriggerEnabled) {
        this.loadTriggerEnabled = loadTriggerEnabled;
    }

    private final RecyclerView.OnScrollListener onScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            if (!loadTriggerEnabled || isAnyLoading()) {
                return;
            }
            //0 = try both
            if (dy <= 0) {
                checkPrevAutoLoadTrigger();
            }
            if (dy >= 0) {
                checkNextAutoLoadTrigger();
            }
        }
    };

    private RecyclerView recyclerView;

    private boolean checkLoadPrevAllowed(LinearLayoutManager llm) {
        int firstFullyVisible = llm.findFirstCompletelyVisibleItemPosition();
        int prefetch = getAdjustedPrevPrefetch();
        if (firstFullyVisible != RecyclerView.NO_POSITION) {
            if (firstFullyVisible <= prefetch) {
                return true;
            }
        } else {
            int firstVisible = llm.findFirstVisibleItemPosition();
            if (firstVisible != RecyclerView.NO_POSITION) {
                if (firstVisible <= Math.max(0, prefetch - 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkLoadNextAllowed(LinearLayoutManager llm) {
        int lastVisible = llm.findLastVisibleItemPosition();
        int prefetch = getNextPrefetch();
        if (lastVisible != RecyclerView.NO_POSITION) {
            if (lastVisible >= llm.getItemCount() - 1 - prefetch) {
                return true;
            }
        }
        return false;
    }

    private void checkNextAutoLoadTrigger() {
        if (!loadNextEnabled) {
            return;
        }
        //recyclerView can be null if detached during the post
        if (recyclerView != null && recyclerView.isAttachedToWindow() && recyclerView.getLayoutManager() instanceof LinearLayoutManager llm) {
            if (checkLoadNextAllowed(llm)) {
                callLoadNext();
            }
        }
    }

    private boolean isAnyLoading() {
        return isNextLoading() || isPrevLoading();
    }

    private void checkPrevAutoLoadTrigger() {
        if (!loadPrevEnabled || isAnyLoading()) {
            return;
        }
        if (recyclerView != null && recyclerView.isAttachedToWindow() && recyclerView.getLayoutManager() instanceof LinearLayoutManager llm) {
            if (checkLoadPrevAllowed(llm)) {
                callLoadPrev();
            }
        }
    }

    public void requestNextAutoLoadTrigger() {
        if (recyclerView != null) {
            recyclerView.post(this::checkNextAutoLoadTrigger);
        }
    }

    public void requestPrevAutoLoadTrigger() {
        if (recyclerView != null) {
            recyclerView.post(this::checkPrevAutoLoadTrigger);
        }
    }

    @Override
    public void notifyLoadNextFinished() {
        super.notifyLoadNextFinished();
        checkPrevAutoLoadTrigger();
    }

    @Override
    public void notifyLoadPrevFinished() {
        super.notifyLoadPrevFinished();
        checkNextAutoLoadTrigger();
    }

    private int getAdjustedPrevPrefetch() {
        return loadPrevCalled ? getPrevPrefetch() : 0;
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.recyclerView = recyclerView;
        recyclerView.addOnScrollListener(onScrollListener);
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        recyclerView.removeOnScrollListener(onScrollListener);
        this.recyclerView = null;
    }

    protected int getPrevPrefetch() {
        return 0;
    }

    protected int getNextPrefetch() {
        return 0;
    }

    /**
     * Called when it is determined that more items should be loaded.
     * When done, {@link #notifyLoadPrevFinished()} must be called to hide the progress spinner.
     * Alternatively {@link #setLoadPrevButtonEnabled(boolean)} may be called to disable the button
     * in case of an error.
     */
    protected abstract void onLoadPrevRequested();

    /**
     * Called when it is determined that more items should be loaded.
     * When done, {@link #notifyLoadNextFinished()} must be called to hide the progress spinner.
     * Alternatively {@link #setLoadNextButtonEnabled(boolean)} may be called to disable the button
     * in case of an error.
     */
    protected abstract void onLoadNextRequested();

    private LoadMore createLoadMoreView(ViewGroup parent) {
        return new LoadMore(parent);
    }

    private static class LoadMoreViewHolder extends RecyclerView.ViewHolder {

        public LoadMoreViewHolder(LoadMore view) {
            super(view.root);
        }
    }

    private static class LoadMore {

        private final FrameLayout root;
        private final CircularProgressIndicator loading;

        public LoadMore(ViewGroup parent) {
            root = new FrameLayout(parent.getContext());
            root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, parent.getResources().getDimensionPixelSize(R.dimen.load_more_height)));

            loading = new CircularProgressIndicator(parent.getContext());
            loading.setId(R.id.pbLoading);
            loading.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            loading.setIndeterminate(true);

            root.addView(loading);
        }
    }
}
