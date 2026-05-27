package com.mk.omrscanner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * A custom view that draws a translucent polygon over the detected document corners.
 * This provides visual feedback for full-frame auto-detection.
 */
public class DocumentOverlayView extends View {

    private Paint fillPaint;
    private Paint strokePaint;
    private Paint dotPaint;
    private Path documentPath;
    private float[][] corners; // [4][2] array of corner coordinates

    public DocumentOverlayView(Context context) {
        super(context);
        init();
    }

    public DocumentOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setColor(Color.parseColor("#4D3B82F6")); // Semi-transparent blue
        fillPaint.setStyle(Paint.Style.FILL);

        strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setColor(Color.parseColor("#3B82F6")); // Solid blue
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(6f);

        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(Color.WHITE);
        dotPaint.setStyle(Paint.Style.FILL);

        documentPath = new Path();
    }

    /**
     * Set the 4 corner points of the detected document and trigger a redraw.
     * @param points Array of 4 points [x, y], e.g., { {x1, y1}, {x2, y2}, {x3, y3}, {x4, y4} }
     */
    public void setDocumentCorners(float[][] points) {
        this.corners = points;
        invalidate();
    }

    /**
     * Clear the overlay when no document is detected.
     */
    public void clearOverlay() {
        this.corners = null;
        invalidate();
    }

    /**
     * Change the overlay color to indicate successful capture (e.g., green).
     */
    public void setSuccessColor() {
        fillPaint.setColor(Color.parseColor("#4D10B981")); // Semi-transparent green
        strokePaint.setColor(Color.parseColor("#10B981"));
        invalidate();
    }

    /**
     * Reset the overlay color back to scanning (blue).
     */
    public void setScanningColor() {
        fillPaint.setColor(Color.parseColor("#4D3B82F6"));
        strokePaint.setColor(Color.parseColor("#3B82F6"));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (corners != null && corners.length == 4) {
            documentPath.reset();
            documentPath.moveTo(corners[0][0], corners[0][1]); // Top-left
            documentPath.lineTo(corners[1][0], corners[1][1]); // Top-right
            documentPath.lineTo(corners[3][0], corners[3][1]); // Bottom-right
            documentPath.lineTo(corners[2][0], corners[2][1]); // Bottom-left
            documentPath.close();

            canvas.drawPath(documentPath, fillPaint);
            canvas.drawPath(documentPath, strokePaint);

            // Draw small white dots at the corners
            for (int i = 0; i < 4; i++) {
                canvas.drawCircle(corners[i][0], corners[i][1], 10f, dotPaint);
            }
        }
    }
}
