package com.ece420.lab6;

import android.graphics.Bitmap;

public class FacePreprocessor {
    // Target size for Fisherface classification
    private static final int TARGET_SIZE = 96;

    // Used for training and test images
    // Both are pre-cropped face bitmaps from MediaPipe
    // just resize, convert to grayscale, and do hist eq
    public byte[] processBitmapForTraining(Bitmap inputBmp) {
        // Resize to TARGET_SIZE x TARGET_SIZE
        Bitmap resized = Bitmap.createScaledBitmap(inputBmp, TARGET_SIZE, TARGET_SIZE, true);

        int[] pixels = new int[TARGET_SIZE * TARGET_SIZE];
        resized.getPixels(pixels, 0, TARGET_SIZE, 0, 0, TARGET_SIZE, TARGET_SIZE);

        // Convert to grayscale using standard luminance formula
        byte[] grayData = new byte[TARGET_SIZE * TARGET_SIZE];
        for (int i = 0; i < pixels.length; i++) {
            int r = (pixels[i] >> 16) & 0xff;
            int g = (pixels[i] >> 8) & 0xff;
            int b = pixels[i] & 0xff;
            grayData[i] = (byte) (0.299 * r + 0.587 * g + 0.114 * b);
        }

        // Run hist eq
        byte[] equalizedFace = new byte[TARGET_SIZE * TARGET_SIZE];
        histEq(grayData, equalizedFace, TARGET_SIZE, TARGET_SIZE);
        return equalizedFace;
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