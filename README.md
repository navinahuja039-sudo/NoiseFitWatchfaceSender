# NoiseFit Watchface Sender

An Android app to send custom watchfaces to NoiseFit watches via Bluetooth BLE.

## Project Structure

```
NoiseFitWatchfaceSender/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/noisefit/watchface/
│       │   ├── ble/
│       │   │   └── NoiseFitBleManager.kt    # BLE connection & transfer
│       │   └── ui/
│       │       └── MainActivity.kt          # Main UI
│       └── res/
│           └── layout/
│               └── activity_main.xml
└── README.md
```

## How It Works

Based on reverse-engineering the NoiseFit app:

1. **BLE Protocol**: Uses CRrepa BLE SDK
2. **Service UUID**: `0000FEE7-0000-1000-8000-00805F9B34FB`
3. **Characteristics**: 
   - Write: `0000FEE8-0000-1000-8000-00805F9B34FB`
   - Notify: `0000FEE9-0000-1000-8000-00805F9B34FB`

## Building

1. Open in Android Studio
2. Connect your Android phone
3. Build and run

## Usage

1. Grant Bluetooth permissions
2. Tap "Scan for Watch" to find your NoiseFit watch
3. Select your watch from the list
4. Tap "Send Watchface" to transfer

## Watchface File

Place your watchface file at:
- `/storage/emulated/0/Download/3743` (or modify the code to use a file picker)

## Notes

- The BLE transfer protocol may vary slightly - you may need to adjust chunk size
- Some watches require pairing first through the official app
- The watchface format must be compatible with your specific watch model
