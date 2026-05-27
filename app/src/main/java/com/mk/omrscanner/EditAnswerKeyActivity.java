package com.mk.omrscanner;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EditAnswerKeyActivity extends AppCompatActivity {

    private EditText inputKeyName;
    private TextView chipQ50, chipQ100;
    private ProgressBar progressAnswers;
    private TextView txtAnswersSetLabel;
    private TextView btnClearAll;

    // Collapsible Scan Master Key Card
    private LinearLayout cardScanMasterKey;
    private RelativeLayout headerScanMasterKey;
    private LinearLayout bodyScanMasterKey;
    private ImageView chevronScanMasterKey;
    private LinearLayout btnMasterCamera, btnMasterGallery;

    // Collapsible Scoring Options Card
    private LinearLayout cardScoringOptions;
    private RelativeLayout headerScoringOptions;
    private LinearLayout bodyScoringOptions;
    private ImageView chevronScoringOptions;
    private View scoringActiveDot;
    private EditText inputPenaltyIncorrect, inputPenaltyMultiMark;
    private TextView chipQuickNone, chipQuickQuarter, chipQuickThird, chipQuickHalf, chipQuickOne;
    private SwitchCompat switchCustomPoints;
    private LinearLayout layoutSetAllPoints;
    private TextView chipPtHalf, chipPtOne, chipPtTwo, chipPtThree, chipPtFive;
    private SwitchCompat switchMultiCorrect;

    // Questions Grid
    private RecyclerView recyclerQuestions;
    private QuestionAdapter questionAdapter;
    private List<QuestionModel> questionList;

    private static final int REQUEST_CAMERA_PERMISSION = 101;
    private static final int REQUEST_CAMERA_CAPTURE = 2001;
    private static final int REQUEST_GALLERY_PICK = 2002;
    private android.net.Uri cameraImageUri;

    private int currentQuestionsCount = 100;
    private int selectedColumns = 4;
    private int selectedIdDigits = 6; // Student ID digits: 4 or 6
    private boolean isCustomPointsActive = false;
    private boolean isMultiCorrectActive = false;
    private String editingKeyId = "";

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
        setContentView(R.layout.activity_edit_answer_key);

        if (savedInstanceState != null) {
            String uriString = savedInstanceState.getString("camera_image_uri");
            if (uriString != null) {
                cameraImageUri = Uri.parse(uriString);
            }
        }

        // Bind Toolbar
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSave).setOnClickListener(v -> saveAnswerKey());

        // Bind Base Inputs & chips
        inputKeyName = findViewById(R.id.inputKeyName);
        chipQ50 = findViewById(R.id.chipQ50);
        chipQ100 = findViewById(R.id.chipQ100);
        progressAnswers = findViewById(R.id.progressAnswers);
        txtAnswersSetLabel = findViewById(R.id.txtAnswersSetLabel);
        btnClearAll = findViewById(R.id.btnClearAll);

        // Bind Collapsible 1: Scan Master Key
        cardScanMasterKey = findViewById(R.id.cardScanMasterKey);
        headerScanMasterKey = findViewById(R.id.headerScanMasterKey);
        bodyScanMasterKey = findViewById(R.id.bodyScanMasterKey);
        chevronScanMasterKey = findViewById(R.id.chevronScanMasterKey);
        btnMasterCamera = findViewById(R.id.btnMasterCamera);
        btnMasterGallery = findViewById(R.id.btnMasterGallery);

        // Bind Collapsible 2: Scoring Options
        cardScoringOptions = findViewById(R.id.cardScoringOptions);
        headerScoringOptions = findViewById(R.id.headerScoringOptions);
        bodyScoringOptions = findViewById(R.id.bodyScoringOptions);
        chevronScoringOptions = findViewById(R.id.chevronScoringOptions);
        scoringActiveDot = findViewById(R.id.scoringActiveDot);
        inputPenaltyIncorrect = findViewById(R.id.inputPenaltyIncorrect);
        inputPenaltyMultiMark = findViewById(R.id.inputPenaltyMultiMark);
        chipQuickNone = findViewById(R.id.chipQuickNone);
        chipQuickQuarter = findViewById(R.id.chipQuickQuarter);
        chipQuickThird = findViewById(R.id.chipQuickThird);
        chipQuickHalf = findViewById(R.id.chipQuickHalf);
        chipQuickOne = findViewById(R.id.chipQuickOne);
        switchCustomPoints = findViewById(R.id.switchCustomPoints);
        layoutSetAllPoints = findViewById(R.id.layoutSetAllPoints);
        chipPtHalf = findViewById(R.id.chipPtHalf);
        chipPtOne = findViewById(R.id.chipPtOne);
        chipPtTwo = findViewById(R.id.chipPtTwo);
        chipPtThree = findViewById(R.id.chipPtThree);
        chipPtFive = findViewById(R.id.chipPtFive);
        switchMultiCorrect = findViewById(R.id.switchMultiCorrect);

        // Bind Recycler
        recyclerQuestions = findViewById(R.id.recyclerQuestions);
        recyclerQuestions.setLayoutManager(new LinearLayoutManager(this));

        // Set Up Lists & Adapters
        questionList = new ArrayList<>();
        questionAdapter = new QuestionAdapter(questionList);
        recyclerQuestions.setAdapter(questionAdapter);

        // Setup Initial Listeners & UI State
        initListeners();
        int initialQuestionsCount = AnswerKeyManager.normalizeQuestionsCount(
                getIntent().getIntExtra("questions_count", currentQuestionsCount)
        );
        selectQuestionCount(initialQuestionsCount);
        updateQuickPenaltyHighlight("None");
        setupBottomNav();

        // Check if editing an existing key
        String keyId = getIntent().getStringExtra("key_id");
        if (keyId != null && !keyId.isEmpty()) {
            loadExistingKey(keyId);
        }
    }

    private void initListeners() {
        // Questions Count Chips
        chipQ50.setOnClickListener(v -> selectQuestionCount(50));
        chipQ100.setOnClickListener(v -> selectQuestionCount(100));

        // Clear All Answers
        btnClearAll.setOnClickListener(v -> clearAllAnswers());

        // Card 1 Collapsible toggle
        headerScanMasterKey.setOnClickListener(v -> toggleCardScanMasterKey());

        // Card 1 Camera & Gallery actions
        btnMasterCamera.setOnClickListener(v -> checkCameraPermissionAndStart());
        btnMasterGallery.setOnClickListener(v -> openGalleryPicker());

        // Card 2 Collapsible toggle
        headerScoringOptions.setOnClickListener(v -> toggleCardScoringOptions());

        // Penalty Edittext listeners
        TextWatcher penaltyWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateScoringIndicatorDot();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };
        inputPenaltyIncorrect.addTextChangedListener(penaltyWatcher);
        inputPenaltyMultiMark.addTextChangedListener(penaltyWatcher);

        // Quick Penalty Chips
        chipQuickNone.setOnClickListener(v -> {
            inputPenaltyIncorrect.setText("0");
            inputPenaltyMultiMark.setText("0");
            updateQuickPenaltyHighlight("None");
        });
        chipQuickQuarter.setOnClickListener(v -> {
            inputPenaltyIncorrect.setText("-0.25");
            inputPenaltyMultiMark.setText("-0.25");
            updateQuickPenaltyHighlight("Quarter");
        });
        chipQuickThird.setOnClickListener(v -> {
            inputPenaltyIncorrect.setText("-0.33");
            inputPenaltyMultiMark.setText("-0.33");
            updateQuickPenaltyHighlight("Third");
        });
        chipQuickHalf.setOnClickListener(v -> {
            inputPenaltyIncorrect.setText("-0.5");
            inputPenaltyMultiMark.setText("-0.5");
            updateQuickPenaltyHighlight("Half");
        });
        chipQuickOne.setOnClickListener(v -> {
            inputPenaltyIncorrect.setText("-1.0");
            inputPenaltyMultiMark.setText("-1.0");
            updateQuickPenaltyHighlight("One");
        });

        // Custom Points Switch
        switchCustomPoints.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isCustomPointsActive = isChecked;
            TransitionManager.beginDelayedTransition((ViewGroup) cardScoringOptions);
            layoutSetAllPoints.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            updateScoringIndicatorDot();
            questionAdapter.notifyDataSetChanged();
        });

        // Set all custom point chips
        chipPtHalf.setOnClickListener(v -> setAllQuestionsPoints(0.5, "0.5pt"));
        chipPtOne.setOnClickListener(v -> setAllQuestionsPoints(1.0, "1pt"));
        chipPtTwo.setOnClickListener(v -> setAllQuestionsPoints(2.0, "2pt"));
        chipPtThree.setOnClickListener(v -> setAllQuestionsPoints(3.0, "3pt"));
        chipPtFive.setOnClickListener(v -> setAllQuestionsPoints(5.0, "5pt"));

        // Multi-correct Switch
        switchMultiCorrect.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isMultiCorrectActive = isChecked;
            updateScoringIndicatorDot();
        });
    }

    private void selectQuestionCount(int count) {
        if (currentQuestionsCount != count && getFilledAnswersCount() > 0) {
            // Warn/confirm before resetting, or reset immediately for fast prototype
            Toast.makeText(this, "Resetting key grid for " + count + " questions", Toast.LENGTH_SHORT).show();
        }

        currentQuestionsCount = AnswerKeyManager.normalizeQuestionsCount(count);
        progressAnswers.setMax(currentQuestionsCount);

        // Highlight selected questions chip
        int activeColor = ContextCompat.getColor(this, R.color.dashboard_card_teal);
        int activeText = ContextCompat.getColor(this, R.color.white);
        int inactiveColor = ContextCompat.getColor(this, R.color.bg_dark_card);
        int inactiveText = ContextCompat.getColor(this, R.color.text_primary);

        chipQ50.setBackgroundTintList(ColorStateList.valueOf(inactiveColor));
        chipQ50.setTextColor(inactiveText);
        chipQ100.setBackgroundTintList(ColorStateList.valueOf(inactiveColor));
        chipQ100.setTextColor(inactiveText);

        if (count == 50) {
            chipQ50.setBackgroundTintList(ColorStateList.valueOf(activeColor));
            chipQ50.setTextColor(activeText);
        } else if (count == 100) {
            chipQ100.setBackgroundTintList(ColorStateList.valueOf(activeColor));
            chipQ100.setTextColor(activeText);
        }

        // Lock columns dynamically for scanner return intent consistency
        selectedColumns = AnswerKeyManager.columnsForQuestionCount(currentQuestionsCount);

        // Build list
        questionList.clear();
        for (int i = 1; i <= currentQuestionsCount; i++) {
            questionList.add(new QuestionModel(i));
        }

        updateProgress();
        questionAdapter.notifyDataSetChanged();
    }



    private void toggleCardScanMasterKey() {
        TransitionManager.beginDelayedTransition((ViewGroup) cardScanMasterKey);
        if (bodyScanMasterKey.getVisibility() == View.GONE) {
            bodyScanMasterKey.setVisibility(View.VISIBLE);
            chevronScanMasterKey.setImageResource(R.drawable.ic_chevron_up);
        } else {
            bodyScanMasterKey.setVisibility(View.GONE);
            chevronScanMasterKey.setImageResource(R.drawable.ic_chevron_down);
        }
    }

    private void toggleCardScoringOptions() {
        TransitionManager.beginDelayedTransition((ViewGroup) cardScoringOptions);
        if (bodyScoringOptions.getVisibility() == View.GONE) {
            bodyScoringOptions.setVisibility(View.VISIBLE);
            chevronScoringOptions.setImageResource(R.drawable.ic_chevron_up);
        } else {
            bodyScoringOptions.setVisibility(View.GONE);
            chevronScoringOptions.setImageResource(R.drawable.ic_chevron_down);
        }
    }

    private void updateQuickPenaltyHighlight(String selection) {
        int activeColor = ContextCompat.getColor(this, R.color.dashboard_card_teal);
        int activeText = ContextCompat.getColor(this, R.color.white);
        int inactiveColor = ContextCompat.getColor(this, R.color.bg_dark_card);
        int inactiveText = ContextCompat.getColor(this, R.color.text_primary);

        chipQuickNone.setBackgroundTintList(ColorStateList.valueOf(inactiveColor));
        chipQuickNone.setTextColor(inactiveText);
        chipQuickQuarter.setBackgroundTintList(ColorStateList.valueOf(inactiveColor));
        chipQuickQuarter.setTextColor(inactiveText);
        chipQuickThird.setBackgroundTintList(ColorStateList.valueOf(inactiveColor));
        chipQuickThird.setTextColor(inactiveText);
        chipQuickHalf.setBackgroundTintList(ColorStateList.valueOf(inactiveColor));
        chipQuickHalf.setTextColor(inactiveText);
        chipQuickOne.setBackgroundTintList(ColorStateList.valueOf(inactiveColor));
        chipQuickOne.setTextColor(inactiveText);

        switch (selection) {
            case "None":
                chipQuickNone.setBackgroundTintList(ColorStateList.valueOf(activeColor));
                chipQuickNone.setTextColor(activeText);
                break;
            case "Quarter":
                chipQuickQuarter.setBackgroundTintList(ColorStateList.valueOf(activeColor));
                chipQuickQuarter.setTextColor(activeText);
                break;
            case "Third":
                chipQuickThird.setBackgroundTintList(ColorStateList.valueOf(activeColor));
                chipQuickThird.setTextColor(activeText);
                break;
            case "Half":
                chipQuickHalf.setBackgroundTintList(ColorStateList.valueOf(activeColor));
                chipQuickHalf.setTextColor(activeText);
                break;
            case "One":
                chipQuickOne.setBackgroundTintList(ColorStateList.valueOf(activeColor));
                chipQuickOne.setTextColor(activeText);
                break;
        }
        updateScoringIndicatorDot();
    }

    private void setAllQuestionsPoints(double points, String chipSelected) {
        int activeColor = ContextCompat.getColor(this, R.color.dashboard_card_teal);
        int activeText = ContextCompat.getColor(this, R.color.white);
        int inactiveColor = ContextCompat.getColor(this, R.color.bg_dark_card);
        int inactiveText = ContextCompat.getColor(this, R.color.text_primary);

        chipPtHalf.setBackgroundTintList(ColorStateList.valueOf(inactiveColor));
        chipPtHalf.setTextColor(inactiveText);
        chipPtOne.setBackgroundTintList(ColorStateList.valueOf(inactiveColor));
        chipPtOne.setTextColor(inactiveText);
        chipPtTwo.setBackgroundTintList(ColorStateList.valueOf(inactiveColor));
        chipPtTwo.setTextColor(inactiveText);
        chipPtThree.setBackgroundTintList(ColorStateList.valueOf(inactiveColor));
        chipPtThree.setTextColor(inactiveText);
        chipPtFive.setBackgroundTintList(ColorStateList.valueOf(inactiveColor));
        chipPtFive.setTextColor(inactiveText);

        switch (chipSelected) {
            case "0.5pt":
                chipPtHalf.setBackgroundTintList(ColorStateList.valueOf(activeColor));
                chipPtHalf.setTextColor(activeText);
                break;
            case "1pt":
                chipPtOne.setBackgroundTintList(ColorStateList.valueOf(activeColor));
                chipPtOne.setTextColor(activeText);
                break;
            case "2pt":
                chipPtTwo.setBackgroundTintList(ColorStateList.valueOf(activeColor));
                chipPtTwo.setTextColor(activeText);
                break;
            case "3pt":
                chipPtThree.setBackgroundTintList(ColorStateList.valueOf(activeColor));
                chipPtThree.setTextColor(activeText);
                break;
            case "5pt":
                chipPtFive.setBackgroundTintList(ColorStateList.valueOf(activeColor));
                chipPtFive.setTextColor(activeText);
                break;
        }

        for (QuestionModel model : questionList) {
            model.points = points;
        }
        questionAdapter.notifyDataSetChanged();
    }

    private void updateScoringIndicatorDot() {
        boolean customPoints = switchCustomPoints.isChecked();
        boolean penaltyActive = !inputPenaltyIncorrect.getText().toString().equals("0") 
                || !inputPenaltyMultiMark.getText().toString().equals("0");
        boolean multiCorrect = switchMultiCorrect.isChecked();

        if (customPoints || penaltyActive || multiCorrect) {
            scoringActiveDot.setVisibility(View.VISIBLE);
        } else {
            scoringActiveDot.setVisibility(View.GONE);
        }
    }

    private int getFilledAnswersCount() {
        int count = 0;
        for (QuestionModel model : questionList) {
            if (model.isAnyOptionSelected()) {
                count++;
            }
        }
        return count;
    }

    private void updateProgress() {
        int filled = getFilledAnswersCount();
        progressAnswers.setProgress(filled);
        txtAnswersSetLabel.setText(filled + " / " + currentQuestionsCount + " answers set");
    }

    private void clearAllAnswers() {
        for (QuestionModel model : questionList) {
            model.resetOptions();
        }
        updateProgress();
        questionAdapter.notifyDataSetChanged();
        Toast.makeText(this, "Answers cleared", Toast.LENGTH_SHORT).show();
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
            File photoFile = new File(getExternalFilesDir(null), "master_key_" + System.currentTimeMillis() + ".jpg");
            if (photoFile.exists()) {
                photoFile.delete();
            }
            cameraImageUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
            
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_CAMERA_CAPTURE);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to open camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
            int reqWidth = 800;
            int reqHeight = 800;
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
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void openGalleryPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(Intent.createChooser(intent, "Select Master Sheet Image"), REQUEST_GALLERY_PICK);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to open gallery: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Camera permission is required to scan master key", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_CAMERA_CAPTURE) {
                if (cameraImageUri != null) {
                    showMasterScanOverlay(cameraImageUri);
                }
            } else if (requestCode == REQUEST_GALLERY_PICK) {
                if (data != null && data.getData() != null) {
                    showMasterScanOverlay(data.getData());
                }
            }
        }
    }

    private void showMasterScanOverlay(Uri imageUri) {
        // Inflate simulated master key scanner dialog
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_master_key_scan, null);
        final android.app.Dialog scannerDialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        scannerDialog.setContentView(dialogView);

        ImageView imgPreview = dialogView.findViewById(R.id.imgMasterSheetPreview);
        final View laserLine = dialogView.findViewById(R.id.scanLaserLine);
        final LinearLayout layoutSuccessCard = dialogView.findViewById(R.id.layoutScanSuccessCard);
        final LinearLayout layoutProgress = dialogView.findViewById(R.id.layoutScanningProgress);
        final TextView txtStatus = dialogView.findViewById(R.id.txtScanningStatus);
        TextView txtDetected = dialogView.findViewById(R.id.txtDetectedInfo);
        androidx.appcompat.widget.AppCompatButton btnFinish = dialogView.findViewById(R.id.btnFinishScan);

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

        // Stage 1: Template alignment
        new Handler().postDelayed(() -> {
            if (txtStatus != null) {
                txtStatus.setText("Aligning master grid registration anchors...");
            }
        }, 800);

        // Stage 2: Scanning responses
        new Handler().postDelayed(() -> {
            if (txtStatus != null) {
                txtStatus.setText("Scanning bubble responses (A, B, C, D)...");
            }
        }, 1600);

        // Stage 3: Complete scan
        new Handler().postDelayed(() -> {
            if (laserLine != null) laserLine.setVisibility(View.GONE);
            if (layoutProgress != null) layoutProgress.setVisibility(View.GONE);
            if (layoutSuccessCard != null) layoutSuccessCard.setVisibility(View.VISIBLE);
            if (txtDetected != null) {
                txtDetected.setText("Detected " + currentQuestionsCount + " answers from OMR template");
            }
        }, 2500);

        btnFinish.setOnClickListener(v -> {
            android.graphics.Bitmap masterBitmap = loadScaledBitmap(imageUri);
            
            // Detect filled bubbles from the master sheet image
            List<boolean[]> detectedKey = OMRProcessor.detectFilledBubbles(
                    masterBitmap, 
                    currentQuestionsCount, 
                    selectedColumns
            );

            // Update the grid with detected answers
            for (int i = 0; i < questionList.size(); i++) {
                if (i < detectedKey.size()) {
                    QuestionModel model = questionList.get(i);
                    model.selected = detectedKey.get(i);
                }
            }
            
            updateProgress();
            questionAdapter.notifyDataSetChanged();

            // Collapse the card layout smoothly
            if (bodyScanMasterKey.getVisibility() == View.VISIBLE) {
                toggleCardScanMasterKey();
            }

            scannerDialog.dismiss();
            Toast.makeText(this, "Master sheet scanned! Answer key populated from image.", Toast.LENGTH_LONG).show();
        });

        scannerDialog.show();
    }

    private void loadExistingKey(String keyId) {
        editingKeyId = keyId;
        List<AnswerKeyManager.AnswerKey> savedKeys = AnswerKeyManager.getSavedKeys(this);
        AnswerKeyManager.AnswerKey targetKey = null;
        for (AnswerKeyManager.AnswerKey key : savedKeys) {
            if (key.id.equals(keyId)) {
                targetKey = key;
                break;
            }
        }

        if (targetKey != null) {
            // Set text name
            inputKeyName.setText(targetKey.name);

            // Set variables
            currentQuestionsCount = AnswerKeyManager.normalizeQuestionsCount(targetKey.questionsCount);
            selectedColumns = AnswerKeyManager.columnsForQuestionCount(currentQuestionsCount);
            selectedIdDigits = targetKey.idDigits;
            isCustomPointsActive = targetKey.customPointsActive;
            isMultiCorrectActive = targetKey.multiCorrectActive;

            // Load UI States
            selectQuestionCount(currentQuestionsCount);

            inputPenaltyIncorrect.setText(targetKey.incorrectPenalty);
            inputPenaltyMultiMark.setText(targetKey.multiMarkPenalty);
            switchCustomPoints.setChecked(targetKey.customPointsActive);
            switchMultiCorrect.setChecked(targetKey.multiCorrectActive);

            layoutSetAllPoints.setVisibility(isCustomPointsActive ? View.VISIBLE : View.GONE);
            updateScoringIndicatorDot();

            // Deserialize answers and points
            try {
                org.json.JSONArray answersArray = new org.json.JSONArray(targetKey.answersJson);
                org.json.JSONArray pointsArray = new org.json.JSONArray(targetKey.pointsJson);
                for (int i = 0; i < questionList.size(); i++) {
                    if (i < answersArray.length()) {
                        org.json.JSONArray row = answersArray.getJSONArray(i);
                        QuestionModel model = questionList.get(i);
                        for (int b = 0; b < 4; b++) {
                            model.selected[b] = row.getBoolean(b);
                        }
                    }
                    if (i < pointsArray.length()) {
                        questionList.get(i).points = pointsArray.getDouble(i);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            updateProgress();
            questionAdapter.notifyDataSetChanged();
        }
    }

    private void saveAnswerKey() {
        String keyName = inputKeyName.getText().toString().trim();
        if (TextUtils.isEmpty(keyName)) {
            keyName = "New Answer Key";
        }

        int filled = getFilledAnswersCount();
        if (filled == 0) {
            Toast.makeText(this, "Please set at least one correct answer bubble!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Serialize answers & points to JSON arrays
        org.json.JSONArray answersArray = new org.json.JSONArray();
        org.json.JSONArray pointsArray = new org.json.JSONArray();
        try {
            for (QuestionModel model : questionList) {
                org.json.JSONArray row = new org.json.JSONArray();
                row.put(model.selected[0]);
                row.put(model.selected[1]);
                row.put(model.selected[2]);
                row.put(model.selected[3]);
                answersArray.put(row);

                pointsArray.put(model.points);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        String id = editingKeyId.isEmpty() ? String.valueOf(System.currentTimeMillis()) : editingKeyId;
        AnswerKeyManager.AnswerKey key = new AnswerKeyManager.AnswerKey(
                id,
                keyName,
                currentQuestionsCount,
                selectedColumns,
                selectedIdDigits,
                answersArray.toString(),
                inputPenaltyIncorrect.getText().toString().trim(),
                inputPenaltyMultiMark.getText().toString().trim(),
                isCustomPointsActive,
                isMultiCorrectActive,
                pointsArray.toString()
        );

        AnswerKeyManager.saveKey(this, key);
        AnswerKeyManager.setSelectedKeyId(this, key.id);

        Intent data = new Intent();
        data.putExtra("key_id", key.id);
        data.putExtra("key_name", keyName);
        data.putExtra("questions_count", currentQuestionsCount);
        data.putExtra("columns_layout", selectedColumns);
        setResult(RESULT_OK, data);
        finish();
    }

    private void setupBottomNav() {
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

        findViewById(R.id.navScanner).setOnClickListener(v -> {
            Intent intent = new Intent(this, ScanGradeActivity.class);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.navResults).setOnClickListener(v -> {
            Intent intent = new Intent(this, ScanGradeActivity.class);
            intent.putExtra("show_results", true);
            startActivity(intent);
            finish();
        });
    }

    // Question Data Model
    private static class QuestionModel {
        int index;
        boolean[] selected = new boolean[4]; // A, B, C, D
        double points = 1.0;

        QuestionModel(int index) {
            this.index = index;
        }

        boolean isAnyOptionSelected() {
            return selected[0] || selected[1] || selected[2] || selected[3];
        }

        void resetOptions() {
            selected[0] = false;
            selected[1] = false;
            selected[2] = false;
            selected[3] = false;
        }
    }

    // Question Recycler Adapter
    private class QuestionAdapter extends RecyclerView.Adapter<QuestionAdapter.QuestionViewHolder> {

        private final List<QuestionModel> list;

        QuestionAdapter(List<QuestionModel> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public QuestionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_answer_key_question, parent, false);
            return new QuestionViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull QuestionViewHolder holder, int position) {
            QuestionModel model = list.get(position);
            holder.bind(model);
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class QuestionViewHolder extends RecyclerView.ViewHolder {
            private final TextView txtQuestionNum;
            private final TextView btnOptA, btnOptB, btnOptC, btnOptD;
            private final TextView badgePoints;

            QuestionViewHolder(@NonNull View itemView) {
                super(itemView);
                txtQuestionNum = itemView.findViewById(R.id.txtQuestionNum);
                btnOptA = itemView.findViewById(R.id.btnOptA);
                btnOptB = itemView.findViewById(R.id.btnOptB);
                btnOptC = itemView.findViewById(R.id.btnOptC);
                btnOptD = itemView.findViewById(R.id.btnOptD);
                badgePoints = itemView.findViewById(R.id.badgePoints);
            }

            void bind(QuestionModel model) {
                txtQuestionNum.setText(String.valueOf(model.index));
                
                // Set selections
                btnOptA.setSelected(model.selected[0]);
                btnOptB.setSelected(model.selected[1]);
                btnOptC.setSelected(model.selected[2]);
                btnOptD.setSelected(model.selected[3]);

                // Contrast text and show tick mark inside selected bubbles
                btnOptA.setText(model.selected[0] ? "✔" : "A");
                btnOptB.setText(model.selected[1] ? "✔" : "B");
                btnOptC.setText(model.selected[2] ? "✔" : "C");
                btnOptD.setText(model.selected[3] ? "✔" : "D");

                btnOptA.setTextColor(model.selected[0] ? Color.WHITE : ContextCompat.getColor(itemView.getContext(), R.color.text_primary));
                btnOptB.setTextColor(model.selected[1] ? Color.WHITE : ContextCompat.getColor(itemView.getContext(), R.color.text_primary));
                btnOptC.setTextColor(model.selected[2] ? Color.WHITE : ContextCompat.getColor(itemView.getContext(), R.color.text_primary));
                btnOptD.setTextColor(model.selected[3] ? Color.WHITE : ContextCompat.getColor(itemView.getContext(), R.color.text_primary));

                // Point badge visibility & text
                if (isCustomPointsActive) {
                    badgePoints.setVisibility(View.VISIBLE);
                    // format points to avoid .0 if integer
                    if (model.points == (long) model.points) {
                        badgePoints.setText(String.format("%dpt", (long) model.points));
                    } else {
                        badgePoints.setText(String.format("%.1fpt", model.points));
                    }
                } else {
                    badgePoints.setVisibility(View.GONE);
                }

                // Setup Bubble option click listeners
                btnOptA.setOnClickListener(v -> toggleOption(model, 0));
                btnOptB.setOnClickListener(v -> toggleOption(model, 1));
                btnOptC.setOnClickListener(v -> toggleOption(model, 2));
                btnOptD.setOnClickListener(v -> toggleOption(model, 3));

                // Cycle point value on badge click
                badgePoints.setOnClickListener(v -> {
                    if (model.points == 1.0) {
                        model.points = 2.0;
                    } else if (model.points == 2.0) {
                        model.points = 3.0;
                    } else if (model.points == 3.0) {
                        model.points = 5.0;
                    } else if (model.points == 5.0) {
                        model.points = 0.5;
                    } else {
                        model.points = 1.0;
                    }
                    notifyItemChanged(getAdapterPosition());
                });
            }

            private void toggleOption(QuestionModel model, int optionIndex) {
                if (isMultiCorrectActive) {
                    model.selected[optionIndex] = !model.selected[optionIndex];
                } else {
                    boolean alreadySelected = model.selected[optionIndex];
                    model.resetOptions();
                    model.selected[optionIndex] = !alreadySelected;
                }
                updateProgress();
                notifyItemChanged(getAdapterPosition());
            }
        }
    }
}
