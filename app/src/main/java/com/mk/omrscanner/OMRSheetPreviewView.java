package com.mk.omrscanner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public class OMRSheetPreviewView extends View {

    private String examTitle = "Midterm Exam";
    private String subtitle = "";
    private String footerText = "";
    private int selectedQuestions = 100;
    private int selectedColumns = 4;
    private int selectedIdDigits = 6;
    private List<String> customFieldsList = new ArrayList<>();

    private Paint paintSolidBlack;
    private Paint paintStrokeBlack;
    private Paint paintFillGrey;
    private Paint paintTextBold;
    private Paint paintTextRegular;
    private Paint paintTextSmall;

    public OMRSheetPreviewView(Context context) {
        super(context);
        init();
    }

    public OMRSheetPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public OMRSheetPreviewView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paintSolidBlack = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintSolidBlack.setColor(Color.BLACK);
        paintSolidBlack.setStyle(Paint.Style.FILL);

        paintStrokeBlack = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintStrokeBlack.setColor(Color.BLACK);
        paintStrokeBlack.setStyle(Paint.Style.STROKE);
        paintStrokeBlack.setStrokeWidth(1.0f);

        paintFillGrey = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintFillGrey.setColor(Color.WHITE);
        paintFillGrey.setStyle(Paint.Style.FILL);

        paintTextBold = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintTextBold.setColor(Color.BLACK);
        paintTextBold.setFakeBoldText(true);

        paintTextRegular = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintTextRegular.setColor(Color.BLACK);

        paintTextSmall = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintTextSmall.setColor(Color.DKGRAY);
    }

    public void setSheetData(String examTitle, String subtitle, String footerText, int questions, int columns, int idDigits, List<String> customFields) {
        this.examTitle = TextUtils.isEmpty(examTitle) ? "Midterm Exam" : examTitle;
        this.subtitle = subtitle;
        this.footerText = footerText;
        this.selectedQuestions = AnswerKeyManager.normalizeQuestionsCount(questions);
        this.selectedColumns = AnswerKeyManager.columnsForQuestionCount(this.selectedQuestions);
        this.selectedIdDigits = idDigits;
        this.customFieldsList = customFields != null ? customFields : new ArrayList<>();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int viewWidth = getWidth();
        int viewHeight = getHeight();

        // Target A4 Canvas proportions: 595 x 842 points
        float targetWidth = 595f;
        float targetHeight = 842f;

        float scaleX = (float) viewWidth / targetWidth;
        float scaleY = (float) viewHeight / targetHeight;
        float scale = Math.min(scaleX, scaleY);

        float dx = (viewWidth - (targetWidth * scale)) / 2f;
        float dy = (viewHeight - (targetHeight * scale)) / 2f;

        // Draw outside backdrop color (pure white)
        canvas.drawColor(Color.WHITE);

        canvas.save();
        canvas.translate(dx, dy);
        canvas.scale(scale, scale);

        // Fill background of paper sheet (white)
        Paint bgPaint = new Paint();
        bgPaint.setColor(Color.WHITE);
        canvas.drawRect(0, 0, targetWidth, targetHeight, bgPaint);

        // Draw thin boundary line around sheet to outline A4 page bounds
        canvas.drawRect(0, 0, targetWidth, targetHeight, paintStrokeBlack);

        // Corner Registration Blocks (20x20 squares)
        canvas.drawRect(30, 40, 50, 60, paintSolidBlack); // Top-left
        canvas.drawRect(545, 40, 565, 60, paintSolidBlack); // Top-right
        canvas.drawRect(30, 782, 50, 802, paintSolidBlack); // Bottom-left
        canvas.drawRect(545, 782, 565, 802, paintSolidBlack); // Bottom-right

        // Left & Right Timing Tracks (small black squares 4x4)
        for (int y = 80; y <= 760; y += 15) {
            canvas.drawRect(38, y, 42, y + 4, paintSolidBlack); // Left timing
            canvas.drawRect(553, y, 557, y + 4, paintSolidBlack); // Right timing
        }

        // --- DRAW HEADER/METADATA INFO BOX ON THE LEFT ---
        // Outline box coordinates: (60, 50) to (400, 195)
        canvas.drawRect(60, 50, 400, 195, paintStrokeBlack);

        // Draw title text centered inside the info box
        paintTextBold.setTextSize(18f);
        float titleW = paintTextBold.measureText(examTitle);
        canvas.drawText(examTitle, 60 + (340 - titleW)/2, 85, paintTextBold);

        // Draw custom fields (Name, Class) dynamically inside the info box
        paintTextBold.setTextSize(11f);
        int customFieldsCount = customFieldsList.size();
        for (int fIndex = 0; fIndex < Math.min(customFieldsCount, 3); fIndex++) {
            String label = customFieldsList.get(fIndex) + ":";
            float labelY = 120 + (fIndex * 30);
            canvas.drawText(label, 75, labelY, paintTextBold);
            canvas.drawLine(135, labelY, 385, labelY, paintStrokeBlack);
        }

        // Subtitle inside header
        if (!TextUtils.isEmpty(subtitle)) {
            paintTextRegular.setTextSize(10f);
            float subW = paintTextRegular.measureText(subtitle);
            canvas.drawText(subtitle, 60 + (340 - subW)/2, 102, paintTextRegular);
        }

        // --- DRAW STUDENT ID SECTION ON THE RIGHT ---
        paintTextBold.setTextSize(11f);
        canvas.drawText("Student ID:", 420, 42, paintTextBold);
        
        // Draw 4 or 6 digit boxes aligned directly above bubble columns
        int idDigits = selectedIdDigits;
        for (int col = 0; col < idDigits; col++) {
            float colX = 456 + (col * 14);
            float boxLeft = colX - 5;
            canvas.drawRect(boxLeft, 50, boxLeft + 10, 60, paintStrokeBlack);
        }

        // Student ID Bubbles (close gap from numbers 0-9)
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

            // Header bar rect
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
                // 2-column: center the 4 bubbles more tightly within the wide column
                bubblePitch = 22f;
                float totalBubblesWidth = 3 * bubblePitch; // span from center of A to center of D
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

        // Draw Footer Mark guidelines
        if (!TextUtils.isEmpty(footerText)) {
            paintTextRegular.setTextSize(9f);
            String footerString = "How to mark:  ● good  ·  avoid:  ⊙ partial  ⊗ cross  ⊘ tick  ·  " + footerText;
            float footerW = paintTextRegular.measureText(footerString);
            canvas.drawText(footerString, (targetWidth - footerW)/2, 815, paintTextRegular);
        }

        canvas.restore();
    }
}
