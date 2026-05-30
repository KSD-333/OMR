package com.mk.omrscanner;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

/**
 * Room entity for graded scan results.
 * Replaces JSON-in-SharedPreferences storage for scalability.
 */
@Entity(tableName = "graded_results")
public class GradedResultEntity {

    @PrimaryKey
    @NonNull
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

    public GradedResultEntity() {}

    @Ignore
    public GradedResultEntity(@NonNull String id, String examName, String studentId,
                               double score, double maxScore, int correct,
                               int incorrect, int blank, int multiMark, long timestamp) {
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

    // Convert from the legacy GradedResult model
    public static GradedResultEntity fromLegacy(OMRResultsManager.GradedResult legacy) {
        return new GradedResultEntity(
                legacy.id, legacy.examName, legacy.studentId,
                legacy.score, legacy.maxScore, legacy.correct,
                legacy.incorrect, legacy.blank, legacy.multiMark, legacy.timestamp
        );
    }

    // Convert to the legacy GradedResult model for backward compatibility
    public OMRResultsManager.GradedResult toLegacy() {
        return new OMRResultsManager.GradedResult(
                id, examName, studentId, score, maxScore,
                correct, incorrect, blank, multiMark, timestamp
        );
    }
}
