package com.noisefit.watchface.ble;

import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class NoiseFitBleManager {
    private static final String TAG = "NoiseFitBLE";
    private Context context;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private BluetoothGatt bluetoothGatt;
    
    private List<BluetoothDevice> discoveredDevices = new CopyOnWriteArrayList<>();
    private Map<UUID, BluetoothGattService> services = new HashMap<>();
    private List<String> logs = new ArrayList<>();
    
    private ConnectionCallback connectionCallback;
    private DeviceScanCallback deviceScanCallback;
    private DataCallback dataCallback;
    private ScanCallback leScanCallback;
    
    public interface ConnectionCallback {
        void onStateChanged(int state);
        void onServicesDiscovered(Map<UUID, BluetoothGattService> services);
    }
    
    public interface DeviceScanCallback {
        void onDeviceFound(BluetoothDevice device);
    }
    
    public interface DataCallback {
        void onLog(String message);
        void onProgress(float progress);
    }
    
    public NoiseFitBleManager(Context context) {
        this.context = context;
        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;
        if (bluetoothAdapter != null) {
            bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
    }
    
    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }
    
    public void setConnectionCallback(ConnectionCallback cb) {
        this.connectionCallback = cb;
    }
    
    public void setScanCallback(DeviceScanCallback cb) {
        this.deviceScanCallback = cb;
    }
    
    public void setDataCallback(DataCallback cb) {
        this.dataCallback = cb;
    }
    
    public void log(String msg) {
        Log.d(TAG, msg);
        if (dataCallback != null) {
            dataCallback.onLog(msg);
        }
        logs.add(msg);
    }
    
    public List<String> getLogs() {
        return logs;
    }
    
    private static final String[] DEVICE_NAMES = {"NF", "Noise", "ColorFit", "FitPro", "Watch", "Bolt"};
    
    private Handler scanStopHandler;
    
    public void startScan() {
        if (!isBluetoothEnabled()) {
            log("ERROR: Bluetooth not enabled");
            return;
        }
        
        if (bleScanner == null) {
            log("ERROR: BLE Scanner null");
            return;
        }
        
        log("Starting BLE scan...");
        
        stopScan();
        
        discoveredDevices.clear();
        
        leScanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result.getDevice();
                String name = device.getName();
                if (name == null) name = "Unknown";
                
                int rssi = result.getRssi();
                log("BLE: " + name + " (" + device.getAddress() + ") RSSI:" + rssi);
                
                boolean isWatch = false;
                String lowerName = name.toLowerCase();
                for (String pattern : DEVICE_NAMES) {
                    if (pattern.isEmpty() || lowerName.contains(pattern.toLowerCase())) {
                        isWatch = true;
                        break;
                    }
                }
                
                if (isWatch && !discoveredDevices.contains(device)) {
                    discoveredDevices.add(device);
                    log(">>> WATCH: " + name + " (" + device.getAddress() + ")");
                    if (deviceScanCallback != null) {
                        deviceScanCallback.onDeviceFound(device);
                    }
                }
            }
            
            @Override
            public void onScanFailed(int errorCode) {
                log("Scan failed: " + errorCode);
            }
        };
        
        try {
            ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
            
            bleScanner.startScan(null, settings, leScanCallback);
            log("Scan started");
        } catch (Exception e) {
            log("ERROR: " + e.getMessage());
        }
        
        if (scanStopHandler == null) {
            scanStopHandler = new Handler(Looper.getMainLooper());
        }
        scanStopHandler.postDelayed(() -> stopScan(), 30000);
    }
    
    public void stopScan() {
        try {
            if (bleScanner != null && leScanCallback != null) {
                bleScanner.stopScan(leScanCallback);
                leScanCallback = null;
                log("Scan stopped");
            }
        } catch (Exception e) {
            log("Stop error: " + e.getMessage());
        }
    }
    
    public void connect(BluetoothDevice device) {
        log("Connecting to " + device.getName() + "...");
        
        if (connectionCallback != null) {
            connectionCallback.onStateChanged(1);
        }
        
        bluetoothGatt = device.connectGatt(context, false, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    log("Connected!");
                    gatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    log("Disconnected");
                    if (connectionCallback != null) {
                        connectionCallback.onStateChanged(0);
                    }
                }
            }
            
            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    services.clear();
                    for (BluetoothGattService service : gatt.getServices()) {
                        services.put(service.getUuid(), service);
                        log("Service: " + service.getUuid());
                        
                        for (BluetoothGattCharacteristic charac : service.getCharacteristics()) {
                            String props = "";
                            if ((charac.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) > 0) props += "R ";
                            if ((charac.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) props += "W ";
                            if ((charac.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) props += "N ";
                            log("  Char: " + charac.getUuid() + " (" + props + ")");
                        }
                    }
                    
                    if (connectionCallback != null) {
                        connectionCallback.onStateChanged(2);
                        connectionCallback.onServicesDiscovered(services);
                    }
                }
            }
            
            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                log("Sent " + characteristic.getValue().length + " bytes");
            }
        });
    }
    
    public void sendWatchFace(byte[] data) {
        if (bluetoothGatt == null || services.isEmpty()) {
            log("Not connected");
            return;
        }
        
        log("Sending " + data.length + " bytes...");
        
        for (BluetoothGattService service : services.values()) {
            for (BluetoothGattCharacteristic charac : service.getCharacteristics()) {
                if ((charac.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) {
                    log("Using: " + charac.getUuid());
                    
                    int chunkSize = 20;
                    int total = (data.length + chunkSize - 1) / chunkSize;
                    
                    for (int i = 0; i < total; i++) {
                        int start = i * chunkSize;
                        int end = Math.min(start + chunkSize, data.length);
                        byte[] chunk = Arrays.copyOfRange(data, start, end);
                        
                        charac.setValue(chunk);
                        bluetoothGatt.writeCharacteristic(charac);
                        
                        float progress = (float)(i + 1) / total;
                        if (dataCallback != null) {
                            dataCallback.onProgress(progress);
                        }
                        
                        try { Thread.sleep(10); } catch (Exception e) {}
                    }
                    
                    log("Done!");
                    return;
                }
            }
        }
        
        log("No writable characteristic");
    }
    
    public void disconnect() {
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
    }
    
    public List<BluetoothDevice> getDevices() {
        return discoveredDevices;
    }
    
    public Map<UUID, BluetoothGattService> getServices() {
        return services;
    }
}
