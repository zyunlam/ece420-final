package com.ece420.finalproject;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.TextView;

import java.io.IOException;
import java.lang.Math;


public class CameraActivity extends Activity implements SurfaceHolder.Callback{

    // UI Variable
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private SurfaceView surfaceView2;
    private SurfaceHolder surfaceHolder2;
    private TextView textHelper;
    // Camera Variable
    private Camera camera;
    boolean previewing = false;
    private int width = 640;
    private int height = 480;
    // Kernels
    private double[][] kernelS = new double[][] {{-1,-1,-1},{-1,9,-1},{-1,-1,-1}};
    private double[][] kernelX = new double[][] {{1,0,-1},{1,0,-1},{1,0,-1}};
    private double[][] kernelY = new double[][] {{1,1,1},{0,0,0},{-1,-1,-1}};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFormat(PixelFormat.UNKNOWN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_camera);
        super.setRequestedOrientation (ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // Modify UI Text
        textHelper = (TextView) findViewById(R.id.Helper);
        if(MainActivity.appFlag == 1) textHelper.setText("Histogram Equalized Image");
        else if(MainActivity.appFlag == 2) textHelper.setText("Sharpened Image");
        else if(MainActivity.appFlag == 3) textHelper.setText("Edge Detected Image");

        // Setup Surface View handler
        surfaceView = (SurfaceView)findViewById(R.id.ViewOrigin);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        surfaceView2 = (SurfaceView)findViewById(R.id.ViewHisteq);
        surfaceHolder2 = surfaceView2.getHolder();
    }


    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // Must have to override native method
        return;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if(!previewing) {
            camera = Camera.open();
            if (camera != null) {
                try {
                    // Modify Camera Settings
                    Camera.Parameters parameters = camera.getParameters();
                    parameters.setPreviewSize(width, height);
                    // Following lines could log possible camera resolutions, including
                    // 2592x1944;1920x1080;1440x1080;1280x720;640x480;352x288;320x240;176x144;
                    // List<Camera.Size> sizes = parameters.getSupportedPictureSizes();
                    // for(int i=0; i<sizes.size(); i++) {
                    //     int height = sizes.get(i).height;
                    //     int width = sizes.get(i).width;
                    //     Log.d("size: ", Integer.toString(width) + ";" + Integer.toString(height));
                    // }
                    camera.setParameters(parameters);
                    camera.setDisplayOrientation(90);
                    camera.setPreviewDisplay(surfaceHolder);
                    camera.setPreviewCallback(new PreviewCallback() {
                        public void onPreviewFrame(byte[] data, Camera camera)
                        {
                            // Lock canvas
                            Canvas canvas = surfaceHolder2.lockCanvas(null);
                            // Where Callback Happens, camera preview frame ready
                            onCameraFrame(canvas,data);
                            // Unlock canvas
                            surfaceHolder2.unlockCanvasAndPost(canvas);
                        }
                    });
                    camera.startPreview();
                    previewing = true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // Cleaning Up
        if (camera != null && previewing) {
            camera.stopPreview();
            camera.setPreviewCallback(null);
            camera.release();
            camera = null;
            previewing = false;
        }
    }

    // Camera Preview Frame Callback Function
    protected void onCameraFrame(Canvas canvas, byte[] data) {

        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        int retData[] = new int[width * height];

        // Apply different processing methods
        if(MainActivity.appFlag == 1){
            byte[] histeqData = histEq(data, width, height);
            retData = yuv2rgb(histeqData);
        }
        else if (MainActivity.appFlag == 2){

            int[] sharpData = conv2(data, width, height, kernelS);
            retData = merge(sharpData, sharpData);
        }
        else if (MainActivity.appFlag == 3){
            int[] xData = conv2(data, width, height, kernelX);
            int[] yData = conv2(data, width, height, kernelY);
            retData = merge(xData, yData);
        }

        // Create ARGB Image, rotate and draw
        Bitmap bmp = Bitmap.createBitmap(retData, width, height, Bitmap.Config.ARGB_8888);
        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
        canvas.drawBitmap(bmp, new Rect(0,0, height, width), new Rect(0,0, canvas.getWidth(), canvas.getHeight()),null);
    }

    // Helper function to convert YUV to RGB
    public int[] yuv2rgb(byte[] data){
        final int frameSize = width * height;
        int[] rgb = new int[frameSize];

        for (int j = 0, yp = 0; j < height; j++) {
            int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
            for (int i = 0; i < width; i++, yp++) {
                int y = (0xff & ((int) data[yp])) - 16;
                y = y<0? 0:y;

                if ((i & 1) == 0) {
                    v = (0xff & data[uvp++]) - 128;
                    u = (0xff & data[uvp++]) - 128;
                }

                int y1192 = 1192 * y;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);

                r = r<0? 0:r;
                r = r>262143? 262143:r;
                g = g<0? 0:g;
                g = g>262143? 262143:g;
                b = b<0? 0:b;
                b = b>262143? 262143:b;

                rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
            }
        }
        return rgb;
    }

    // Helper function to merge the results and convert GrayScale to RGB
    public int[] merge(int[] xdata,int[] ydata){
        int size = height * width;
        int[] mergeData = new int[size];
        for(int i=0; i<size; i++)
        {
            int p = (int)Math.sqrt((xdata[i] * xdata[i] + ydata[i] * ydata[i]) / 2);
            mergeData[i] = 0xff000000 | p<<16 | p<<8 | p;
        }
        return mergeData;
    }

    // Function for Histogram Equalization
    public byte[] histEq(byte[] data, int width, int height){
        byte[] histeqData = new byte[data.length];
        final int size = height * width;

        // Perform Histogram Equalization
        // Note that you only need to manipulate data[0:size] that corresponds to luminance
        // The rest data[size:data.length] is for colorness that we handle for you
        // *********************** START YOUR CODE HERE  **************************** //
        int[] hist = new int[257];
        // Compute histogram
        for (int i = 0; i < size; i++) {
            // Need to mask so that we actually get it in the range from 0 - 255
            int val = data[i] & 0xFF;
            hist[val]++;
        }

        // Then get our cumulative sum
        int cum = 0;
        // surely this isn't possible
        int min_val = size + 1;
        int max_val = 0;
        for (int i = 0; i < 257; i++) {
            cum += hist[i];
            hist[i] = cum;
        }
        for (int i = 0; i < hist.length; i++) {
            if (hist[i] == 0) {
                continue;
            }
            if (hist[i] < min_val) {
                min_val = hist[i];
            }
            if (hist[i] > max_val) {
                max_val = hist[i];
            }
        }

        float Q = (float) (size - 1);
        float L = 255.f;
        // then compute (val - mim) / (max - min) * L to normalize our histogram
        for (int i = 0; i < size; i++) {
            int brightness = data[i] & 0xff;
            float val = (float) hist[brightness];
            val = Math.round((val - min_val) / (max_val - min_val) * L);
            histeqData[i] = (byte) val;
        }
        // *********************** End YOUR CODE HERE  **************************** //
        // We copy the colorness part for you, do not modify if you want rgb images
        for(int i=size; i<data.length; i++){
            histeqData[i] = data[i];
        }
        return histeqData;
    }

    public int[] conv2(byte[] data, int width, int height, double kernel[][]){
        // 0 is black and 255 is white.
        int size = height * width;
        int[] convData = new int[size];

        // Perform single channel 2D Convolution
        // Note that you only need to manipulate data[0:size] that corresponds to luminance
        // The rest data[size:data.length] is ignored since we only want grayscale output
        // *********************** START YOUR CODE HERE  **************************** //
        final int pad = kernel.length / 2;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                double sm = 0;
                // Compute the sum for the specific pixel for the convolution
                for (int kx = -pad; kx < pad + 1; kx++) {
                    for (int ky = -pad; ky < pad + 1; ky++) {
                        if (x + kx < 0 || x + kx >= width) {
                            continue;
                        }
                        if (y + ky < 0 || y + ky >= height) {
                            continue;
                        }
                        int brightness_at_pos = data[(x + kx) + width * (y + ky)] & 0xff;
                        sm += (double) brightness_at_pos * kernel[(kx + pad)][ky + pad];
                    }
                }
                convData[x + width * y] = (byte) sm;
            }
        }


        // *********************** End YOUR CODE HERE  **************************** //
        return convData;
    }

}
