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
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
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

    // ── Constants ────────────────────────────────────────────────────────
    private static final String CORRECT_PIN   = "12345"; // Change to your admin PIN
    private static final int    ENROLL_TARGET = 15;      // Images required per person
    private static final int    PIXEL_COUNT   = 96 * 96; // Matches FacePreprocessor.TARGET_SIZE

    // ── Camera state ─────────────────────────────────────────────────────
    private byte[]        lastPreviewData;
    private SurfaceView   surfaceView;
    private SurfaceHolder surfaceHolder;
    private Camera        camera;
    boolean               previewing = false;
    private int           width      = 640;
    private int           height     = 480;
    private boolean       isFrozen   = false;
    private byte[]        frozenData;

    // ── Core objects ─────────────────────────────────────────────────────
    private FacePreprocessor         processor      = new FacePreprocessor();
    private FaceDetector             detector;
    private FisherClassifier         classifier;
    private HashMap<Integer, String> identification;

    // ── UI — shared ───────────────────────────────────────────────────────
    private TextView  textHelper;
    private TextView  textModeLabel;
    private ImageView overlayView;

    // ── UI — recognition buttons ──────────────────────────────────────────
    private Button btnTakeImage;
    private Button btnRetake;

    // ── UI — PIN overlay ──────────────────────────────────────────────────
    private View          panelPin;
    private TextView      textPinDots;
    private TextView      textPinError;
    private StringBuilder pinBuffer = new StringBuilder();

    // ── UI — enrollment overlay ───────────────────────────────────────────
    private View     panelEnrollment;
    private EditText enrollNameInput;
    private TextView textEnrollCount;
    private View     enrollProgressBar;
    private Button   btnEnrollCapture;

    // ── Enrollment state ──────────────────────────────────────────────────
    private int    enrollCount = 0;
    private String enrollName  = "";
    private int    nextLabel   = 0;

    // ── Last detected face crop (used by enrollment to save the right image) ──
    // Populated every time MediaPipe successfully detects a face so that
    // the enrollment capture button always saves the most-recently-seen crop.
    private Bitmap lastFaceCrop = null;

    // ═════════════════════════════════════════════════════════════════════
    // MEDIAPIPE HELPERS  (teammates' version, unchanged)
    // ═════════════════════════════════════════════════════════════════════

    private FaceDetectorResult detectImage(Bitmap image) {
        MPImage mpImage = new BitmapImageBuilder(image).build();
        return detector.detect(mpImage);
    }

    /**
     * FIX #1 + #2: Unified classification path (teammates' version).
     * Receives a MediaPipe-cropped, 96×96-scaled face bitmap.
     * Routes through processBitmapForTraining() for identical preprocessing
     * to training. Shows top-10 matches in the helper TextView.
     */
    private void classifyFace(Bitmap faceBmp) {
        byte[]   processed = processor.processBitmapForTraining(faceBmp);
        double[] classificationInput = new double[PIXEL_COUNT];
        for (int i = 0; i < PIXEL_COUNT; i++) {
            classificationInput[i] = (double) (processed[i] & 0xFF); // FIX #2: & 0xFF
        }

        ClassifierResult result    = classifier.ClassifyFace(classificationInput, 100);
        String           id_result = identification.get(result.getIndex());
        Log.i("CameraActivity", "Identified " + id_result + " dist=" + result.getDistance());

        runOnUiThread(() -> {
            StringBuilder sb = new StringBuilder("Top Matches:\n");
            List<ClassifierResult> top = result.getTopMatches();
            List<ClassifierResult> filtered = new ArrayList<>();
            boolean added = false;
            if (top != null) {
                for (ClassifierResult r : top) {
                    if (r.getDistance() <= 100 & !added) {
                        filtered.add(r);
                        added = true;
                    }
                }
            }

            if (filtered.isEmpty()) {
                textHelper.setText("Unknown");
            } else {
                StringBuilder bs = new StringBuilder("Top Matches:\n");
                for (int i = 0; i < filtered.size(); i++) {
                    String name = identification.get(filtered.get(i).getIndex());
                    bs.append((i + 1)).append(". ").append(name)
                            .append(" (Dist: ").append((int) filtered.get(i).getDistance()).append(")\n");
                }
                textHelper.setText(bs.toString());
            }
        });
    }

    // ═════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        classifier     = new FisherClassifier();
        identification = new HashMap<>();

        // ── Initialise MediaPipe face detector (teammates' version) ───────
        BaseOptions.Builder baseOptionsBuilder = BaseOptions.builder();
        baseOptionsBuilder.setDelegate(Delegate.CPU);
        baseOptionsBuilder.setModelAssetPath("face_detection_short_range.tflite");
        try {
            FaceDetector.FaceDetectorOptions options =
                    FaceDetector.FaceDetectorOptions.builder()
                            .setBaseOptions(baseOptionsBuilder.build())
                            .setMinDetectionConfidence(0.5f)
                            .setRunningMode(RunningMode.IMAGE)
                            .build();
            detector = FaceDetector.createFromOptions(getApplicationContext(), options);
        } catch (IllegalStateException e) {
            Log.e("CameraActivity", "Illegal state exception! " + e.getMessage());
        } catch (RuntimeException re) {
            Log.e("CameraActivity", "Runtime Exception! " + re.getMessage());
        }
        Log.i("CameraActivity", "Loaded face detector");

        // ── Background training (assets only on startup) ──────────────────
        new Thread(() -> {
            try {
                startTraining(false);
                runOnUiThread(() -> textHelper.setText("Training complete!"));
            } catch (IOException e) {
                Log.e("CameraActivity", "Training failed", e);
            }
        }).start();

        getWindow().setFormat(PixelFormat.UNKNOWN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_camera);
        super.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // ── Bind views ────────────────────────────────────────────────────
        textHelper    = findViewById(R.id.Helper);
        textModeLabel = findViewById(R.id.text_mode_label);
        surfaceView   = findViewById(R.id.ViewOrigin);
        overlayView   = findViewById(R.id.overlay_view);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);

        btnTakeImage = findViewById(R.id.button_take_image);
        btnRetake    = findViewById(R.id.button_retake);

        panelPin      = findViewById(R.id.panel_pin);
        textPinDots   = findViewById(R.id.text_pin_dots);
        textPinError  = findViewById(R.id.text_pin_error);

        panelEnrollment   = findViewById(R.id.panel_enrollment);
        enrollNameInput   = findViewById(R.id.enroll_name_input);
        textEnrollCount   = findViewById(R.id.text_enroll_count);
        enrollProgressBar = findViewById(R.id.enroll_progress_bar);
        btnEnrollCapture  = findViewById(R.id.button_enroll_capture);

        // ── Route to correct startup mode ─────────────────────────────────
        if (MainActivity.appFlag == MainActivity.MODE_ENROLLMENT) {
            showPinOverlay();
        } else {
            startRecognitionMode();
        }

        setupRecognitionButtons();
        setupPinButtons();
        setupEnrollmentButtons();
    }

    // ═════════════════════════════════════════════════════════════════════
    // MODE SETUP
    // ═════════════════════════════════════════════════════════════════════

    private void startRecognitionMode() {
        textHelper.setText("Align face in center and press Take Image");
        textModeLabel.setText("FACE RECOGNITION MODE");
        panelPin.setVisibility(View.GONE);
        panelEnrollment.setVisibility(View.GONE);
        btnTakeImage.setVisibility(View.VISIBLE);
        btnRetake.setVisibility(View.GONE);
    }

    private void showPinOverlay() {
        pinBuffer.setLength(0);
        updatePinDots();
        textPinError.setVisibility(View.INVISIBLE);
        panelPin.setVisibility(View.VISIBLE);
        panelEnrollment.setVisibility(View.GONE);
        btnTakeImage.setVisibility(View.GONE);
        btnRetake.setVisibility(View.GONE);
    }

    private void showEnrollmentPanel() {
        panelPin.setVisibility(View.GONE);
        panelEnrollment.setVisibility(View.VISIBLE);
        enrollCount = 0;
        enrollName  = "";
        lastFaceCrop = null;
        enrollNameInput.setText("");
        updateEnrollUI();
        textHelper.setText("Position face in frame and press TAKE PHOTO");
        textModeLabel.setText("ENROLLMENT MODE");
        btnTakeImage.setVisibility(View.GONE);
        btnRetake.setVisibility(View.GONE);
    }

    // ═════════════════════════════════════════════════════════════════════
    // RECOGNITION BUTTON LOGIC  (teammates' detection + classification)
    // ═════════════════════════════════════════════════════════════════════

    private void setupRecognitionButtons() {

        btnTakeImage.setOnClickListener(v -> {
            if (camera != null && lastPreviewData != null) {
                isFrozen   = true;
                frozenData = lastPreviewData.clone();

                Bitmap cameraBitmap = getBitmapFromVUY(frozenData, width, height);
                FaceDetectorResult result = detectImage(cameraBitmap);

                // Draw bounding boxes onto overlay
                Bitmap overlayBitmap = Bitmap.createBitmap(
                        overlayView.getWidth(), overlayView.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(overlayBitmap);
                android.graphics.Paint paint = new android.graphics.Paint();
                paint.setColor(android.graphics.Color.GREEN);
                paint.setStyle(android.graphics.Paint.Style.STROKE);
                paint.setStrokeWidth(10.0f);

                if (result.detections() != null) {
                    float scaleX = (float) overlayView.getWidth()  / cameraBitmap.getWidth();
                    float scaleY = (float) overlayView.getHeight() / cameraBitmap.getHeight();
                    for (Detection detection : result.detections()) {
                        RectF box = detection.boundingBox();
                        canvas.drawRect(
                                box.left  * scaleX, box.top    * scaleY,
                                box.right * scaleX, box.bottom * scaleY,
                                paint);
                    }
                }
                overlayView.setImageBitmap(overlayBitmap);

                if (result.detections() != null && !result.detections().isEmpty()) {
                    RectF box       = result.detections().get(0).boundingBox();
                    int   left      = Math.max(0, (int) box.left);
                    int   top       = Math.max(0, (int) box.top);
                    int   rectW     = Math.min((int) box.width(),  cameraBitmap.getWidth()  - left);
                    int   rectH     = Math.min((int) box.height(), cameraBitmap.getHeight() - top);
                    Bitmap faceCrop    = Bitmap.createBitmap(cameraBitmap, left, top, rectW, rectH);
                    Bitmap resizedFace = Bitmap.createScaledBitmap(faceCrop, 96, 96, true);
                    classifyFace(resizedFace);
                } else {
                    runOnUiThread(() -> textHelper.setText("No face detected. Please retake."));
                }

                camera.stopPreview();
                camera.setPreviewCallback(null);
            }

            btnTakeImage.setVisibility(View.GONE);
            btnRetake.setVisibility(View.VISIBLE);
        });

        btnRetake.setOnClickListener(v -> {
            if (camera != null) {
                isFrozen = false;
                camera.startPreview();
                camera.setPreviewCallback(new PreviewCallback() {
                    public void onPreviewFrame(byte[] data, Camera camera) {
                        if (!isFrozen) lastPreviewData = data;
                    }
                });
            }
            overlayView.setImageBitmap(null);
            btnTakeImage.setVisibility(View.VISIBLE);
            btnRetake.setVisibility(View.GONE);
            textHelper.setText("Align face and press Take Image");
        });
    }

    // ═════════════════════════════════════════════════════════════════════
    // PIN BUTTON LOGIC
    // ═════════════════════════════════════════════════════════════════════

    private void setupPinButtons() {
        int[] btnIds = {
                R.id.pin_btn_0, R.id.pin_btn_1, R.id.pin_btn_2, R.id.pin_btn_3,
                R.id.pin_btn_4, R.id.pin_btn_5, R.id.pin_btn_6, R.id.pin_btn_7,
                R.id.pin_btn_8, R.id.pin_btn_9
        };
        for (int id : btnIds) {
            Button b = findViewById(id);
            b.setOnClickListener(v -> {
                if (pinBuffer.length() < CORRECT_PIN.length()) {
                    pinBuffer.append(((Button) v).getText().toString());
                    updatePinDots();
                    textPinError.setVisibility(View.INVISIBLE);
                }
            });
        }
        findViewById(R.id.pin_btn_del).setOnClickListener(v -> {
            if (pinBuffer.length() > 0) {
                pinBuffer.deleteCharAt(pinBuffer.length() - 1);
                updatePinDots();
                textPinError.setVisibility(View.INVISIBLE);
            }
        });
        findViewById(R.id.pin_btn_ok).setOnClickListener(v -> {
            if (pinBuffer.toString().equals(CORRECT_PIN)) {
                showEnrollmentPanel();
            } else {
                textPinError.setVisibility(View.VISIBLE);
                pinBuffer.setLength(0);
                updatePinDots();
            }
        });
    }

    private void updatePinDots() {
        int total  = CORRECT_PIN.length();
        int filled = pinBuffer.length();
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < total; i++) {
            dots.append(i < filled ? "●" : "○");
            if (i < total - 1) dots.append("  ");
        }
        textPinDots.setText(dots.toString());
    }

    // ═════════════════════════════════════════════════════════════════════
    // ENROLLMENT BUTTON LOGIC
    // ═════════════════════════════════════════════════════════════════════

    private void setupEnrollmentButtons() {

        btnEnrollCapture.setOnClickListener(v -> {
            String typedName = enrollNameInput.getText().toString().trim();
            if (typedName.isEmpty()) {
                enrollNameInput.setError("Please enter a name first");
                return;
            }
            enrollName = typedName;
            if (camera == null || lastPreviewData == null) return;

            // Freeze the frame and run face detection to get the crop
            isFrozen   = true;
            frozenData = lastPreviewData.clone();
            camera.stopPreview();
            camera.setPreviewCallback(null);

            Bitmap cameraBitmap = getBitmapFromVUY(frozenData, width, height);
            FaceDetectorResult detResult = detectImage(cameraBitmap);

            if (detResult.detections() == null || detResult.detections().isEmpty()) {
                // No face found — unfreeze and let them try again
                textHelper.setText("No face detected. Try again.");
                isFrozen = false;
                camera.startPreview();
                camera.setPreviewCallback((data, cam) -> {
                    if (!isFrozen) lastPreviewData = data;
                });
                return;
            }

            // Crop the detected face — same pipeline as recognition
            RectF  box      = detResult.detections().get(0).boundingBox();
            int    left     = Math.max(0, (int) box.left);
            int    top      = Math.max(0, (int) box.top);
            int    rectW    = Math.min((int) box.width(),  cameraBitmap.getWidth()  - left);
            int    rectH    = Math.min((int) box.height(), cameraBitmap.getHeight() - top);
            Bitmap faceCrop = Bitmap.createBitmap(cameraBitmap, left, top, rectW, rectH);

            // Save the face crop (NOT the full frame) — processBitmapForTraining
            // now expects a pre-cropped face bitmap, matching how asset images
            // are prepared and how classifyFace sends images to the classifier.
            boolean saved = saveEnrollmentImage(faceCrop, enrollName, enrollCount);
            if (saved) {
                enrollCount++;
                updateEnrollUI();
                textHelper.setText("Captured " + enrollCount + " / " + ENROLL_TARGET
                        + " — move slightly and press again");
            }

            if (enrollCount >= ENROLL_TARGET) {
                onEnrollmentComplete();
            } else {
                // Resume preview for next shot
                isFrozen = false;
                camera.startPreview();
                camera.setPreviewCallback((data, cam) -> {
                    if (!isFrozen) lastPreviewData = data;
                });
            }
        });

        findViewById(R.id.button_enroll_cancel).setOnClickListener(v -> {
            isFrozen = false;
            if (camera != null) {
                camera.startPreview();
                camera.setPreviewCallback((data, cam) -> {
                    if (!isFrozen) lastPreviewData = data;
                });
            }
            startRecognitionMode();
        });
    }

    /**
     * Saves a MediaPipe-cropped face bitmap to internal storage as a JPEG.
     * Path: getFilesDir()/faces/<name>/<name>_<index>.jpg
     *
     * We save the RAW crop (before processBitmapForTraining). During training,
     * startTraining() calls processBitmapForTraining() on each saved image,
     * applying resize→grayscale→histEq exactly once — identical to how asset
     * images are loaded and how classifyFace processes a recognition frame.
     *
     * On index==0 any leftover files from a previous partial session are cleared.
     */
    private boolean saveEnrollmentImage(Bitmap faceCrop, String name, int index) {
        try {
            File dir = new File(getFilesDir(), "faces/" + name);
            if (index == 0 && dir.exists()) {
                for (File f : dir.listFiles()) f.delete();
                Log.d("Enrollment", "Cleared previous partial enrollment for: " + name);
            }
            if (!dir.exists()) dir.mkdirs();

            File outFile = new File(dir, name + "_" + index + ".jpg");
            Log.d("Enrollment", "Saving image " + index + " to: " + outFile.getAbsolutePath());

            FileOutputStream fos = new FileOutputStream(outFile);
            faceCrop.compress(Bitmap.CompressFormat.JPEG, 95, fos);
            fos.close();
            return true;
        } catch (IOException e) {
            Log.e("Enrollment", "Failed to save image: " + e.getMessage());
            return false;
        }
    }

    private void updateEnrollUI() {
        textEnrollCount.setText(enrollCount + " / " + ENROLL_TARGET);
        btnEnrollCapture.setText("TAKE PHOTO  (" + enrollCount + " / " + ENROLL_TARGET + ")");
        enrollProgressBar.post(() -> {
            View parent     = (View) enrollProgressBar.getParent();
            int  totalWidth = parent.getWidth();
            int  filled     = (int) ((enrollCount / (float) ENROLL_TARGET) * totalWidth);
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(filled, LinearLayout.LayoutParams.MATCH_PARENT);
            enrollProgressBar.setLayoutParams(params);
        });
    }

    /** Recursively deletes all files and subdirectories inside a directory. */
    private void deleteDirectoryContents(File dir) {
        if (dir == null || !dir.exists()) return;
        for (File child : dir.listFiles()) {
            if (child.isDirectory()) deleteDirectoryContents(child);
            child.delete();
        }
        dir.delete();
    }

    private void onEnrollmentComplete() {
        panelEnrollment.setVisibility(View.GONE);
        textHelper.setText("Enrollment complete! Re-training model...");

        final String justEnrolledName = enrollName;

        new Thread(() -> {
            try {
                // Remove all enrollment dirs except the one just completed
                File internalFacesDir = new File(getFilesDir(), "faces");
                if (internalFacesDir.exists()) {
                    for (File personDir : internalFacesDir.listFiles()) {
                        if (!personDir.getName().equals(justEnrolledName)) {
                            deleteDirectoryContents(personDir);
                            Log.d("Enrollment", "Removed stale enrollment: " + personDir.getName());
                        }
                    }
                }
                startTraining(true); // assets + newly enrolled member
                runOnUiThread(() -> {
                    textHelper.setText("Training complete! " + justEnrolledName + " has been enrolled.");
                    startRecognitionMode();
                    isFrozen = false;
                    if (camera != null) {
                        camera.startPreview();
                        camera.setPreviewCallback((data, cam) -> {
                            if (!isFrozen) lastPreviewData = data;
                        });
                    }
                });
            } catch (IOException e) {
                Log.e("CameraActivity", "Re-training failed", e);
                runOnUiThread(() -> textHelper.setText("Re-training failed. Check logs."));
            }
        }).start();
    }

    // ═════════════════════════════════════════════════════════════════════
    // CAMERA LIFECYCLE  (teammates' version, unchanged)
    // ═════════════════════════════════════════════════════════════════════

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (!previewing) {
            int frontCameraId   = -1;
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
                            if (!isFrozen) lastPreviewData = data;
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
        int[]  retData = yuv2rgb(data);
        Bitmap bmp     = Bitmap.createBitmap(retData, width, height, Bitmap.Config.ARGB_8888);
        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
        canvas.drawBitmap(bmp, new Rect(0, 0, bmp.getWidth(), bmp.getHeight()),
                new Rect(0, 0, canvas.getWidth(), canvas.getHeight()), null);
    }

    /** Teammates' yuv2rgb — uses UV offset of 96 instead of 128. */
    public int[] yuv2rgb(byte[] data) {
        final int frameSize = width * height;
        int[]     rgb       = new int[frameSize];
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
                int r = y1192 + 1634 * v;
                int g = y1192 - 833  * v - 400 * u;
                int b = y1192 + 2066 * u;
                r = r < 0 ? 0 : (r > 262143 ? 262143 : r);
                g = g < 0 ? 0 : (g > 262143 ? 262143 : g);
                b = b < 0 ? 0 : (b > 262143 ? 262143 : b);
                rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
            }
        }
        return rgb;
    }

    private Bitmap getBitmapFromVUY(byte[] data, int w, int h) {
        int[]  argb   = yuv2rgb(data);
        Bitmap bitmap = Bitmap.createBitmap(argb, w, h, Bitmap.Config.ARGB_8888);
        Matrix matrix = new Matrix();
        matrix.postRotate(270);
        matrix.postScale(-1, 1);
        return Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, true);
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (camera != null && previewing) {
            camera.stopPreview();
            camera.setPreviewCallback(null);
            camera.release();
            camera     = null;
            previewing = false;
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // TRAINING
    // ═════════════════════════════════════════════════════════════════════

    /**
     * @param includeEnrolled  false → assets only (startup, avoids stale enrollments)
     *                         true  → assets + internal storage (post-enrollment retrain)
     */
    private void startTraining(boolean includeEnrolled) throws IOException {
        Log.d("CameraActivity", "startTraining called, includeEnrolled=" + includeEnrolled);

        ArrayList<double[]> imageList = new ArrayList<>();
        ArrayList<Integer>  labels    = new ArrayList<>();
        identification.clear();

        int labelIndex = 0;

        // 1. Load original team members from bundled assets/faces/
        for (String personName : getAssets().list("faces")) {
            String[] imageFiles = getAssets().list("faces/" + personName);
            if (imageFiles == null || imageFiles.length == 0) continue;

            identification.put(labelIndex, personName);
            for (String imgFile : imageFiles) {
                Bitmap bmp = BitmapFactory.decodeStream(
                        getAssets().open("faces/" + personName + "/" + imgFile));
                if (bmp == null) continue;
                byte[]   processed = processor.processBitmapForTraining(bmp);
                double[] arr       = toDoubleArray(processed);
                bmp.recycle();
                if (arr.length == PIXEL_COUNT) {
                    imageList.add(arr);
                    labels.add(labelIndex);
                }
            }
            labelIndex++;
        }

        // 2. Optionally load newly enrolled people from internal storage
        if (includeEnrolled) {
            File internalFacesDir = new File(getFilesDir(), "faces");
            if (internalFacesDir.exists() && internalFacesDir.listFiles() != null) {
                for (File personDir : internalFacesDir.listFiles()) {
                    if (!personDir.isDirectory()) continue;
                    String personName = personDir.getName();
                    if (identification.containsValue(personName)) continue;
                    File[] images = personDir.listFiles();
                    if (images == null || images.length == 0) continue;

                    identification.put(labelIndex, personName);
                    nextLabel = Math.max(nextLabel, labelIndex + 1);
                    for (File imgFile : images) {
                        Bitmap bmp = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                        if (bmp == null) continue;
                        byte[]   processed = processor.processBitmapForTraining(bmp);
                        double[] arr       = toDoubleArray(processed);
                        bmp.recycle();
                        if (arr.length == PIXEL_COUNT) {
                            imageList.add(arr);
                            labels.add(labelIndex);
                        }
                    }
                    labelIndex++;
                }
            }
        }

        Log.d("CameraActivity", "Training with " + imageList.size()
                + " images across " + labelIndex + " classes"
                + (includeEnrolled ? " (enrolled included)" : " (assets only)"));

        if (imageList.size() < 2 || labelIndex < 2) return;

        double[][] imageArray = imageList.toArray(new double[0][]);
        int[]      labelArray = labels.stream().mapToInt(Integer::intValue).toArray();
        classifier.ComputeTrainingWeights(imageArray, labelArray, 96, 96);
    }

    // ═════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═════════════════════════════════════════════════════════════════════

    private double[] toDoubleArray(byte[] src) {
        double[] dst = new double[src.length];
        for (int i = 0; i < src.length; i++) dst[i] = src[i] & 0xFF;
        return dst;
    }
}