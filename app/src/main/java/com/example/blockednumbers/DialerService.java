package com.example.blockednumbers;

import android.telecom.Call;
import android.telecom.InCallService;
import android.util.Log;

// This is a dummy service
public class DialerService extends InCallService {

    private static final String TAG = "DialerService";

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        Log.d(TAG, "Incoming or outgoing call added.");
        call.answer(0); // Automatically answer the call (for testing).
    }

    @Override
    public void onCallRemoved(Call call) {
        super.onCallRemoved(call);
        Log.d(TAG, "Call removed.");
    }
}
