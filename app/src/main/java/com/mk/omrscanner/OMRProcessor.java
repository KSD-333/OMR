package com.mk.omrscanner;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

public class OMRProcessor {

    private static final String TAG = "OMRProcessor";

    /**
     * Analyzes an OMR sheet bitmap to detect which bubbles are filled.
     * Uses 4-corner marker detection to align the coordinate system for high accuracy.
     * If OpenCV is available, applies perspective transform first for best results.
     */
    public static List<boolean[]> detectFilledBubbles(Bitmap bitmap, int totalQuestions, int columns) {
        List<boolean[]> results = new ArrayList<>();
        if (bitmap == null) return results;
        totalQuestions = AnswerKeyManager.normalizeQuestionsCount(totalQuestions);
        columns = AnswerKeyManager.columnsForQuestionCount(totalQuestions);

        // Use the bitmap directly — contrast enhancement should be done ONCE by the caller
        // to avoid double-enhancement artifacts
        Bitmap processedBitmap = bitmap;

        // 1. Find the 4 corner markers in the actual image for registration
        // Ideal markers centers in 595x842 PDF points
        float[][] idealMarkers = {
            {40f, 50f},   // Top-Left
            {555f, 50f},  // Top-Right
            {40f, 792f},  // Bottom-Left
            {555f, 792f}  // Bottom-Right
        };

        float[][] realMarkers = new float[4][2];
        for (int i = 0; i < 4; i++) {
            // Since we use applyPerspectiveTransformToTemplate, the markers are physically locked 
            // to these exact coordinates. No secondary search is needed, which prevents snapping to shadows.
            realMarkers[i][0] = idealMarkers[i][0];
            realMarkers[i][1] = idealMarkers[i][1];
        }

        // 2. Map questions using the adjusted coordinate system
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
                
                // Map ideal PDF point to actual bitmap pixel using 4-corner bilinear interpolation
                float[] pixelCoord = mapIdealToReal(idealX, rowY, idealMarkers, realMarkers);
                
                options[b] = isBubbleFilledProper(processedBitmap, (int)pixelCoord[0], (int)pixelCoord[1]);
            }
            results.add(options);
        }

        return results;
    }

    /**
     * Strictly detects if all 4 OMR sheet corner markers are present and correctly aligned.
     * Returns the 4 real marker coordinates if valid, otherwise returns null.
     */
    public static float[][] detectMarkersStrict(Bitmap bitmap) {
        if (bitmap == null) return null;

        float[][] idealMarkers = {
            {40f, 50f},   // Top-Left
            {555f, 50f},  // Top-Right
            {40f, 792f},  // Bottom-Left
            {555f, 792f}  // Bottom-Right
        };

        float[][] realMarkers = new float[4][2];
        float scaleX = bitmap.getWidth() / 595f;
        float scaleY = bitmap.getHeight() / 842f;

        for (int i = 0; i < 4; i++) {
            float idealX = idealMarkers[i][0];
            float idealY = idealMarkers[i][1];

            // Increased search radius to handle paper at varying distances
            int searchR = (int)(120 * scaleX);
            int centerX = (int)(idealX * scaleX);
            int centerY = (int)(idealY * scaleY);

            // Guard against solid black lens cover or total darkness (average brightness < 80)
            double windowAvg = getAverageLuminance(bitmap, centerX, centerY, searchR);
            if (windowAvg < 80) {
                return null; 
            }

            long sumX = 0, sumY = 0, count = 0;

            for (int x = centerX - searchR; x <= centerX + searchR; x++) {
                for (int y = centerY - searchR; y <= centerY + searchR; y++) {
                    if (x >= 0 && x < bitmap.getWidth() && y >= 0 && y < bitmap.getHeight()) {
                        int pixel = bitmap.getPixel(x, y);
                        // Relaxed threshold to capture dark gray pen/ink markers
                        if ((Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3 < 130) {
                            sumX += x;
                            sumY += y;
                            count++;
                        }
                    }
                }
            }

            // Expanded range: A valid OMR dot should occupy between 5 and 2500 pixels at scaled resolution
            double minPixels = 5 * scaleX * scaleY;
            double maxPixels = 2500 * scaleX * scaleY;
            if (count < minPixels || count > maxPixels) {
                return null; // Dot missing, or size is invalid (too big/noisy)
            }

            realMarkers[i][0] = (float)sumX / count;
            realMarkers[i][1] = (float)sumY / count;
        }

        // Geometry verification to confirm page alignment inside frame
        float w = bitmap.getWidth();
        float h = bitmap.getHeight();

        float dxTop = realMarkers[1][0] - realMarkers[0][0];
        float dyTop = Math.abs(realMarkers[1][1] - realMarkers[0][1]);
        float dxLeft = Math.abs(realMarkers[2][0] - realMarkers[0][0]);
        float dyLeft = realMarkers[2][1] - realMarkers[0][1];

        // Relaxed separation constraint to 35% for capturing from distance
        if (dxTop < w * 0.35f) return null;
        if (dyLeft < h * 0.35f) return null;

        // Allow up to 18% tilt for real-world conditions
        if (dyTop > w * 0.18f) return null;
        if (dxLeft > h * 0.18f) return null;

        return realMarkers;
    }

    /**
     * Detects which of the 4 corner markers are individually present.
     * Returns a boolean[4] indicating [TL, TR, BL, BR] found status.
     * Used for per-corner visual feedback in the camera UI.
     */
    public static boolean[] detectMarkersIndividual(Bitmap bitmap) {
        boolean[] found = new boolean[4];
        if (bitmap == null) return found;

        float[][] idealMarkers = {
            {40f, 50f},   // Top-Left
            {555f, 50f},  // Top-Right
            {40f, 792f},  // Bottom-Left
            {555f, 792f}  // Bottom-Right
        };

        float scaleX = bitmap.getWidth() / 595f;
        float scaleY = bitmap.getHeight() / 842f;

        for (int i = 0; i < 4; i++) {
            float idealX = idealMarkers[i][0];
            float idealY = idealMarkers[i][1];

            int searchR = (int)(120 * scaleX);
            int centerX = (int)(idealX * scaleX);
            int centerY = (int)(idealY * scaleY);

            // Fast brightness check
            double windowAvg = getAverageLuminance(bitmap, centerX, centerY, searchR);
            if (windowAvg < 80) {
                continue;
            }

            long count = 0;

            for (int x = centerX - searchR; x <= centerX + searchR; x++) {
                for (int y = centerY - searchR; y <= centerY + searchR; y++) {
                    if (x >= 0 && x < bitmap.getWidth() && y >= 0 && y < bitmap.getHeight()) {
                        int pixel = bitmap.getPixel(x, y);
                        if ((Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3 < 130) {
                            count++;
                        }
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
     * Loosely checks if at least 2 OMR corner markers are present,
     * indicating a sheet is roughly in view.
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
     * Finds the darkest centroid in a window around the expected marker location.
     */
    private static float[] findMarkerCentroid(Bitmap bitmap, float idealX, float idealY) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        float scaleX = w / 595f;
        float scaleY = h / 842f;

        // Define the corner target point we want to be closest to
        float targetX = 0;
        float targetY = 0;
        if (idealX > 300) targetX = w;
        if (idealY > 400) targetY = h;

        int searchR = (int) (180 * scaleX);
        int centerX = (int) (idealX * scaleX);
        int centerY = (int) (idealY * scaleY);

        int closestX = -1;
        int closestY = -1;
        double minDistance = Double.MAX_VALUE;

        // 1. Find the dark pixel closest to the page corner in the search window
        for (int x = centerX - searchR; x <= centerX + searchR; x++) {
            for (int y = centerY - searchR; y <= centerY + searchR; y++) {
                if (x >= 0 && x < w && y >= 0 && y < h) {
                    int pixel = bitmap.getPixel(x, y);
                    int luminance = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3;

                    if (luminance < 110) {
                        double dist = Math.pow(x - targetX, 2) + Math.pow(y - targetY, 2);
                        if (dist < minDistance) {
                            minDistance = dist;
                            closestX = x;
                            closestY = y;
                        }
                    }
                }
            }
        }

        // If no dark pixel found, fall back to expected center
        if (closestX == -1) {
            return new float[]{centerX, centerY};
        }

        // 2. Compute the centroid of dark pixels in a small window around the closest pixel
        long sumX = 0, sumY = 0, count = 0;
        int r = (int) (18 * scaleX);
        for (int x = closestX - r; x <= closestX + r; x++) {
            for (int y = closestY - r; y <= closestY + r; y++) {
                // BUG FIX: was `y < w`, now correctly `y < h`
                if (x >= 0 && x < w && y >= 0 && y < h) {
                    int pixel = bitmap.getPixel(x, y);
                    int luminance = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3;
                    if (luminance < 125) {
                        sumX += x;
                        sumY += y;
                        count++;
                    }
                }
            }
        }

        if (count == 0) return new float[]{closestX, closestY};
        return new float[]{(float) sumX / count, (float) sumY / count};
    }

    /**
     * Uses bilinear mapping to adjust ideal coordinates to the actual scanned image perspective.
     */
    private static float[] mapIdealToReal(float x, float y, float[][] ideal, float[][] real) {
        // Normalize x, y to [0, 1] relative to the ideal marker bounds
        float u = (x - ideal[0][0]) / (ideal[1][0] - ideal[0][0]);
        float v = (y - ideal[0][1]) / (ideal[2][1] - ideal[0][1]);

        // Bilinear interpolation between the 4 detected marker points
        float realX = (1-u)*(1-v)*real[0][0] + u*(1-v)*real[1][0] + (1-u)*v*real[2][0] + u*v*real[3][0];
        float realY = (1-u)*(1-v)*real[0][1] + u*(1-v)*real[1][1] + (1-u)*v*real[2][1] + u*v*real[3][1];

        return new float[]{realX, realY};
    }

    /**
     * Strictly verifies if a bubble is properly filled in a round and dark shape.
     * Evaluates fill ratio and uniformity/roundness across quadrants.
     */
    public static boolean isBubbleFilledProper(Bitmap bitmap, int centerX, int centerY) {
        float scale = bitmap.getWidth() / 595f;
        int innerR = Math.max(2, (int)(5.0f * scale)); // Check ~80% of the bubble interior for reliable detection
        int outerR = Math.max(4, (int)(8f * scale));   // Sample the white paper outside — stays within column bounds

        // 1. Get reference background luminance from the surrounding area
        double outerAvg = getAverageLuminance(bitmap, centerX, centerY, outerR);

        // 2. Define adaptive threshold based on background brightness
        // A pixel is "dark" if it is at least 45% darker than the background paper
        // This makes it immune to shadows which darken both the paper and the bubble equally.
        int adaptiveThreshold = (int)(outerAvg * 0.55);
        // Absolute minimum threshold to avoid detecting noise on very bright paper
        if (adaptiveThreshold < 40) adaptiveThreshold = 40;

        int darkCount = 0;
        int totalCount = 0;

        int[] quadDark = new int[4];
        int[] quadTotal = new int[4];

        for (int dx = -innerR; dx <= innerR; dx++) {
            for (int dy = -innerR; dy <= innerR; dy++) {
                if (dx*dx + dy*dy <= innerR*innerR) {
                    int x = centerX + dx;
                    int y = centerY + dy;
                    if (x >= 0 && x < bitmap.getWidth() && y >= 0 && y < bitmap.getHeight()) {
                        int p = bitmap.getPixel(x, y);
                        int luminance = (Color.red(p) + Color.green(p) + Color.blue(p)) / 3;

                        totalCount++;
                        
                        // Determine quadrant: 0=TL, 1=TR, 2=BL, 3=BR
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
        }

        if (totalCount == 0) return false;

        double overallRatio = (double) darkCount / totalCount;

        // Verify uniformity (roundness):
        // Each quadrant must have a significant amount of dark pixels.
        boolean isUniform = true;
        for (int i = 0; i < 4; i++) {
            if (quadTotal[i] > 0) {
                double qRatio = (double) quadDark[i] / quadTotal[i];
                // Quadrant fill must be at least 15% to ensure it's not a single line or scribble
                if (qRatio < 0.15) {
                    isUniform = false;
                    break;
                }
            }
        }

        // Return true if it satisfies fill density and uniformity, or is extremely dark overall
        return (overallRatio >= 0.28 && isUniform) || (overallRatio > 0.55);
    }

    /**
     * Scans the Student ID section of the OMR sheet.
     * @param bitmap The perspective-corrected 595x842 bitmap
     * @param maxDigits Number of ID digit columns to scan (4 or 6)
     * @return The detected Student ID string
     */
    public static String scanStudentID(Bitmap bitmap, int maxDigits) {
        if (bitmap == null) return "";

        float[][] idealMarkers = {
            {40f, 50f},   // Top-Left
            {555f, 50f},  // Top-Right
            {40f, 792f},  // Bottom-Left
            {555f, 792f}  // Bottom-Right
        };

        float[][] realMarkers = new float[4][2];
        for (int i = 0; i < 4; i++) {
            // Since we use applyPerspectiveTransformToTemplate, the markers are physically locked 
            // to these exact coordinates. No secondary search is needed.
            realMarkers[i][0] = idealMarkers[i][0];
            realMarkers[i][1] = idealMarkers[i][1];
        }

        // Clamp to valid range
        if (maxDigits != 4 && maxDigits != 6) {
            maxDigits = 6;
        }

        StringBuilder sb = new StringBuilder();

        for (int col = 0; col < maxDigits; col++) {
            float idealX = 456f + (col * 14f);
            int selectedRow = -1;
            int filledCount = 0;

            for (int row = 0; row < 10; row++) {
                float idealY = 76f + (row * 11f);
                float[] pixelCoord = mapIdealToReal(idealX, idealY, idealMarkers, realMarkers);
                
                if (isBubbleFilledProper(bitmap, (int)pixelCoord[0], (int)pixelCoord[1])) {
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
     * Compute average luminance of pixels within a square window centered at (cx, cy).
     */
    private static double getAverageLuminance(Bitmap bitmap, int cx, int cy, int r) {
        long sum = 0;
        int count = 0;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int y = cy - r; y <= cy + r; y++) {
                if (x >= 0 && x < bitmap.getWidth() && y >= 0 && y < bitmap.getHeight()) {
                    int p = bitmap.getPixel(x, y);
                    sum += (Color.red(p) + Color.green(p) + Color.blue(p)) / 3;
                    count++;
                }
            }
        }
        return count == 0 ? 0 : (double) sum / count;
    }

    /**
     * Generates an image overlaying the grading results on the scanned sheet.
     */
    public static Bitmap generateGradedImage(Bitmap source, List<boolean[]> correctOptionsList, List<boolean[]> studentOptionsList, int columns) {
        if (source == null) return null;
        
        Bitmap output = source.copy(Bitmap.Config.ARGB_8888, true);
        android.graphics.Canvas canvas = new android.graphics.Canvas(output);
        
        android.graphics.Paint paintGreen = new android.graphics.Paint();
        paintGreen.setColor(Color.parseColor("#10B981")); // Green
        paintGreen.setStyle(android.graphics.Paint.Style.FILL);
        paintGreen.setAntiAlias(true);

        android.graphics.Paint paintRed = new android.graphics.Paint();
        paintRed.setColor(Color.parseColor("#EF4444")); // Red
        paintRed.setStyle(android.graphics.Paint.Style.FILL);
        paintRed.setAntiAlias(true);

        android.graphics.Paint paintYellow = new android.graphics.Paint();
        paintYellow.setColor(Color.parseColor("#FBBF24")); // Yellow
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
                
                // Assuming the source is perfectly aligned/dewarped, we use the ideal coordinates scaled
                float cx = idealX * scaleX;
                float cy = rowY * scaleY;
                float radius = 7f * scaleX; // Base radius

                boolean isCorrect = correctOptions[b];
                boolean isMarked = studentOptions[b];

                if (isMarked && isCorrect) {
                    // Green solid circle with white tick
                    canvas.drawCircle(cx, cy, radius, paintGreen);
                    android.graphics.Path path = new android.graphics.Path();
                    path.moveTo(cx - radius*0.4f, cy);
                    path.lineTo(cx - radius*0.1f, cy + radius*0.4f);
                    path.lineTo(cx + radius*0.5f, cy - radius*0.4f);
                    canvas.drawPath(path, paintWhiteIcon);
                } else if (isMarked && !isCorrect) {
                    // Red solid circle with white cross
                    canvas.drawCircle(cx, cy, radius, paintRed);
                    canvas.drawLine(cx - radius*0.4f, cy - radius*0.4f, cx + radius*0.4f, cy + radius*0.4f, paintWhiteIcon);
                    canvas.drawLine(cx + radius*0.4f, cy - radius*0.4f, cx - radius*0.4f, cy + radius*0.4f, paintWhiteIcon);
                } else if (!isMarked && isCorrect) {
                    // Yellow outlined circle for missed answer
                    canvas.drawCircle(cx, cy, radius, paintYellow);
                }
            }
        }
        return output;
    }
}
