package cz.spojenka.android.ui.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import cz.spojenka.android.system.SubscreenSwitcher;
import cz.spojenka.lwt.demoapp.R;

/**
 * A container that can toggle between a loading screen and multiple content views.
 *
 * @see LoadingPlaceholderContainer#wrapContainer(Context, ViewGroup)
 * @see LoadingPlaceholderContainer#wrapScreens(Context, View...)
 */
public class LoadingPlaceholderContainer {

    private final ViewGroup root;
    private final View loadingScreen;
    private final LoadingScreen loadingScreenController;
    private final SubscreenSwitcher subscreens;
    private View defaultViewOption;
    private View lastContentView;

    private LoadingPlaceholderContainer(Context context, View... viewOptions) {
        this(context, createRootContainer(context, viewOptions));
    }

    private static FrameLayout createRootContainer(Context context, View... children) {
        FrameLayout root = new FrameLayout(context);
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        for (View view : children) {
            view.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            view.setVisibility(View.GONE);
            root.addView(view);
        }

        return root;
    }

    private LoadingPlaceholderContainer(Context context, ViewGroup root) {
        this.root = root;

        loadingScreen = LayoutInflater.from(context).inflate(R.layout.loading_screen, root, false);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        loadingScreen.setLayoutParams(lp);
        root.addView(loadingScreen);
        loadingScreenController = new LoadingScreen(loadingScreen, null);

        View[] subscreenViews = new View[root.getChildCount()];
        for (int i = 0; i < root.getChildCount(); i++) {
            subscreenViews[i] = root.getChildAt(i);
        }

        subscreens = new SubscreenSwitcher(subscreenViews);
        if (subscreenViews.length > 1) { //more than just loading screen
            this.defaultViewOption = subscreenViews[0];
        }

        showContent(loadingScreen);
    }

    public void setDimensions(int width, int height) {
        ViewGroup.LayoutParams params = loadingScreen.getLayoutParams();
        params.width = width;
        params.height = height;
        loadingScreen.setLayoutParams(params);
    }

    public void setPadding(int horizontal, int vertical) {
        loadingScreen.setPadding(horizontal, vertical, horizontal, vertical);
    }

    /**
     * Create a new LoadingPlaceholderContainer that uses the provided ViewGroup as the list
     * of available screens. The loading screen will be injected as a child of the provided root.
     * This method is useful when wrapping a container with screens from XML.
     *
     * @param context Context
     * @param root    Root ViewGroup
     * @return
     */
    public static LoadingPlaceholderContainer wrapContainer(Context context, ViewGroup root) {
        return new LoadingPlaceholderContainer(context, root);
    }

    /**
     * Create a new LoadingPlaceholderContainer for the provided screens.
     * A root ViewGroup will be created which contains the loading screen as well as the provided
     * View options. This method is useful when creating a container with screens programmatically.
     *
     * @param context     Context
     * @param viewOptions List of views to switch between
     * @return
     */
    public static LoadingPlaceholderContainer wrapScreens(Context context, View... viewOptions) {
        return new LoadingPlaceholderContainer(context, viewOptions);
    }

    /**
     * Get the root View of the container.
     *
     * @return
     */
    public ViewGroup getRoot() {
        return root;
    }

    /**
     * Show the loading screen.
     */
    public void showLoading() {
        showContent(loadingScreen);
    }

    /**
     * Hide all screens.
     */
    public void hideAll() {
        subscreens.setSubscreen(null);
    }

    /**
     * Show the default content view. This is either the last shown content screen, or the first of all view options provided when creating
     * the container. The method should mostly be used in cases where the container only has one non-loading screen.
     */
    public void showContent() {
        showContent(lastContentView != null ? lastContentView : defaultViewOption);
    }

    /**
     * Show the provided content view.
     *
     * @param contentView View to show, must be one of the views provided when creating the container.
     */
    public void showContent(View contentView) {
        if (contentView != loadingScreen) {
            lastContentView = contentView;
        }
        subscreens.setSubscreen(contentView);
    }

    /**
     * Set the loading description text.
     *
     * @param text Text string
     */
    public void setLoadingDescription(String text) {
        loadingScreenController.setDescription(text);
    }

    /**
     * Set the loading description text.
     *
     * @param textId Text resource ID
     */
    public void setLoadingDescription(int textId) {
        loadingScreenController.setDescription(textId);
    }
}
