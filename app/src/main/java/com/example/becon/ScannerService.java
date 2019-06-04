package com.example.becon;

import android.app.Service;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import static com.example.becon.UrlUtils.decodeUrl;


public class ScannerService extends Service {
    private static final String TAG =
            ScannerService.class.getSimpleName();



    private String url;

    // Eddystone service uuid (0xfeaa)
    private static final ParcelUuid UID_SERVICE =
            ParcelUuid.fromString("0000feaa-0000-1000-8000-00805f9b34fb");

    // Eddystone frame types
    private static final byte TYPE_UID = 0x00;
    private static final byte TYPE_URL = 0x10;
    private static final byte TYPE_TLM = 0x20;

    //Callback interface for the UI
    public interface OnBeaconEventListener {
        void onBeaconIdentifier(String deviceAddress, int rssi, String instanceId);
        void onBeaconTelemetry(String deviceAddress, float battery, float temperature, String url, String serviceUuid);
    }

    private BluetoothLeScanner mBluetoothLeScanner;
    private OnBeaconEventListener mBeaconEventListener;

    @Override
    public void onCreate() {
        super.onCreate();

        BluetoothManager manager =
                (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBluetoothLeScanner = manager.getAdapter().getBluetoothLeScanner();

        startScanning();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        stopScanning();
    }

    public void setBeaconEventListener(OnBeaconEventListener listener) {
        mBeaconEventListener = listener;
    }

    /* Using as a bound service to allow event callbacks */
    private LocalBinder mBinder = new LocalBinder();
    public class LocalBinder extends Binder {
        public ScannerService getService() {
            return ScannerService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /* Being scanning for Eddystone advertisers */
    private void startScanning() {

        // No Filters
        List<ScanFilter> filters = new ArrayList<>();

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        mBluetoothLeScanner.startScan(filters, settings, mScanCallback);
        Log.d(TAG, "Scanning started…");
    }

    /* Terminate scanning */
    private void stopScanning() {
        mBluetoothLeScanner.stopScan(mScanCallback);
        Log.d(TAG, "Scanning stopped…");
    }

    /* Handle UID packet discovery on the main thread */
    private void processUidPacket(String deviceAddress, int rssi, String id) {
        Log.d(TAG, "Eddystone(" + deviceAddress + ") id = " + id);

        if (mBeaconEventListener != null) {
            mBeaconEventListener
                    .onBeaconIdentifier(deviceAddress, rssi, id);
        }
    }

    /* Handle TLM packet discovery on the main thread */
    private void processTlmPacket(String deviceAddress, float battery, float temp, String url, String serviceUuid) {
        Log.d(TAG, "Eddystone(" + deviceAddress + ") battery = " + battery
                + ", temp = " + temp);

        if (mBeaconEventListener != null) {
            mBeaconEventListener
                    .onBeaconTelemetry(deviceAddress, battery, temp, url, serviceUuid);
        }
    }

    /* Process each unique BLE scan result */
    private ScanCallback mScanCallback = new ScanCallback() {
        private Handler mCallbackHandler =
                new Handler(Looper.getMainLooper());

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            processResult(result);
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.w(TAG, "Scan Error Code: " + errorCode);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                processResult(result);
            }
        }

        private void processResult(ScanResult result) {
            byte[] data = result.getScanRecord().getServiceData(UID_SERVICE);
            if (data == null) {
                Log.w(TAG, "Invalid Eddystone scan result.");
                return;
            }

            final String deviceAddress = result.getDevice().getAddress();
            final int rssi = result.getRssi();
            final String serviceUuid = String.valueOf(result.getScanRecord().getServiceUuids());
            byte frameType = data[0];
            switch (frameType) {
                case TYPE_UID:
                    final String id = Beacon.getInstanceId(data);

                    mCallbackHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            processUidPacket(deviceAddress, rssi, id);
                        }
                    });
                    break;
                case TYPE_TLM:
                    //Parse out battery voltage
                    final float battery = Beacon.getTlmBattery(data);
                    final float temp = Beacon.getTlmTemperature(data);
                    mCallbackHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            processTlmPacket(deviceAddress, battery, temp, url, serviceUuid);
                        }
                    });
                    break;
                case TYPE_URL:
                    url = decodeUrl(data);
                    break;
                default:
                    Log.w(TAG, "Invalid Eddystone scan result.");
            }
        }
    };
}
