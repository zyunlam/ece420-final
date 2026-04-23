package com.ece420.lab6;

import static com.ece420.lab6.FacePreprocessor.histEq;

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
import android.widget.Button;
import android.widget.TextView;
import android.view.View;

import java.io.IOException;

public class CameraActivity extends Activity implements SurfaceHolder.Callback {

    // UI Variables - Only one SurfaceView now
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private TextView textHelper;

    // Action Buttons
    private Button btnTakeImage;
    private Button btnRetake;
    private Button btnClassify;

    // Camera Variables
    private Camera camera;
    boolean previewing = false;
    private int width = 640;
    private int height = 480;

    // FACE Logic & State Variables
    private boolean isFrozen = false;
    private byte[] frozenData;
    private FacePreprocessor processor = new FacePreprocessor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFormat(PixelFormat.UNKNOWN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_camera);
        super.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        textHelper = (TextView) findViewById(R.id.Helper);
        textHelper.setText("Align face in center and press Take Image");

        // Setup Single Surface View for HistEq Display
        surfaceView = (SurfaceView) findViewById(R.id.ViewOrigin);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);

        // --- BUTTON LOGIC ---
        btnTakeImage = (Button) findViewById(R.id.button_take_image);
        btnRetake = (Button) findViewById(R.id.button_retake);
        btnClassify = (Button) findViewById(R.id.button_classify);

        btnTakeImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (camera != null) {
                    isFrozen = true;
                    // This stops the hardware from pushing new frames to the SurfaceView
                    camera.stopPreview();
                    camera.setPreviewCallback(null);
                }
                btnTakeImage.setVisibility(View.GONE);
                btnRetake.setVisibility(View.VISIBLE);
                btnClassify.setVisibility(View.VISIBLE);
                textHelper.setText("Image Locked. Ready to Classify?");
            }
        });

//        btnTakeImage.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                isFrozen = true;
//                btnTakeImage.setVisibility(View.GONE);
//                btnRetake.setVisibility(View.VISIBLE);
//                btnClassify.setVisibility(View.VISIBLE);
//                textHelper.setText("Image Locked for Classification");
//            }
//        });

        btnRetake.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (camera != null) {
                    isFrozen = false;
                    // Restart the hardware stream
                    camera.startPreview();
                    // Re-attach your callback to resume the Histogram Equalized display
                    camera.setPreviewCallback(new PreviewCallback() {
                        public void onPreviewFrame(byte[] data, Camera camera) {
                            frozenData = data.clone();
                            Canvas canvas = surfaceHolder.lockCanvas(null);
                            if (canvas != null) {
                                onCameraFrame(canvas, data);
                                surfaceHolder.unlockCanvasAndPost(canvas);
                            }
                        }
                    });
                }
                btnTakeImage.setVisibility(View.VISIBLE);
                btnRetake.setVisibility(View.GONE);
                btnClassify.setVisibility(View.GONE);
                textHelper.setText("Align face and press Take Image");
            }
        });



//        btnRetake.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                isFrozen = false;
//                btnTakeImage.setVisibility(View.VISIBLE);
//                btnRetake.setVisibility(View.GONE);
//                btnClassify.setVisibility(View.GONE);
//                textHelper.setText("Align face and press Take Image");
//            }
//        });

        btnClassify.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (frozenData != null) {
                    textHelper.setText("Identifying... Please wait.");
                    // The Fisherface classification logic will go here
                }
            }
        });
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (!previewing) {
            int frontCameraId = -1;
            int numberOfCameras = Camera.getNumberOfCameras();

            // Loop through available cameras to find the front-facing one
            for (int i = 0; i < numberOfCameras; i++) {
                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    frontCameraId = i;
                    break;
                }
            }

            // Open the front camera if found, otherwise default to back
            if (frontCameraId != -1) {
                camera = Camera.open(frontCameraId);
            } else {
                camera = Camera.open();
            }

            if (camera != null) {
                try {
                    // IMPORTANT: We do NOT setPreviewDisplay to the actual surfaceHolder.
                    // We use a dummy SurfaceTexture so the hardware doesn't draw the color feed.
                    camera.setPreviewTexture(new android.graphics.SurfaceTexture(10));

                    Camera.Parameters parameters = camera.getParameters();
                    parameters.setPreviewSize(width, height);
                    camera.setParameters(parameters);
                    camera.setDisplayOrientation(90);

                    camera.setPreviewCallback(new Camera.PreviewCallback() {
                        public void onPreviewFrame(byte[] data, Camera camera) {
                            if (!isFrozen) {
                                frozenData = data.clone();
                                // We manually lock and draw to the visible surfaceHolder
                                Canvas canvas = surfaceHolder.lockCanvas(null);
                                if (canvas != null) {
                                    onCameraFrame(canvas, data);
                                    surfaceHolder.unlockCanvasAndPost(canvas);
                                }
                            }
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

//    public void surfaceCreated(SurfaceHolder holder) {
//        if (!previewing) {
//            camera = Camera.open();
//            if (camera != null) {
//                try {
//                    Camera.Parameters parameters = camera.getParameters();
//                    parameters.setPreviewSize(width, height);
//                    camera.setParameters(parameters);
//                    camera.setDisplayOrientation(90);
//                    camera.setPreviewDisplay(surfaceHolder);
//                    camera.setPreviewCallback(new PreviewCallback() {
//                        public void onPreviewFrame(byte[] data, Camera camera) {
//                            if (!isFrozen) {
//                                frozenData = data.clone();
//                                Canvas canvas = surfaceHolder.lockCanvas(null);
//                                if (canvas != null) {
//                                    // Always display the Equalized version [cite: 238, 242, 247]
//                                    onCameraFrame(canvas, data);
//                                    surfaceHolder.unlockCanvasAndPost(canvas);
//                                }
//                            }
//                        }
//                    });
//                    camera.startPreview();
//                    previewing = true;
//                } catch (IOException e) {
//                    throw new RuntimeException(e);
//                }
//            }
//        }
//    }


    // FOR GRAYSCALE AND HISTEQ'D CAMERA PREVIEW
    protected void onCameraFrame(Canvas canvas, byte[] data) {
        Matrix matrix = new Matrix();

        // Front camera orientation and mirroring for a "mirror" effect
        matrix.postRotate(270);
        matrix.postScale(-1, 1);

        // 1. SKIP HISTEQ - Pass the raw 'data' directly to the converter
        // We are no longer using 'processedData' or 'histEq' here.
        int[] retData = yuv2rgb(data);

        // 2. DRAW TO CANVAS
        // Create the bitmap from the raw YUV-to-RGB conversion
        Bitmap bmp = Bitmap.createBitmap(retData, width, height, Bitmap.Config.ARGB_8888);
        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);

        // Ensure the rect matches your portrait orientation
        canvas.drawBitmap(bmp, new Rect(0, 0, bmp.getWidth(), bmp.getHeight()),
                new Rect(0, 0, canvas.getWidth(), canvas.getHeight()), null);
    }

//    protected void onCameraFrame(Canvas canvas, byte[] data) {
//        Matrix matrix = new Matrix();
//        matrix.postRotate(90);
//
//        // Apply manual Histogram Equalization immediately [cite: 142, 247, 282]
//        byte[] histeqData = FacePreprocessor.histEq(data, width, height);
//
//        // Convert YUV to RGB for screen display
//        int[] retData = yuv2rgb(histeqData);
//
//        Bitmap bmp = Bitmap.createBitmap(retData, width, height, Bitmap.Config.ARGB_8888);
//        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
//        canvas.drawBitmap(bmp, new Rect(0, 0, height, width), new Rect(0, 0, canvas.getWidth(), canvas.getHeight()), null);
//    }




//     -- FOR COLOR FEED --
    public int[] yuv2rgb(byte[] data) {
        final int frameSize = width * height;
        int[] rgb = new int[frameSize];
        for (int j = 0, yp = 0; j < height; j++) {
            int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
            for (int i = 0; i < width; i++, yp++) {
                int y = (0xff & ((int) data[yp])) - 16;
                y = y < 0 ? 0 : y;
                if ((i & 1) == 0) {
                    v = (0xff & data[uvp++]) - 128;
                    u = (0xff & data[uvp++]) - 128;
                }
                int y1192 = 1192 * y;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);
                r = r < 0 ? 0 : r; r = r > 262143 ? 262143 : r;
                g = g < 0 ? 0 : g; g = g > 262143 ? 262143 : g;
                b = b < 0 ? 0 : b; b = b > 262143 ? 262143 : b;
                rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
            }
        }
        return rgb;
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        if (camera != null && previewing) {
            camera.stopPreview();
            camera.setPreviewCallback(null);
            camera.release();
            camera = null;
            previewing = false;
        }
    }
}









//package com.ece420.lab6;
//
//import static com.ece420.lab6.FacePreprocessor.histEq;
//
//import android.app.Activity;
//import android.content.pm.ActivityInfo;
//import android.hardware.Camera;
//import android.hardware.Camera.PreviewCallback;
//import android.graphics.Bitmap;
//import android.graphics.Canvas;
//import android.graphics.Matrix;
//import android.graphics.PixelFormat;
//import android.graphics.Rect;
//import android.os.Bundle;
//import android.view.SurfaceHolder;
//import android.view.SurfaceView;
//import android.view.WindowManager;
//import android.widget.Button;
//import android.widget.TextView;
//import android.view.View;
//
//import org.opencv.core.CvType;
//import org.opencv.core.Mat;
//import org.opencv.core.Size;
//import org.opencv.imgproc.Imgproc;
//
//import java.io.IOException;
//
//public class CameraActivity extends Activity implements SurfaceHolder.Callback {
//
//    // UI Variables
//    private SurfaceView surfaceView;
//    private SurfaceHolder surfaceHolder;
//    private SurfaceView surfaceView2;
//    private SurfaceHolder surfaceHolder2;
//    private TextView textHelper;
//    private Button captureButton;
//
//    // Camera Variables
//    private Camera camera;
//    boolean previewing = false;
//    private int width = 640;
//    private int height = 480;
//
//    // FACE Logic Variables
//    private boolean isProcessing = false;
//    private FacePreprocessor processor = new FacePreprocessor();
//    // Assuming you have a class to handle the PCA/LDA math
//    // private FisherClassifier classifier = new FisherClassifier();
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        getWindow().setFormat(PixelFormat.UNKNOWN);
//        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//        setContentView(R.layout.activity_camera);
//        super.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
//
//        textHelper = (TextView) findViewById(R.id.Helper);
//        textHelper.setText("Align face in center and press Capture");
//
//        // Setup Surface Views
//        surfaceView = (SurfaceView) findViewById(R.id.ViewOrigin);
//        surfaceHolder = surfaceView.getHolder();
//        surfaceHolder.addCallback(this);
//
//        surfaceView2 = (SurfaceView) findViewById(R.id.ViewHisteq);
//        surfaceHolder2 = surfaceView2.getHolder();
//
//        // New Capture Button logic for the Doorbell trigger
//        captureButton = (Button) findViewById(R.id.captureButton);
//        captureButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                isProcessing = true; // Trigger recognition on next frame
//            }
//        });
//    }
//
//    @Override
//    public void surfaceCreated(SurfaceHolder holder) {
//        if (!previewing) {
//            camera = Camera.open();
//            if (camera != null) {
//                try {
//                    Camera.Parameters parameters = camera.getParameters();
//                    parameters.setPreviewSize(width, height);
//                    camera.setParameters(parameters);
//                    camera.setDisplayOrientation(90);
//                    camera.setPreviewDisplay(surfaceHolder);
//                    camera.setPreviewCallback(new PreviewCallback() {
//                        public void onPreviewFrame(byte[] data, Camera camera) {
//                            Canvas canvas = surfaceHolder2.lockCanvas(null);
//                            onCameraFrame(canvas, data);
//                            surfaceHolder2.unlockCanvasAndPost(canvas);
//                        }
//                    });
//                    camera.startPreview();
//                    previewing = true;
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//        }
//    }
//
//    protected void onCameraFrame(Canvas canvas, byte[] data) {
//        Matrix matrix = new Matrix();
//        matrix.postRotate(90);
//
//        // 1. Manual Histogram Equalization (Ensures training/testing consistency) [cite: 250]
//        byte[] histeqData = histEq(data, width, height);
//
//        // 2. Identification Logic [cite: 172, 210]
//        if (isProcessing) {
//            // In a real implementation, you would:
//            // a) Crop the center 128x128 from histeqData [cite: 62]
//            // b) Project to Fisher-space [cite: 143]
//            // c) Compare Euclidean distance [cite: 116]
//
//            // Placeholder: for now we just show the processed image
//            textHelper.setText("Identifying...");
//            isProcessing = false;
//        }
//
//        // Convert YUV to RGB for display
//        int[] retData = yuv2rgb(histeqData);
//
//        Bitmap bmp = Bitmap.createBitmap(retData, width, height, Bitmap.Config.ARGB_8888);
//        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
//        canvas.drawBitmap(bmp, new Rect(0, 0, height, width), new Rect(0, 0, canvas.getWidth(), canvas.getHeight()), null);
//    }
//
//    public int[] yuv2rgb(byte[] data) {
//        final int frameSize = width * height;
//        int[] rgb = new int[frameSize];
//        for (int j = 0, yp = 0; j < height; j++) {
//            int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
//            for (int i = 0; i < width; i++, yp++) {
//                int y = (0xff & ((int) data[yp])) - 16;
//                y = y < 0 ? 0 : y;
//                if ((i & 1) == 0) {
//                    v = (0xff & data[uvp++]) - 128;
//                    u = (0xff & data[uvp++]) - 128;
//                }
//                int y1192 = 1192 * y;
//                int r = (y1192 + 1634 * v);
//                int g = (y1192 - 833 * v - 400 * u);
//                int b = (y1192 + 2066 * u);
//                r = r < 0 ? 0 : r; r = r > 262143 ? 262143 : r;
//                g = g < 0 ? 0 : g; g = g > 262143 ? 262143 : g;
//                b = b < 0 ? 0 : b; b = b > 262143 ? 262143 : b;
//                rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
//            }
//        }
//        return rgb;
//    }
//
//    @Override
//    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
//
//    @Override
//    public void surfaceDestroyed(SurfaceHolder holder) {
//        if (camera != null && previewing) {
//            camera.stopPreview();
//            camera.setPreviewCallback(null);
//            camera.release();
//            camera = null;
//            previewing = false;
//        }
//    }
//}