package cz.spojenka.android.ui.helpers;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;

import cz.spojenka.lwt.demoapp.R;

public class EdgeToEdgeAttrs implements LayoutInflaterHook.ViewDecorator {

    private static EdgeToEdgeAttrs INSTANCE;

    public static EdgeToEdgeAttrs getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new EdgeToEdgeAttrs();
        }
        return INSTANCE;
    }

    @Override
    public void decorate(View view, String name, Context context, AttributeSet attrs) {
        try (TypedArray options = context.obtainStyledAttributes(attrs, R.styleable.EdgeToEdgeAttrs)) {
            int flags = 0;
            int sides = options.getInt(R.styleable.EdgeToEdgeAttrs_marginWindowInsets, 0);
            if (sides == 0) {
                flags = EdgeToEdgeSupport.FLAG_APPLY_AS_PADDING;
                sides = options.getInt(R.styleable.EdgeToEdgeAttrs_paddingWindowInsets, 0);
            }
            if (sides == 0) {
                flags = EdgeToEdgeSupport.FLAG_APPLY_AS_DIMENSION;
                sides = options.getInt(R.styleable.EdgeToEdgeAttrs_widthWindowInsets, 0) |
                        options.getInt(R.styleable.EdgeToEdgeAttrs_heightWindowInsets, 0);
            }

            if (sides != 0) {
                EdgeToEdgeSupport.installInsets(view, sides, flags);
            }
        }
    }
}
