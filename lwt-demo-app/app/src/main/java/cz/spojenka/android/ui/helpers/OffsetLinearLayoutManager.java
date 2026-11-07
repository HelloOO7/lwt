package cz.spojenka.android.ui.helpers;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.core.os.ParcelCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * {@link LinearLayoutManager} that will start at a specific item position when first
 * viewed with available data. The implementation is taken from <a href="https://stackoverflow.com/questions/51499834/how-to-tell-recyclerview-to-start-at-specific-item-position">StackOverflow</a>
 * and modified to persist the state across configuration changes.
 */
public class OffsetLinearLayoutManager extends LinearLayoutManager {

    public OffsetLinearLayoutManager(Context context) {
        super(context);
    }

    public OffsetLinearLayoutManager(Context context, int orientation, boolean reverseLayout) {
        super(context, orientation, reverseLayout);
    }

    public OffsetLinearLayoutManager(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    private int mPendingTargetPos = -1;
    private int mPendingPosOffset = -1;

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (mPendingTargetPos != -1 && state.getItemCount() > 0) {
            /*
            Data is present now, we can set the real scroll position
            */
            scrollToPositionWithOffset(mPendingTargetPos, mPendingPosOffset);
            mPendingTargetPos = -1;
            mPendingPosOffset = -1;
        }
        super.onLayoutChildren(recycler, state);
    }

    @Override
    public Parcelable onSaveInstanceState() {
        SavedInstanceState state = new SavedInstanceState();
        state.savedPendingTargetPos = mPendingTargetPos;
        state.savedPendingPosOffset = mPendingPosOffset;
        state.savedSuperState = super.onSaveInstanceState();
        return state;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (state instanceof SavedInstanceState savedState) {
            mPendingTargetPos = savedState.savedPendingTargetPos;
            mPendingPosOffset = savedState.savedPendingPosOffset;
            super.onRestoreInstanceState(savedState.savedSuperState);
        }
    }

    /**
     * Sets a start position that will be used <b>as soon as data is available</b>.
     * May be used if your Adapter starts with itemCount=0 (async data loading) but you need to
     * set the start position already at this time. As soon as itemCount > 0,
     * it will set the scrollPosition, so that given itemPosition is visible.
     *
     * @param position Index of the item to be shown
     * @param offset Offset in pixels between the start of the item and the start of the RecyclerView
     */
    public void setTargetStartPos(int position, int offset) {
        mPendingTargetPos = position;
        mPendingPosOffset = offset;
    }

    /**
     * Shortcut to call {@link #setTargetStartPos(int, int)} on a RecyclerView's LayoutManager,
     * provided that the LayoutManager is an instance of {@link OffsetLinearLayoutManager}.
     *
     * @param recyclerView The RecyclerView
     * @param position The index of the item to be shown
     * @param offset Offset in pixels between the start of the item and the start of the RecyclerView
     *
     * @throws IllegalStateException if the LayoutManager is not an instance of {@link OffsetLinearLayoutManager}
     */
    public static void setTargetStartPosTo(RecyclerView recyclerView, int position, int offset) {
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager instanceof OffsetLinearLayoutManager ollm) {
            ollm.setTargetStartPos(position, offset);
        } else {
            throw new IllegalStateException("RecyclerView's LayoutManager is not an instance of OffsetLinearLayoutManager");
        }
    }

    private static class SavedInstanceState implements Parcelable {

        public int savedPendingTargetPos;
        public int savedPendingPosOffset;
        public Parcelable savedSuperState;

        public SavedInstanceState() {

        }

        protected SavedInstanceState(Parcel in) {
            savedPendingTargetPos = in.readInt();
            savedPendingPosOffset = in.readInt();
            savedSuperState = ParcelCompat.readParcelable(in, RecyclerView.LayoutManager.class.getClassLoader(), Parcelable.class);
        }

        public static final Creator<SavedInstanceState> CREATOR = new Creator<SavedInstanceState>() {
            @Override
            public SavedInstanceState createFromParcel(Parcel in) {
                return new SavedInstanceState(in);
            }

            @Override
            public SavedInstanceState[] newArray(int size) {
                return new SavedInstanceState[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeInt(savedPendingTargetPos);
            dest.writeInt(savedPendingPosOffset);
            dest.writeParcelable(savedSuperState, flags);
        }
    }
}
