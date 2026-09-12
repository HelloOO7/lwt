package cz.spojenka.lwt.demoapp;

import android.bluetooth.BluetoothManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import cz.spojenka.android.ui.activity.BaseActivity;
import cz.spojenka.lwdn.BluetoothLwdnAddress;
import cz.spojenka.lwdn.BluetoothLwdnScanner;
import cz.spojenka.lwdn.LwdnScan;
import cz.spojenka.lwdn.LwdnScanConfig;
import cz.spojenka.lwdn.LwdnScanException;
import cz.spojenka.lwdn.LwdnScanResult;
import cz.spojenka.lwdn.LwdnServiceID;
import cz.spojenka.lwt.LwtServiceConstants;
import cz.spojenka.lwt.demoapp.databinding.ActivityRssiTrackerBinding;

public class RssiTrackerActivity extends BaseActivity {

    // made by gemini

    private ActivityRssiTrackerBinding binding;
    private LwdnScan activeScan;
    private TrackerAdapter adapter;

    private final List<TrackerEntry> entries = new ArrayList<>();
    private final Map<String, Integer> addressToIndexMap = new HashMap<>();

    private static class TrackerEntry {
        final String address;
        int rssi;

        TrackerEntry(String address, int rssi) {
            this.address = address;
            this.rssi = rssi;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRssiTrackerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("CICO Keepalive RSSI Tracker");
        }

        adapter = new TrackerAdapter();
        binding.rvTrackerList.setAdapter(adapter);

        startTracking();
    }

    private void startTracking() {
        BluetoothManager btm = getSystemService(BluetoothManager.class);
        if (btm == null || btm.getAdapter() == null) {
            Toast.makeText(this, "Bluetooth not available", Toast.LENGTH_SHORT).show();
            return;
        }

        BluetoothLwdnScanner scanner = new BluetoothLwdnScanner(this, btm.getAdapter(), LwtServiceConstants.BLE_API_PSM, true);
        LwdnScanConfig config = new LwdnScanConfig.Builder()
                .setTimeout(null) // continuous
                .setDeviceLostTimeout(Duration.ofSeconds(15))
                .build();

        var serviceId = new LwdnServiceID.UUID(LwtServiceConstants.BLE_SERVICE_UUID_CICO_KEEPALIVE);

        activeScan = scanner.startScan(List.of(serviceId), config);
        activeScan.addOnResultListener(new LwdnScan.OnResultListener() {
            @Override
            public void onResult(LwdnScan scan, LwdnScanResult result) {
                String addressStr = "Unknown";
                if (result.deviceAddress() instanceof BluetoothLwdnAddress btAddress) {
                    addressStr = btAddress.getDevice().getAddress();
                } else if (result.deviceAddress() != null) {
                    addressStr = result.deviceAddress().toString();
                }

                int rssi = result.rssi();
                Integer index = addressToIndexMap.get(addressStr);

                if (index == null) {
                    TrackerEntry newEntry = new TrackerEntry(addressStr, rssi);
                    entries.add(newEntry);
                    int newIndex = entries.size() - 1;
                    addressToIndexMap.put(addressStr, newIndex);
                    adapter.notifyItemInserted(newIndex);
                } else {
                    TrackerEntry entry = entries.get(index);
                    entry.rssi = rssi;
                    adapter.notifyItemChanged(index);
                }
            }

            @Override
            public void onResultLost(LwdnScan scan, LwdnScanResult result) {
                String addressStr = "Unknown";
                if (result.deviceAddress() instanceof BluetoothLwdnAddress btAddress) {
                    addressStr = btAddress.getDevice().getAddress();
                } else if (result.deviceAddress() != null) {
                    addressStr = result.deviceAddress().toString();
                }

                Integer index = addressToIndexMap.remove(addressStr);
                if (index != null) {
                    entries.remove((int) index);
                    adapter.notifyItemRemoved(index);
                    
                    // Since an item was removed, we must re-map indices for all subsequent entries
                    for (int i = index; i < entries.size(); i++) {
                        addressToIndexMap.put(entries.get(i).address, i);
                    }
                }
            }

            @Override
            public void onFailure(LwdnScan scan, LwdnScanException e) {
                Toast.makeText(RssiTrackerActivity.this, "Scan failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (activeScan != null) {
            activeScan.cancel();
        }
    }

    private class TrackerAdapter extends RecyclerView.Adapter<TrackerViewHolder> {

        @NonNull
        @Override
        public TrackerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.rssi_tracker_item, parent, false);
            return new TrackerViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull TrackerViewHolder holder, int position) {
            TrackerEntry entry = entries.get(position);
            holder.tvDeviceAddress.setText(entry.address);
            holder.tvRssiValue.setText(entry.rssi + " dBm");
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }
    }

    private static class TrackerViewHolder extends RecyclerView.ViewHolder {
        final TextView tvDeviceAddress;
        final TextView tvRssiValue;

        TrackerViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDeviceAddress = itemView.findViewById(R.id.tvDeviceAddress);
            tvRssiValue = itemView.findViewById(R.id.tvRssiValue);
        }
    }
}
