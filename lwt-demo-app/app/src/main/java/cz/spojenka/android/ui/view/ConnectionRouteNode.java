package cz.spojenka.android.ui.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import com.google.android.material.color.MaterialColors;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import cz.spojenka.android.util.ViewUtils;
import cz.spojenka.lwt.demoapp.R;

/**
 * Custom view for displaying a node in a connection route.
 * The node may have a path leading into it and out of it, as well as one of
 * several kinds of icons and colors.
 * XML configuration is also supported.
 */
public class ConnectionRouteNode extends View {

    private PathType pathTypeIn = PathType.NORMAL;
    private boolean showPathIn = true;
    private PathType pathTypeOut = PathType.NORMAL;
    private boolean showPathOut = true;
    private NodeType nodeType = NodeType.NORMAL;
    private boolean showNode = true;
    private boolean isNodeCurrent = false;

    private int drawHeight;
    private int pathWidth;
    private int nodeDiameter;
    private int nodeMargin;

    private final Paint nodePaint = new Paint();
    private final Paint pathPaint = new Paint();

    private Drawable iconDrawable;

    private boolean hasCustomColor = false;
    private int customColor;

    private float specialNodeRadiusScale = 1.5f;

    public ConnectionRouteNode(Context context) {
        super(context);
    }

    public ConnectionRouteNode(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ConnectionRouteNode(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ConnectionRouteNode(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        try (TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ConnectionRouteNode, defStyleAttr, defStyleRes)) {
            pathWidth = a.getDimensionPixelSize(R.styleable.ConnectionRouteNode_pathWidth, ViewUtils.dpToPx(this, 5));
            nodeDiameter = a.getDimensionPixelSize(R.styleable.ConnectionRouteNode_nodeDiameter, ViewUtils.dpToPx(this, 10));
            nodeMargin = a.getDimensionPixelSize(R.styleable.ConnectionRouteNode_nodeMargin, ViewUtils.dpToPx(this, 3));
            nodeType = NodeType.values()[a.getInt(R.styleable.ConnectionRouteNode_nodeType, NodeType.NORMAL.ordinal())];
            showNode = a.getBoolean(R.styleable.ConnectionRouteNode_showNode, true);
            pathTypeIn = PathType.values()[a.getInt(R.styleable.ConnectionRouteNode_pathTypeIn, PathType.NORMAL.ordinal())];
            showPathIn = a.getBoolean(R.styleable.ConnectionRouteNode_showPathIn, true);
            pathTypeOut = PathType.values()[a.getInt(R.styleable.ConnectionRouteNode_pathTypeOut, PathType.NORMAL.ordinal())];
            showPathOut = a.getBoolean(R.styleable.ConnectionRouteNode_showPathOut, true);
            if (a.getType(R.styleable.ConnectionRouteNode_pathColor) != TypedValue.TYPE_NULL) {
                hasCustomColor = true;
                customColor = a.getColor(R.styleable.ConnectionRouteNode_pathColor, MaterialColors.getColor(this, android.R.attr.colorPrimary));
            }
        }
    }

    private float getNodeScaleAnimationCurveValue() {
        float baseWeight = System.currentTimeMillis() % 650 / 650f;
        float smooth = (float) Math.sin(baseWeight * Math.PI);
        return 0.75f + smooth * 0.75f;
    }

    public void setCustomIconDrawable(Drawable drawable) {
        this.iconDrawable = drawable;
        invalidate();
    }

    public void setCustomIconDrawable(@DrawableRes int resId) {
        this.iconDrawable = ResourcesCompat.getDrawable(getResources(), resId, null);
        invalidate();
    }

    public void setSpecialNodeRadiusScale(float specialNodeRadiusScale) {
        this.specialNodeRadiusScale = specialNodeRadiusScale;
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float nodeRadius = nodeDiameter / 2f;

        float nodeCX = getWidth() / 2f;
        float nodeCY = drawHeight / 2f;

        pathPaint.setStrokeWidth(pathWidth);

        if (showNode) {
            nodePaint.setColor(getNodeColor());
            nodePaint.setStyle(isNodeActive() ? Paint.Style.FILL : Paint.Style.STROKE);
            nodePaint.setStrokeWidth(nodeDiameter / 4f);

            if (nodeType == NodeType.SEGMENT_START || nodeType == NodeType.START || nodeType == NodeType.SEGMENT_END || nodeType == NodeType.FINISH) {
                nodeRadius = nodeRadius * specialNodeRadiusScale;
            }
            float nodeRadiusForBounds = nodeRadius;
            if (isNodeCurrent) {
                nodeRadius *= getNodeScaleAnimationCurveValue();
                nodeRadiusForBounds *= 1.5f;
                invalidate();
            }
            if (nodeType != NodeType.FINISH && nodeType != NodeType.START) {
                canvas.drawCircle(nodeCX, nodeCY, nodeRadius, nodePaint);
            } else {
                nodeRadius *= 1.75f;
                if (iconDrawable == null) {
                    iconDrawable = ResourcesCompat.getDrawable(getResources(), nodeType == NodeType.FINISH ? R.drawable.ic_finish_flag_24px : R.drawable.ic_flag_24px, null);
                }
                if (iconDrawable == null) {
                    throw new IllegalStateException("Finish drawable not found");
                }
                iconDrawable.setBounds((int) (nodeCX - nodeRadius), (int) (nodeCY - nodeRadius), (int) (nodeCX + nodeRadius), (int) (nodeCY + nodeRadius));
                iconDrawable.setTint(getNodeColor());
                iconDrawable.draw(canvas);
            }

            pathPaint.setStrokeCap(Paint.Cap.ROUND);

            float capSize = pathWidth / 2f;

            doDrawPath(canvas, nodeCX, nodeCY - nodeRadiusForBounds - nodeMargin - capSize, 0, pathTypeIn, showPathIn);
            doDrawPath(canvas, nodeCX, nodeCY + nodeRadiusForBounds + nodeMargin + capSize, drawHeight, pathTypeOut, showPathOut);
        }
        else {
            pathPaint.setStrokeCap(Paint.Cap.BUTT);

            doDrawPath(canvas, nodeCX, nodeCY, 0, pathTypeIn, showPathIn);
            doDrawPath(canvas, nodeCX, nodeCY, drawHeight, pathTypeOut, showPathOut);
        }
    }

    private void doDrawPath(Canvas canvas, float atX, float innerY, float outerY, PathType pathType, boolean show) {
        if (!show) {
            return;
        }
        pathPaint.setColor(getColorForPathType(pathType));
        if (pathType == PathType.ERROR) {
            float sw = pathPaint.getStrokeWidth();
            pathPaint.setPathEffect(new DashPathEffect(new float[]{0, sw * 2}, 0)); //innerY a outerY zajisti, ze se cesty sejdou
        }
        else {
            pathPaint.setPathEffect(null);
        }
        canvas.drawLine(atX, outerY, atX, innerY, pathPaint);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        drawHeight = h;
    }

    private int getColorForPathType(PathType pathType) {
        if (hasCustomColor) {
            return customColor;
        }
        return MaterialColors.getColor(this, switch (pathType) {
            case NORMAL -> R.attr.colorPrimaryMuted;
            case SELECTED -> android.R.attr.colorPrimary;
            case ERROR -> android.R.attr.colorError;
            default -> throw new IllegalStateException("Unexpected value: " + pathType);
        });
    }

    private PathType getNodeControllingPathType() {
        PathType decidePathType = pathTypeIn;
        if (pathTypeIn == pathTypeOut) {
            decidePathType = pathTypeIn;
        }
        else if (!showPathIn) {
            decidePathType = pathTypeOut;
        }
        else if (!showPathOut) {
            decidePathType = pathTypeIn;
        }
        else {
            if (nodeType == NodeType.SEGMENT_END || nodeType == NodeType.FINISH) {
                decidePathType = pathTypeIn;
            }
            else {
                decidePathType = pathTypeOut;
            }
        }
        return decidePathType;
    }

    private boolean isNodeActive() {
        return switch (getNodeControllingPathType()) {
            case SELECTED, ERROR -> true;
            default -> false;
        };
    }

    private int getNodeColor() {
        return getColorForPathType(getNodeControllingPathType());
    }

    /**
     * Set the type of the path leading into the node.
     * @param pathTypeIn
     */
    public void setPathTypeIn(PathType pathTypeIn) {
        if (this.pathTypeIn != pathTypeIn) {
            this.pathTypeIn = pathTypeIn;
            invalidate();
        }
    }

    public void setShowPathIn(boolean showPathIn) {
        if (this.showPathIn != showPathIn) {
            this.showPathIn = showPathIn;
            invalidate();
        }
    }

    /**
     * Set the type of the path leading out of the node.
     * @param pathTypeOut
     */
    public void setPathTypeOut(PathType pathTypeOut) {
        if (this.pathTypeOut != pathTypeOut) {
            this.pathTypeOut = pathTypeOut;
            invalidate();
        }
    }

    public void setShowPathOut(boolean showPathOut) {
        if (this.showPathOut != showPathOut) {
            this.showPathOut = showPathOut;
            invalidate();
        }
    }

    /**
     * Set the type of the node in the connection route.
     * @param nodeType
     */
    public void setNodeType(NodeType nodeType) {
        if (this.nodeType != nodeType) {
            this.nodeType = nodeType;
            invalidate();
        }
    }

    /**
     * Set whether the node should be shown. If false, only the paths will be drawn.
     * @param showNode
     */
    public void setShowNode(boolean showNode) {
        if (this.showNode != showNode) {
            this.showNode = showNode;
            invalidate();
        }
    }

    /**
     * Marks the node as the current node in the route.
     * In turn, the node graphic will pulsate (scale up and down) when displayed.
     *
     * @param nodeCurrent
     */
    public void setNodeCurrent(boolean nodeCurrent) {
        isNodeCurrent = nodeCurrent;
        invalidate();
    }

    /**
     * Set a custom color that will override the default color of the node and paths.
     *
     * @param customColor The color
     */
    public void setCustomColor(@ColorInt int customColor) {
        this.customColor = customColor;
        hasCustomColor = true;
        invalidate();
    }

    /**
     * Removes the custom color set with {@link #setCustomColor(int)} and reverts to the default colors of the node and paths.
     */
    public void removeCustomColor() {
        hasCustomColor = false;
        invalidate();
    }

    /**
     * Returns whether the node is marked as the current node in the route.
     *
     * @see #setNodeCurrent(boolean)
     *
     * @return True if the node is the current node, false otherwise
     */
    public boolean isNodeCurrent() {
        return isNodeCurrent;
    }

    /**
     * Get the type of the path leading into the node.
     *
     * @return
     */
    public PathType getPathTypeIn() {
        return pathTypeIn;
    }

    /**
     * Get the type of the path leading out of the node.
     *
     * @return
     */
    public PathType getPathTypeOut() {
        return pathTypeOut;
    }

    /**
     * Get the type of the node in the connection route.
     *
     * @return
     */
    public NodeType getNodeType() {
        return nodeType;
    }

    /**
     * Get the custom color set for the node and paths.
     *
     * @see #setCustomColor(int)
     *
     * @return The custom color
     */
    public int getCustomColor() {
        return customColor;
    }

    /**
     * Type of the node in the connection route.
     * This affects the appearance of the circular point that the node is drawn as.
     */
    public enum NodeType {
        /**
         * A segment start node is drawn. The node will be slightly larger than normal nodes.
         */
        SEGMENT_START,
        /**
         * A segment end node is drawn. The node will be slightly larger than normal nodes.
         */
        SEGMENT_END,
        /**
         * A start node is drawn. The node will have a starting flag icon.
         */
        START,
        /**
         * A finish node is drawn. The node will have a finish flag icon.
         */
        FINISH,
        /**
         * A normal node is drawn.
         */
        NORMAL
    }

    /**
     * Type of the path stroke used for the line in the middle of the route graphic.
     */
    public enum PathType {
        /**
         * A solid path is drawn.
         */
        NORMAL,
        /**
         * A solid path with a "selected" (highlight) color is drawn.
         */
        SELECTED,
        /**
         * A dashed path with an "error" color is drawn.
         */
        ERROR
    }
}
