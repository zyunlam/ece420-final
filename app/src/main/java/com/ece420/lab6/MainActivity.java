package com.ece420.lab6;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ActivityInfo;
import android.Manifest;
import android.os.Bundle;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import org.opencv.android.OpenCVLoader;

public class MainActivity extends Activity {

    // App state flags for doorbell logic
    public static int appFlag = 0;
    public static final int MODE_RECOGNITION = 1; // Doorbell Mode
    public static final int MODE_ENROLLMENT = 2;  // Training Mode

    // UI vars
    private Button enrollmentButton;
    private Button runDoorbellButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        super.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // Initialize OpenCV for matrix math (PCA/LDA)
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Unable to load OpenCV!");
        } else {
            Log.d("OpenCV", "OpenCV loaded successfully.");
        }

        // Request camera perms
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
        }

        // Set up enrollment button
        enrollmentButton = (Button) findViewById(R.id.button_enroll_mode);
        enrollmentButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appFlag = MODE_ENROLLMENT;
                startActivity(new Intent(MainActivity.this, CameraActivity.class));
            }
        });

        // Set up doorbell start button
        runDoorbellButton = (Button) findViewById(R.id.button_recognition_mode);
        runDoorbellButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appFlag = MODE_RECOGNITION;
                startActivity(new Intent(MainActivity.this, CameraActivity.class));
            }
        });

        // Can uncomment the line below to have doorbell autolaunch on startup instead
        // recognitionButton.performClick();
    }

    @Override
    protected void onResume() {
        super.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        super.onResume();
    }
}