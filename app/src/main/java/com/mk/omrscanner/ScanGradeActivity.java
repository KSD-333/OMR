package com.mk.omrscanner;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;
import android.app.Dialog;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.util.Random;
import java.util.ArrayList;
import java.text.DecimalFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanGradeActivity extends AppCompatActivity {

    private EditText inputExamName;
    private TextView txtExamNameError;

    // Answer Key views
    private LinearLayout layoutWarningNoKeys;
    private AppCompatButton btnCreateKey;
    private LinearLayout layoutSelectedKeyCard;
    private TextView btnChangeKey;
    private TextView txtKeyCardTitle;
    private TextView txtKeyCardDetails;

    // Sheet Config Chips
    private TextView chipQ50, chipQ100;
    private TextView txtScannerLookFor;

    private static final int REQUEST_CAMERA_PERMISSION = 101;
    private static final int REQUEST_CAMERA_CAPTURE = 2001;
    private static final int REQUEST_GALLERY_PICK = 2002;
    private android.net.Uri cameraImageUri;

    private int selectedQuestions = 100;
    private int selectedColumns = 4;
    private boolean isAnswerKeyCreated = false;

    // Scan Mode section views
    private LinearLayout layoutScanModeSection;
    private LinearLayout cardLiveCamera, cardBatchUpload;
    private ImageView imgLiveCameraIcon, imgBatchUploadIcon;
    private TextView txtLiveCameraTitle, txtLiveCameraSubtitle;
    private TextView txtBatchUploadTitle, txtBatchUploadSubtitle;
    private AppCompatButton btnStartScan;

    private boolean isLiveCameraSelected = true; // default scan mode

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (cameraImageUri != null) {
            outState.putString("camera_image_uri", cameraImageUri.toString());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_grade);

        if (savedInstanceState != null) {
            String uriString = savedInstanceState.getString("camera_image_uri");
            if (uriString != null) {
                cameraImageUri = Uri.parse(uriString);
            }
        }

        // Bind Exam Name & error views
        inputExamName = findViewById(R.id.inputExamName);
        txtExamNameError = findViewById(R.id.txtExamNameError);

        // Bind Answer Key views
        layoutWarningNoKeys = findViewById(R.id.layoutWarningNoKeys);
        btnCreateKey = findViewById(R.id.btnCreateKey);
        layoutSelectedKeyCard = findViewById(R.id.layoutSelectedKeyCard);
        btnChangeKey = findViewById(R.id.btnChangeKey);
        txtKeyCardTitle = findViewById(R.id.txtKeyCardTitle);
        txtKeyCardDetails = findViewById(R.id.txtKeyCardDetails);

        // Bind Config Chips
        chipQ50 = findViewById(R.id.chipQ50);
        chipQ100 = findViewById(R.id.chipQ100);
        txtScannerLookFor = findViewById(R.id.txtScannerLookFor);

        // Bind Scan Mode Section
        layoutScanModeSection = findViewById(R.id.layoutScanModeSection);
        cardLiveCamera = findViewById(R.id.cardLiveCamera);
        cardBatchUpload = findViewById(R.id.cardBatchUpload);
        imgLiveCameraIcon = findViewById(R.id.imgLiveCameraIcon);
        imgBatchUploadIcon = findViewById(R.id.imgBatchUploadIcon);
        txtLiveCameraTitle = findViewById(R.id.txtLiveCameraTitle);
        txtLiveCameraSubtitle = findViewById(R.id.txtLiveCameraSubtitle);
        txtBatchUploadTitle = findViewById(R.id.txtBatchUploadTitle);
        txtBatchUploadSubtitle = findViewById(R.id.txtBatchUploadSubtitle);
        btnStartScan = findViewById(R.id.btnStartScan);

        // Setup Text Watcher on Exam Name
        inputExamName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    txtExamNameError.setVisibility(View.GONE);
                    inputExamName.setBackgroundResource(R.drawable.bg_input_field);
                } else {
                    txtExamNameError.setVisibility(View.VISIBLE);
                    inputExamName.setBackgroundResource(R.drawable.bg_input_field_error);
                }
                updateStartScanButtonState();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Setup Chip Listeners
        chipQ50.setOnClickListener(v -> selectQuestionChip(50));
        chipQ100.setOnClickListener(v -> selectQuestionChip(100));

        // Default set selection chip (100 Qs selected, columns locked to 4)
        selectQuestionChip(100);

        // Open selection dialog to choose/create key
        btnCreateKey.setOnClickListener(v -> showSelectKeyDialog());
        btnChangeKey.setOnClickListener(v -> showSelectKeyDialog());

        // Scan Mode Selection Click Listeners
        cardLiveCamera.setOnClickListener(v -> selectScanMode(true));
        cardBatchUpload.setOnClickListener(v -> selectScanMode(false));

        // Start scanning overlay trigger
        btnStartScan.setOnClickListener(v -> {
            if (isAnswerKeyCreated && !TextUtils.isEmpty(inputExamName.getText().toString().trim())) {
                if (getActiveKeyForSelectedSheetFormat() == null) {
                    return;
                }
                if (isLiveCameraSelected) {
                    checkCameraPermissionAndStart();
                } else {
                    openGalleryPicker();
                }
            }
        });

        // Setup Bottom Navigation Bar click listeners
        findViewById(R.id.navHome).setOnClickListener(v -> {
            Intent intent = new Intent(this, DashboardActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.navGenerator).setOnClickListener(v -> {
            Intent intent = new Intent(this, ConfigureSheetActivity.class);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.navKeys).setOnClickListener(v -> showSelectKeyDialog());

        findViewById(R.id.navResults).setOnClickListener(v -> showResultsDialog());

        loadSelectedKeyOnStart();
    }

    // ── Background processing executor (single thread — serialises scans) ────────────────
    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** Shows a compact loading spinner while heavy OMR work runs on a background thread. */
    private android.app.ProgressDialog showScanningSpinner() {
        android.app.ProgressDialog pd = new android.app.ProgressDialog(this);
        pd.setMessage("⚡ Scanning OMR sheet…");
        pd.setProgressStyle(android.app.ProgressDialog.STYLE_SPINNER);
        pd.setCancelable(false);
        pd.show();
        return pd;
    }

    private void processAndShowVerification(Uri imageUri) {
        AnswerKeyManager.AnswerKey activeKey = getActiveKeyForSelectedSheetFormat();
        if (activeKey == null) return;

        OpenCVHelper.initOpenCV();

        android.app.ProgressDialog spinner = showScanningSpinner();

        scanExecutor.execute(() -> {
            try {
                android.graphics.Bitmap studentBitmap = loadScaledBitmap(imageUri);
                if (studentBitmap == null) {
                    mainHandler.post(() -> {
                        spinner.dismiss();
                        Toast.makeText(this, "Error: Failed to load captured image!", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                // 1. Try OpenCV printed-marker detection (fastest & most robust)
                float[][] contourCorners = null;
                if (OpenCVHelper.isInitialized()) {
                    contourCorners = OpenCVHelper.findPrintedMarkers(studentBitmap);
                }

                if (contourCorners != null) {
                    // CON #5: Apply rotation correction before perspective transform
                    android.graphics.Bitmap rotCorrected =
                            OpenCVHelper.correctRotation(studentBitmap, contourCorners);
                    // Re-detect markers after rotation correction
                    if (rotCorrected != studentBitmap) {
                        float[][] newCorners = OpenCVHelper.findPrintedMarkers(rotCorrected);
                        if (newCorners != null) {
                            contourCorners = newCorners;
                            studentBitmap = rotCorrected;
                        }
                    }
                    android.graphics.Bitmap dewarped =
                            OpenCVHelper.applyPerspectiveTransformToTemplate(studentBitmap, contourCorners);
                    if (dewarped != null) {
                        final android.graphics.Bitmap finalBitmap = dewarped;
                        mainHandler.post(() -> {
                            spinner.dismiss();
                            proceedWithGradingOnMain(imageUri, finalBitmap, activeKey);
                        });
                        return;
                    }
                }

                // 2. Fallback: strict marker detection at native resolution
                //    (CON #10: avoid force-scaling to 595x842 which destroys detail)
                float[][] markers = OMRProcessor.detectMarkersStrict(
                        android.graphics.Bitmap.createScaledBitmap(studentBitmap, 595, 842, true));

                if (markers != null) {
                    android.graphics.Bitmap processedBitmap = studentBitmap;
                    if (OpenCVHelper.isInitialized()) {
                        float scaleBackX = (float) studentBitmap.getWidth() / 595f;
                        float scaleBackY = (float) studentBitmap.getHeight() / 842f;
                        float[][] scaledMarkers = new float[4][2];
                        for (int i = 0; i < 4; i++) {
                            scaledMarkers[i][0] = markers[i][0] * scaleBackX;
                            scaledMarkers[i][1] = markers[i][1] * scaleBackY;
                        }
                        // CON #5: Apply rotation correction for fallback path too
                        android.graphics.Bitmap rotCorrected =
                                OpenCVHelper.correctRotation(studentBitmap, scaledMarkers);
                        if (rotCorrected != studentBitmap) {
                            float[][] newMarkers = OpenCVHelper.findPrintedMarkers(rotCorrected);
                            if (newMarkers != null) scaledMarkers = newMarkers;
                            studentBitmap = rotCorrected;
                        }
                        android.graphics.Bitmap dewarped =
                                OpenCVHelper.applyPerspectiveTransformToTemplate(studentBitmap, scaledMarkers);
                        if (dewarped != null) processedBitmap = dewarped;
                    }
                    final android.graphics.Bitmap finalBitmap = processedBitmap;
                    mainHandler.post(() -> {
                        spinner.dismiss();
                        proceedWithGradingOnMain(imageUri, finalBitmap, activeKey);
                    });
                    return;
                }

                // 3. No markers found — ask user on the main thread
                final android.graphics.Bitmap rawBitmap = studentBitmap;
                mainHandler.post(() -> {
                    spinner.dismiss();
                    new android.app.AlertDialog.Builder(this)
                            .setTitle("OMR Alignment Failed")
                            .setMessage("The scanner could not locate the 4 corner markers on the sheet.\n\nPlease ensure:\n• The page is flat and not curled.\n• All 4 corner black boxes are fully visible.\n• There is good lighting and no glare/harsh shadows.")
                            .setPositiveButton("Try Again", (dialog, which) -> {
                                if (isLiveCameraSelected) openCamera(); else openGalleryPicker();
                            })
                            .setNeutralButton("Grade Anyway", (dialog, which) ->
                                    proceedWithGradingOnMain(imageUri, rawBitmap, activeKey))
                            .setNegativeButton("Cancel", null)
                            .show();
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    spinner.dismiss();
                    Toast.makeText(this, "Scan error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * Called on the main thread after alignment. Kicks off grading on background thread
     * then posts the result dialog back to main thread.
     */
    private void proceedWithGradingOnMain(Uri imageUri, android.graphics.Bitmap studentBitmap,
                                          AnswerKeyManager.AnswerKey activeKey) {
        android.app.ProgressDialog spinner = showScanningSpinner();
        scanExecutor.execute(() -> {
            // CON #9: Apply denoise + sharpen preprocessing before bubble detection
            android.graphics.Bitmap enhancedBitmap = studentBitmap;
            if (OpenCVHelper.isInitialized()) {
                // First: contrast enhancement (CLAHE)
                android.graphics.Bitmap enhanced = OpenCVHelper.enhanceContrast(studentBitmap);
                if (enhanced != null) enhancedBitmap = enhanced;
                // Second: denoise + sharpen for noise removal and edge clarity
                android.graphics.Bitmap denoised = OpenCVHelper.denoiseAndSharpen(enhancedBitmap);
                if (denoised != null) enhancedBitmap = denoised;
            }

            int scanQuestions = AnswerKeyManager.normalizeQuestionsCount(selectedQuestions);
            int scanColumns = AnswerKeyManager.columnsForQuestionCount(scanQuestions);

            // Both bubble detection and student-ID scan reuse the same pixel array internally
            List<boolean[]> studentOptionsList =
                    OMRProcessor.detectFilledBubbles(enhancedBitmap, scanQuestions, scanColumns);
            String scannedStudentID = OMRProcessor.scanStudentID(enhancedBitmap, activeKey.idDigits);
            String finalStudentID = scannedStudentID.isEmpty() ? "Blank" : scannedStudentID;

            final android.graphics.Bitmap finalEnhanced = enhancedBitmap;
            mainHandler.post(() -> {
                spinner.dismiss();
                showCrossVerificationDialog(imageUri, finalEnhanced, activeKey,
                        finalStudentID, studentOptionsList);
            });
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == 1001 && data != null) {
                String keyName = data.getStringExtra("key_name");
                int questionsCount = AnswerKeyManager.normalizeQuestionsCount(
                        data.getIntExtra("questions_count", selectedQuestions)
                );
                int columnsLayout = AnswerKeyManager.columnsForQuestionCount(questionsCount);

                isAnswerKeyCreated = true;
                selectedQuestions = questionsCount;
                selectedColumns = columnsLayout;

                // Prefill Exam Name field if empty
                if (TextUtils.isEmpty(inputExamName.getText().toString().trim())) {
                    inputExamName.setText(keyName);
                }

                // Update card label views
                txtKeyCardTitle.setText(keyName);
                txtKeyCardDetails.setText(questionsCount + " Questions · " + columnsLayout + "-Column Layout");

                // Sync scanner layout configuration chips
                selectQuestionChip(questionsCount);

                updateAnswerKeyStates();
                Toast.makeText(this, "Answer Key loaded successfully", Toast.LENGTH_SHORT).show();
            } else if (requestCode == REQUEST_CAMERA_CAPTURE) {
                if (data != null && data.getStringExtra("image_uri") != null) {
                    cameraImageUri = Uri.parse(data.getStringExtra("image_uri"));
                }
                if (cameraImageUri != null) {
                    processAndShowVerification(cameraImageUri);
                }
            } else if (requestCode == REQUEST_GALLERY_PICK) {
                if (data != null && data.getData() != null) {
                    processAndShowVerification(data.getData());
                }
            }
        }
    }

    private void selectQuestionChip(int count) {
        selectedQuestions = AnswerKeyManager.normalizeQuestionsCount(count);
        selectedColumns = AnswerKeyManager.columnsForQuestionCount(selectedQuestions);
        
        int activeBgColor = ContextCompat.getColor(this, R.color.dashboard_card_teal);
        int activeTextColor = ContextCompat.getColor(this, R.color.white);
        int inactiveBgColor = ContextCompat.getColor(this, R.color.bg_dark_card);
        int inactiveTextColor = ContextCompat.getColor(this, R.color.text_primary);

        // Reset chips colors
        chipQ50.setBackgroundTintList(ColorStateList.valueOf(inactiveBgColor));
        chipQ50.setTextColor(inactiveTextColor);
        chipQ100.setBackgroundTintList(ColorStateList.valueOf(inactiveBgColor));
        chipQ100.setTextColor(inactiveTextColor);

        if (selectedQuestions == 50) {
            chipQ50.setBackgroundTintList(ColorStateList.valueOf(activeBgColor));
            chipQ50.setTextColor(activeTextColor);
        } else {
            chipQ100.setBackgroundTintList(ColorStateList.valueOf(activeBgColor));
            chipQ100.setTextColor(activeTextColor);
        }

        updateLookForBanner();
    }

    private void updateLookForBanner() {
        if (txtScannerLookFor != null) {
            txtScannerLookFor.setText("Scanner will look for: " + selectedQuestions + " questions, " + selectedColumns + "-column layout");
        }
    }

    private AnswerKeyManager.AnswerKey getActiveKeyForSelectedSheetFormat() {
        AnswerKeyManager.AnswerKey activeKey = AnswerKeyManager.getSelectedKey(this);
        if (activeKey == null) {
            Toast.makeText(this, "Error: No active Answer Key loaded!", Toast.LENGTH_LONG).show();
            return null;
        }

        selectedQuestions = AnswerKeyManager.normalizeQuestionsCount(selectedQuestions);
        selectedColumns = AnswerKeyManager.columnsForQuestionCount(selectedQuestions);

        int keyQuestions = AnswerKeyManager.normalizeQuestionsCount(activeKey.questionsCount);
        int keyColumns = AnswerKeyManager.columnsForQuestionCount(keyQuestions);
        activeKey.questionsCount = keyQuestions;
        activeKey.columnsLayout = keyColumns;

        if (keyQuestions != selectedQuestions || keyColumns != selectedColumns) {
            Toast.makeText(
                    this,
                    "Sheet format is " + selectedQuestions + " questions / " + selectedColumns
                            + " columns, but the selected key is " + keyQuestions
                            + " questions / " + keyColumns + " columns. Choose or create a matching key.",
                    Toast.LENGTH_LONG
            ).show();
            return null;
        }

        return activeKey;
    }

    private void updateAnswerKeyStates() {
        if (isAnswerKeyCreated) {
            layoutWarningNoKeys.setVisibility(View.GONE);
            btnCreateKey.setVisibility(View.GONE);
            layoutSelectedKeyCard.setVisibility(View.VISIBLE);
            layoutScanModeSection.setVisibility(View.VISIBLE);
        } else {
            layoutWarningNoKeys.setVisibility(View.VISIBLE);
            btnCreateKey.setVisibility(View.VISIBLE);
            layoutSelectedKeyCard.setVisibility(View.GONE);
            layoutScanModeSection.setVisibility(View.GONE);
        }
        updateStartScanButtonState();
    }

    private void selectScanMode(boolean isLive) {
        isLiveCameraSelected = isLive;
        if (isLive) {
            cardLiveCamera.setBackgroundResource(R.drawable.bg_selected_scan_card);
            imgLiveCameraIcon.setImageTintList(ColorStateList.valueOf(Color.parseColor("#3B82F6")));
            txtLiveCameraTitle.setTextColor(Color.WHITE);
            txtLiveCameraSubtitle.setTextColor(Color.parseColor("#60A5FA"));

            cardBatchUpload.setBackgroundResource(R.drawable.bg_unselected_scan_card);
            imgBatchUploadIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.text_secondary)));
            txtBatchUploadTitle.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            txtBatchUploadSubtitle.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));

            btnStartScan.setText("📷   Start Camera Scan");
        } else {
            cardBatchUpload.setBackgroundResource(R.drawable.bg_selected_scan_card);
            imgBatchUploadIcon.setImageTintList(ColorStateList.valueOf(Color.parseColor("#3B82F6")));
            txtBatchUploadTitle.setTextColor(Color.WHITE);
            txtBatchUploadSubtitle.setTextColor(Color.parseColor("#60A5FA"));

            cardLiveCamera.setBackgroundResource(R.drawable.bg_unselected_scan_card);
            imgLiveCameraIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.text_secondary)));
            txtLiveCameraTitle.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            txtLiveCameraSubtitle.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));

            btnStartScan.setText("📁   Select Images to Scan");
        }
        updateStartScanButtonState();
    }

    private void updateStartScanButtonState() {
        boolean isNameFilled = !TextUtils.isEmpty(inputExamName.getText().toString().trim());
        boolean isReady = isAnswerKeyCreated && isNameFilled;

        if (isReady) {
            btnStartScan.setEnabled(true);
            btnStartScan.setBackgroundResource(R.drawable.bg_pill_button);
            btnStartScan.setTextColor(Color.WHITE);
        } else {
            btnStartScan.setEnabled(false);
            btnStartScan.setBackgroundResource(R.drawable.bg_disabled_button);
            btnStartScan.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (getIntent().getBooleanExtra("show_results", false)) {
            getIntent().removeExtra("show_results");
            showResultsDialog();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        scanExecutor.shutdownNow();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Camera permission is required to scan OMR sheets", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void checkCameraPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            openCamera();
        }
    }

    private void openCamera() {
        try {
            Intent intent = new Intent(this, CameraScanActivity.class);
            startActivityForResult(intent, REQUEST_CAMERA_CAPTURE);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to open scanner camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openGalleryPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(Intent.createChooser(intent, "Select Student OMR Image"), REQUEST_GALLERY_PICK);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to open gallery: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private android.graphics.Bitmap loadScaledBitmap(Uri uri) {
        try {
            java.io.InputStream input = getContentResolver().openInputStream(uri);
            android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeStream(input, null, options);
            if (input != null) input.close();

            int srcWidth = options.outWidth;
            int srcHeight = options.outHeight;
            int reqWidth = 1600;
            int reqHeight = 1600;
            int inSampleSize = 1;

            if (srcHeight > reqHeight || srcWidth > reqWidth) {
                final int halfHeight = srcHeight / 2;
                final int halfWidth = srcWidth / 2;
                while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2;
                }
            }

            options.inJustDecodeBounds = false;
            options.inSampleSize = inSampleSize;

            input = getContentResolver().openInputStream(uri);
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(input, null, options);
            if (input != null) input.close();

            // Correct EXIF Rotation
            input = getContentResolver().openInputStream(uri);
            if (input != null) {
                android.media.ExifInterface exif = new android.media.ExifInterface(input);
                int orientation = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL);
                int degrees = 0;
                switch (orientation) {
                    case android.media.ExifInterface.ORIENTATION_ROTATE_90:
                        degrees = 90;
                        break;
                    case android.media.ExifInterface.ORIENTATION_ROTATE_180:
                        degrees = 180;
                        break;
                    case android.media.ExifInterface.ORIENTATION_ROTATE_270:
                        degrees = 270;
                        break;
                }
                if (degrees != 0) {
                    android.graphics.Matrix matrix = new android.graphics.Matrix();
                    matrix.postRotate(degrees);
                    bitmap = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                }
                input.close();
            }

            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void showSimulatedScannerOverlay(Uri imageUri) {
        // Build simulated camera scanner screen dialog overlay
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_simulated_scanner, null);
        final android.app.Dialog scannerDialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        scannerDialog.setContentView(dialogView);

        TextView txtScannerOverlayTitle = dialogView.findViewById(R.id.txtScannerOverlayTitle);
        txtScannerOverlayTitle.setText("OMR Scanner - " + inputExamName.getText().toString().trim());

        ImageView imgPreview = dialogView.findViewById(R.id.imgStudentSheetPreview);
        final View laserLine = dialogView.findViewById(R.id.scanLaserLine);
        final LinearLayout layoutScanSuccessCard = dialogView.findViewById(R.id.layoutScanSuccessCard);
        final LinearLayout layoutProgress = dialogView.findViewById(R.id.layoutScanningProgress);
        final TextView txtStatus = dialogView.findViewById(R.id.txtScanningStatus);

        if (imgPreview != null) {
            try {
                android.graphics.Bitmap bitmap = loadScaledBitmap(imageUri);
                if (bitmap != null) {
                    imgPreview.setImageBitmap(bitmap);
                } else {
                    imgPreview.setImageURI(imageUri);
                }
            } catch (Exception e) {
                e.printStackTrace();
                imgPreview.setImageURI(imageUri);
            }
        }

        // Retrieve active key
        final AnswerKeyManager.AnswerKey activeKey = getActiveKeyForSelectedSheetFormat();
        if (activeKey == null) {
            scannerDialog.dismiss();
            return;
        }

        // Perform mock image recognition steps
        new Handler().postDelayed(() -> {
            if (txtStatus != null) txtStatus.setText("Aligning sheet corner anchors...");
        }, 800);

        new Handler().postDelayed(() -> {
            if (txtStatus != null) txtStatus.setText("Reading timing tracks and bubbles...");
        }, 1600);

        // Generate deterministic seed based on image uri
        long seed = imageUri.toString().hashCode();
        try {
            File file = new File(imageUri.getPath());
            if (file.exists()) {
                seed += file.lastModified() + file.length();
            } else {
                android.database.Cursor cursor = getContentResolver().query(imageUri, null, null, null, null);
                if (cursor != null) {
                    int sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
                    if (sizeIndex != -1 && cursor.moveToFirst()) {
                        seed += cursor.getLong(sizeIndex);
                    }
                    cursor.close();
                }
            }
        } catch (Exception e) {}

        final android.graphics.Bitmap studentBitmap = loadScaledBitmap(imageUri);
        
        final String scannedStudentID = OMRProcessor.scanStudentID(studentBitmap, activeKey.idDigits);
        final String finalStudentID = scannedStudentID.isEmpty() ? "Blank" : scannedStudentID;

        // Use OMRProcessor to detect actual marks from the image
        int scanQuestions = AnswerKeyManager.normalizeQuestionsCount(selectedQuestions);
        int scanColumns = AnswerKeyManager.columnsForQuestionCount(scanQuestions);

        final List<boolean[]> studentOptionsList = OMRProcessor.detectFilledBubbles(
                studentBitmap, 
                scanQuestions, 
                scanColumns
        );

        // Dismiss simulated camera laser overlay and show verification comparison screen
        new Handler().postDelayed(() -> {
            scannerDialog.dismiss();
            showCrossVerificationDialog(imageUri, studentBitmap, activeKey, finalStudentID, studentOptionsList);
        }, 2500);

        scannerDialog.show();
    }

    private void showCrossVerificationDialog(Uri imageUri, android.graphics.Bitmap studentBitmap, AnswerKeyManager.AnswerKey activeKey, String studentID, List<boolean[]> studentOptionsList) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_grade_verification, null);
        final android.app.Dialog verifyDialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        verifyDialog.setContentView(dialogView);

        TextView txtStudentID = dialogView.findViewById(R.id.txtVerifyStudentID);
        ImageView imgPreview = dialogView.findViewById(R.id.imgVerifySheetPreview);
        TextView txtStats = dialogView.findViewById(R.id.txtVerifyStats);
        TextView txtScore = dialogView.findViewById(R.id.txtVerifyScore);
        AppCompatButton btnDiscard = dialogView.findViewById(R.id.btnVerifyDiscard);
        AppCompatButton btnSave = dialogView.findViewById(R.id.btnVerifySave);

        txtStudentID.setText("Student ID: " + studentID);

        // Grade calculation
        int totalQuestions = AnswerKeyManager.normalizeQuestionsCount(activeKey.questionsCount);
        int columnsLayout = AnswerKeyManager.columnsForQuestionCount(totalQuestions);
        int correctCount = 0;
        int incorrectCount = 0;
        int blankCount = 0;
        int multiMarkCount = 0;
        double earnedPoints = 0.0;
        double maxPoints = 0.0;

        // Parse key answers & points
        org.json.JSONArray answersArray = null;
        org.json.JSONArray pointsArray = null;
        List<boolean[]> correctOptionsList = new ArrayList<>();
        try {
            answersArray = new org.json.JSONArray(activeKey.answersJson);
            pointsArray = new org.json.JSONArray(activeKey.pointsJson);
            for (int i = 0; i < totalQuestions; i++) {
                boolean[] correctOptions = new boolean[4];
                if (answersArray != null && i < answersArray.length()) {
                    org.json.JSONArray row = answersArray.getJSONArray(i);
                    for (int b = 0; b < 4; b++) {
                        correctOptions[b] = row.getBoolean(b);
                    }
                }
                correctOptionsList.add(correctOptions);
            }
        } catch (Exception e) {}

        if (imgPreview != null) {
            android.graphics.Bitmap gradedImage = null;
            if (studentBitmap != null) {
                gradedImage = OMRProcessor.generateGradedImage(studentBitmap, correctOptionsList, studentOptionsList, columnsLayout);
            }
            if (gradedImage != null) {
                imgPreview.setImageBitmap(gradedImage);
            } else if (studentBitmap != null) {
                imgPreview.setImageBitmap(studentBitmap);
            } else {
                imgPreview.setImageURI(imageUri);
            }
        }

        double incorrectPenaltyVal = 0.0;
        double multiMarkPenaltyVal = 0.0;
        try {
            incorrectPenaltyVal = Math.abs(Double.parseDouble(activeKey.incorrectPenalty));
        } catch (Exception e) {}
        try {
            multiMarkPenaltyVal = Math.abs(Double.parseDouble(activeKey.multiMarkPenalty));
        } catch (Exception e) {}

        for (int i = 0; i < totalQuestions; i++) {
            double qPoints = 1.0;
            // Force 1 mark per question as requested, ignoring pointsArray
            maxPoints += qPoints;

            // Get correct options from key
            boolean[] correctOptions = new boolean[4];
            if (i < correctOptionsList.size()) {
                correctOptions = correctOptionsList.get(i);
            }

            boolean[] studentOptions = i < studentOptionsList.size() ? studentOptionsList.get(i) : new boolean[4];

            // Grade studentOptions against correctOptions
            boolean isCorrect = true;
            for (int b = 0; b < 4; b++) {
                if (studentOptions[b] != correctOptions[b]) {
                    isCorrect = false;
                    break;
                }
            }

            int studentSelectedCount = 0;
            for (int b = 0; b < 4; b++) {
                if (studentOptions[b]) studentSelectedCount++;
            }

            int status; // 0 = correct, 1 = incorrect, 2 = blank, 3 = multi-mark
            if (isCorrect) {
                correctCount++;
                earnedPoints += qPoints;
                status = 0;
            } else {
                if (studentSelectedCount == 0) {
                    blankCount++;
                    // Blanks are NOT penalized — they are simply unanswered
                    status = 2;
                } else if (studentSelectedCount > 1 && !activeKey.multiCorrectActive) {
                    multiMarkCount++;
                    // earnedPoints -= multiMarkPenaltyVal; // Removed negative marking
                    status = 3;
                } else {
                    incorrectCount++;
                    // earnedPoints -= incorrectPenaltyVal; // Removed negative marking
                    status = 1;
                }
            }
        }
        // Removed artificial zero-flooring for scores so negative marks accurately display

        final double finalScore = earnedPoints;
        final double finalMaxScore = maxPoints;
        final int finalCorrect = correctCount;
        final int finalIncorrect = incorrectCount;
        final int finalBlank = blankCount;
        final int finalMultiMark = multiMarkCount;

        DecimalFormat df = new DecimalFormat("#.##");
        txtScore.setText(df.format(earnedPoints) + " / " + df.format(maxPoints));
        txtStats.setText("Correct: " + correctCount + "  |  Incorrect: " + incorrectCount + "  |  Blank: " + blankCount + "\nMulti-marked: " + multiMarkCount);

        btnDiscard.setOnClickListener(v -> verifyDialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String examName = inputExamName.getText().toString().trim();
            OMRResultsManager.GradedResult result = new OMRResultsManager.GradedResult(
                    String.valueOf(System.currentTimeMillis()),
                    examName,
                    studentID,
                    finalScore,
                    finalMaxScore,
                    finalCorrect,
                    finalIncorrect,
                    finalBlank,
                    finalMultiMark,
                    System.currentTimeMillis()
            );
            OMRResultsManager.saveResult(this, result);
            verifyDialog.dismiss();
            Toast.makeText(this, "Scan graded & saved successfully!", Toast.LENGTH_SHORT).show();
            showResultsDialog();
        });

        verifyDialog.show();
    }

    // Verification Data Model
    private static class VerificationItem {
        int qNum;
        boolean[] correct;
        boolean[] student;
        int status; // 0 = correct, 1 = incorrect, 2 = blank, 3 = multi-mark

        VerificationItem(int qNum, boolean[] correct, boolean[] student, int status) {
            this.qNum = qNum;
            this.correct = correct;
            this.student = student;
            this.status = status;
        }

        String getCorrectText() {
            return formatAnswers(correct);
        }

        String getStudentText() {
            if (status == 2) return "None";
            return formatAnswers(student);
        }

        private String formatAnswers(boolean[] arr) {
            StringBuilder sb = new StringBuilder();
            String[] letters = {"A", "B", "C", "D"};
            for (int i = 0; i < 4; i++) {
                if (arr[i]) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(letters[i]);
                }
            }
            return sb.toString();
        }
    }

    // Verification Recycler Adapter
    private class VerificationAdapter extends RecyclerView.Adapter<VerificationAdapter.VerifyViewHolder> {
        private final List<VerificationItem> list;

        VerificationAdapter(List<VerificationItem> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public VerifyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_verification_row, parent, false);
            return new VerifyViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VerifyViewHolder holder, int position) {
            VerificationItem item = list.get(position);
            holder.bind(item);
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class VerifyViewHolder extends RecyclerView.ViewHolder {
            private final TextView txtQNum;
            private final TextView txtKey;
            private final ImageView imgStatus;
            private final TextView txtOptA;
            private final TextView txtOptB;
            private final TextView txtOptC;
            private final TextView txtOptD;

            VerifyViewHolder(@NonNull View itemView) {
                super(itemView);
                txtQNum = itemView.findViewById(R.id.txtVerifyQNum);
                txtKey = itemView.findViewById(R.id.txtVerifyKeyAns);
                imgStatus = itemView.findViewById(R.id.imgVerifyStatusIcon);
                txtOptA = itemView.findViewById(R.id.txtVerifyOptA);
                txtOptB = itemView.findViewById(R.id.txtVerifyOptB);
                txtOptC = itemView.findViewById(R.id.txtVerifyOptC);
                txtOptD = itemView.findViewById(R.id.txtVerifyOptD);
            }

            void bind(VerificationItem item) {
                txtQNum.setText("Q" + item.qNum);
                txtKey.setText(item.getCorrectText());

                TextView[] opts = {txtOptA, txtOptB, txtOptC, txtOptD};
                String[] letters = {"A", "B", "C", "D"};

                for (int b = 0; b < 4; b++) {
                    TextView txtOpt = opts[b];
                    boolean isStudentSelected = item.student[b];
                    boolean isKeyCorrect = item.correct[b];

                    txtOpt.setText(letters[b]);

                    if (isStudentSelected) {
                        txtOpt.setSelected(true);
                        if (isKeyCorrect) {
                            txtOpt.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#10B981"))); // Green
                            txtOpt.setTextColor(Color.WHITE);
                        } else {
                            txtOpt.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#EF4444"))); // Red
                            txtOpt.setTextColor(Color.WHITE);
                        }
                    } else {
                        txtOpt.setSelected(false);
                        if (isKeyCorrect) {
                            // Highlight the correct option that was missed
                            txtOpt.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#10B981"))); // Green outline
                            txtOpt.setTextColor(Color.parseColor("#10B981"));
                        } else {
                            txtOpt.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#52525B"))); // Gray outline
                            txtOpt.setTextColor(Color.parseColor("#A1A1AA"));
                        }
                    }
                }

                // Style status icon based on overall status
                if (item.status == 0) {
                    imgStatus.setImageResource(R.drawable.ic_check);
                    imgStatus.setImageTintList(ColorStateList.valueOf(Color.parseColor("#10B981"))); // Green
                } else if (item.status == 1) {
                    imgStatus.setImageResource(R.drawable.ic_delete);
                    imgStatus.setImageTintList(ColorStateList.valueOf(Color.parseColor("#EF4444"))); // Red
                } else if (item.status == 2) {
                    imgStatus.setImageResource(R.drawable.ic_sliders);
                    imgStatus.setImageTintList(ColorStateList.valueOf(Color.parseColor("#EAB308"))); // Yellow for not attempted
                } else {
                    imgStatus.setImageResource(R.drawable.ic_chevron_down);
                    imgStatus.setImageTintList(ColorStateList.valueOf(Color.parseColor("#F59E0B"))); // Orange for multi-mark
                }
            }
        }
    }

    private void showResultsDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view_results, null);
        final android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.setContentView(dialogView);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.parseColor("#B309090B")));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        View txtEmpty = dialogView.findViewById(R.id.txtResultsEmpty);
        RecyclerView recyclerResults = dialogView.findViewById(R.id.recyclerResults);
        TextView btnClearAll = dialogView.findViewById(R.id.btnClearAllResults);
        TextView btnClose = dialogView.findViewById(R.id.btnResultsClose);

        recyclerResults.setLayoutManager(new LinearLayoutManager(this));

        List<OMRResultsManager.GradedResult> savedResults = OMRResultsManager.getSavedResults(this);
        GradedResultsAdapter adapter = new GradedResultsAdapter(savedResults, txtEmpty, btnClearAll);
        recyclerResults.setAdapter(adapter);

        if (savedResults.isEmpty()) {
            txtEmpty.setVisibility(View.VISIBLE);
            recyclerResults.setVisibility(View.GONE);
            btnClearAll.setVisibility(View.GONE);
        } else {
            txtEmpty.setVisibility(View.GONE);
            recyclerResults.setVisibility(View.VISIBLE);
            btnClearAll.setVisibility(View.VISIBLE);
        }

        btnClearAll.setOnClickListener(v -> {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Clear All Reports")
                    .setMessage("Are you sure you want to permanently delete all grading reports?")
                    .setPositiveButton("Clear All", (dialogInterface, which) -> {
                        OMRResultsManager.clearAllResults(this);
                        savedResults.clear();
                        adapter.notifyDataSetChanged();
                        txtEmpty.setVisibility(View.VISIBLE);
                        recyclerResults.setVisibility(View.GONE);
                        btnClearAll.setVisibility(View.GONE);
                        Toast.makeText(this, "All reports cleared", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private class GradedResultsAdapter extends RecyclerView.Adapter<GradedResultsAdapter.ResultViewHolder> {
        private final List<OMRResultsManager.GradedResult> list;
        private final View emptyView;
        private final View clearAllView;

        GradedResultsAdapter(List<OMRResultsManager.GradedResult> list, View emptyView, View clearAllView) {
            this.list = list;
            this.emptyView = emptyView;
            this.clearAllView = clearAllView;
        }

        @NonNull
        @Override
        public ResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_graded_result_row, parent, false);
            return new ResultViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ResultViewHolder holder, int position) {
            OMRResultsManager.GradedResult result = list.get(position);
            holder.bind(result);
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class ResultViewHolder extends RecyclerView.ViewHolder {
            private final TextView txtExamName;
            private final TextView txtDate;
            private final TextView txtStudentID;
            private final TextView txtScore;
            private final TextView txtStats;
            private final ImageView btnDelete;

            ResultViewHolder(@NonNull View itemView) {
                super(itemView);
                txtExamName = itemView.findViewById(R.id.txtResultExamName);
                txtDate = itemView.findViewById(R.id.txtResultDate);
                txtStudentID = itemView.findViewById(R.id.txtResultStudentID);
                txtScore = itemView.findViewById(R.id.txtResultScore);
                txtStats = itemView.findViewById(R.id.txtResultStats);
                btnDelete = itemView.findViewById(R.id.btnDeleteResult);
            }

            void bind(OMRResultsManager.GradedResult result) {
                txtExamName.setText(result.examName);
                txtDate.setText(android.text.format.DateFormat.format("MMM dd, yyyy HH:mm", result.timestamp));
                txtStudentID.setText("Student ID: " + result.studentId);
                
                DecimalFormat numFormat = new DecimalFormat("#.##");
                txtScore.setText(numFormat.format(result.score) + " / " + numFormat.format(result.maxScore));
                
                txtStats.setText("Correct: " + result.correct + "  |  Incorrect: " + result.incorrect + "  |  Blank: " + result.blank + "  |  Multi: " + result.multiMark);

                btnDelete.setOnClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos == RecyclerView.NO_ID || pos < 0 || pos >= list.size()) return;

                    OMRResultsManager.deleteResult(ScanGradeActivity.this, result.id);
                    list.remove(pos);
                    notifyItemRemoved(pos);

                    if (list.isEmpty()) {
                        emptyView.setVisibility(View.VISIBLE);
                        clearAllView.setVisibility(View.GONE);
                    }
                    Toast.makeText(itemView.getContext(), "Report deleted", Toast.LENGTH_SHORT).show();
                });
            }
        }
    }

    private void loadSelectedKeyOnStart() {
        AnswerKeyManager.AnswerKey selectedKey = AnswerKeyManager.getSelectedKey(this);
        if (selectedKey != null) {
            isAnswerKeyCreated = true;
            selectedQuestions = AnswerKeyManager.normalizeQuestionsCount(selectedKey.questionsCount);
            selectedColumns = AnswerKeyManager.columnsForQuestionCount(selectedQuestions);

            txtKeyCardTitle.setText(selectedKey.name);
            txtKeyCardDetails.setText(selectedKey.questionsCount + " Questions · " + selectedKey.columnsLayout + "-Column Layout");

            selectQuestionChip(selectedQuestions);
            updateAnswerKeyStates();
        } else {
            isAnswerKeyCreated = false;
            updateAnswerKeyStates();
        }
    }

    private void showSelectKeyDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_select_key, null);
        final Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.setContentView(dialogView);

        // Customize dialog window layout
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.parseColor("#B309090B"))); // Dark dim background
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView txtDialogEmpty = dialogView.findViewById(R.id.txtDialogEmpty);
        RecyclerView recyclerDialogKeys = dialogView.findViewById(R.id.recyclerDialogKeys);
        AppCompatButton btnDialogCreateKey = dialogView.findViewById(R.id.btnDialogCreateKey);
        TextView btnDialogClose = dialogView.findViewById(R.id.btnDialogClose);

        recyclerDialogKeys.setLayoutManager(new LinearLayoutManager(this));

        // Load saved keys
        List<AnswerKeyManager.AnswerKey> savedKeys = AnswerKeyManager.getSavedKeys(this);
        
        SavedKeysAdapter adapter = new SavedKeysAdapter(savedKeys, dialog);
        recyclerDialogKeys.setAdapter(adapter);

        if (savedKeys.isEmpty()) {
            txtDialogEmpty.setVisibility(View.VISIBLE);
            recyclerDialogKeys.setVisibility(View.GONE);
        } else {
            txtDialogEmpty.setVisibility(View.GONE);
            recyclerDialogKeys.setVisibility(View.VISIBLE);
        }

        btnDialogCreateKey.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(ScanGradeActivity.this, EditAnswerKeyActivity.class);
            intent.putExtra("questions_count", selectedQuestions);
            intent.putExtra("columns_layout", selectedColumns);
            startActivityForResult(intent, 1001);
        });

        btnDialogClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    // Recycler Adapter for Saved Keys list inside dialog
    private class SavedKeysAdapter extends RecyclerView.Adapter<SavedKeysAdapter.KeyViewHolder> {
        private final List<AnswerKeyManager.AnswerKey> list;
        private final Dialog dialog;

        SavedKeysAdapter(List<AnswerKeyManager.AnswerKey> list, Dialog dialog) {
            this.list = list;
            this.dialog = dialog;
        }

        @NonNull
        @Override
        public KeyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_saved_key_row, parent, false);
            return new KeyViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull KeyViewHolder holder, int position) {
            AnswerKeyManager.AnswerKey key = list.get(position);
            holder.bind(key);
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class KeyViewHolder extends RecyclerView.ViewHolder {
            private final View cardKeyRow;
            private final ImageView imgRowIcon;
            private final TextView txtRowKeyName;
            private final TextView txtRowKeyDetails;
            private final ImageView imgRowCheck;
            private final TextView btnRowEdit;
            private final ImageView btnRowDelete;

            KeyViewHolder(@NonNull View itemView) {
                super(itemView);
                cardKeyRow = itemView.findViewById(R.id.cardKeyRow);
                imgRowIcon = itemView.findViewById(R.id.imgRowIcon);
                txtRowKeyName = itemView.findViewById(R.id.txtRowKeyName);
                txtRowKeyDetails = itemView.findViewById(R.id.txtRowKeyDetails);
                imgRowCheck = itemView.findViewById(R.id.imgRowCheck);
                btnRowEdit = itemView.findViewById(R.id.btnRowEdit);
                btnRowDelete = itemView.findViewById(R.id.btnRowDelete);
            }

            void bind(AnswerKeyManager.AnswerKey key) {
                txtRowKeyName.setText(key.name);
                txtRowKeyDetails.setText(key.questionsCount + " Questions · " + key.columnsLayout + "-Column Layout");

                String selectedId = AnswerKeyManager.getSelectedKeyId(ScanGradeActivity.this);
                boolean isSelected = key.id.equals(selectedId);

                // Update styling based on selected key state
                if (isSelected) {
                    imgRowCheck.setVisibility(View.VISIBLE);
                    imgRowIcon.setImageResource(R.drawable.ic_check);
                    imgRowIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(itemView.getContext(), R.color.color_success)));
                    if (cardKeyRow instanceof com.google.android.material.card.MaterialCardView) {
                        ((com.google.android.material.card.MaterialCardView) cardKeyRow).setStrokeColor(ContextCompat.getColor(itemView.getContext(), R.color.dashboard_card_teal));
                    }
                } else {
                    imgRowCheck.setVisibility(View.GONE);
                    imgRowIcon.setImageResource(R.drawable.ic_key);
                    imgRowIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(itemView.getContext(), R.color.text_secondary)));
                    if (cardKeyRow instanceof com.google.android.material.card.MaterialCardView) {
                        ((com.google.android.material.card.MaterialCardView) cardKeyRow).setStrokeColor(Color.parseColor("#27272A"));
                    }
                }

                // Row click selects/uses key
                itemView.setOnClickListener(v -> {
                    AnswerKeyManager.setSelectedKeyId(ScanGradeActivity.this, key.id);
                    dialog.dismiss();

                    // Apply to scanner activity states
                    isAnswerKeyCreated = true;
                    selectedQuestions = key.questionsCount;
                    selectedColumns = key.columnsLayout;

                    // Prefill exam name if empty
                    if (TextUtils.isEmpty(inputExamName.getText().toString().trim())) {
                        inputExamName.setText(key.name);
                    }

                    txtKeyCardTitle.setText(key.name);
                    txtKeyCardDetails.setText(key.questionsCount + " Questions · " + key.columnsLayout + "-Column Layout");

                    selectQuestionChip(selectedQuestions);
                    updateAnswerKeyStates();
                });

                // Edit key launches EditAnswerKeyActivity in edit mode
                btnRowEdit.setOnClickListener(v -> {
                    dialog.dismiss();
                    Intent intent = new Intent(ScanGradeActivity.this, EditAnswerKeyActivity.class);
                    intent.putExtra("key_id", key.id);
                    startActivityForResult(intent, 1001);
                });

                // Delete key updates storage and list
                btnRowDelete.setOnClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos == RecyclerView.NO_ID || pos < 0 || pos >= list.size()) return;

                    AnswerKeyManager.deleteKey(ScanGradeActivity.this, key.id);
                    list.remove(pos);
                    notifyItemRemoved(pos);

                    // If empty, show placeholder
                    if (list.isEmpty()) {
                        dialog.findViewById(R.id.txtDialogEmpty).setVisibility(View.VISIBLE);
                        dialog.findViewById(R.id.recyclerDialogKeys).setVisibility(View.GONE);
                    }

                    // Check if deleted key was active
                    if (isSelected) {
                        loadSelectedKeyOnStart(); // will reset states since selection was cleared
                    }
                });
            }
        }
    }
}
