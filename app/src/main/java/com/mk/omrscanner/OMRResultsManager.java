package com.mk.omrscanner;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages graded scan results storage.
 * CON #12: Now backed by Room database for scalability.
 * Public API remains unchanged for backward compatibility.
 */
public class OMRResultsManager {

    public static class GradedResult {
        public String id;
        public String examName;
        public String studentId;
        public double score;
        public double maxScore;
        public int correct;
        public int incorrect;
        public int blank;
        public int multiMark;
        public long timestamp;

        public GradedResult() {}

        public GradedResult(String id, String examName, String studentId, double score, double maxScore,
                            int correct, int incorrect, int blank, int multiMark, long timestamp) {
            this.id = id;
            this.examName = examName;
            this.studentId = studentId;
            this.score = score;
            this.maxScore = maxScore;
            this.correct = correct;
            this.incorrect = incorrect;
            this.blank = blank;
            this.multiMark = multiMark;
            this.timestamp = timestamp;
        }
    }

    public static List<GradedResult> getSavedResults(Context context) {
        List<GradedResult> resultsList = new ArrayList<>();
        try {
            GradedResultDao dao = AppDatabase.getInstance(context).gradedResultDao();
            List<GradedResultEntity> entities = dao.getAll();
            for (GradedResultEntity entity : entities) {
                resultsList.add(entity.toLegacy());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultsList;
    }

    public static void saveResult(Context context, GradedResult result) {
        try {
            GradedResultDao dao = AppDatabase.getInstance(context).gradedResultDao();
            dao.insert(GradedResultEntity.fromLegacy(result));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void deleteResult(Context context, String resultId) {
        try {
            GradedResultDao dao = AppDatabase.getInstance(context).gradedResultDao();
            dao.deleteById(resultId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void clearAllResults(Context context) {
        try {
            GradedResultDao dao = AppDatabase.getInstance(context).gradedResultDao();
            dao.deleteAll();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
