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

    // App state flags for the Doorbell Logic
    public static int appFlag = 0;
    public static final int MODE_RECOGNITION = 1;
    public static final int MODE_ENROLLMENT = 2;

    // UI Variables
    private Button recognitionButton;
    private Button enrollmentButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        super.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // 1. Initialize OpenCV for your matrix math
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Unable to load OpenCV!");
        } else {
            Log.d("OpenCV", "OpenCV loaded successfully.");
        }

        // 2. Request Camera Permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
        }

        // 3. Setup Doorbell Mode (Recognition)
        recognitionButton = (Button) findViewById(R.id.histeqButton); // Reusing ID for now
        recognitionButton.setText("Start Smart Doorbell");
        recognitionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appFlag = MODE_RECOGNITION;
                startActivity(new Intent(MainActivity.this, CameraActivity.class));
            }
        });

        // 4. Setup Smart Enrollment Mode (Training) [cite: 10, 49, 208]
        enrollmentButton = (Button) findViewById(R.id.sharpButton); // Reusing ID for now
        enrollmentButton.setText("Enroll Household Member");
        enrollmentButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appFlag = MODE_ENROLLMENT;
                startActivity(new Intent(MainActivity.this, CameraActivity.class));
            }
        });

        // Note: The Edge Button is hidden or reused for a 'Settings/Reset' later
        findViewById(R.id.edgeButton).setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        super.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        super.onResume();
    }
}