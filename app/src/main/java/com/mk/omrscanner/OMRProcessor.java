package com.mk.omrscanner;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

public class OMRProcessor {

    private static final String TAG = "OMRProcessor";

    /** BT.709 perceptually-weighted luminance — critical for pencil mark detection. */
    private static int bt709Luminance(int pixel) {
        int r = (pixel >> 16) & 0xFF;
        int g = (pixel >> 8) & 0xFF;
        int b = pixel & 0xFF;
        return (int)(0.2126 * r + 0.7152 * g + 0.0722 * b);
    }

    /** Bubble radius constant matching the PDF generator (ConfigureSheetActivity L488). */
    private static final float PDF_BUBBLE_RADIUS_PT = 6.0f;

    /**
     * Analyzes an OMR sheet bitmap to detect which bubbles are filled.
     * Uses bulk getPixels() for maximum performance instead of per-pixel getPixel() calls.
     */
    public static List<boolean[]> detectFilledBubbles(Bitmap bitmap, int totalQuestions, int columns) {
        List<boolean[]> results = new ArrayList<>();
        if (bitmap == null) return results;
        totalQuestions = AnswerKeyManager.normalizeQuestionsCount(totalQuestions);
        columns = AnswerKeyManager.columnsForQuestionCount(totalQuestions);

        // ── FAST PATH: bulk-load all pixels into a flat int[] array once ──────────────────
        final int bw = bitmap.getWidth();
        final int bh = bitmap.getHeight();
        final int[] pixels = new int[bw * bh];
        bitmap.getPixels(pixels, 0, bw, 0, 0, bw, bh);

        // Ideal markers in 595×842 PDF point space (after perspective correction the markers
        // are physically at these exact coordinates — no secondary search needed).
        float[][] idealMarkers = {
            {40f, 50f},   // Top-Left
            {555f, 50f},  // Top-Right
            {40f, 792f},  // Bottom-Left
            {555f, 792f}  // Bottom-Right
        };
        // realMarkers == idealMarkers after applyPerspectiveTransformToTemplate
        float[][] realMarkers = idealMarkers;

        int qPerCol = (int) Math.ceil((double) totalQuestions / columns);

        for (int q = 0; q < totalQuestions; q++) {
            boolean[] options = new boolean[4];
            int col = q / qPerCol;
            int qIndexInCol = q % qPerCol;

            float colWidth = 230f;
            float spacing = 20f;
            if (columns == 4) {
                colWidth = 110f;
                spacing = 15f;
            }

            float colLeft = 60 + col * (colWidth + spacing);
            float tableTop = 220;
            float rowY = tableTop + 18 + (qIndexInCol * 18) + 9;

            float bubbleStart;
            float bubblePitch;

            if (columns == 4) {
                bubblePitch = colWidth / 6f;
                bubbleStart = colLeft + bubblePitch * 1.8f;
            } else {
                bubblePitch = 22f;
                float totalBubblesWidth = 3 * bubblePitch;
                bubbleStart = colLeft + (colWidth / 2f) - (totalBubblesWidth / 2f);
            }

            for (int b = 0; b < 4; b++) {
                float idealX = bubbleStart + (b * bubblePitch);
                float[] px = mapIdealToReal(idealX, rowY, idealMarkers, realMarkers);
                options[b] = isBubbleFilledFast(pixels, bw, bh, (int) px[0], (int) px[1]);
            }
            results.add(options);
        }

        return results;
    }

    /**
     * Strictly detects if all 4 OMR sheet corner markers are present and correctly aligned.
     * Returns the 4 real marker coordinates if valid, otherwise returns null.
     * Uses bulk pixel array for speed.
     */
    public static float[][] detectMarkersStrict(Bitmap bitmap) {
        if (bitmap == null) return null;

        final int bw = bitmap.getWidth();
        final int bh = bitmap.getHeight();
        final int[] pixels = new int[bw * bh];
        bitmap.getPixels(pixels, 0, bw, 0, 0, bw, bh);

        float[][] idealMarkers = {
            {40f, 50f},
            {555f, 50f},
            {40f, 792f},
            {555f, 792f}
        };

        float[][] realMarkers = new float[4][2];
        float scaleX = bw / 595f;
        float scaleY = bh / 842f;

        for (int i = 0; i < 4; i++) {
            float idealX = idealMarkers[i][0];
            float idealY = idealMarkers[i][1];

            int searchR = (int) (120 * scaleX);
            int centerX = (int) (idealX * scaleX);
            int centerY = (int) (idealY * scaleY);

            double windowAvg = getAverageLuminanceFast(pixels, bw, bh, centerX, centerY, searchR);
            if (windowAvg < 80) return null;

            long sumX = 0, sumY = 0, count = 0;

            int x0 = Math.max(0, centerX - searchR);
            int x1 = Math.min(bw - 1, centerX + searchR);
            int y0 = Math.max(0, centerY - searchR);
            int y1 = Math.min(bh - 1, centerY + searchR);

            for (int y = y0; y <= y1; y++) {
                final int rowOffset = y * bw;
                for (int x = x0; x <= x1; x++) {
                    int p = pixels[rowOffset + x];
                    if (bt709Luminance(p) < 130) {
                        sumX += x;
                        sumY += y;
                        count++;
                    }
                }
            }

            double minPixels = 5 * scaleX * scaleY;
            double maxPixels = 2500 * scaleX * scaleY;
            if (count < minPixels || count > maxPixels) return null;

            realMarkers[i][0] = (float) sumX / count;
            realMarkers[i][1] = (float) sumY / count;
        }

        // Geometry verification
        float w = bw;
        float h = bh;
        float dxTop = realMarkers[1][0] - realMarkers[0][0];
        float dyTop = Math.abs(realMarkers[1][1] - realMarkers[0][1]);
        float dxLeft = Math.abs(realMarkers[2][0] - realMarkers[0][0]);
        float dyLeft = realMarkers[2][1] - realMarkers[0][1];

        if (dxTop < w * 0.35f) return null;
        if (dyLeft < h * 0.35f) return null;
        if (dyTop > w * 0.18f) return null;
        if (dxLeft > h * 0.18f) return null;

        return realMarkers;
    }

    /**
     * Detects which of the 4 corner markers are individually present.
     * Returns a boolean[4] indicating [TL, TR, BL, BR] found status.
     * Uses bulk pixel array for speed.
     */
    public static boolean[] detectMarkersIndividual(Bitmap bitmap) {
        boolean[] found = new boolean[4];
        if (bitmap == null) return found;

        final int bw = bitmap.getWidth();
        final int bh = bitmap.getHeight();
        final int[] pixels = new int[bw * bh];
        bitmap.getPixels(pixels, 0, bw, 0, 0, bw, bh);

        float[][] idealMarkers = {
            {40f, 50f},
            {555f, 50f},
            {40f, 792f},
            {555f, 792f}
        };

        float scaleX = bw / 595f;
        float scaleY = bh / 842f;

        for (int i = 0; i < 4; i++) {
            float idealX = idealMarkers[i][0];
            float idealY = idealMarkers[i][1];

            int searchR = (int) (120 * scaleX);
            int centerX = (int) (idealX * scaleX);
            int centerY = (int) (idealY * scaleY);

            double windowAvg = getAverageLuminanceFast(pixels, bw, bh, centerX, centerY, searchR);
            if (windowAvg < 80) continue;

            long count = 0;
            int x0 = Math.max(0, centerX - searchR);
            int x1 = Math.min(bw - 1, centerX + searchR);
            int y0 = Math.max(0, centerY - searchR);
            int y1 = Math.min(bh - 1, centerY + searchR);

            for (int y = y0; y <= y1; y++) {
                final int rowOffset = y * bw;
                for (int x = x0; x <= x1; x++) {
                    int p = pixels[rowOffset + x];
                    if (bt709Luminance(p) < 130) {
                        count++;
                    }
                }
            }

            double minPixels = 5 * scaleX * scaleY;
            double maxPixels = 2500 * scaleX * scaleY;
            if (count >= minPixels && count <= maxPixels) {
                found[i] = true;
            }
        }

        return found;
    }

    /**
     * Loosely checks if at least 2 OMR corner markers are present.
     */
    public static boolean detectMarkersLoose(Bitmap bitmap) {
        boolean[] individual = detectMarkersIndividual(bitmap);
        int count = 0;
        for (boolean b : individual) {
            if (b) count++;
        }
        return count >= 2;
    }

    /**
     * Uses bilinear mapping to adjust ideal coordinates to the actual scanned image perspective.
     */
    private static float[] mapIdealToReal(float x, float y, float[][] ideal, float[][] real) {
        float u = (x - ideal[0][0]) / (ideal[1][0] - ideal[0][0]);
        float v = (y - ideal[0][1]) / (ideal[2][1] - ideal[0][1]);

        float realX = (1 - u) * (1 - v) * real[0][0] + u * (1 - v) * real[1][0]
                + (1 - u) * v * real[2][0] + u * v * real[3][0];
        float realY = (1 - u) * (1 - v) * real[0][1] + u * (1 - v) * real[1][1]
                + (1 - u) * v * real[2][1] + u * v * real[3][1];

        return new float[]{realX, realY};
    }

    /**
     * Fast bubble-fill check using a pre-loaded pixel array.
     * Replaces the old isBubbleFilledProper(Bitmap,int,int) which called getPixel() in a loop.
     */
    public static boolean isBubbleFilledFast(int[] pixels, int bw, int bh, int centerX, int centerY) {
        float scale = bw / 595f;
        // Derive sampling radius from the actual PDF bubble size (6pt radius = 12pt diameter)
        float bubbleDiameterPx = (PDF_BUBBLE_RADIUS_PT * 2f / 595f) * bw;
        int innerR = Math.max(3, (int)(bubbleDiameterPx * 0.38f)); // Sample ~76% of bubble diameter
        int outerR = Math.max(5, (int)(bubbleDiameterPx * 0.75f)); // Background ring outside bubble

        double outerAvg = getAverageLuminanceFast(pixels, bw, bh, centerX, centerY, outerR);
        // Use a wider background ring for more stable reference
        int bgR = Math.max(outerR + 2, (int)(bubbleDiameterPx * 0.90f));
        double bgAvg = getAverageLuminanceFast(pixels, bw, bh, centerX, centerY, bgR);
        // Dynamic threshold: 60% of local background with a floor relative to contrast range
        int adaptiveThreshold = (int)(bgAvg * 0.60);
        int dynamicFloor = Math.max(40, (int)(bgAvg * 0.25)); // Floor scales with lighting
        if (adaptiveThreshold < dynamicFloor) adaptiveThreshold = dynamicFloor;

        int darkCount = 0;
        int totalCount = 0;
        int[] quadDark = new int[4];
        int[] quadTotal = new int[4];

        int r2 = innerR * innerR;
        int x0 = Math.max(0, centerX - innerR);
        int x1 = Math.min(bw - 1, centerX + innerR);
        int y0 = Math.max(0, centerY - innerR);
        int y1 = Math.min(bh - 1, centerY + innerR);

        for (int dy = y0 - centerY; dy <= y1 - centerY; dy++) {
            int y = centerY + dy;
            final int rowOffset = y * bw;
            for (int dx = x0 - centerX; dx <= x1 - centerX; dx++) {
                if (dx * dx + dy * dy <= r2) {
                    int x = centerX + dx;
                    int p = pixels[rowOffset + x];
                    int luminance = bt709Luminance(p);

                    totalCount++;
                    int quad = 0;
                    if (dx >= 0 && dy < 0) quad = 1;
                    else if (dx < 0 && dy >= 0) quad = 2;
                    else if (dx >= 0 && dy >= 0) quad = 3;

                    quadTotal[quad]++;
                    if (luminance < adaptiveThreshold) {
                        darkCount++;
                        quadDark[quad]++;
                    }
                }
            }
        }

        if (totalCount == 0) return false;

        double overallRatio = (double) darkCount / totalCount;

        boolean isUniform = true;
        for (int i = 0; i < 4; i++) {
            if (quadTotal[i] > 0) {
                double qRatio = (double) quadDark[i] / quadTotal[i];
                if (qRatio < 0.15) {
                    isUniform = false;
                    break;
                }
            }
        }

        return (overallRatio >= 0.28 && isUniform) || (overallRatio > 0.55);
    }

    /**
     * Legacy overload — creates a pixel array on demand.
     * Prefer isBubbleFilledFast(int[], int, int, int, int) when you already have the array.
     */
    public static boolean isBubbleFilledProper(Bitmap bitmap, int centerX, int centerY) {
        if (bitmap == null) return false;
        final int bw = bitmap.getWidth();
        final int bh = bitmap.getHeight();
        final int[] pixels = new int[bw * bh];
        bitmap.getPixels(pixels, 0, bw, 0, 0, bw, bh);
        return isBubbleFilledFast(pixels, bw, bh, centerX, centerY);
    }

    /**
     * Scans the Student ID section of the OMR sheet.
     * Uses bulk pixel array for fast lookup.
     */
    public static String scanStudentID(Bitmap bitmap, int maxDigits) {
        if (bitmap == null) return "";

        final int bw = bitmap.getWidth();
        final int bh = bitmap.getHeight();
        final int[] pixels = new int[bw * bh];
        bitmap.getPixels(pixels, 0, bw, 0, 0, bw, bh);

        float[][] idealMarkers = {
            {40f, 50f},
            {555f, 50f},
            {40f, 792f},
            {555f, 792f}
        };
        float[][] realMarkers = idealMarkers;

        if (maxDigits != 4 && maxDigits != 6) maxDigits = 6;

        StringBuilder sb = new StringBuilder();

        for (int col = 0; col < maxDigits; col++) {
            float idealX = 456f + (col * 14f);
            int selectedRow = -1;
            int filledCount = 0;

            for (int row = 0; row < 10; row++) {
                float idealY = 76f + (row * 11f);
                float[] pixelCoord = mapIdealToReal(idealX, idealY, idealMarkers, realMarkers);
                if (isBubbleFilledFast(pixels, bw, bh, (int) pixelCoord[0], (int) pixelCoord[1])) {
                    selectedRow = row;
                    filledCount++;
                }
            }

            if (filledCount == 1) {
                sb.append(selectedRow);
            } else {
                sb.append(" ");
            }
        }

        return sb.toString().trim();
    }

    /**
     * Overloaded scanStudentID for backward compatibility (defaults to 6 digits).
     */
    public static String scanStudentID(Bitmap bitmap) {
        return scanStudentID(bitmap, 6);
    }

    /**
     * Fast average luminance using a pre-loaded pixel array.
     * Replaces per-pixel getPixel() calls in getAverageLuminance.
     */
    static double getAverageLuminanceFast(int[] pixels, int bw, int bh, int cx, int cy, int r) {
        long sum = 0;
        int count = 0;
        int x0 = Math.max(0, cx - r);
        int x1 = Math.min(bw - 1, cx + r);
        int y0 = Math.max(0, cy - r);
        int y1 = Math.min(bh - 1, cy + r);
        for (int y = y0; y <= y1; y++) {
            final int rowOffset = y * bw;
            for (int x = x0; x <= x1; x++) {
                int p = pixels[rowOffset + x];
                sum += bt709Luminance(p);
                count++;
            }
        }
        return count == 0 ? 0 : (double) sum / count;
    }

    /**
     * Generates an image overlaying the grading results on the scanned sheet.
     */
    public static Bitmap generateGradedImage(Bitmap source, List<boolean[]> correctOptionsList,
                                             List<boolean[]> studentOptionsList, int columns) {
        if (source == null) return null;

        Bitmap output = source.copy(Bitmap.Config.ARGB_8888, true);
        android.graphics.Canvas canvas = new android.graphics.Canvas(output);

        android.graphics.Paint paintGreen = new android.graphics.Paint();
        paintGreen.setColor(Color.parseColor("#10B981"));
        paintGreen.setStyle(android.graphics.Paint.Style.FILL);
        paintGreen.setAntiAlias(true);

        android.graphics.Paint paintRed = new android.graphics.Paint();
        paintRed.setColor(Color.parseColor("#EF4444"));
        paintRed.setStyle(android.graphics.Paint.Style.FILL);
        paintRed.setAntiAlias(true);

        android.graphics.Paint paintYellow = new android.graphics.Paint();
        paintYellow.setColor(Color.parseColor("#FBBF24"));
        paintYellow.setStyle(android.graphics.Paint.Style.STROKE);
        paintYellow.setStrokeWidth(5f);
        paintYellow.setAntiAlias(true);

        android.graphics.Paint paintWhiteIcon = new android.graphics.Paint();
        paintWhiteIcon.setColor(Color.WHITE);
        paintWhiteIcon.setStrokeWidth(4f);
        paintWhiteIcon.setStyle(android.graphics.Paint.Style.STROKE);
        paintWhiteIcon.setAntiAlias(true);

        float scaleX = source.getWidth() / 595f;
        float scaleY = source.getHeight() / 842f;

        int totalQuestions = correctOptionsList.size();
        columns = AnswerKeyManager.columnsForQuestionCount(totalQuestions);
        int qPerCol = (int) Math.ceil((double) totalQuestions / columns);

        for (int q = 0; q < totalQuestions; q++) {
            boolean[] correctOptions = correctOptionsList.get(q);
            boolean[] studentOptions = q < studentOptionsList.size() ? studentOptionsList.get(q) : new boolean[4];

            int col = q / qPerCol;
            int qIndexInCol = q % qPerCol;

            float colWidth = 230f;
            float spacing = 20f;
            if (columns == 4) {
                colWidth = 110f;
                spacing = 15f;
            }

            float colLeft = 60 + col * (colWidth + spacing);
            float tableTop = 220;
            float rowY = tableTop + 18 + (qIndexInCol * 18) + 9;

            float bubbleStart;
            float bubblePitch;

            if (columns == 4) {
                bubblePitch = colWidth / 6f;
                bubbleStart = colLeft + bubblePitch * 1.8f;
            } else {
                bubblePitch = 22f;
                float totalBubblesWidth = 3 * bubblePitch;
                bubbleStart = colLeft + (colWidth / 2f) - (totalBubblesWidth / 2f);
            }

            for (int b = 0; b < 4; b++) {
                float idealX = bubbleStart + (b * bubblePitch);
                float cx = idealX * scaleX;
                float cy = rowY * scaleY;
                float radius = 7f * scaleX;

                boolean isCorrect = correctOptions[b];
                boolean isMarked = studentOptions[b];

                if (isMarked && isCorrect) {
                    canvas.drawCircle(cx, cy, radius, paintGreen);
                    android.graphics.Path path = new android.graphics.Path();
                    path.moveTo(cx - radius * 0.4f, cy);
                    path.lineTo(cx - radius * 0.1f, cy + radius * 0.4f);
                    path.lineTo(cx + radius * 0.5f, cy - radius * 0.4f);
                    canvas.drawPath(path, paintWhiteIcon);
                } else if (isMarked && !isCorrect) {
                    canvas.drawCircle(cx, cy, radius, paintRed);
                    canvas.drawLine(cx - radius * 0.4f, cy - radius * 0.4f,
                            cx + radius * 0.4f, cy + radius * 0.4f, paintWhiteIcon);
                    canvas.drawLine(cx + radius * 0.4f, cy - radius * 0.4f,
                            cx - radius * 0.4f, cy + radius * 0.4f, paintWhiteIcon);
                } else if (!isMarked && isCorrect) {
                    canvas.drawCircle(cx, cy, radius, paintYellow);
                }
            }
        }
        return output;
    }
}
