package com.ece420.lab6;

import static com.ece420.lab6.FacePreprocessor.histEq;
import java.util.List;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.View;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorResult;
import com.google.mediapipe.tasks.components.containers.Detection;


public class CameraActivity extends Activity implements SurfaceHolder.Callback {
    private byte[] lastPreviewData;

    // UI Variables
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
    private FaceDetector detector;

    private ImageView overlayView;

    private FisherClassifier classifier;
    private HashMap<Integer, String> identification;

    private FaceDetectorResult detectImage(Bitmap image) {
        MPImage mpImage = new BitmapImageBuilder(image).build();
        FaceDetectorResult result = detector.detect(mpImage);
        return result;
    }

    /**
     * FIX #1 + #2: Unified classification path.
     * Previously used (r+g+b)/3 grayscale averaging and skipped the full
     * FacePreprocessor pipeline. Now routes through processBitmapForTraining()
     * so the grayscale formula, scaling, cropping, and histogram equalization
     * are identical to training. Also fixed the byte sign extension bug (& 0xFF).
     */
    private void classifyFace(Bitmap faceBmp) {
        int pixelCount = 96 * 96;

        // Use the same preprocessing pipeline as training data
        byte[] processed = processor.processBitmapForTraining(faceBmp);

        double[] classificationInput = new double[pixelCount];
        for (int i = 0; i < pixelCount; i++) {
            // FIX #2: Must mask with 0xFF — Java bytes are signed (-128..127),
            // so any pixel > 127 would go negative without this.
            classificationInput[i] = (double) (processed[i] & 0xFF);
        }

        ClassifierResult result = classifier.ClassifyFace(classificationInput, 3000);
        String id_result = identification.get(result.getIndex());
        Log.i("CameraActivity", "Identified " + id_result + " dist=" + result.getDistance());
        runOnUiThread(() -> {
            StringBuilder sb = new StringBuilder("Top Matches:\n");
            List<ClassifierResult> top = result.getTopMatches();
            if (top != null) {
                for (int i = 0; i < top.size(); i++) {
                    String name = identification.get(top.get(i).getIndex());
                    sb.append((i + 1)).append(". ").append(name)
                            .append(" (Dist: ").append((int) top.get(i).getDistance()).append(")\n");
                }
            }
            textHelper.setText(sb.toString());
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        classifier = new FisherClassifier();
        identification = new HashMap<>();
        BaseOptions.Builder baseOptionsBuilder = BaseOptions.builder();
        baseOptionsBuilder.setDelegate(Delegate.CPU);

        String modelName = "face_detection_short_range.tflite";
        baseOptionsBuilder.setModelAssetPath(modelName);

        try {
            FaceDetector.FaceDetectorOptions.Builder optionsBuilder = FaceDetector.FaceDetectorOptions.builder().setBaseOptions(baseOptionsBuilder.build()).setMinDetectionConfidence(0.5f).setRunningMode(RunningMode.IMAGE);
            FaceDetector.FaceDetectorOptions options = optionsBuilder.build();
            detector = FaceDetector.createFromOptions(getApplicationContext(), options);
        } catch (IllegalStateException e) {
            Log.e("CameraActivity", "Illegal state exception!" + e.getMessage());
        } catch (RuntimeException re) {
            Log.e("CameraActivity", "Runtime Exception!" + re.getMessage());
        }
        Log.i("CameraActivity", "Loaded face detector");

        // Put training in a background thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    startTraining();
                    runOnUiThread(() -> textHelper.setText("Training complete!"));
                } catch (IOException e) {
                    Log.e("CameraActivity", "Training failed", e);
                }
            }
        }).start();

        getWindow().setFormat(PixelFormat.UNKNOWN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_camera);
        super.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        textHelper = (TextView) findViewById(R.id.Helper);
        textHelper.setText("Align face in center and press Take Image");

        surfaceView = (SurfaceView) findViewById(R.id.ViewOrigin);
        overlayView = (ImageView) findViewById(R.id.overlay_view);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);

        btnTakeImage = (Button) findViewById(R.id.button_take_image);
        btnRetake = (Button) findViewById(R.id.button_retake);
        btnClassify = (Button) findViewById(R.id.button_classify);

        btnTakeImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (camera != null && lastPreviewData != null) {
                    isFrozen = true;
                    frozenData = lastPreviewData.clone();

                    Bitmap cameraBitmap = getBitmapFromVUY(frozenData, width, height);
                    FaceDetectorResult result = detectImage(cameraBitmap);

                    Bitmap overlayBitmap = Bitmap.createBitmap(overlayView.getWidth(), overlayView.getHeight(), Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(overlayBitmap);

                    android.graphics.Paint paint = new android.graphics.Paint();
                    paint.setColor(android.graphics.Color.GREEN);
                    paint.setStyle(android.graphics.Paint.Style.STROKE);
                    paint.setStrokeWidth(10.0f);

                    if (result.detections() != null) {
                        float scaleX = (float) overlayView.getWidth() / cameraBitmap.getWidth();
                        float scaleY = (float) overlayView.getHeight() / cameraBitmap.getHeight();

                        for (Detection detection : result.detections()) {
                            RectF box = detection.boundingBox();
                            canvas.drawRect(
                                    box.left * scaleX,
                                    box.top * scaleY,
                                    box.right * scaleX,
                                    box.bottom * scaleY,
                                    paint
                            );
                        }
                    }

                    overlayView.setImageBitmap(overlayBitmap);

                    if (result.detections() != null && !result.detections().isEmpty()) {
                        RectF box = result.detections().get(0).boundingBox();

                        int left = Math.max(0, (int) box.left);
                        int top = Math.max(0, (int) box.top);
                        int widthRect = Math.min((int) box.width(), cameraBitmap.getWidth() - left);
                        int heightRect = Math.min((int) box.height(), cameraBitmap.getHeight() - top);

                        Bitmap faceCrop = Bitmap.createBitmap(cameraBitmap, left, top, widthRect, heightRect);
                        Bitmap resizedFace = Bitmap.createScaledBitmap(faceCrop, 96, 96, true);

                        // FIX #1: classifyFace now uses the unified preprocessing pipeline
                        classifyFace(resizedFace);
                    }
                    camera.stopPreview();
                    camera.setPreviewCallback(null);
                }
                btnTakeImage.setVisibility(View.GONE);
                btnRetake.setVisibility(View.VISIBLE);
                btnClassify.setVisibility(View.VISIBLE);
            }
        });

        btnRetake.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (camera != null) {
                    isFrozen = false;
                    camera.startPreview();
                    camera.setPreviewCallback(new PreviewCallback() {
                        public void onPreviewFrame(byte[] data, Camera camera) {
                            if (!isFrozen) {
                                lastPreviewData = data;
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

        btnClassify.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (frozenData != null) {
                    textHelper.setText("Processing Face for Identification...");

                    byte[] processedFace = processor.processCapturedFrame(frozenData, width, height);

                    double[] doubleFace = new double[processedFace.length];
                    for (int i = 0; i < doubleFace.length; i++) {
                        // FIX #2: byte sign extension — must use & 0xFF
                        doubleFace[i] = (double) (processedFace[i] & 0xFF);
                    }

                    ClassifierResult result = classifier.ClassifyFace(doubleFace, 3000);
                    String id_result = identification.get(result.getIndex());
                    List<ClassifierResult> top = result.getTopMatches();
                    StringBuilder sb = new StringBuilder("Top Matches:\n");
                    if (top != null) {
                        for (int i = 0; i < top.size(); i++) {
                            String name = identification.get(top.get(i).getIndex());
                            sb.append((i + 1)).append(". ").append(name)
                                    .append(" (Dist: ").append((int) top.get(i).getDistance()).append(")\n");
                        }
                    }
                    textHelper.setText(sb.toString());
                }
            }
        });
    }

    private Bitmap getBitmapFromVUY(byte[] data, int width, int height) {
        int[] argb = yuv2rgb(data);
        Bitmap bitmap = Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888);
        Matrix matrix = new Matrix();
        matrix.postRotate(270);
        matrix.postScale(-1, 1);
        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (!previewing) {
            int frontCameraId = -1;
            int numberOfCameras = Camera.getNumberOfCameras();

            for (int i = 0; i < numberOfCameras; i++) {
                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    frontCameraId = i;
                    break;
                }
            }

            if (frontCameraId != -1) {
                camera = Camera.open(frontCameraId);
            } else {
                camera = Camera.open();
            }

            if (camera != null) {
                try {
                    camera.setPreviewDisplay(surfaceHolder);

                    Camera.Parameters parameters = camera.getParameters();
                    parameters.setPreviewSize(width, height);
                    camera.setParameters(parameters);
                    camera.setDisplayOrientation(90);

                    camera.setPreviewCallback(new Camera.PreviewCallback() {
                        public void onPreviewFrame(byte[] data, Camera camera) {
                            if (!isFrozen) {
                                lastPreviewData = data;
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

    protected void onCameraFrame(Canvas canvas, byte[] data) {
        Matrix matrix = new Matrix();
        matrix.postRotate(270);
        matrix.postScale(-1, 1);

        int[] retData = yuv2rgb(data);
        Bitmap bmp = Bitmap.createBitmap(retData, width, height, Bitmap.Config.ARGB_8888);
        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
        canvas.drawBitmap(bmp, new Rect(0, 0, bmp.getWidth(), bmp.getHeight()),
                new Rect(0, 0, canvas.getWidth(), canvas.getHeight()), null);
    }

    public int[] yuv2rgb(byte[] data) {
        final int frameSize = width * height;
        int[] rgb = new int[frameSize];
        for (int j = 0, yp = 0; j < height; j++) {
            int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
            for (int i = 0; i < width; i++, yp++) {
                int y = (0xff & ((int) data[yp])) - 16;
                y = y < 0 ? 0 : y;
                if ((i & 1) == 0) {
                    v = (0xff & data[uvp++]) - 96;
                    u = (0xff & data[uvp++]) - 96;
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

    public void startTraining() throws IOException {
        System.out.println("startTraining called\n");

        int pixelCount = 96 * 96;

        ArrayList<double[]> imageList = new ArrayList<>();
        ArrayList<Integer> labels = new ArrayList<Integer>();
        int imageIndex = 0;

        int label_idx = 0;
        for (String currentMember : getAssets().list("faces")) {
            identification.put(label_idx, currentMember);
            String[] imageFiles = getAssets().list("faces/" + currentMember);
            if (imageFiles != null) {
                for (String imgFile : imageFiles) {
                    Bitmap b = BitmapFactory.decodeStream(getAssets().open("faces/" + currentMember + "/" + imgFile));
                    byte[] processed = processor.processBitmapForTraining(b);

                    double sum = 0;
                    double[] image_array = new double[pixelCount];
                    for (int p = 0; p < pixelCount; p++) {
                        // & 0xFF to handle signed byte correctly
                        image_array[p] = (double) (processed[p] & 0xFF);
                        sum += image_array[p];
                    }
                    imageList.add(image_array);
                    Log.d("CameraActivity", "Sum of image: " + sum);

                    labels.add(label_idx);
                    imageIndex++;
                    b.recycle();
                }
                label_idx++;
            } else {
                Log.d("CameraActivity", "can't load image!!!");
            }
        }
        double[][] imageArray = new double[imageList.size()][pixelCount];
        for (int i = 0; i < imageList.size(); i++) {
            imageArray[i] = imageList.get(i);
        }

        int[] label_list = new int[labels.size()];
        for (int i = 0; i < labels.size(); i++) {
            label_list[i] = labels.get(i);
        }

        classifier.ComputeTrainingWeights(imageArray, label_list, 96, 96);
    }
}