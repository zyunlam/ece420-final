package com.ece420.lab6;

import android.graphics.Bitmap;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;


public class FacePreprocessor {
    // Target size for Fisherface classification
    private static final int TARGET_SIZE = 96;

    public byte[] processBitmapForTraining(Bitmap inputBmp) {
        int w = inputBmp.getWidth();
        int h = inputBmp.getHeight();
        int[] pixels = new int[w * h];

        // 1. Get all pixels from the Bitmap
        inputBmp.getPixels(pixels, 0, w, 0, 0, w, h);

        // 2. Manual conversion to Grayscale intensities
        byte[] grayData = new byte[w * h];
        for (int i = 0; i < pixels.length; i++) {
            int r = (pixels[i] >> 16) & 0xff;
            int g = (pixels[i] >> 8) & 0xff;
            int b = pixels[i] & 0xff;
            // Standard Luminance formula: 0.299R + 0.587G + 0.114B
            grayData[i] = (byte) (0.299 * r + 0.587 * g + 0.114 * b);
        }

        // 3. Reuse your existing pipeline (Scale 3x -> Crop 128x128 -> HistEq)
        // This ensures training data matches the live doorbell data exactly
        return processCapturedFrame(grayData, w, h);
    }


    public byte[] processCapturedFrame(byte[] yuvData, int width, int height) {
        // 1. Scale down and convert to Grayscale
        // We scale 640x480 -> 213x160 (3x downscale)
        int scaledWidth = width / 3;
        int scaledHeight = height / 3;
        byte[] scaledGray = scaleAndGrayscale(yuvData, width, height, 3);

        // 2. Crop the center 128x128 from the 213x160 intermediate image
        byte[] finalFace = cropToTarget(scaledGray, scaledWidth, scaledHeight);

        // 3. Equalize the 128x128 result
        byte[] equalizedFace = new byte[TARGET_SIZE * TARGET_SIZE];
        histEq(finalFace, equalizedFace, TARGET_SIZE, TARGET_SIZE);

        return equalizedFace;
    }

    private byte[] scaleAndGrayscale(byte[] data, int w, int h, int factor) {
        int newW = w / factor;
        int newH = h / factor;
        byte[] scaled = new byte[newW * newH];

        for (int y = 0; y < newH; y++) {
            for (int x = 0; x < newW; x++) {
                // Mapping back to the Y-plane (Luminance)
                int sourceIndex = (y * factor) * w + (x * factor);
                scaled[y * newW + x] = data[sourceIndex];
            }
        }
        return scaled;
    }

    private byte[] cropToTarget(byte[] scaledData, int scaledW, int scaledH) {
        byte[] crop = new byte[TARGET_SIZE * TARGET_SIZE];

        // Calculate start positions to center the 128x128 crop
        // If scaledW < 128, startX will be negative (need to handle padding)
        int startX = (scaledW - TARGET_SIZE) / 2;
        int startY = (scaledH - TARGET_SIZE) / 2;

        for (int y = 0; y < TARGET_SIZE; y++) {
            for (int x = 0; x < TARGET_SIZE; x++) {
                int currentX = startX + x;
                int currentY = startY + y;

                // Basic boundary check to prevent crashes
                if (currentX >= 0 && currentX < scaledW && currentY >= 0 && currentY < scaledH) {
                    crop[y * TARGET_SIZE + x] = scaledData[currentY * scaledW + currentX];
                } else {
                    crop[y * TARGET_SIZE + x] = 0; // Black padding if outside bounds
                }
            }
        }
        return crop;
    }

    public static void histEq(byte[] data, byte[] result, int w, int h) {
        int size = w * h;
        int[] hist = new int[256];
        for (int i = 0; i < size; i++) {
            hist[data[i] & 0xFF]++;
        }

        int cum = 0;
        int min_val = size + 1;
        int max_val = 0;
        for (int i = 0; i < 256; i++) {
            cum += hist[i];
            hist[i] = cum;
            if (hist[i] > 0 && hist[i] < min_val) min_val = hist[i];
            if (hist[i] > max_val) max_val = hist[i];
        }

        float range = (float) (max_val - min_val);
        if (range > 0) {
            for (int i = 0; i < size; i++) {
                float val = ((float) (hist[data[i] & 0xFF] - min_val) / range) * 255.0f;
                result[i] = (byte) Math.round(val);
            }
        }
    }
}

    // For debugging -- not used
//    public android.graphics.Bitmap getBitmapFromGrayscale(byte[] data, int width, int height) {
//        int[] pixels = new int[width * height];
//        for (int i = 0; i < width * height; i++) {
//            int y = data[i] & 0xFF;
//            // Pack into ARGB_8888
//            pixels[i] = 0xFF000000 | (y << 16) | (y << 8) | y;
//        }
//        return android.graphics.Bitmap.createBitmap(pixels, width, height, android.graphics.Bitmap.Config.ARGB_8888);
//    }