package com.ece420.lab6;

import android.opencv.core.CvType;
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
        byte[] equalizedBuffer = manualHistEq(buffer, TARGET_WIDTH, TARGET_HEIGHT);

        // 5. Put data back into a Mat and convert to 64-bit float for PCA
        Mat processedMat = new Mat(TARGET_HEIGHT, TARGET_WIDTH, CvType.CV_8UC1);
        processedMat.put(0, 0, equalizedBuffer);

        Mat finalMat = new Mat();
        processedMat.convertTo(finalMat, CvType.CV_64F); // Required for Eigen math

        return finalMat;
    }