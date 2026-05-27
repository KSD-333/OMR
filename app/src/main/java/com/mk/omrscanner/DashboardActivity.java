package com.mk.omrscanner;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.card.MaterialCardView;

public class DashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Load school name from SharedPreferences and display in header
        SharedPreferences prefs = getSharedPreferences("OMRScannerPrefs", MODE_PRIVATE);
        String schoolName = prefs.getString("school_name", "Your School");
        TextView tvSchoolName = findViewById(R.id.tvSchoolName);
        if (tvSchoolName != null) {
            tvSchoolName.setText(schoolName);
        }

        // Bind MaterialCardViews from 2x2 grid (correct type - was CardView before)
        MaterialCardView cardNewSheet = findViewById(R.id.cardNewSheet);
        MaterialCardView cardScanNow = findViewById(R.id.cardScanNow);
        MaterialCardView cardAnswerKey = findViewById(R.id.cardAnswerKey);
        MaterialCardView cardResults = findViewById(R.id.cardResults);

        // Launch ConfigureSheetActivity on tapping New Sheet
        if (cardNewSheet != null) {
            cardNewSheet.setOnClickListener(v -> {
                Intent intent = new Intent(DashboardActivity.this, ConfigureSheetActivity.class);
                startActivity(intent);
            });
        }

        // Launch ScanGradeActivity on tapping Scan Now
        if (cardScanNow != null) {
            cardScanNow.setOnClickListener(v -> {
                Intent intent = new Intent(DashboardActivity.this, ScanGradeActivity.class);
                startActivity(intent);
            });
        }

        if (cardAnswerKey != null) {
            cardAnswerKey.setOnClickListener(v -> {
                Intent intent = new Intent(DashboardActivity.this, EditAnswerKeyActivity.class);
                startActivity(intent);
            });
        }

        if (cardResults != null) {
            cardResults.setOnClickListener(v -> {
                Intent intent = new Intent(DashboardActivity.this, ScanGradeActivity.class);
                intent.putExtra("show_results", true);
                startActivity(intent);
            });
        }

        // Bind Bottom Navigation Bar click listeners
        if (findViewById(R.id.navItemGenerator) != null) {
            findViewById(R.id.navItemGenerator).setOnClickListener(v -> {
                Intent intent = new Intent(DashboardActivity.this, ConfigureSheetActivity.class);
                startActivity(intent);
            });
        }

        if (findViewById(R.id.navItemScanner) != null) {
            findViewById(R.id.navItemScanner).setOnClickListener(v -> {
                Intent intent = new Intent(DashboardActivity.this, ScanGradeActivity.class);
                startActivity(intent);
            });
        }

        if (findViewById(R.id.navItemKeys) != null) {
            findViewById(R.id.navItemKeys).setOnClickListener(v -> {
                Intent intent = new Intent(DashboardActivity.this, EditAnswerKeyActivity.class);
                startActivity(intent);
            });
        }

        if (findViewById(R.id.navItemResults) != null) {
            findViewById(R.id.navItemResults).setOnClickListener(v -> {
                Intent intent = new Intent(DashboardActivity.this, ScanGradeActivity.class);
                intent.putExtra("show_results", true);
                startActivity(intent);
            });
        }
    }
}
