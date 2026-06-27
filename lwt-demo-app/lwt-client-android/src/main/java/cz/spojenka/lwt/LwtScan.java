package cz.spojenka.lwt;

import cz.spojenka.lwdn.AbstractScan;
import cz.spojenka.lwdn.LwdnScanException;

public class LwtScan extends AbstractScan<LwtDevice, LwdnScanException, LwtScan> {

    /*
    protected methods to expose the superclass methods to the package, so that LwtDeviceScanner can call them.
     */

    protected void addResult(LwtDevice result) {
        super.addResult(result);
    }

    protected void markFinished() {
        super.markFinished();
    }

    protected void markFailed(LwdnScanException e) {
        super.markFailed(e);
    }

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

    public static interface OnResultListener extends AbstractScan.OnResultListener<LwtScan, LwtDevice, LwdnScanException> {

    }

    public static interface OnFinishedListener extends AbstractScan.OnFinishedListener<LwtScan, LwtDevice, LwdnScanException> {

    }
}
