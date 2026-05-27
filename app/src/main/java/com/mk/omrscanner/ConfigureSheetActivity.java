package com.mk.omrscanner;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
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
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ConfigureSheetActivity extends AppCompatActivity {

    private ImageView btnBack;
    private TextView btnSave;
    private EditText inputExamTitle;
    private TextView txtCharCounter;
    private EditText inputSubtitle;
    private EditText inputFooter;

    // Question count chips (Only 50 and 100)
    private TextView chipQ50, chipQ100;
    private int selectedQuestions = 100;

    // Column chips
    private int selectedColumns = 4;

    // Custom fields list & layout
    private LinearLayout containerCustomFields;
    private AppCompatButton btnAddField;
    private List<String> customFieldsList;

    // Prefill ID switch and generate buttons
    private SwitchCompat switchPrefill;
    private AppCompatButton btnPreview;
    private AppCompatButton btnGeneratePdf;

    // Student ID chips
    private TextView chipId4, chipId6;
    private int selectedIdDigits = 6;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_configure_sheet);

        // Bind core toolbar
        btnBack = findViewById(R.id.btnBack);
        btnSave = findViewById(R.id.btnSave);
        btnBack.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveConfiguration());

        // Bind title fields & count listener
        inputExamTitle = findViewById(R.id.inputExamTitle);
        txtCharCounter = findViewById(R.id.txtCharCounter);
        inputSubtitle = findViewById(R.id.inputSubtitle);
        inputFooter = findViewById(R.id.inputFooter);

        inputExamTitle.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                txtCharCounter.setText(s.length() + "/35");
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Bind Question chips (Only 50 and 100)
        chipQ50 = findViewById(R.id.chipQ50);
        chipQ100 = findViewById(R.id.chipQ100);

        chipQ50.setOnClickListener(v -> selectQuestionChip(50));
        chipQ100.setOnClickListener(v -> selectQuestionChip(100));



        // Bind Student ID chips
        chipId4 = findViewById(R.id.chipId4);
        chipId6 = findViewById(R.id.chipId6);

        chipId4.setOnClickListener(v -> selectIdDigitsChip(4));
        chipId6.setOnClickListener(v -> selectIdDigitsChip(6));

        // Default set selection chip (100 Qs selected, columns locked to 4)
        selectQuestionChip(100);
        selectIdDigitsChip(6);

        // Setup Custom Fields container and items
        containerCustomFields = findViewById(R.id.containerCustomFields);
        btnAddField = findViewById(R.id.btnAddField);
        customFieldsList = new ArrayList<>(Arrays.asList("Name", "F/Name", "Class"));

        refreshCustomFields();

        btnAddField.setOnClickListener(v -> {
            if (customFieldsList.size() < 4) {
                customFieldsList.add("Section");
                refreshCustomFields();
            }
        });

        // Setup action buttons
        switchPrefill = findViewById(R.id.switchPrefill);
        btnPreview = findViewById(R.id.btnPreview);
        btnGeneratePdf = findViewById(R.id.btnGeneratePdf);

        btnPreview.setOnClickListener(v -> {
            if (validateAllSections()) {
                showPreviewDialog();
            }
        });

        btnGeneratePdf.setOnClickListener(v -> {
            if (validateAllSections()) {
                generateOMRPdf(true);
            }
        });

        // Setup Bottom Navigation Bar click listeners
        findViewById(R.id.navHome).setOnClickListener(v -> {
            Intent intent = new Intent(this, DashboardActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.navScanner).setOnClickListener(v -> {
            Intent intent = new Intent(this, ScanGradeActivity.class);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.navKeys).setOnClickListener(v -> {
            Intent intent = new Intent(ConfigureSheetActivity.this, EditAnswerKeyActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.navResults).setOnClickListener(v -> {
            Intent intent = new Intent(this, ScanGradeActivity.class);
            intent.putExtra("show_results", true);
            startActivity(intent);
            finish();
        });
    }

    private void selectQuestionChip(int count) {
        selectedQuestions = AnswerKeyManager.normalizeQuestionsCount(count);
        selectedColumns = AnswerKeyManager.columnsForQuestionCount(selectedQuestions);
        int activeBgColor = ContextCompat.getColor(this, R.color.dashboard_card_teal);
        int activeTextColor = ContextCompat.getColor(this, R.color.white);
        int inactiveBgColor = ContextCompat.getColor(this, R.color.bg_dark_card);
        int inactiveTextColor = ContextCompat.getColor(this, R.color.text_primary);

        // Reset question chips selection
        chipQ50.setBackgroundTintList(ColorStateList.valueOf(inactiveBgColor));
        chipQ50.setTextColor(inactiveTextColor);
        chipQ100.setBackgroundTintList(ColorStateList.valueOf(inactiveBgColor));
        chipQ100.setTextColor(inactiveTextColor);

        // Apply active question chip styling
        if (count == 50) {
            chipQ50.setBackgroundTintList(ColorStateList.valueOf(activeBgColor));
            chipQ50.setTextColor(activeTextColor);
        } else if (count == 100) {
            chipQ100.setBackgroundTintList(ColorStateList.valueOf(activeBgColor));
            chipQ100.setTextColor(activeTextColor);
        }
    }

    private void selectIdDigitsChip(int digits) {
        selectedIdDigits = digits;
        int activeBgColor = ContextCompat.getColor(this, R.color.dashboard_card_teal);
        int activeTextColor = ContextCompat.getColor(this, R.color.white);
        int inactiveBgColor = ContextCompat.getColor(this, R.color.bg_dark_card);
        int inactiveTextColor = ContextCompat.getColor(this, R.color.text_primary);

        chipId4.setBackgroundTintList(ColorStateList.valueOf(inactiveBgColor));
        chipId4.setTextColor(inactiveTextColor);
        chipId6.setBackgroundTintList(ColorStateList.valueOf(inactiveBgColor));
        chipId6.setTextColor(inactiveTextColor);

        if (digits == 4) {
            chipId4.setBackgroundTintList(ColorStateList.valueOf(activeBgColor));
            chipId4.setTextColor(activeTextColor);
        } else {
            chipId6.setBackgroundTintList(ColorStateList.valueOf(activeBgColor));
            chipId6.setTextColor(activeTextColor);
        }
    }

    private void refreshCustomFields() {
        containerCustomFields.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < customFieldsList.size(); i++) {
            final int index = i;
            String fieldText = customFieldsList.get(i);
            View rowView = inflater.inflate(R.layout.item_custom_field_row, containerCustomFields, false);

            SwitchCompat switchFieldActive = rowView.findViewById(R.id.switchFieldActive);
            EditText inputFieldName = rowView.findViewById(R.id.inputFieldName);
            ImageView btnDeleteField = rowView.findViewById(R.id.btnDeleteField);

            inputFieldName.setText(fieldText);

            inputFieldName.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    if (index < customFieldsList.size()) {
                        customFieldsList.set(index, s.toString());
                    }
                }
            });

            switchFieldActive.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    rowView.setAlpha(1.0f);
                    inputFieldName.setEnabled(true);
                } else {
                    rowView.setAlpha(0.4f);
                    inputFieldName.setEnabled(false);
                }
            });

            btnDeleteField.setOnClickListener(v -> {
                customFieldsList.remove(index);
                refreshCustomFields();
            });

            containerCustomFields.addView(rowView);
        }

        int count = customFieldsList.size();
        btnAddField.setText("+ Add Field (" + count + "/4)");
        if (count >= 4) {
            btnAddField.setEnabled(false);
            btnAddField.setAlpha(0.5f);
        } else {
            btnAddField.setEnabled(true);
            btnAddField.setAlpha(1.0f);
        }
    }

    private boolean validateAllSections() {
        // 1. Validate Exam Title
        String title = inputExamTitle.getText().toString().trim();
        if (TextUtils.isEmpty(title)) {
            Toast.makeText(this, "Please enter an Exam Title", Toast.LENGTH_SHORT).show();
            return false;
        }

        // Subtitle is optional now, so no validation check is performed

        // 3. Validate Custom Fields (check that no field name is blank)
        for (int i = 0; i < customFieldsList.size(); i++) {
            String field = customFieldsList.get(i).trim();
            if (TextUtils.isEmpty(field)) {
                Toast.makeText(this, "Please enter custom field name for Field #" + (i + 1), Toast.LENGTH_SHORT).show();
                return false;
            }
        }

        // Footer is optional now, so no validation check is performed on inputFooter

        return true;
    }

    private void showPreviewDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_preview_sheet, null);

        // Bind custom OMR preview view
        OMRSheetPreviewView previewView = dialogView.findViewById(R.id.omrPreviewView);
        
        // Pass current configuration settings
        String title = inputExamTitle.getText().toString().trim();
        String subtitle = inputSubtitle.getText().toString().trim();
        String footer = inputFooter.getText().toString().trim();
        previewView.setSheetData(title, subtitle, footer, selectedQuestions, selectedColumns, selectedIdDigits, customFieldsList);

        // Build fullscreen preview dialog
        final android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(dialogView);

        // Close preview click listener
        AppCompatButton btnClose = dialogView.findViewById(R.id.btnClose);
        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void generateOMRPdf(boolean openViewer) {
        String examTitle = inputExamTitle.getText().toString().trim();
        String subtitle = inputSubtitle.getText().toString().trim();
        String footerText = inputFooter.getText().toString().trim();

        // 1. Initialize PdfDocument
        PdfDocument document = new PdfDocument();

        // 2. Start Page info (A4 size: 595 x 842 pt)
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        // Fill background of PDF page (pure white)
        Paint paintBg = new Paint();
        paintBg.setColor(Color.WHITE);
        paintBg.setStyle(Paint.Style.FILL);
        canvas.drawRect(0, 0, 595, 842, paintBg);

        // 3. Define Paint parameters
        Paint paintSolidBlack = new Paint();
        paintSolidBlack.setColor(Color.BLACK);
        paintSolidBlack.setStyle(Paint.Style.FILL);

        Paint paintStrokeBlack = new Paint();
        paintStrokeBlack.setColor(Color.BLACK);
        paintStrokeBlack.setStyle(Paint.Style.STROKE);
        paintStrokeBlack.setStrokeWidth(1.0f);

        Paint paintFillGrey = new Paint();
        paintFillGrey.setColor(Color.WHITE);
        paintFillGrey.setStyle(Paint.Style.FILL);

        Paint paintTextBold = new Paint();
        paintTextBold.setColor(Color.BLACK);
        paintTextBold.setTextSize(14f);
        paintTextBold.setFakeBoldText(true);

        Paint paintTextRegular = new Paint();
        paintTextRegular.setColor(Color.BLACK);
        paintTextRegular.setTextSize(11f);
        paintTextRegular.setAntiAlias(true);

        Paint paintTextSmall = new Paint();
        paintTextSmall.setColor(Color.DKGRAY);
        paintTextSmall.setTextSize(8f);
        paintTextSmall.setAntiAlias(true);

        // --- DRAW TIMING TRACKS & CORNER ANCHORS ---
        canvas.drawRect(30, 40, 50, 60, paintSolidBlack); // Top-left
        canvas.drawRect(545, 40, 565, 60, paintSolidBlack); // Top-right
        canvas.drawRect(30, 782, 50, 802, paintSolidBlack); // Bottom-left
        canvas.drawRect(545, 782, 565, 802, paintSolidBlack); // Bottom-right

        for (int y = 80; y <= 760; y += 15) {
            canvas.drawRect(38, y, 42, y + 4, paintSolidBlack); // Left timing square
            canvas.drawRect(553, y, 557, y + 4, paintSolidBlack); // Right timing square
        }

        // --- DRAW HEADER/METADATA INFO BOX ON THE LEFT ---
        // Outline box coordinates: (60, 50) to (400, 195)
        canvas.drawRect(60, 50, 400, 195, paintStrokeBlack);
        
        paintTextBold.setTextSize(18f);
        float titleWidth = paintTextBold.measureText(examTitle);
        canvas.drawText(examTitle, 60 + (340 - titleWidth)/2, 85, paintTextBold);

        // Custom fields Name / Class drawing lines inside the info box
        paintTextBold.setTextSize(11f);
        int customFieldsCount = customFieldsList.size();
        for (int fIndex = 0; fIndex < Math.min(customFieldsCount, 3); fIndex++) {
            String label = customFieldsList.get(fIndex) + ":";
            float labelY = 120 + (fIndex * 30);
            canvas.drawText(label, 75, labelY, paintTextBold);
            canvas.drawLine(135, labelY, 385, labelY, paintStrokeBlack);
        }

        if (!TextUtils.isEmpty(subtitle)) {
            paintTextRegular.setTextSize(10f);
            float subWidth = paintTextRegular.measureText(subtitle);
            canvas.drawText(subtitle, 60 + (340 - subWidth)/2, 102, paintTextRegular);
        }

        // --- DRAW STUDENT ID SECTION ON THE RIGHT ---
        paintTextBold.setTextSize(11f);
        canvas.drawText("Student ID:", 420, 42, paintTextBold);
        
        // Draw dynamic digit boxes aligned directly above bubble columns
        int idDigits = selectedIdDigits;
        for (int col = 0; col < idDigits; col++) {
            float colX = 456 + (col * 14);
            float boxLeft = colX - 5;
            canvas.drawRect(boxLeft, 50, boxLeft + 10, 60, paintStrokeBlack);
        }

        // Student ID Bubbles (aligned close with numbers 0-9)
        paintTextRegular.setTextSize(8f);
        for (int row = 0; row < 10; row++) {
            float rowY = 76 + (row * 11);
            canvas.drawText(String.valueOf(row), 442, rowY + 3, paintTextRegular);

            for (int col = 0; col < idDigits; col++) {
                float colX = 456 + (col * 14);
                canvas.drawCircle(colX, rowY, 4.5f, paintStrokeBlack);
            }
        }

        // --- DRAW QUESTION TABLES ---
        int totalQ = selectedQuestions;
        int cols = selectedColumns;
        int qPerCol = (int) Math.ceil((double) totalQ / cols);

        for (int col = 0; col < cols; col++) {
            float colWidth = 230f;
            float spacing = 20f;
            if (cols == 4) {
                colWidth = 110f;
                spacing = 15f;
            }

            float colLeft = 60 + col * (colWidth + spacing);
            float tableTop = 220;

            canvas.drawRect(colLeft, tableTop, colLeft + colWidth, tableTop + 18, paintFillGrey);
            canvas.drawRect(colLeft, tableTop, colLeft + colWidth, tableTop + 18, paintStrokeBlack);

            paintTextBold.setTextSize(9f);
            canvas.drawText("No.", colLeft + 8, tableTop + 12, paintTextBold);
            
            float bubblePitch;
            float bubbleStart;
            if (cols == 4) {
                bubblePitch = colWidth / 6f;
                bubbleStart = colLeft + bubblePitch * 1.8f;
            } else {
                // 2-column: center 4 bubbles tightly within the wider column
                bubblePitch = 22f;
                float totalBubblesWidth = 3 * bubblePitch;
                bubbleStart = colLeft + (colWidth / 2f) - (totalBubblesWidth / 2f);
            }
            canvas.drawText("A", bubbleStart, tableTop + 12, paintTextBold);
            canvas.drawText("B", bubbleStart + bubblePitch, tableTop + 12, paintTextBold);
            canvas.drawText("C", bubbleStart + bubblePitch * 2, tableTop + 12, paintTextBold);
            canvas.drawText("D", bubbleStart + bubblePitch * 3, tableTop + 12, paintTextBold);

            // Table rows
            paintTextRegular.setTextSize(9f);
            for (int q = 0; q < qPerCol; q++) {
                int qNum = (col * qPerCol) + q + 1;
                if (qNum > totalQ) {
                    break;
                }
                float rowY = tableTop + 18 + (q * 18);

                int blockIndex = (qNum - 1) / 5;
                if (blockIndex % 2 == 0) {
                    canvas.drawRect(colLeft, rowY, colLeft + colWidth, rowY + 18, paintFillGrey);
                }
                canvas.drawRect(colLeft, rowY, colLeft + colWidth, rowY + 18, paintStrokeBlack);

                canvas.drawText(String.valueOf(qNum), colLeft + 8, rowY + 13, paintTextRegular);

                String[] letters = {"A", "B", "C", "D"};
                for (int b = 0; b < 4; b++) {
                    float bubbleX = bubbleStart + (b * bubblePitch);
                    canvas.drawCircle(bubbleX, rowY + 9, 6.0f, paintStrokeBlack);

                    paintTextSmall.setTextSize(6f);
                    float letterW = paintTextSmall.measureText(letters[b]);
                    canvas.drawText(letters[b], bubbleX - letterW/2, rowY + 11, paintTextSmall);
                }
            }
        }

        // --- DRAW FOOTER SECTION ---
        if (!TextUtils.isEmpty(footerText)) {
            paintTextRegular.setTextSize(9f);
            String footerMarkString = "How to mark:  ● good  ·  avoid:  ⊙ partial  ⊗ cross  ⊘ tick  ·  " + footerText;
            float footerW = paintTextRegular.measureText(footerMarkString);
            canvas.drawText(footerMarkString, (595 - footerW)/2, 815, paintTextRegular);
        }

        document.finishPage(page);

        // 4. Save to Scoped Storage Download Directory
        File pdfDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (pdfDir != null && !pdfDir.exists()) {
            pdfDir.mkdirs();
        }

        File file = new File(pdfDir, "OMR_Sheet_" + System.currentTimeMillis() + ".pdf");
        try {
            FileOutputStream fos = new FileOutputStream(file);
            document.writeTo(fos);
            document.close();
            fos.close();

            Toast.makeText(this, "PDF Generated Successfully!", Toast.LENGTH_SHORT).show();

            if (openViewer) {
                openPdfViewer(file);
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error generating PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            document.close();
        }
    }

    private void openPdfViewer(File file) {
        try {
            Uri fileUri = FileProvider.getUriForFile(
                    this,
                    "com.mk.omrscanner.fileprovider",
                    file
            );

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);

            startActivity(Intent.createChooser(intent, "Open OMR PDF Sheet"));
        } catch (Exception e) {
            Toast.makeText(this, "No PDF Viewer app installed", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveConfiguration() {
        if (validateAllSections()) {
            String examTitle = inputExamTitle.getText().toString().trim();
            Toast.makeText(this, "Configuration for \"" + examTitle + "\" Saved!", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}
