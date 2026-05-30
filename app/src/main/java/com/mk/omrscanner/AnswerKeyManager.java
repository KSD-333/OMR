package com.mk.omrscanner;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages saved answer keys.
 * CON #12: Now backed by Room database for scalability.
 * Public API and AnswerKey model remain unchanged for backward compatibility.
 * Selected key ID still uses SharedPreferences (it's a single value, not a collection).
 */
public class AnswerKeyManager {

    private static final String PREFS_NAME = "OMRScannerKeysPrefs";
    private static final String KEY_SELECTED_KEY_ID = "selected_key_id";

    public static int normalizeQuestionsCount(int questionsCount) {
        return questionsCount == 50 ? 50 : 100;
    }

    public static int columnsForQuestionCount(int questionsCount) {
        return normalizeQuestionsCount(questionsCount) == 50 ? 2 : 4;
    }

    private static AnswerKey normalizeKey(AnswerKey key) {
        if (key == null) return null;
        key.questionsCount = normalizeQuestionsCount(key.questionsCount);
        key.columnsLayout = columnsForQuestionCount(key.questionsCount);
        return key;
    }

    // Data model class representing a saved Answer Key
    public static class AnswerKey {
        public String id;
        public String name;
        public int questionsCount;
        public int columnsLayout;
        public int idDigits; // 4 or 6 digit Student ID
        public String answersJson; // e.g. "[[true,false,false,false],...]"
        public String incorrectPenalty;
        public String multiMarkPenalty;
        public boolean customPointsActive;
        public boolean multiCorrectActive;
        public String pointsJson; // e.g. "[1.0, 1.0, 2.0,...]"

        public AnswerKey() {
            this.idDigits = 6; // default
        }

        public AnswerKey(String id, String name, int questionsCount, int columnsLayout, 
                         String answersJson, String incorrectPenalty, String multiMarkPenalty, 
                         boolean customPointsActive, boolean multiCorrectActive, String pointsJson) {
            this.id = id;
            this.name = name;
            this.questionsCount = questionsCount;
            this.columnsLayout = columnsLayout;
            this.idDigits = 6; // default
            this.answersJson = answersJson;
            this.incorrectPenalty = incorrectPenalty;
            this.multiMarkPenalty = multiMarkPenalty;
            this.customPointsActive = customPointsActive;
            this.multiCorrectActive = multiCorrectActive;
            this.pointsJson = pointsJson;
        }

        public AnswerKey(String id, String name, int questionsCount, int columnsLayout, int idDigits,
                         String answersJson, String incorrectPenalty, String multiMarkPenalty, 
                         boolean customPointsActive, boolean multiCorrectActive, String pointsJson) {
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
    }

    // Retrieve all saved answer keys (now via Room)
    public static List<AnswerKey> getSavedKeys(Context context) {
        List<AnswerKey> keysList = new ArrayList<>();
        try {
            AnswerKeyDao dao = AppDatabase.getInstance(context).answerKeyDao();
            List<AnswerKeyEntity> entities = dao.getAll();
            for (AnswerKeyEntity entity : entities) {
                keysList.add(normalizeKey(entity.toLegacy()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return keysList;
    }

    // Save or update an answer key (now via Room)
    public static void saveKey(Context context, AnswerKey keyToSave) {
        normalizeKey(keyToSave);
        try {
            AnswerKeyDao dao = AppDatabase.getInstance(context).answerKeyDao();
            dao.insertOrUpdate(AnswerKeyEntity.fromLegacy(keyToSave));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Delete an answer key by ID (now via Room)
    public static void deleteKey(Context context, String keyId) {
        try {
            AnswerKeyDao dao = AppDatabase.getInstance(context).answerKeyDao();
            dao.deleteById(keyId);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // If deleted key was selected, reset selection
        if (keyId.equals(getSelectedKeyId(context))) {
            setSelectedKeyId(context, "");
        }
    }

    // Set currently selected/active key ID (still SharedPreferences — it's a single value)
    public static void setSelectedKeyId(Context context, String keyId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_SELECTED_KEY_ID, keyId).apply();
    }

    // Get currently selected key ID
    public static String getSelectedKeyId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_SELECTED_KEY_ID, "");
    }

    // Get the active selected AnswerKey object (now via Room)
    public static AnswerKey getSelectedKey(Context context) {
        String selectedId = getSelectedKeyId(context);
        if (selectedId.isEmpty()) return null;

        try {
            AnswerKeyDao dao = AppDatabase.getInstance(context).answerKeyDao();
            AnswerKeyEntity entity = dao.getById(selectedId);
            if (entity != null) {
                return normalizeKey(entity.toLegacy());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
