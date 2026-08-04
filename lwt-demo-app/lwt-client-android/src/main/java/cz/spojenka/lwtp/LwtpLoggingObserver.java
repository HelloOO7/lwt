package cz.spojenka.lwtp;

import android.util.Log;

public class LwtpLoggingObserver implements LwtpSession.ExecutionObserver {

    private static final String TAG = "LWTP";

    @Override
    public void onStartRequest(LwtpPacket request) {
        Log.d(TAG, "-----> " + request.getPayload().remaining() + " b");
    }

    @Override
    public void onResponseReceived(LwtpPacket request, LwtpPacket response) {
        Log.d(TAG, "<----- " + response.getPayload().remaining() + " b");
    }
}
