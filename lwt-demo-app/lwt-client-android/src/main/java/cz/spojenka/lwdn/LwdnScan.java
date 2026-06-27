package cz.spojenka.lwdn;

public class LwdnScan extends AbstractScan<LwdnScanResult, LwdnScanException, LwdnScan> {

    public void addOnResultListener(OnResultListener listener) {
        addOnResultListenerImpl(listener);
    }

    public void addOnFinishedListener(OnFinishedListener listener) {
        addOnFinishedListenerImpl(listener);
    }

    public void removeOnResultListener(OnResultListener listener) {
        removeOnResultListenerImpl(listener);
    }

    public void removeOnFinishedListener(OnFinishedListener listener) {
        removeOnFinishedListenerImpl(listener);
    }

    public static interface OnResultListener extends AbstractScan.OnResultListener<LwdnScan, LwdnScanResult, LwdnScanException> {

    }

    public static interface OnFinishedListener extends AbstractScan.OnFinishedListener<LwdnScan, LwdnScanResult, LwdnScanException> {

    }
}
