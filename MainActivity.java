package com.example.facerecognitionapp;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import androidx.appcompat.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.opencv.android.Utils;
import android.graphics.Rect;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.window.java.layout.WindowInfoTrackerCallbackAdapter;
import androidx.window.layout.DisplayFeature;
import androidx.window.layout.FoldingFeature;
import androidx.window.layout.WindowInfoTracker;
import androidx.window.layout.WindowLayoutInfo;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import androidx.lifecycle.LifecycleOwner;
import androidx.core.util.Consumer;
import androidx.window.layout.WindowInfoTracker;
import androidx.window.layout.WindowLayoutInfo;
import androidx.window.layout.FoldingFeature;
import androidx.window.layout.DisplayFeature;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import androidx.window.layout.WindowInfoTracker;
import androidx.window.layout.WindowLayoutInfo;
import androidx.window.layout.FoldingFeature;
import androidx.window.layout.DisplayFeature;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import androidx.core.util.Consumer;
import java.util.concurrent.Executors;
import java.io.FileInputStream;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {
    private Handler handler = new Handler(Looper.getMainLooper());
    private int imageCount = 0;
    private static final int totalImages = 100;
    private boolean isCapturing = false;
    private boolean isCaptureComplete = false;
    private CascadeClassifier cascadeClassifier;
    private ImageCapture imageCapture;
    private CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
    private PreviewView previewView;
    private Button captureButton, convertButton, recognizeButton;
    private ImageButton clearDataButton;
    private ImageButton switchCameraButton;
    private TextView captureStatusText;
    private String userName;
    private ProgressBar progressBar;
    private Map<Integer, String> labelMap = new HashMap<>();
    private boolean isRecognitionActive = false;
    private Rect[] faces = new Rect[0];
    private String[] names = new String[0];
    private Paint boxPaint = new Paint();
    private Paint textPaint = new Paint();
    private SensorManager sensorManager;
    private Sensor hingeSensor;
    private boolean isFoldedState = false;
    private WindowInfoTrackerCallbackAdapter callbackAdapter;
    private Consumer<WindowLayoutInfo> layoutInfoConsumer;
    private boolean isAutoCapturing = false;
    private TextView recognitionResultText;
    private final List<float[]> allFaceVectors = new ArrayList<>();
    private final List<Integer> allLabels = new ArrayList<>();
    private ArrayList<float[]> faceEmbeddings = new ArrayList<>();
    private ArrayList<Integer> faceLabels = new ArrayList<>();


    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startCamera();
                } else {
                    Toast.makeText(this, "Camera permission not granted", Toast.LENGTH_LONG).show();
                }
            });
    static {
        try {
            System.loadLibrary("opencv_java4");
            Log.d("OpenCV", "✅ Native lib loaded");
        } catch (UnsatisfiedLinkError e) {
            Log.e("OpenCV", "❌ Native lib load failed: " + e.getMessage());
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().setStatusBarColor(Color.parseColor("#6200EE"));
        }

        setContentView(R.layout.activity_main);
        rebindViews(); // ✅ now all views are linked
        recognitionResultText = findViewById(R.id.recognitionResultText);
        recognitionResultText.setVisibility(View.GONE); // Initially hidden

        // ✅ Folding logic
        WindowInfoTracker tracker = WindowInfoTracker.getOrCreate(this);
        callbackAdapter = new WindowInfoTrackerCallbackAdapter(tracker);
        layoutInfoConsumer = layoutInfo -> {
            if (isAutoCapturing) return; // 🚫 Skip layout switch during capture

            for (DisplayFeature feature : layoutInfo.getDisplayFeatures()) {
                if (feature instanceof FoldingFeature) {
                    FoldingFeature fold = (FoldingFeature) feature;

                    if (fold.getState() == FoldingFeature.State.HALF_OPENED &&
                            fold.getOrientation() == FoldingFeature.Orientation.HORIZONTAL) {
                        switchToFoldedLayout();
                    } else {
                        switchToNormalLayout();
                    }
                }
            }
        };
        callbackAdapter.addWindowLayoutInfoListener(this, Executors.newSingleThreadExecutor(), layoutInfoConsumer);

        // ✅ Start camera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        } else {
            startCamera();
        }

        // ✅ Setup button listeners
        captureButton.setOnClickListener(v -> {
            isRecognitionActive = false;
            FaceOverlayView overlay = findViewById(R.id.faceOverlay);
            overlay.setFaces(new Rect[0], new String[0]);
            askForName();
            recognitionResultText.setVisibility(View.GONE);
        });

        switchCameraButton.setOnClickListener(v -> toggleCamera());

        convertButton.setOnClickListener(v -> {
            isRecognitionActive = false;
            FaceOverlayView overlay = findViewById(R.id.faceOverlay);
            overlay.setFaces(new Rect[0], new String[0]);

            if (!isCaptureComplete) {
                Toast.makeText(this, "Wait until 100 images are captured", Toast.LENGTH_SHORT).show();
                return;
            }

            new Thread(() -> {
                runOnUiThread(() -> Toast.makeText(this, "Starting data conversion...", Toast.LENGTH_SHORT).show());
                convertImagesToData();
                runOnUiThread(() -> Toast.makeText(this, "✅ Data conversion complete!", Toast.LENGTH_LONG).show());
            }).start();
            recognitionResultText.setVisibility(View.GONE);
        });

        clearDataButton.setOnClickListener(v -> {
            isRecognitionActive = false;
            FaceOverlayView overlay = findViewById(R.id.faceOverlay);
            overlay.setFaces(new Rect[0], new String[0]);

            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Confirm Delete")
                    .setMessage("Are you sure you want to delete all data (images + labels)?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        boolean allDeleted = true;

                        File capturedImagesDir = new File(getExternalFilesDir(null), "CapturedImages");
                        if (capturedImagesDir.exists()) {
                            for (File personFolder : capturedImagesDir.listFiles()) {
                                if (personFolder.isDirectory()) {
                                    for (File img : personFolder.listFiles()) {
                                        if (!img.delete()) allDeleted = false;
                                    }
                                }
                                if (!personFolder.delete()) allDeleted = false;
                            }
                            if (!capturedImagesDir.delete()) allDeleted = false;
                        }

                        File[] dataFiles = {
                                new File(getExternalFilesDir(null), "face_data.data"),
                                new File(getExternalFilesDir(null), "labels.data"),
                                new File(getExternalFilesDir(null), "label.txt")
                        };
                        for (File file : dataFiles) {
                            if (file.exists() && !file.delete()) allDeleted = false;
                        }

                        Toast.makeText(MainActivity.this,
                                allDeleted ? "🗑️ All data cleared!" : "⚠️ Some files could not be deleted.",
                                Toast.LENGTH_SHORT).show();

                        isCaptureComplete = false;
                        labelMap.clear();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            recognitionResultText.setVisibility(View.GONE);
        });

        recognizeButton.setOnClickListener(v -> {
            FaceOverlayView overlay = findViewById(R.id.faceOverlay);
            overlay.setFaces(new Rect[0], new String[0]);

            if (isRecognitionActive) {
                isRecognitionActive = false;
                recognitionResultText.setText(""); // Clear text
                recognitionResultText.setVisibility(View.GONE);
                Toast.makeText(this, "🛑 Recognition stopped", Toast.LENGTH_SHORT).show();
                return;
            }

            if (imageCapture == null) {
                Toast.makeText(this, "Camera not ready!", Toast.LENGTH_SHORT).show();
                return;
            }

            File tempFile = new File(getCacheDir(), "temp_recognize.jpg");
            ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(tempFile).build();

            imageCapture.takePicture(options, ContextCompat.getMainExecutor(this),
                    new ImageCapture.OnImageSavedCallback() {
                        @Override
                        public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                            Bitmap bitmap = BitmapFactory.decodeFile(tempFile.getAbsolutePath());
                            float[] testVector = extractRawFaceVector(bitmap);

                            if (testVector == null) {
                                recognitionResultText.setText("⚠️ No Face Found");
                                recognitionResultText.setTextColor(Color.RED);
                                recognitionResultText.setVisibility(View.VISIBLE);
                                return;
                            }

                            int bestLabel = findBestMatch(testVector);
                            if (bestLabel == -1 || labelMap == null || !labelMap.containsKey(bestLabel)) {
                                recognitionResultText.setText("🙅 Unknown Face");
                                recognitionResultText.setTextColor(Color.RED);
                            } else {
                                String name = labelMap.get(bestLabel);
                                recognitionResultText.setText("✅ Recognized: " + name);
                                recognitionResultText.setTextColor(Color.parseColor("#4CAF50")); // Green
                                isRecognitionActive = true;
                            }

                            recognitionResultText.setTextSize(24f); // Bigger
                            recognitionResultText.setVisibility(View.VISIBLE);
                        }

                        @Override
                        public void onError(@NonNull ImageCaptureException exception) {
                            recognitionResultText.setText("❌ Error: " + exception.getMessage());
                            recognitionResultText.setTextColor(Color.RED);
                            recognitionResultText.setTextSize(20f);
                            recognitionResultText.setVisibility(View.VISIBLE);
                        }
                    });
        });
        loadSavedData();

    }
    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (callbackAdapter != null && layoutInfoConsumer != null) {
            callbackAdapter.removeWindowLayoutInfoListener(layoutInfoConsumer);
        }
    }
    private final SensorEventListener hingeListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            float hingeAngle = event.values[0];
            Log.d("HingeSensor", "Hinge angle: " + hingeAngle);

            if (hingeAngle >= 80 && hingeAngle <= 100 && !isFoldedState) {
                isFoldedState = true;
                switchToFoldedLayout(); // You define this
            } else if (hingeAngle < 80 && isFoldedState) {
                isFoldedState = false;
                switchToNormalLayout(); // You define this
            }
        }
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    private void switchToNormalLayout() {
        runOnUiThread(() -> {
            setContentView(R.layout.activity_main);
            rebindViews();
            startCamera(); // ✅ ensure camera is started after layout change
        });
    }
    private void switchToFoldedLayout() {
        runOnUiThread(() -> {
            setContentView(R.layout.activity_main_folded);
            rebindViews();
            startCamera(); // ✅ same here
        });
    }

    private void rebindViews() {
        // Step 1: Re-bind all view elements
        previewView = findViewById(R.id.previewView);
        captureButton = findViewById(R.id.btnCapture);
        switchCameraButton = findViewById(R.id.btnSwitchCamera);
        convertButton = findViewById(R.id.btnConvert);
        recognizeButton = findViewById(R.id.btnRecognize);
        clearDataButton = findViewById(R.id.btnClear);
        progressBar = findViewById(R.id.progressBar);
        captureStatusText = findViewById(R.id.captureStatusText);
        recognitionResultText = findViewById(R.id.recognitionResultText);  // 🟢 Important: bind first
        recognitionResultText.setVisibility(View.GONE);                     // 🔴 Immediately hide

        FaceOverlayView overlay = findViewById(R.id.faceOverlay);

        // Step 2: Set up each button’s functionality again
        captureButton.setOnClickListener(v -> {
            isRecognitionActive = false;
            overlay.setFaces(new Rect[0], new String[0]);
            recognitionResultText.setVisibility(View.GONE);  // 🔁 Hide name when capturing
            askForName();  // Will start auto-capture
        });

        switchCameraButton.setOnClickListener(v -> {
            isRecognitionActive = false;
            overlay.setFaces(new Rect[0], new String[0]);
            recognitionResultText.setVisibility(View.GONE);  // 🔁 Hide name when switching
            toggleCamera();  // Switch between front/back camera
        });

        convertButton.setOnClickListener(v -> {
            isRecognitionActive = false;
            overlay.setFaces(new Rect[0], new String[0]);
            recognitionResultText.setVisibility(View.GONE);  // 🔁 Hide name during conversion

            if (!isCaptureComplete) {
                Toast.makeText(this, "Wait until 100 images are captured", Toast.LENGTH_SHORT).show();
                return;
            }

            new Thread(() -> {
                runOnUiThread(() -> Toast.makeText(this, "Starting data conversion...", Toast.LENGTH_SHORT).show());
                convertImagesToData();
                runOnUiThread(() -> Toast.makeText(this, "✅ Data conversion complete!", Toast.LENGTH_LONG).show());
            }).start();
        });

        recognizeButton.setOnClickListener(v -> {
            if (isRecognitionActive) {
                isRecognitionActive = false;
                overlay.setFaces(new Rect[0], new String[0]);
                recognitionResultText.setText("");
                recognitionResultText.setVisibility(View.GONE);
                Toast.makeText(this, "🛑 Recognition stopped", Toast.LENGTH_SHORT).show();
                return;
            }

            if (imageCapture == null) {
                Toast.makeText(this, "Camera not ready!", Toast.LENGTH_SHORT).show();
                return;
            }

            File tempFile = new File(getCacheDir(), "temp_recognize.jpg");
            ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(tempFile).build();

            imageCapture.takePicture(options, ContextCompat.getMainExecutor(this),
                    new ImageCapture.OnImageSavedCallback() {
                        @Override
                        public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                            Bitmap bitmap = BitmapFactory.decodeFile(tempFile.getAbsolutePath());
                            float[] testVector = extractRawFaceVector(bitmap);

                            if (testVector == null) {
                                recognitionResultText.setText("⚠️ No Face Found");
                                recognitionResultText.setTextColor(Color.RED);
                                recognitionResultText.setTextSize(20);
                                recognitionResultText.setVisibility(View.VISIBLE);
                                return;
                            }

                            int bestLabel = findBestMatch(testVector);
                            if (bestLabel == -1 || labelMap == null || !labelMap.containsKey(bestLabel)) {
                                recognitionResultText.setText("🙅 Unknown Face");
                                recognitionResultText.setTextColor(Color.RED);
                            } else {
                                String name = labelMap.get(bestLabel);
                                recognitionResultText.setText("✅ Recognized: " + name);
                                recognitionResultText.setTextColor(Color.parseColor("#4CAF50"));
                                isRecognitionActive = true;
                            }

                            recognitionResultText.setTextSize(22);
                            recognitionResultText.setVisibility(View.VISIBLE);
                        }

                        @Override
                        public void onError(@NonNull ImageCaptureException exception) {
                            recognitionResultText.setText("❌ Error: " + exception.getMessage());
                            recognitionResultText.setTextColor(Color.RED);
                            recognitionResultText.setTextSize(20);
                            recognitionResultText.setVisibility(View.VISIBLE);
                        }
                    });
        });

        clearDataButton.setOnClickListener(v -> {
            isRecognitionActive = false;
            overlay.setFaces(new Rect[0], new String[0]);
            recognitionResultText.setVisibility(View.GONE);  // 🔁 Hide on clear

            new AlertDialog.Builder(this)
                    .setTitle("Confirm Delete")
                    .setMessage("Are you sure you want to delete all data (images + labels)?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        boolean allDeleted = true;

                        File capturedImagesDir = new File(getExternalFilesDir(null), "CapturedImages");
                        if (capturedImagesDir.exists()) {
                            for (File personFolder : capturedImagesDir.listFiles()) {
                                if (personFolder.isDirectory()) {
                                    for (File img : personFolder.listFiles()) {
                                        if (!img.delete()) allDeleted = false;
                                    }
                                }
                                if (!personFolder.delete()) allDeleted = false;
                            }
                            if (!capturedImagesDir.delete()) allDeleted = false;
                        }

                        File[] dataFiles = {
                                new File(getExternalFilesDir(null), "face_data.data"),
                                new File(getExternalFilesDir(null), "labels.data"),
                                new File(getExternalFilesDir(null), "label.txt")
                        };
                        for (File file : dataFiles) {
                            if (file.exists() && !file.delete()) allDeleted = false;
                        }

                        Toast.makeText(this,
                                allDeleted ? "🗑️ All data cleared!" : "⚠️ Some files could not be deleted.",
                                Toast.LENGTH_SHORT).show();

                        isCaptureComplete = false;
                        labelMap.clear();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }
    private void loadSavedData() {
        faceEmbeddings.clear();
        faceLabels.clear();

        try {
            // Read face data
            File dataFile = new File(getExternalFilesDir(null), "face_data.data");
            FileInputStream dataFis = new FileInputStream(dataFile);
            ObjectInputStream dataOis = new ObjectInputStream(dataFis);

            while (true) {
                try {
                    float[] face = (float[]) dataOis.readObject();
                    faceEmbeddings.add(face);
                } catch (EOFException eof) {
                    break; // Reached end of file
                }
            }

            dataOis.close();

            // Read label data
            File lblFile = new File(getExternalFilesDir(null), "labels.data");
            FileInputStream lblFis = new FileInputStream(lblFile);
            ObjectInputStream lblOis = new ObjectInputStream(lblFis);

            while (true) {
                try {
                    int label = lblOis.readInt();
                    faceLabels.add(label);
                } catch (EOFException eof) {
                    break;
                }
            }

            lblOis.close();

            loadLabelMap();  // optional: reloads name mapping from label.txt

            Log.d("LoadData", "✅ Loaded " + faceEmbeddings.size() + " embeddings and " + faceLabels.size() + " labels");

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "❌ Failed to load saved face data", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleCamera() {
        cameraSelector = (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
                ? CameraSelector.DEFAULT_FRONT_CAMERA
                : CameraSelector.DEFAULT_BACK_CAMERA;
        startCamera();
    }
    private void askForName() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Your Name");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            String rawName = input.getText().toString().trim();
            if (rawName.isEmpty()) {
                Toast.makeText(this, "Name cannot be empty!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Check for existing folders and auto-increment
            File baseDir = new File(getExternalFilesDir(null), "CapturedImages");
            File nameDir = new File(baseDir, rawName);
            int count = 1;
            while (nameDir.exists()) {
                nameDir = new File(baseDir, rawName + "_" + count);
                count++;
            }

            userName = nameDir.getName();  // Set the new unique name
            Toast.makeText(this, "Using name: " + userName, Toast.LENGTH_SHORT).show();

            startCaptureSequence();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }
    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                deleteRecursive(child);
            }
        }
        fileOrDirectory.delete();
    }
    private void startCaptureSequence() {
        imageCount = 0;
        isCapturing = true;
        isCaptureComplete = false;
        isAutoCapturing = true;

        updateCaptureTextColor();  // 👈 Set color based on theme
        captureStatusText.setText("Starting capture...");
        startAutoCapture();
    }

    private void startAutoCapture() {
        File userDir = new File(getExternalFilesDir(null), "CapturedImages/" + userName);
        if (!userDir.exists()) userDir.mkdirs();

        final Runnable[] captureRunnable = new Runnable[1];

        captureRunnable[0] = new Runnable() {
            @Override
            public void run() {
                if (imageCount >= totalImages) {
                    isCapturing = false;
                    isCaptureComplete = true;
                    isAutoCapturing = false;
                    updateCaptureTextColor(); // ✅ Update again after capture ends
                    captureStatusText.setText("✅ Done capturing!");
                    handler.postDelayed(() -> captureStatusText.setText("Ready"), 2000);

                    captureButton.setEnabled(true);
                    convertButton.setEnabled(true);
                    recognizeButton.setEnabled(true);
                    clearDataButton.setEnabled(true);
                    switchCameraButton.setEnabled(true);

                    Toast.makeText(MainActivity.this, "🎉 All images captured!", Toast.LENGTH_SHORT).show();
                    return;
                }

                File photoFile = new File(userDir, imageCount + ".jpg");
                ImageCapture.OutputFileOptions outputOptions =
                        new ImageCapture.OutputFileOptions.Builder(photoFile).build();

                imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(MainActivity.this),
                        new ImageCapture.OnImageSavedCallback() {
                            @Override
                            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                                imageCount++;
                                updateCaptureTextColor(); // ✅ Adjust color every update
                                captureStatusText.setText("📸 Captured " + imageCount + " / " + totalImages);
                                handler.post(captureRunnable[0]);
                            }

                            @Override
                            public void onError(@NonNull ImageCaptureException exception) {
                                captureStatusText.setText("⚠️ Error: " + exception.getMessage());
                                isCapturing = false;
                                isAutoCapturing = false;
                            }
                        });
            }
        };
        handler.postDelayed(captureRunnable[0], 25);
    }
    private void updateCaptureTextColor() {
        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;

        if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
            captureStatusText.setTextColor(Color.WHITE);  // Light text for dark mode
        } else {
            captureStatusText.setTextColor(Color.BLACK);  // Dark text for light mode
        }
    }
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                imageCapture = new ImageCapture.Builder().build();

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(new android.util.Size(640, 480))
                        .build();

                imageAnalysis.setAnalyzer(getExecutor(), image -> {
                    if (image.getFormat() != ImageFormat.YUV_420_888) {
                        image.close();
                        return;
                    }

                    Bitmap bitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
                    new YuvToRgbConverter(this).yuvToRgb(image, bitmap);

                    if (bitmap == null) {
                        image.close();
                        return;
                    }

                    Mat mat = new Mat();
                    Utils.bitmapToMat(bitmap, mat);

                    if (bitmap.getWidth() > bitmap.getHeight()) {
                        Core.transpose(mat, mat);
                        Core.flip(mat, mat, 1);
                    }

                    Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2GRAY);
                    Imgproc.equalizeHist(mat, mat);

                    org.opencv.core.Rect[] facesArray;
                    if (cascadeClassifier != null) {
                        MatOfRect faces = new MatOfRect();
                        cascadeClassifier.detectMultiScale(mat, faces, 1.1, 3, 0, new Size(30, 30));
                        facesArray = faces.toArray();
                    } else {
                        facesArray = new org.opencv.core.Rect[0];
                    }

                    int imgWidth = mat.width();
                    int imgHeight = mat.height();
                    int viewWidth = previewView.getWidth();
                    int viewHeight = previewView.getHeight();

                    float scaleX = viewWidth / (float) imgWidth;
                    float scaleY = viewHeight / (float) imgHeight;

                    android.graphics.Rect[] androidRects = Arrays.stream(facesArray)
                            .map(r -> new android.graphics.Rect(
                                    (int) (r.x * scaleX),
                                    (int) (r.y * scaleY),
                                    (int) ((r.x + r.width) * scaleX),
                                    (int) ((r.y + r.height) * scaleY)
                            ))
                            .toArray(android.graphics.Rect[]::new);

                    runOnUiThread(() -> {
                        if (isRecognitionActive && facesArray.length > 0) {
                            int maxArea = -1;
                            int maxIndex = -1;
                            for (int i = 0; i < facesArray.length; i++) {
                                int area = facesArray[i].width * facesArray[i].height;
                                if (area > maxArea) {
                                    maxArea = area;
                                    maxIndex = i;
                                }
                            }

                            if (maxIndex != -1) {
                                org.opencv.core.Rect faceRect = facesArray[maxIndex];
                                Mat faceROI = new Mat(mat, faceRect);
                                String name = recognizeFace(faceROI);

                                // ✅ Show persistent recognition result
                                recognitionResultText.setText("Recognized: " + name);
                                recognitionResultText.setVisibility(View.VISIBLE);
                            }
                        }
                    });

                    image.close();
                });

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalysis);
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, getExecutor());
    }

    private void convertImagesToData() {
        runOnUiThread(() -> {
            ProgressBar progressBar = findViewById(R.id.progressBar);
            if (progressBar != null) {
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(0);
            }
        });

        File capturedImagesDir = new File(getExternalFilesDir(null), "CapturedImages");
        if (!capturedImagesDir.exists()) {
            runOnUiThread(() -> Toast.makeText(this, "No captured images found!", Toast.LENGTH_SHORT).show());
            return;
        }

        Map<String, Integer> processedCache = new HashMap<>();
        File cacheFile = new File(getExternalFilesDir(null), "processed_log.txt");

        // Read existing cache
        if (cacheFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(cacheFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.trim().split(" ");
                    if (parts.length == 2) {
                        processedCache.put(parts[0], Integer.parseInt(parts[1]));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        ArrayList<float[]> faceDataList = new ArrayList<>();
        ArrayList<Integer> labelList = new ArrayList<>();
        Map<Integer, String> labelNameMap = new HashMap<>();

        File[] personDirs = capturedImagesDir.listFiles();
        if (personDirs == null || personDirs.length == 0) {
            runOnUiThread(() -> Toast.makeText(this, "No folders found inside CapturedImages!", Toast.LENGTH_SHORT).show());
            return;
        }

        int totalImages = 0;
        for (File dir : personDirs) {
            if (dir.isDirectory()) {
                File[] files = dir.listFiles((f, name) -> name.endsWith(".jpg"));
                if (files != null) totalImages += files.length;
            }
        }

        int currentProgress = 0;
        int label = 0;
        Map<String, Integer> newCache = new HashMap<>();

        for (File personDir : personDirs) {
            if (!personDir.isDirectory()) continue;

            File[] imageFiles = personDir.listFiles((f, name) -> name.endsWith(".jpg"));
            if (imageFiles == null) continue;

            int existingCount = processedCache.getOrDefault(personDir.getName(), -1);
            if (existingCount == imageFiles.length) {
                Log.d("ConvertSkip", "Skipping " + personDir.getName() + ", already processed.");
                label++;
                continue;
            }

            labelNameMap.put(label, personDir.getName());

            for (File imageFile : imageFiles) {
                Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
                if (bitmap == null) continue;

                Mat img = new Mat();
                Utils.bitmapToMat(bitmap, img);
                if (img.empty()) continue;

                Imgproc.cvtColor(img, img, Imgproc.COLOR_BGR2GRAY);
                Imgproc.resize(img, img, new Size(100, 100));

                float[] flatData = new float[100 * 100];
                for (int y = 0; y < 100; y++) {
                    for (int x = 0; x < 100; x++) {
                        double[] pixel = img.get(y, x);
                        flatData[y * 100 + x] = (float) (pixel[0] / 255.0);
                    }
                }

                faceDataList.add(flatData);
                labelList.add(label);

                currentProgress++;
                final int progressPercent = (int) ((currentProgress * 100.0) / totalImages);
                runOnUiThread(() -> {
                    ProgressBar progressBar = findViewById(R.id.progressBar);
                    if (progressBar != null) progressBar.setProgress(progressPercent);
                });
            }
            newCache.put(personDir.getName(), imageFiles.length);
            label++;
        }

        if (faceDataList.isEmpty()) {
            runOnUiThread(() -> Toast.makeText(this, "No new face data to convert.", Toast.LENGTH_SHORT).show());
            return;
        }

        try {
            FileOutputStream dataFos = new FileOutputStream(new File(getExternalFilesDir(null), "face_data.data"));
            FileOutputStream lblFos = new FileOutputStream(new File(getExternalFilesDir(null), "labels.data"));
            ObjectOutputStream dataOos = new ObjectOutputStream(dataFos);
            ObjectOutputStream lblOos = new ObjectOutputStream(lblFos);

            for (float[] face : faceDataList) dataOos.writeObject(face);
            for (Integer lbl : labelList) lblOos.writeInt(lbl);

            dataOos.close();
            lblOos.close();

            File labelMapFile = new File(getExternalFilesDir(null), "label.txt");
            FileWriter writer = new FileWriter(labelMapFile);
            for (Map.Entry<Integer, String> entry : labelNameMap.entrySet()) {
                writer.write(entry.getKey() + " " + entry.getValue() + "\n");
            }
            writer.close();

            // Save updated cache
            FileWriter cacheWriter = new FileWriter(cacheFile);
            for (Map.Entry<String, Integer> entry : newCache.entrySet()) {
                cacheWriter.write(entry.getKey() + " " + entry.getValue() + "\n");
            }
            cacheWriter.close();

            loadLabelMap();
            runOnUiThread(() -> {
                Toast.makeText(this, "✅ Data conversion complete!", Toast.LENGTH_SHORT).show();
                ProgressBar progressBar = findViewById(R.id.progressBar);
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            runOnUiThread(() -> {
                Toast.makeText(this, "❌ Failed to save data!", Toast.LENGTH_SHORT).show();
                ProgressBar progressBar = findViewById(R.id.progressBar);
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
            });
        }
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }
    private float[] extractRawFaceVector(Bitmap bitmap) {
        try {
            Mat img = new Mat();
            Utils.bitmapToMat(bitmap, img);
            if (img.empty()) return null;

            Imgproc.cvtColor(img, img, Imgproc.COLOR_BGR2GRAY);
            Imgproc.resize(img, img, new Size(100, 100));

            float[] vector = new float[100 * 100];
            for (int y = 0; y < 100; y++) {
                for (int x = 0; x < 100; x++) {
                    double[] pixel = img.get(y, x);
                    vector[y * 100 + x] = (float) (pixel[0] / 255.0);
                }
            }
            return vector;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    private int findBestMatch(float[] testFace) {
        try {
            FileInputStream dataFis = new FileInputStream(new File(getExternalFilesDir(null), "face_data.data"));
            FileInputStream labelFis = new FileInputStream(new File(getExternalFilesDir(null), "labels.data"));
            ObjectInputStream dataOis = new ObjectInputStream(dataFis);
            ObjectInputStream labelOis = new ObjectInputStream(labelFis);

            List<float[]> storedFaces = new ArrayList<>();
            List<Integer> storedLabels = new ArrayList<>();

            while (true) {
                try {
                    float[] face = (float[]) dataOis.readObject();
                    storedFaces.add(face);
                } catch (EOFException e) {
                    break;
                }
            }
            while (true) {
                try {
                    storedLabels.add(labelOis.readInt());
                } catch (EOFException e) {
                    break;
                }
            }
            dataOis.close();
            labelOis.close();
            double bestScore = -1;
            int bestLabel = -1;

            for (int i = 0; i < storedFaces.size(); i++) {
                float[] known = storedFaces.get(i);
                double sim = cosineSimilarity(testFace, known);
                if (sim > bestScore) {
                    bestScore = sim;
                    bestLabel = storedLabels.get(i);
                }
            }
            return bestScore > 0.85 ? bestLabel : -1;  // Adjust threshold as needed
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }
    private void loadLabelMap() {
        labelMap = new HashMap<>();
        File labelFile = new File(getExternalFilesDir(null), "label.txt");
        try (BufferedReader br = new BufferedReader(new FileReader(labelFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(" ");
                if (parts.length == 2) {
                    int id = Integer.parseInt(parts[0]);
                    labelMap.put(id, parts[1]);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
    public static class FaceOverlayView extends View {
        private Paint boxPaint = new Paint();
        private Paint textPaint = new Paint();
        private Rect face = null;
        private String name = null;

        public FaceOverlayView(Context context, AttributeSet attrs) {
            super(context, attrs);

            boxPaint.setColor(Color.GREEN);
            boxPaint.setStyle(Paint.Style.STROKE);
            boxPaint.setStrokeWidth(5f);

            textPaint.setColor(Color.GREEN);
            textPaint.setTextSize(40f);
            textPaint.setStyle(Paint.Style.FILL);
        }

        // Accept single face and name only
        public void setFaces(Rect[] faces, String[] names) {
            if (faces != null && faces.length > 0 && names != null && names.length > 0) {
                this.face = faces[0];
                this.name = names[0];
            } else {
                this.face = null;
                this.name = null;
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (face != null && name != null) {
                canvas.drawRect(face, boxPaint);
                canvas.drawText(name, face.left, face.bottom + 40, textPaint);
            }
            // 🧹 Removed dummy "Align Face" box
        }
    }
    public boolean isRecognitionActive() {
        return isRecognitionActive;
    }
    private String recognizeFace(Mat faceMat) {
        try {
            Imgproc.resize(faceMat, faceMat, new Size(100, 100));

            float[] testVector = new float[100 * 100];
            for (int y = 0; y < 100; y++) {
                for (int x = 0; x < 100; x++) {
                    double[] pixel = faceMat.get(y, x);
                    testVector[y * 100 + x] = (float) (pixel[0] / 255.0);
                }
            }

            int label = findBestMatch(testVector);
            if (label != -1 && labelMap != null && labelMap.containsKey(label)) {
                return labelMap.get(label);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Unknown";
    }
    private Executor getExecutor() {
        return ContextCompat.getMainExecutor(this);
    }
}
