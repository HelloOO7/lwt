package cz.spojenka.lwt.demoapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.pm.PackageManager;
import android.net.wifi.aware.WifiAwareManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.flatbuffers.FlatBufferBuilder;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import lwt.Operation;
import lwt.PingResponse;
import lwt.RequestPacket;
import lwt.ResponsePacket;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "LWTDemoApp";

    private BluetoothAdapter bluetoothAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /*if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE) && getSystemService(WifiAwareManager.class).isAvailable()) {
            Toast.makeText(this, "Wi-Fi Aware is supported and available on this device.", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Wi-Fi Aware is not supported or not available on this device.", Toast.LENGTH_LONG).show();
        }*/
        bluetoothAdapter = getSystemService(BluetoothManager.class).getAdapter();
        ScanFilter filter = new ScanFilter.Builder()
                .setDeviceName("LWT ESP32")
                .build();
        ScanSettings settings = new ScanSettings.Builder()
                .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            bluetoothAdapter.getBluetoothLeScanner().startScan(List.of(filter), settings, scanCallback);
        } else {
            Toast.makeText(this, "Bluetooth scan permission not granted.", Toast.LENGTH_LONG).show();
        }
    }

    private void testBluetoothComm(BluetoothDevice dev) {
        try (BluetoothSocket socket = dev.createInsecureL2capChannel(0xD7)) {
            socket.connect();
            FlatBufferBuilder fbb = new FlatBufferBuilder();
            fbb.finish(RequestPacket.createRequestPacket(fbb, Operation.Ping, RequestPacket.createDataVector(fbb, new byte[0])));
            try (DataOutputStream out = new DataOutputStream(socket.getOutputStream()); DataInputStream in = new DataInputStream(socket.getInputStream())) {
                out.write('L');
                out.write('W');
                out.write('T');
                out.write('P');
                out.writeShort(1);
                out.write(0); // flags
                out.write(10);
                byte[] data = fbb.sizedByteArray();
                out.writeShort(data.length);
                out.write(data);

                byte[] header = new byte[4];
                in.readFully(header);
                int version = in.readUnsignedShort();
                if (header[0] == 'L' && header[1] == 'W' && header[2] == 'T' && header[3] == 'P') {
                    if (version == 1) {
                        int flags = in.readUnsignedByte();
                        int headerSize = in.readUnsignedByte();
                        int length = in.readUnsignedShort();
                        byte[] payload = new byte[length];
                        in.readFully(payload);
                        ResponsePacket resp = ResponsePacket.getRootAsResponsePacket(ByteBuffer.wrap(payload));
                        Log.i(TAG, "Resp=" + resp.statusCode());
                        if (resp.statusCode() == 200) {
                            PingResponse pingResponse = PingResponse.getRootAsPingResponse(resp.dataAsByteBuffer());
                            Log.i(TAG, "Ping response: " + pingResponse.deviceId() + ", uptime=" + pingResponse.deviceTime());
                        }
                    } else {
                        Log.e(TAG, "Unsupported protocol version: " + version);
                    }
                } else {
                    Log.e(TAG, "Invalid response header");
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to create Bluetooth socket", e);
        }
    }

    private ScanCallback scanCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            testBluetoothComm(result.getDevice());
            bluetoothAdapter.getBluetoothLeScanner().stopScan(scanCallback);
        }
    };
}
