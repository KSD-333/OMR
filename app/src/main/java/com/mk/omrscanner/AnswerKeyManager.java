package com.mk.omrscanner;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class AnswerKeyManager {

    private static final String PREFS_NAME = "OMRScannerKeysPrefs";
    private static final String KEY_SAVED_KEYS_JSON = "saved_keys_json";
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

    // Retrieve all saved answer keys
    public static List<AnswerKey> getSavedKeys(Context context) {
        List<AnswerKey> keysList = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String jsonString = prefs.getString(KEY_SAVED_KEYS_JSON, "[]");

        try {
            JSONArray array = new JSONArray(jsonString);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                int questionsCount = normalizeQuestionsCount(obj.optInt("questionsCount", 100));
                AnswerKey key = new AnswerKey(
                        obj.getString("id"),
                        obj.getString("name"),
                        questionsCount,
                        columnsForQuestionCount(questionsCount),
                        obj.optInt("idDigits", 6),
                        obj.getString("answersJson"),
                        obj.getString("incorrectPenalty"),
                        obj.getString("multiMarkPenalty"),
                        obj.getBoolean("customPointsActive"),
                        obj.getBoolean("multiCorrectActive"),
                        obj.optString("pointsJson", "[]")
                );
                keysList.add(normalizeKey(key));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return keysList;
    }

    // Save or update an answer key
    public static void saveKey(Context context, AnswerKey keyToSave) {
        normalizeKey(keyToSave);
        List<AnswerKey> currentKeys = getSavedKeys(context);
        boolean isExisting = false;

        // Check if updating an existing key
        for (int i = 0; i < currentKeys.size(); i++) {
            if (currentKeys.get(i).id.equals(keyToSave.id)) {
                currentKeys.set(i, keyToSave);
                isExisting = true;
                break;
            }
        }

        // If new, add it
        if (!isExisting) {
            currentKeys.add(keyToSave);
        }

        // Serialize and save back
        saveKeysList(context, currentKeys);
    }

    // Delete an answer key by ID
    public static void deleteKey(Context context, String keyId) {
        List<AnswerKey> currentKeys = getSavedKeys(context);
        AnswerKey target = null;
        for (AnswerKey key : currentKeys) {
            if (key.id.equals(keyId)) {
                target = key;
                break;
            }
        }

        if (target != null) {
            currentKeys.remove(target);
            saveKeysList(context, currentKeys);
        }

        // If deleted key was selected, reset selection
        if (keyId.equals(getSelectedKeyId(context))) {
            setSelectedKeyId(context, "");
        }
    }

    // Set currently selected/active key ID
    public static void setSelectedKeyId(Context context, String keyId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_SELECTED_KEY_ID, keyId).apply();
    }

    // Get currently selected key ID
    public static String getSelectedKeyId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_SELECTED_KEY_ID, "");
    }

    // Get the active selected AnswerKey object
    public static AnswerKey getSelectedKey(Context context) {
        String selectedId = getSelectedKeyId(context);
        if (selectedId.isEmpty()) return null;

        List<AnswerKey> keys = getSavedKeys(context);
        for (AnswerKey key : keys) {
            if (key.id.equals(selectedId)) {
                return key;
            }
        }
        return null;
    }

    // Internal helper to serialize keys list
    private static void saveKeysList(Context context, List<AnswerKey> keysList) {
        JSONArray array = new JSONArray();
        try {
            for (AnswerKey key : keysList) {
                normalizeKey(key);
                JSONObject obj = new JSONObject();
                obj.put("id", key.id);
                obj.put("name", key.name);
                obj.put("questionsCount", key.questionsCount);
                obj.put("columnsLayout", key.columnsLayout);
                obj.put("idDigits", key.idDigits);
                obj.put("answersJson", key.answersJson);
                obj.put("incorrectPenalty", key.incorrectPenalty);
                obj.put("multiMarkPenalty", key.multiMarkPenalty);
                obj.put("customPointsActive", key.customPointsActive);
                obj.put("multiCorrectActive", key.multiCorrectActive);
                obj.put("pointsJson", key.pointsJson);
                array.put(obj);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_SAVED_KEYS_JSON, array.toString()).apply();
    }
}
