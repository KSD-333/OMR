package com.mk.omrscanner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.media.MediaActionSound;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.TextView;
import android.widget.Toast;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Live camera scanning activity for OMR sheets.
 * Full-frame edge detection using OpenCV contour analysis.
 */
public class CameraScanActivity extends AppCompatActivity {

    private static final String TAG = "CameraScanActivity";
    private static final int PERMISSION_REQUEST_CAMERA = 1001;

    // Stabilization: require document corners to be stable for ~0.5s (4 frames at 8fps)
    private static final int STABILITY_FRAMES_REQUIRED = 4;
    private static final float STABILITY_THRESHOLD_PX = 20.0f; // allow slight handshake

    private PreviewView viewFinder;
    private DocumentOverlayView documentOverlay;
    private TextView txtScanStatus;
    private ImageCapture imageCapture;

    private boolean isCaptured = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService cameraExecutor;

    // Stabilization tracking
    private float[][] lastDocumentCorners = null;
    private int stableFrameCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_scan);

        OpenCVHelper.initOpenCV();

        viewFinder = findViewById(R.id.viewFinder);
        documentOverlay = findViewById(R.id.documentOverlay);
        txtScanStatus = findViewById(R.id.txtScanStatus);

        cameraExecutor = Executors.newSingleThreadExecutor();

        findViewById(R.id.btnCameraClose).setOnClickListener(v -> finish());
        startLaserAnimation();

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CAMERA);
        }
    }

    private void startLaserAnimation() {
        View laser = findViewById(R.id.cameraLaserLine);
        TranslateAnimation animation = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, -0.45f,
                Animation.RELATIVE_TO_PARENT, 0.45f
        );
        animation.setDuration(2200);
        animation.setRepeatCount(Animation.INFINITE);
        animation.setRepeatMode(Animation.REVERSE);
        laser.startAnimation(animation);
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize camera", e);
                Toast.makeText(this, "Camera init failed", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void bindCameraUseCases(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build();

        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis, imageCapture);
        } catch (Exception e) {
            Log.e(TAG, "Failed to bind camera use cases", e);
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void analyzeFrame(ImageProxy imageProxy) {
        try {
            if (isCaptured || !OpenCVHelper.isInitialized()) {
                imageProxy.close();
                return;
            }

            Bitmap bitmap = null;
            try {
                bitmap = imageProxy.toBitmap();
            } catch (Exception e) {
                bitmap = imageProxyToBitmapSafe(imageProxy);
            }

            if (bitmap == null) {
                imageProxy.close();
                return;
            }

            int rotation = imageProxy.getImageInfo().getRotationDegrees();
            Bitmap rotated = rotateBitmap(bitmap, rotation);

            // 1. Detect the 4 printed markers directly (robust industry standard)
            float[][] markerCorners = OpenCVHelper.findPrintedMarkers(rotated);

            if (markerCorners != null) {
                // Map the markers from Bitmap to View space for the green overlay
                float[][] uiCorners = mapToUI(markerCorners, rotated.getWidth(), rotated.getHeight());
                
                mainHandler.post(() -> {
                    if (!isCaptured) {
                        documentOverlay.setDocumentCorners(uiCorners);
                        documentOverlay.setScanningColor();
                        txtScanStatus.setText("🎯 4 Markers found — hold steady!");
                        txtScanStatus.setTextColor(Color.parseColor("#60A5FA"));
                    }
                });

                // Check stability
                if (lastDocumentCorners != null && isStable(markerCorners, lastDocumentCorners)) {
                    stableFrameCount++;
                } else {
                    stableFrameCount = 1;
                }
                lastDocumentCorners = markerCorners;

                if (stableFrameCount >= STABILITY_FRAMES_REQUIRED && !isCaptured) {
                    isCaptured = true;
                    
                    mainHandler.post(() -> {
                        documentOverlay.setSuccessColor();
                        updateStatusCaptured();
                        captureHighResSheet();
                    });
                }

            } else {
                // No markers found
                stableFrameCount = 0;
                lastDocumentCorners = null;
                mainHandler.post(() -> {
                    if (!isCaptured) {
                        documentOverlay.clearOverlay();
                        txtScanStatus.setText("📋 Looking for 4 corner markers...");
                        txtScanStatus.setTextColor(Color.parseColor("#F4F4F5"));
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Frame analysis error", e);
        } finally {
            imageProxy.close();
        }
    }

    /**
     * Maps coordinates from the camera image to the PreviewView UI surface
     * assuming scaleType="fillCenter".
     */
    private float[][] mapToUI(float[][] imageCorners, int imageWidth, int imageHeight) {
        int viewWidth = viewFinder.getWidth();
        int viewHeight = viewFinder.getHeight();

        if (viewWidth == 0 || viewHeight == 0) return imageCorners; // UI not ready

        float scaleX = (float) viewWidth / imageWidth;
        float scaleY = (float) viewHeight / imageHeight;
        float scale = Math.max(scaleX, scaleY);

        float scaledWidth = scale * imageWidth;
        float scaledHeight = scale * imageHeight;

        float leftOffset = (viewWidth - scaledWidth) / 2f;
        float topOffset = (viewHeight - scaledHeight) / 2f;

        float[][] uiCorners = new float[4][2];
        for (int i = 0; i < 4; i++) {
            uiCorners[i][0] = (imageCorners[i][0] * scale) + leftOffset;
            uiCorners[i][1] = (imageCorners[i][1] * scale) + topOffset;
        }

        return uiCorners;
    }

    private boolean isStable(float[][] current, float[][] previous) {
        for (int i = 0; i < 4; i++) {
            float dx = Math.abs(current[i][0] - previous[i][0]);
            float dy = Math.abs(current[i][1] - previous[i][1]);
            if (dx > STABILITY_THRESHOLD_PX || dy > STABILITY_THRESHOLD_PX) {
                return false;
            }
        }
        return true;
    }

    private void updateStatusCaptured() {
        txtScanStatus.setText("✅ Captured!");
        txtScanStatus.setTextColor(Color.parseColor("#10B981"));

        AlphaAnimation flash = new AlphaAnimation(1.0f, 0.3f);
        flash.setDuration(150);
        flash.setRepeatCount(1);
        flash.setRepeatMode(Animation.REVERSE);
        viewFinder.startAnimation(flash);
    }

    private Bitmap rotateBitmap(Bitmap bitmap, int degrees) {
        if (degrees == 0) return bitmap;
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private Bitmap imageProxyToBitmapSafe(ImageProxy imageProxy) {
        @SuppressLint("UnsafeOptInUsageError")
        Image image = imageProxy.getImage();
        if (image == null) return null;

        try {
            Image.Plane[] planes = image.getPlanes();
            int width = image.getWidth();
            int height = image.getHeight();

            ByteBuffer yBuffer = planes[0].getBuffer();
            int yRowStride = planes[0].getRowStride();
            int yPixelStride = planes[0].getPixelStride();

            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();
            int uvRowStride = planes[1].getRowStride();
            int uvPixelStride = planes[1].getPixelStride();

            byte[] nv21 = new byte[width * height * 3 / 2];

            int pos = 0;
            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    nv21[pos++] = yBuffer.get(row * yRowStride + col * yPixelStride);
                }
            }

            int uvHeight = height / 2;
            int uvWidth = width / 2;
            for (int row = 0; row < uvHeight; row++) {
                for (int col = 0; col < uvWidth; col++) {
                    int uvIndex = row * uvRowStride + col * uvPixelStride;
                    nv21[pos++] = vBuffer.get(uvIndex); 
                    nv21[pos++] = uBuffer.get(uvIndex); 
                }
            }

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, width, height), 90, out);
            byte[] imageBytes = out.toByteArray();

            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        } catch (Exception e) {
            Log.e(TAG, "YUV to Bitmap conversion failed", e);
            return null;
        }
    }

    private void captureHighResSheet() {
        if (imageCapture == null) return;
        
        try {
            MediaActionSound sound = new MediaActionSound();
            sound.play(MediaActionSound.SHUTTER_CLICK);
        } catch (Exception e) {}

        try {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        } catch (Exception e) {}

        File photoFile = new File(getExternalFilesDir(null),
                "student_sheet_" + System.currentTimeMillis() + ".jpg");

        ImageCapture.OutputFileOptions outputOptions = 
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), 
                new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                Uri uri = androidx.core.content.FileProvider.getUriForFile(
                        CameraScanActivity.this, getPackageName() + ".fileprovider", photoFile);
                
                mainHandler.postDelayed(() -> {
                    Intent data = new Intent();
                    data.putExtra("image_uri", uri.toString());
                    setResult(RESULT_OK, data);
                    finish();
                }, 500); // Slight delay to show green overlay
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "ImageCapture failed", exception);
                isCaptured = false;
                stableFrameCount = 0;
                lastDocumentCorners = null;
                Toast.makeText(CameraScanActivity.this, "Failed to capture high-res image", Toast.LENGTH_SHORT).show();
                txtScanStatus.setText("❌ Capture failed — try again");
                txtScanStatus.setTextColor(Color.parseColor("#EF4444"));
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
}
