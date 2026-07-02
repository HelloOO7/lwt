package cz.spojenka.android.ui.helpers;

import android.view.View;
import android.view.ViewGroup;

import java.util.function.Supplier;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class SingleViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final Supplier<View> viewCreator;

    public SingleViewAdapter(Supplier<View> viewCreator) {
        this.viewCreator = viewCreator;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = viewCreator.get();
        if (view.getLayoutParams() == null) {
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        return new RecyclerView.ViewHolder(view) {
        };
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {

    }

    @Override
    public int getItemCount() {
        return 1;
    }
}
