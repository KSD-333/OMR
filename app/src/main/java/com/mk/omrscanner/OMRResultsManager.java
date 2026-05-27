package com.mk.omrscanner;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class OMRResultsManager {

    private static final String PREFS_NAME = "OMRScannerResultsPrefs";
    private static final String KEY_SAVED_RESULTS_JSON = "saved_results_json";

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
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String jsonString = prefs.getString(KEY_SAVED_RESULTS_JSON, "[]");

        try {
            JSONArray array = new JSONArray(jsonString);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                GradedResult result = new GradedResult(
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
                resultsList.add(result);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return resultsList;
    }

    public static void saveResult(Context context, GradedResult result) {
        List<GradedResult> currentResults = getSavedResults(context);
        currentResults.add(0, result); // Add to the top of the list
        saveResultsList(context, currentResults);
    }

    public static void deleteResult(Context context, String resultId) {
        List<GradedResult> currentResults = getSavedResults(context);
        GradedResult target = null;
        for (GradedResult r : currentResults) {
            if (r.id.equals(resultId)) {
                target = r;
                break;
            }
        }
        if (target != null) {
            currentResults.remove(target);
            saveResultsList(context, currentResults);
        }
    }

    public static void clearAllResults(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_SAVED_RESULTS_JSON, "[]").apply();
    }

    private static void saveResultsList(Context context, List<GradedResult> resultsList) {
        JSONArray array = new JSONArray();
        try {
            for (GradedResult r : resultsList) {
                JSONObject obj = new JSONObject();
                obj.put("id", r.id);
                obj.put("examName", r.examName);
                obj.put("studentId", r.studentId);
                obj.put("score", r.score);
                obj.put("maxScore", r.maxScore);
                obj.put("correct", r.correct);
                obj.put("incorrect", r.incorrect);
                obj.put("blank", r.blank);
                obj.put("multiMark", r.multiMark);
                obj.put("timestamp", r.timestamp);
                array.put(obj);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_SAVED_RESULTS_JSON, array.toString()).apply();
    }
}
