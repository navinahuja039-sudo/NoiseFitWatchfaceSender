package com.noisefit.watchface.util;

import android.graphics.Bitmap;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class WatchFaceCreator {
    
    public static String analyzeWatchface(String filePath) {
        File f = new File(filePath);
        if (!f.exists()) return "File not found";
        
        try {
            byte[] data = new byte[(int) f.length()];
            new FileInputStream(f).read(data);
            
            if (data.length < 12) return "File too small";
            
            StringBuilder sb = new StringBuilder();
            sb.append("=== Watchface Analysis ===\n");
            sb.append("Size: ").append(data.length).append(" bytes\n");
            sb.append("Magic: 0x").append(String.format("%02X", data[0]));
            
            // Try to get resolution
            int w = (data[8] & 0xFF) | ((data[9] & 0xFF) << 8);
            int h = (data[10] & 0xFF) | ((data[11] & 0xFF) << 8);
            sb.append("\nResolution: ").append(w).append("x").append(h);
            
            return sb.toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
    
    public static void createWatchface(Bitmap bitmap, String outputPath, int width, int height) throws IOException {
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, width, height, true);
        
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        
        // Header
        output.write(0x1D);
        output.write(0x00);
        output.write(0xFF);
        output.write(0xFF);
        output.write(0xAC);
        output.write(0xFF);
        output.write(0x01);
        output.write(0x00);
        
        // Resolution
        output.write(width & 0xFF);
        output.write((width >> 8) & 0xFF);
        output.write(height & 0xFF);
        output.write((height >> 8) & 0xFF);
        
        // Bit depth (16 = RGB565)
        output.write(0x10);
        output.write(0x00);
        
        // Compression (0 = none)
        output.write(0x00);
        
        // Image size placeholder
        int sizePos = output.size();
        output.write(0);
        output.write(0);
        output.write(0);
        output.write(0);
        
        // Pixel data (RGB565)
        int[] pixels = new int[width * height];
        resized.getPixels(pixels, 0, width, 0, 0, width, height);
        
        for (int pixel : pixels) {
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;
            
            int r5 = (r >> 3) & 0x1F;
            int g6 = (g >> 2) & 0x3F;
            int b5 = (b >> 3) & 0x1F;
            
            int rgb565 = (r5 << 11) | (g6 << 5) | b5;
            output.write(rgb565 & 0xFF);
            output.write((rgb565 >> 8) & 0xFF);
        }
        
        // Update size
        byte[] data = output.toByteArray();
        int imageSize = data.length - sizePos - 4;
        data[sizePos] = (byte)(imageSize & 0xFF);
        data[sizePos + 1] = (byte)((imageSize >> 8) & 0xFF);
        data[sizePos + 2] = (byte)((imageSize >> 16) & 0xFF);
        data[sizePos + 3] = (byte)((imageSize >> 24) & 0xFF);
        
        FileOutputStream fos = new FileOutputStream(outputPath);
        fos.write(data);
        fos.close();
    }
    
    public static Bitmap extractPreview(String filePath) {
        try {
            File f = new File(filePath);
            if (!f.exists()) return null;
            
            byte[] data = new byte[(int) f.length()];
            new FileInputStream(f).read(data);
            
            if (data.length < 12) return null;
            
            int w = (data[8] & 0xFF) | ((data[9] & 0xFF) << 8);
            int h = (data[10] & 0xFF) | ((data[11] & 0xFF) << 8);
            
            if (w <= 0 || w > 500 || h <= 0 || h > 500) {
                w = 140; h = 140;
            }
            
            return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        } catch (Exception e) {
            return null;
        }
    }
}
