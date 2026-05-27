package com.mk.omrscanner;
import android.graphics.Bitmap;
import android.util.Log;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Encapsulates all OpenCV operations for the OMR Scanner.
 * Provides perspective transform, contour detection, preprocessing, and contrast enhancement.
 */
public class OpenCVHelper {

    private static final String TAG = "OpenCVHelper";
    private static boolean sInitialized = false;

    /**
     * Initialize OpenCV. Must be called before any OpenCV operations.
     * Safe to call multiple times — only initializes once.
     * @return true if OpenCV is ready
     */
    public static boolean initOpenCV() {
        if (sInitialized) return true;
        try {
            sInitialized = OpenCVLoader.initLocal();
            if (sInitialized) {
                Log.i(TAG, "OpenCV initialized successfully: " + Core.VERSION);
            } else {
                Log.e(TAG, "OpenCV initialization failed");
            }
        } catch (Exception e) {
            Log.e(TAG, "OpenCV initialization error", e);
            sInitialized = false;
        }
        return sInitialized;
    }

    public static boolean isInitialized() {
        return sInitialized;
    }

    /**
     * Convert Android Bitmap to OpenCV Mat (RGBA format).
     */
    public static Mat bitmapToMat(Bitmap bitmap) {
        Mat mat = new Mat(bitmap.getHeight(), bitmap.getWidth(), CvType.CV_8UC4);
        Utils.bitmapToMat(bitmap, mat);
        return mat;
    }

    /**
     * Convert OpenCV Mat back to Android Bitmap.
     */
    public static Bitmap matToBitmap(Mat mat) {
        Bitmap bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mat, bitmap);
        return bitmap;
    }

    /**
     * Apply perspective transform to flatten a photographed OMR sheet.
     * Takes 4 source corner points and warps the image to a flat rectangle.
     *
     * @param src        Source bitmap (camera frame)
     * @param srcPoints  4 corner points detected in the source image [TL, TR, BL, BR] as float[4][2]
     * @param outWidth   Desired output width (e.g., 595 for A4)
     * @param outHeight  Desired output height (e.g., 842 for A4)
     * @return Flattened, perspective-corrected bitmap, or null on failure
     */
    public static Bitmap applyPerspectiveTransform(Bitmap src, float[][] srcPoints, int outWidth, int outHeight) {
        if (!sInitialized || src == null || srcPoints == null || srcPoints.length != 4) return null;

        try {
            Mat srcMat = bitmapToMat(src);

            // Source corners from detection (order: TL, TR, BL, BR)
            MatOfPoint2f srcCorners = new MatOfPoint2f(
                new Point(srcPoints[0][0], srcPoints[0][1]),  // Top-left
                new Point(srcPoints[1][0], srcPoints[1][1]),  // Top-right
                new Point(srcPoints[2][0], srcPoints[2][1]),  // Bottom-left
                new Point(srcPoints[3][0], srcPoints[3][1])   // Bottom-right
            );

            // Destination corners (flat rectangle)
            MatOfPoint2f dstCorners = new MatOfPoint2f(
                new Point(0, 0),                    // Top-left
                new Point(outWidth - 1, 0),          // Top-right
                new Point(0, outHeight - 1),         // Bottom-left
                new Point(outWidth - 1, outHeight - 1) // Bottom-right
            );

            // Compute perspective transformation matrix
            Mat transformMatrix = Imgproc.getPerspectiveTransform(srcCorners, dstCorners);

            // Apply warp
            Mat dstMat = new Mat();
            Imgproc.warpPerspective(srcMat, dstMat, transformMatrix, new Size(outWidth, outHeight),
                    Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE, new Scalar(255, 255, 255, 255));

            Bitmap result = matToBitmap(dstMat);

            // Release native memory
            srcMat.release();
            srcCorners.release();
            dstCorners.release();
            transformMatrix.release();
            dstMat.release();

            return result;
        } catch (Exception e) {
            Log.e(TAG, "Perspective transform failed", e);
            return null;
        }
    }

    /**
     * Maps the 4 detected printed markers to their exact ideal coordinates in the 595x842 template.
     * This ensures the resulting image is perfectly aligned for grading.
     */
    public static Bitmap applyPerspectiveTransformToTemplate(Bitmap src, float[][] srcMarkers) {
        if (!sInitialized || src == null || srcMarkers == null || srcMarkers.length != 4) return null;

        int outWidth = 595;
        int outHeight = 842;

        try {
            Mat srcMat = bitmapToMat(src);

            MatOfPoint2f srcCorners = new MatOfPoint2f(
                new Point(srcMarkers[0][0], srcMarkers[0][1]),
                new Point(srcMarkers[1][0], srcMarkers[1][1]),
                new Point(srcMarkers[2][0], srcMarkers[2][1]),
                new Point(srcMarkers[3][0], srcMarkers[3][1])
            );

            // The ideal centers of the markers in the 595x842 template
            MatOfPoint2f dstCorners = new MatOfPoint2f(
                new Point(40, 50),
                new Point(555, 50),
                new Point(40, 792),
                new Point(555, 792)
            );

            Mat transformMatrix = Imgproc.getPerspectiveTransform(srcCorners, dstCorners);
            Mat dstMat = new Mat();
            // Use BORDER_CONSTANT with white background to fill areas outside the paper
            Imgproc.warpPerspective(srcMat, dstMat, transformMatrix, new Size(outWidth, outHeight),
                    Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, new Scalar(255, 255, 255, 255));

            Bitmap result = matToBitmap(dstMat);

            srcMat.release();
            srcCorners.release();
            dstCorners.release();
            transformMatrix.release();
            dstMat.release();

            return result;
        } catch (Exception e) {
            Log.e(TAG, "Perspective transform to template failed", e);
            return null;
        }
    }

    /**
     * Detects the 4 black printed square markers on the OMR sheet using contour analysis.
     * This is much more robust than finding paper edges on varying backgrounds.
     */
    public static float[][] findPrintedMarkers(Bitmap bitmap) {
        if (!sInitialized || bitmap == null) return null;
        
        try {
            Mat src = bitmapToMat(bitmap);
            Mat gray = new Mat();
            Mat binary = new Mat();

            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY);
            // Otsu's thresholding (inverted) to find dark shapes on white paper
            Imgproc.threshold(gray, binary, 0, 255, Imgproc.THRESH_BINARY_INV | Imgproc.THRESH_OTSU);

            List<MatOfPoint> contours = new ArrayList<>();
            Mat hierarchy = new Mat();
            Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            List<Point> markerCenters = new ArrayList<>();
            double imageArea = bitmap.getWidth() * bitmap.getHeight();
            // Typical marker size when phone is held high (allows scanning from a distance)
            double minArea = imageArea * 0.00005;
            double maxArea = imageArea * 0.01;

            for (MatOfPoint contour : contours) {
                double area = Imgproc.contourArea(contour);
                if (area < minArea || area > maxArea) continue;

                MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
                MatOfPoint2f approx = new MatOfPoint2f();
                double peri = Imgproc.arcLength(contour2f, true);
                Imgproc.approxPolyDP(contour2f, approx, 0.04 * peri, true);

                if (approx.total() == 4) {
                    Rect rect = Imgproc.boundingRect(contour);
                    double aspect = (double) rect.width / rect.height;
                    // Roughly square
                    if (aspect >= 0.7 && aspect <= 1.4) {
                        Point center = new Point(rect.x + rect.width / 2.0, rect.y + rect.height / 2.0);
                        markerCenters.add(center);
                    }
                }
                approx.release();
                contour2f.release();
            }

            src.release();
            gray.release();
            binary.release();
            hierarchy.release();

            if (markerCenters.size() >= 4) {
                return findBest4Markers(markerCenters, bitmap.getWidth(), bitmap.getHeight());
            }

            return null;
        } catch(Exception e) {
            Log.e(TAG, "Printed marker detection failed", e);
            return null;
        }
    }

    private static float[][] findBest4Markers(List<Point> centers, int w, int h) {
        if (centers.size() == 4) {
            return sortCorners(centers.toArray(new Point[0]), w, h);
        }
        
        double maxArea = 0;
        Point[] best4 = null;
        
        int n = Math.min(centers.size(), 12); // Limit combinatorics if too many noise squares found
        for(int i=0; i<n-3; i++) {
            for(int j=i+1; j<n-2; j++) {
                for(int k=j+1; k<n-1; k++) {
                    for(int l=k+1; l<n; l++) {
                        Point[] pts = {centers.get(i), centers.get(j), centers.get(k), centers.get(l)};
                        float[][] sorted = sortCorners(pts, w, h);
                        if (sorted != null) {
                            double area = 0;
                            for(int p=0; p<4; p++) {
                                int q = (p + 1) % 4;
                                area += sorted[p][0] * sorted[q][1] - sorted[q][0] * sorted[p][1];
                            }
                            area = Math.abs(area) / 2.0;
                            
                            // A valid marker arrangement usually covers >25% of the image
                            if (area > maxArea && area > (w * h * 0.25)) {
                                maxArea = area;
                                best4 = pts;
                            }
                        }
                    }
                }
            }
        }
        
        if (best4 != null) {
            return sortCorners(best4, w, h);
        }
        return null;
    }

    /**
     * Given the 4 printed markers detected in the camera frame, calculates where the physical
     * paper edges should be by projecting the ideal 595x842 corners outwards using Homography.
     * @param detectedMarkers The 4 marker coordinates [TL, TR, BL, BR] in the camera frame
     * @return The 4 calculated paper corners [TL, TR, BL, BR] in the camera frame
     */
    public static float[][] extrapolatePaperEdges(float[][] detectedMarkers) {
        if (!sInitialized || detectedMarkers == null || detectedMarkers.length != 4) return null;

        try {
            // Ideal markers in 595x842 space
            MatOfPoint2f idealMarkers = new MatOfPoint2f(
                new Point(40, 50),
                new Point(555, 50),
                new Point(40, 792),
                new Point(555, 792)
            );

            // Detected markers in camera frame space
            MatOfPoint2f cameraMarkers = new MatOfPoint2f(
                new Point(detectedMarkers[0][0], detectedMarkers[0][1]),
                new Point(detectedMarkers[1][0], detectedMarkers[1][1]),
                new Point(detectedMarkers[2][0], detectedMarkers[2][1]),
                new Point(detectedMarkers[3][0], detectedMarkers[3][1])
            );

            // Calculate homography from Ideal -> Camera
            Mat homography = Imgproc.getPerspectiveTransform(idealMarkers, cameraMarkers);

            // Ideal paper corners in 595x842 space
            MatOfPoint2f idealPaperCorners = new MatOfPoint2f(
                new Point(0, 0),
                new Point(595, 0),
                new Point(0, 842),
                new Point(595, 842)
            );

            // Project ideal paper corners into camera frame space
            MatOfPoint2f cameraPaperCorners = new MatOfPoint2f();
            Core.perspectiveTransform(idealPaperCorners, cameraPaperCorners, homography);

            Point[] projectedPts = cameraPaperCorners.toArray();

            idealMarkers.release();
            cameraMarkers.release();
            homography.release();
            idealPaperCorners.release();
            cameraPaperCorners.release();

            return sortCorners(projectedPts, 10000, 10000); 
        } catch (Exception e) {
            Log.e(TAG, "Failed to extrapolate paper edges", e);
            return null;
        }
    }

    /**
     * Detect the 4 corner points of a document/OMR sheet using edge detection and contour analysis.
     * Useful as a fallback when printed corner markers are not found.
     *
     * @param bitmap Source image from camera
     * @return float[4][2] with corners [TL, TR, BL, BR], or null if not found
     */
    public static float[][] findDocumentCorners(Bitmap bitmap) {
        if (!sInitialized || bitmap == null) return null;

        try {
            Mat src = bitmapToMat(bitmap);
            
            // 1. Calculate downscale ratio for optimization and noise reduction (max 800px)
            int maxDim = Math.max(src.cols(), src.rows());
            double scale = 1.0;
            if (maxDim > 800) {
                scale = 800.0 / maxDim;
            }
            
            Mat resized = new Mat();
            if (scale < 1.0) {
                Imgproc.resize(src, resized, new Size(), scale, scale, Imgproc.INTER_AREA);
            } else {
                src.copyTo(resized);
            }

            Mat gray = new Mat();
            Imgproc.cvtColor(resized, gray, Imgproc.COLOR_RGBA2GRAY);

            // 2. Enhance contrast using CLAHE (helps separate white paper from light grey desk)
            org.opencv.imgproc.CLAHE clahe = Imgproc.createCLAHE(2.0, new Size(8, 8));
            clahe.apply(gray, gray);

            // 3. Blur to reduce noise (Gaussian + Median for salt/pepper removal)
            Imgproc.GaussianBlur(gray, gray, new Size(5, 5), 0);
            Imgproc.medianBlur(gray, gray, 5);

            // 4. Adaptive Thresholding for Canny (using Otsu to find optimal thresholds)
            Mat edges = new Mat();
            Mat tempBinary = new Mat();
            double highThresh = Imgproc.threshold(gray, tempBinary, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
            double lowThresh = 0.5 * highThresh;
            tempBinary.release();

            Imgproc.Canny(gray, edges, lowThresh, highThresh);

            // 5. Morphological operations to close gaps in paper edges (shadows, etc.)
            Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5, 5));
            Imgproc.dilate(edges, edges, kernel);
            Imgproc.erode(edges, edges, kernel);

            // 6. Find contours
            List<MatOfPoint> contours = new ArrayList<>();
            Mat hierarchy = new Mat();
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            // 7. Find largest quadrilateral
            double maxArea = 0;
            MatOfPoint2f bestQuad = null;
            double imageArea = resized.cols() * resized.rows();

            for (MatOfPoint contour : contours) {
                double area = Imgproc.contourArea(contour);
                
                // Document must be at least 15% of the screen
                if (area < imageArea * 0.15) continue;

                MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
                double perimeter = Imgproc.arcLength(contour2f, true);
                MatOfPoint2f approx = new MatOfPoint2f();
                // 2% of perimeter is standard for document polygon approximation
                Imgproc.approxPolyDP(contour2f, approx, 0.02 * perimeter, true);

                if (approx.total() == 4 && area > maxArea) {
                    // Check if it's convex
                    if (Imgproc.isContourConvex(new MatOfPoint(approx.toArray()))) {
                        maxArea = area;
                        if (bestQuad != null) bestQuad.release();
                        bestQuad = approx;
                    } else {
                        approx.release();
                    }
                } else {
                    approx.release();
                }
                contour2f.release();
            }

            // Cleanup
            src.release();
            resized.release();
            gray.release();
            edges.release();
            kernel.release();
            hierarchy.release();
            for (MatOfPoint c : contours) c.release();

            if (bestQuad == null) return null;

            Point[] points = bestQuad.toArray();
            bestQuad.release();

            // Scale points back to original image size
            if (scale < 1.0) {
                for (int i = 0; i < points.length; i++) {
                    points[i].x = points[i].x / scale;
                    points[i].y = points[i].y / scale;
                }
            }

            return sortCorners(points, bitmap.getWidth(), bitmap.getHeight());
        } catch (Exception e) {
            Log.e(TAG, "Document corner detection failed", e);
            return null;
        }
    }

    /**
     * Sort 4 detected corner points into [TL, TR, BL, BR] order.
     */
    private static float[][] sortCorners(Point[] points, int imageWidth, int imageHeight) {
        if (points.length != 4) return null;

        // Sort by sum (x+y) and difference (x-y)
        // TL has smallest sum, BR has largest sum
        // TR has largest difference (x-y), BL has smallest difference

        List<Point> ptsList = new ArrayList<>();
        Collections.addAll(ptsList, points);

        // Sort by sum (x + y)
        ptsList.sort(Comparator.comparingDouble(p -> p.x + p.y));

        Point tl = ptsList.get(0);
        Point br = ptsList.get(3);

        // For the middle two, the one with larger x-y is TR
        Point mid1 = ptsList.get(1);
        Point mid2 = ptsList.get(2);

        Point tr, bl;
        if ((mid1.x - mid1.y) > (mid2.x - mid2.y)) {
            tr = mid1;
            bl = mid2;
        } else {
            tr = mid2;
            bl = mid1;
        }

        // Basic validity check: convex polygon shape check
        if (tr.x <= tl.x || bl.y <= tl.y) return null;

        return new float[][]{
            {(float) tl.x, (float) tl.y},
            {(float) tr.x, (float) tr.y},
            {(float) bl.x, (float) bl.y},
            {(float) br.x, (float) br.y}
        };
    }

    /**
     * Preprocess an OMR sheet image for optimal bubble detection.
     * Pipeline: Grayscale → Gaussian Blur → Adaptive Threshold
     *
     * @param bitmap Source OMR sheet (ideally already perspective-corrected)
     * @return Preprocessed bitmap with enhanced contrast, or original on failure
     */
    public static Bitmap preprocessForScanning(Bitmap bitmap) {
        if (!sInitialized || bitmap == null) return bitmap;

        try {
            Mat src = bitmapToMat(bitmap);
            Mat gray = new Mat();
            Mat blurred = new Mat();
            Mat result = new Mat();

            // Convert to grayscale
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY);

            // Slight blur to reduce camera noise
            Imgproc.GaussianBlur(gray, blurred, new Size(3, 3), 0);

            // Adaptive threshold for robust bubble detection in varying lighting
            Imgproc.adaptiveThreshold(blurred, result, 255,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    Imgproc.THRESH_BINARY,
                    15, 8);

            // Convert back to RGBA for Android
            Mat rgba = new Mat();
            Imgproc.cvtColor(result, rgba, Imgproc.COLOR_GRAY2RGBA);

            Bitmap output = matToBitmap(rgba);

            src.release();
            gray.release();
            blurred.release();
            result.release();
            rgba.release();

            return output;
        } catch (Exception e) {
            Log.e(TAG, "Preprocessing failed", e);
            return bitmap;
        }
    }

    /**
     * Enhance image contrast using CLAHE (Contrast Limited Adaptive Histogram Equalization).
     * Particularly helpful for scans taken in poor lighting conditions.
     *
     * @param bitmap Input image
     * @return Contrast-enhanced image
     */
    public static Bitmap enhanceContrast(Bitmap bitmap) {
        if (!sInitialized || bitmap == null) return bitmap;

        try {
            Mat src = bitmapToMat(bitmap);
            Mat lab = new Mat();
            
            // Convert to LAB color space
            Imgproc.cvtColor(src, lab, Imgproc.COLOR_RGBA2RGB);
            Mat labConverted = new Mat();
            Imgproc.cvtColor(lab, labConverted, Imgproc.COLOR_RGB2Lab);

            // Split channels
            List<Mat> labChannels = new ArrayList<>();
            Core.split(labConverted, labChannels);

            // Apply CLAHE to L channel
            org.opencv.imgproc.CLAHE clahe = Imgproc.createCLAHE(2.0, new Size(8, 8));
            Mat enhanced = new Mat();
            clahe.apply(labChannels.get(0), enhanced);
            labChannels.set(0, enhanced);

            // Merge back
            Core.merge(labChannels, labConverted);

            // Convert back to RGBA
            Mat rgb = new Mat();
            Imgproc.cvtColor(labConverted, rgb, Imgproc.COLOR_Lab2RGB);
            Mat rgba = new Mat();
            Imgproc.cvtColor(rgb, rgba, Imgproc.COLOR_RGB2RGBA);

            Bitmap output = matToBitmap(rgba);

            // Cleanup
            src.release();
            lab.release();
            labConverted.release();
            for (Mat ch : labChannels) ch.release();
            enhanced.release();
            rgb.release();
            rgba.release();

            return output;
        } catch (Exception e) {
            Log.e(TAG, "Contrast enhancement failed", e);
            return bitmap;
        }
    }

    /**
     * Create a binary (black/white) version of the image using Otsu's thresholding.
     * Better than a fixed threshold for varying print quality.
     */
    public static Bitmap binarize(Bitmap bitmap) {
        if (!sInitialized || bitmap == null) return bitmap;

        try {
            Mat src = bitmapToMat(bitmap);
            Mat gray = new Mat();
            Mat binary = new Mat();

            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY);
            Imgproc.threshold(gray, binary, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);

            Mat rgba = new Mat();
            Imgproc.cvtColor(binary, rgba, Imgproc.COLOR_GRAY2RGBA);

            Bitmap output = matToBitmap(rgba);

            src.release();
            gray.release();
            binary.release();
            rgba.release();

            return output;
        } catch (Exception e) {
            Log.e(TAG, "Binarization failed", e);
            return bitmap;
        }
    }
}
