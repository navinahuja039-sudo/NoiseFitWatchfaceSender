package com.noisefit.watchface;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;
import android.provider.OpenableColumns;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.noisefit.watchface.ble.NoiseFitBleManager;
import com.noisefit.watchface.util.WatchFaceCreator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import android.bluetooth.BluetoothGattService;

public class MainActivity extends AppCompatActivity {
    
    private static final int PERM_REQUEST = 1001;
    
    private NoiseFitBleManager bleManager;
    private ArrayAdapter<String> adapter;
    private List<BluetoothDevice> devices = new ArrayList<>();
    
    private Button scanBtn, sendBtn, selectBtn, analyzeBtn;
    private ListView deviceList;
    private TextView statusText, logText;
    private ProgressBar progressBar;
    private ImageView previewImage;
    private ScrollView scrollView;
    
    private String watchfacePath;
    private Bitmap selectedBitmap;
    
    private ActivityResultLauncher<String> imagePicker;
    
    private android.os.Handler timerHandler;
    private Runnable timerRunnable;
    private int scanSecondsLeft = 30;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        bleManager = new NoiseFitBleManager(this);
        
        initViews();
        setupListeners();
        requestPermissions();
        
        log("App ready. Select image or scan for watch.");
    }
    
    private void initViews() {
        scanBtn = findViewById(R.id.scanButton);
        sendBtn = findViewById(R.id.sendButton);
        selectBtn = findViewById(R.id.selectImageButton);
        analyzeBtn = findViewById(R.id.analyzeButton);
        deviceList = findViewById(R.id.deviceListView);
        statusText = findViewById(R.id.statusText);
        logText = findViewById(R.id.bleLogText);
        progressBar = findViewById(R.id.progressBar);
        previewImage = findViewById(R.id.previewImage);
        scrollView = findViewById(R.id.scrollView);
        
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        deviceList.setAdapter(adapter);
        
        imagePicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            this::handleImageSelected
        );
    }
    
    private void setupListeners() {
        selectBtn.setOnClickListener(v -> imagePicker.launch("image/*"));
        
        analyzeBtn.setOnClickListener(v -> analyzeWatchface());
        
        scanBtn.setOnClickListener(v -> startScan());
        
        sendBtn.setOnClickListener(v -> sendWatchface());
        
        bleManager.setConnectionCallback(new NoiseFitBleManager.ConnectionCallback() {
            @Override
            public void onStateChanged(int state) {
                runOnUiThread(() -> {
                    switch (state) {
                        case 0: // DISCONNECTED
                            statusText.setText("Disconnected");
                            scanBtn.setEnabled(true);
                            sendBtn.setEnabled(false);
                            break;
                        case 1: // CONNECTING
                            statusText.setText("Connecting...");
                            break;
                        case 2: // CONNECTED
                            statusText.setText("Connected!");
                            sendBtn.setEnabled(true);
                            break;
                    }
                });
            }
            
            @Override
            public void onServicesDiscovered(Map<UUID, BluetoothGattService> services) {
                runOnUiThread(() -> log("=== Services discovered: " + services.size() + " ==="));
            }
        });
        
        bleManager.setScanCallback(device -> {
            runOnUiThread(() -> {
                if (!devices.contains(device)) {
                    devices.add(device);
                    adapter.add(device.getName() + "\n" + device.getAddress());
                    adapter.notifyDataSetChanged();
                }
            });
        });
        
        bleManager.setDataCallback(new NoiseFitBleManager.DataCallback() {
            @Override
            public void onLog(String message) {
                runOnUiThread(() -> {
                    logText.append(message + "\n");
                    scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
                });
            }
            
            @Override
            public void onProgress(float progress) {
                runOnUiThread(() -> {
                    progressBar.setProgress((int)(progress * 100));
                    statusText.setText("Sending: " + (int)(progress * 100) + "%");
                });
            }
        });
    }
    
    private void handleImageSelected(Uri uri) {
        if (uri == null) return;
        
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            selectedBitmap = BitmapFactory.decodeStream(is);
            is.close();
            
            if (selectedBitmap != null) {
                previewImage.setImageBitmap(selectedBitmap);
                previewImage.setVisibility(View.VISIBLE);
                
                // Save as watchface
                File cache = getCacheDir();
                File wf = new File(cache, "custom_wf.wf");
                watchfacePath = wf.getAbsolutePath();
                
                WatchFaceCreator.createWatchface(selectedBitmap, watchfacePath, 140, 140);
                
                statusText.setText("Image ready!");
                log("Watchface prepared (140x140 RGB565)");
                Toast.makeText(this, "Ready to send!", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            log("Error: " + e.getMessage());
        }
    }
    
    private void analyzeWatchface() {
        // Try multiple paths
        String[] paths = {
            getCacheDir() + "/3743",
            "/storage/emulated/0/Download/3743",
            "/sdcard/Download/3743"
        };
        
        String foundPath = null;
        for (String p : paths) {
            File f = new File(p);
            if (f.exists()) {
                foundPath = p;
                break;
            }
        }
        
        if (foundPath != null) {
            String info = WatchFaceCreator.analyzeWatchface(foundPath);
            log(info);
            
            Bitmap preview = WatchFaceCreator.extractPreview(foundPath);
            if (preview != null) {
                previewImage.setImageBitmap(preview);
                previewImage.setVisibility(View.VISIBLE);
            }
            
            // Set this as the watchface to send
            watchfacePath = foundPath;
            log("Watchface loaded from: " + foundPath);
        } else {
            log("File not found. Use 'Select Image' to pick one.");
        }
    }
    
    private void startScan() {
        if (!bleManager.isBluetoothEnabled()) {
            Toast.makeText(this, "Enable Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Cancel any existing timer
        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
        
        devices.clear();
        adapter.clear();
        progressBar.setVisibility(View.VISIBLE);
        
        scanSecondsLeft = 30;
        scanBtn.setEnabled(false);
        
        bleManager.startScan();
        
        // Start countdown timer
        timerHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (scanSecondsLeft > 0) {
                    statusText.setText("Scanning... (" + scanSecondsLeft + "s)");
                    scanSecondsLeft--;
                    timerHandler.postDelayed(this, 1000);
                } else {
                    scanBtn.setEnabled(true);
                    statusText.setText("Scan complete. Found " + devices.size() + " device(s)");
                }
            }
        };
        timerHandler.post(timerRunnable);
    }
    
    private void sendWatchface() {
        int pos = deviceList.getCheckedItemPosition();
        if (pos < 0 || pos >= devices.size()) {
            Toast.makeText(this, "Select a device", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (watchfacePath == null) {
            // Try default
            File f = new File("/storage/emulated/0/Download/3743");
            if (f.exists()) {
                watchfacePath = f.getAbsolutePath();
            } else {
                Toast.makeText(this, "Select image first!", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        
        BluetoothDevice device = devices.get(pos);
        statusText.setText("Connecting to " + device.getName() + "...");
        
        bleManager.connect(device);
        
        // Wait for connection then send (simplified)
        new android.os.Handler().postDelayed(() -> {
            try {
                byte[] data = new byte[(int) new File(watchfacePath).length()];
                new FileInputStream(watchfacePath).read(data);
                bleManager.sendWatchFace(data);
                statusText.setText("Done!");
                log("=== SUCCESS ===");
            } catch (Exception e) {
                log("Error: " + e.getMessage());
            }
        }, 3000);
    }
    
    private void log(String msg) {
        logText.append(msg + "\n");
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }
    
    private void requestPermissions() {
        String[] perms = {
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        };
        
        List<String> needed = new ArrayList<>();
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                needed.add(p);
            }
        }
        
        if (!needed.isEmpty()) {
            log("Requesting permissions: " + needed);
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), PERM_REQUEST);
        } else {
            log("All permissions already granted");
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        log("Permission result: " + requestCode);
        for (int i = 0; i < permissions.length; i++) {
            log(permissions[i] + " = " + (grantResults[i] == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED"));
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        bleManager.disconnect();
    }
}
