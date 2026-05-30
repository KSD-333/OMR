package com.mk.omrscanner;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

/**
 * Room entity for saved answer keys.
 * Replaces JSON-in-SharedPreferences storage for scalability.
 */
@Entity(tableName = "answer_keys")
public class AnswerKeyEntity {

    @PrimaryKey
    @NonNull
    public String id;

    public String name;
    public int questionsCount;
    public int columnsLayout;
    public int idDigits;
    public String answersJson;
    public String incorrectPenalty;
    public String multiMarkPenalty;
    public boolean customPointsActive;
    public boolean multiCorrectActive;
    public String pointsJson;

    public AnswerKeyEntity() {}

    @Ignore
    public AnswerKeyEntity(@NonNull String id, String name, int questionsCount, int columnsLayout,
                            int idDigits, String answersJson, String incorrectPenalty,
                            String multiMarkPenalty, boolean customPointsActive,
                            boolean multiCorrectActive, String pointsJson) {
        this.id = id;
        this.name = name;
        this.questionsCount = questionsCount;
        this.columnsLayout = columnsLayout;
        this.idDigits = idDigits;
        this.answersJson = answersJson;
        this.incorrectPenalty = incorrectPenalty;
        this.multiMarkPenalty = multiMarkPenalty;
        this.customPointsActive = customPointsActive;
        this.multiCorrectActive = multiCorrectActive;
        this.pointsJson = pointsJson;
    }

    // Convert from legacy AnswerKey model
    public static AnswerKeyEntity fromLegacy(AnswerKeyManager.AnswerKey legacy) {
        return new AnswerKeyEntity(
                legacy.id, legacy.name, legacy.questionsCount, legacy.columnsLayout,
                legacy.idDigits, legacy.answersJson, legacy.incorrectPenalty,
                legacy.multiMarkPenalty, legacy.customPointsActive,
                legacy.multiCorrectActive, legacy.pointsJson
        );
    }

    // Convert to legacy AnswerKey model for backward compatibility
    public AnswerKeyManager.AnswerKey toLegacy() {
        return new AnswerKeyManager.AnswerKey(
                id, name, questionsCount, columnsLayout, idDigits,
                answersJson, incorrectPenalty, multiMarkPenalty,
                customPointsActive, multiCorrectActive, pointsJson
        );
    }
}
