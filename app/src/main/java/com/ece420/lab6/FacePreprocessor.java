package com.ece420.lab6;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

public class FacePreprocessor {

    private static final int TARGET_WIDTH = 128;
    private static final int TARGET_HEIGHT = 128;

    /**
     * Prepares saved images for PCA using your custom Histogram Equalization.
     */
    public static byte[] histEq(byte[] data, int width, int height) {
        byte[] histeqData = new byte[data.length];
        final int size = height * width;
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

        float L = 255.0f;
        for (int i = 0; i < size; i++) {
            int brightness = data[i] & 0xFF;
            float val = (float) hist[brightness];
            val = Math.round((val - min_val) / (max_val - min_val) * L);
            histeqData[i] = (byte) val;
        }

        for (int i = size; i < data.length; i++) {
            histeqData[i] = data[i];
        }
        return histeqData;
    }

    public Mat processImageForPCA(String filePath) {
        // 1. Load as Grayscale
        Mat img = Imgcodecs.imread(filePath, Imgcodecs.IMREAD_GRAYSCALE);
        if (img.empty()) return null;

        // 2. Resize to 128x128 per proposal [cite: 69, 70]
        Mat resizedImg = new Mat();
        Imgproc.resize(img, resizedImg, new Size(TARGET_WIDTH, TARGET_HEIGHT));

        // 3. Convert Mat to byte array for your manual function
        int size = (int) (resizedImg.total());
        byte[] buffer = new byte[size];
        resizedImg.get(0, 0, buffer);

        // 4. Apply your manual Histogram Equalization
        byte[] equalizedBuffer = histEq(buffer, TARGET_WIDTH, TARGET_HEIGHT);

        // 5. Put data back into a Mat and convert to 64-bit float for PCA
        Mat processedMat = new Mat(TARGET_HEIGHT, TARGET_WIDTH, CvType.CV_8UC1);
        processedMat.put(0, 0, equalizedBuffer);

        Mat finalMat = new Mat();
        processedMat.convertTo(finalMat, CvType.CV_64F); // Required for Eigen math

        return finalMat;
    }
}