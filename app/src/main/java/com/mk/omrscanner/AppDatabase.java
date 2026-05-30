package com.mk.omrscanner;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * Room database for OMR Scanner app data.
 * Stores graded results and answer keys with proper SQL indexing.
 * Auto-migrates from SharedPreferences on first access.
 */
@Database(entities = {GradedResultEntity.class, AnswerKeyEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;
    private static final String DB_NAME = "omr_scanner_db";

    public abstract GradedResultDao gradedResultDao();
    public abstract AnswerKeyDao answerKeyDao();

    /**
     * Thread-safe singleton access.
     */
    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DB_NAME
                    ).allowMainThreadQueries() // OK for small datasets; use async for large
                     .build();

                    // Migrate legacy SharedPreferences data on first creation
                    migrateLegacyData(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    /**
     * One-time migration of existing SharedPreferences data into Room.
     * Reads old JSON data, inserts into Room, then clears the old prefs.
     */
    private static void migrateLegacyData(Context context) {
        try {
            android.content.SharedPreferences resultsPrefs =
                    context.getSharedPreferences("OMRScannerResultsPrefs", Context.MODE_PRIVATE);
            String resultsJson = resultsPrefs.getString("saved_results_json", null);

            if (resultsJson != null && !resultsJson.equals("[]")) {
                // Migrate results
                org.json.JSONArray array = new org.json.JSONArray(resultsJson);
                GradedResultDao dao = INSTANCE.gradedResultDao();
                for (int i = 0; i < array.length(); i++) {
                    org.json.JSONObject obj = array.getJSONObject(i);
                    GradedResultEntity entity = new GradedResultEntity(
                            obj.getString("id"),
                            obj.getString("examName"),
                            obj.getString("studentId"),
                            obj.getDouble("score"),
                            obj.getDouble("maxScore"),
                            obj.getInt("correct"),
                            obj.getInt("incorrect"),
                            obj.getInt("blank"),
                            obj.getInt("multiMark"),
                            obj.getLong("timestamp")
                    );
                    dao.insert(entity);
                }
                // Clear migrated data
                resultsPrefs.edit().remove("saved_results_json").apply();
                android.util.Log.i("AppDatabase", "Migrated " + array.length() + " results to Room");
            }

            android.content.SharedPreferences keysPrefs =
                    context.getSharedPreferences("OMRScannerKeysPrefs", Context.MODE_PRIVATE);
            String keysJson = keysPrefs.getString("saved_keys_json", null);

            if (keysJson != null && !keysJson.equals("[]")) {
                // Migrate answer keys
                org.json.JSONArray keyArray = new org.json.JSONArray(keysJson);
                AnswerKeyDao keyDao = INSTANCE.answerKeyDao();
                for (int i = 0; i < keyArray.length(); i++) {
                    org.json.JSONObject obj = keyArray.getJSONObject(i);
                    int questionsCount = AnswerKeyManager.normalizeQuestionsCount(
                            obj.optInt("questionsCount", 100));
                    AnswerKeyEntity entity = new AnswerKeyEntity(
                            obj.getString("id"),
                            obj.getString("name"),
                            questionsCount,
                            AnswerKeyManager.columnsForQuestionCount(questionsCount),
                            obj.optInt("idDigits", 6),
                            obj.getString("answersJson"),
                            obj.getString("incorrectPenalty"),
                            obj.getString("multiMarkPenalty"),
                            obj.getBoolean("customPointsActive"),
                            obj.getBoolean("multiCorrectActive"),
                            obj.optString("pointsJson", "[]")
                    );
                    keyDao.insertOrUpdate(entity);
                }
                // Clear migrated data
                keysPrefs.edit().remove("saved_keys_json").apply();
                android.util.Log.i("AppDatabase", "Migrated " + keyArray.length() + " answer keys to Room");
            }
        } catch (Exception e) {
            android.util.Log.e("AppDatabase", "Legacy migration failed (non-fatal)", e);
        }
    }
}
