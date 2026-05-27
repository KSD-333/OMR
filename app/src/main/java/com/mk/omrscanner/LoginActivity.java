package com.mk.omrscanner;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;

public class LoginActivity extends AppCompatActivity {

    private boolean isSignInMode = true;

    private TextView btnTabSignIn;
    private TextView btnTabSignUp;
    private TextView txtCardTitle;
    private LinearLayout layoutSchoolName;
    private LinearLayout layoutTeacherCode;
    private EditText inputEmail;
    private EditText inputPassword;
    private EditText inputSchool;
    private EditText inputCode;
    private TextView txtError;
    private AppCompatButton btnSubmit;
    private TextView txtFooterToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Bind Views
        btnTabSignIn = findViewById(R.id.btnTabSignIn);
        btnTabSignUp = findViewById(R.id.btnTabSignUp);
        txtCardTitle = findViewById(R.id.txtCardTitle);
        layoutSchoolName = findViewById(R.id.layoutSchoolName);
        layoutTeacherCode = findViewById(R.id.layoutTeacherCode);
        inputEmail = findViewById(R.id.inputEmail);
        inputPassword = findViewById(R.id.inputPassword);
        inputSchool = findViewById(R.id.inputSchool);
        inputCode = findViewById(R.id.inputCode);
        txtError = findViewById(R.id.txtError);
        btnSubmit = findViewById(R.id.btnSubmit);
        txtFooterToggle = findViewById(R.id.txtFooterToggle);

        // Set Tab Click Listeners
        btnTabSignIn.setOnClickListener(v -> switchMode(true));
        btnTabSignUp.setOnClickListener(v -> switchMode(false));
        txtFooterToggle.setOnClickListener(v -> switchMode(!isSignInMode));

        // Submit Button Action
        btnSubmit.setOnClickListener(v -> validateAndSubmit());
    }

    private void switchMode(boolean signIn) {
        if (isSignInMode == signIn) return;
        isSignInMode = signIn;

        txtError.setVisibility(View.GONE);

        int activeColor = ContextCompat.getColor(this, R.color.text_primary);
        int inactiveColor = ContextCompat.getColor(this, R.color.text_secondary);
        int primaryBgColor = ContextCompat.getColor(this, R.color.color_auth_primary);

        if (isSignInMode) {
            // Update Tab Headers
            btnTabSignIn.setBackgroundResource(R.drawable.bg_rounded_card);
            btnTabSignIn.setBackgroundTintList(ColorStateList.valueOf(primaryBgColor));
            btnTabSignIn.setTextColor(activeColor);

            btnTabSignUp.setBackground(null);
            btnTabSignUp.setTextColor(inactiveColor);

            // Update Fields Visibility
            layoutSchoolName.setVisibility(View.GONE);
            layoutTeacherCode.setVisibility(View.GONE);

            // Update Labels
            txtCardTitle.setText("Welcome Back");
            btnSubmit.setText("Sign In");
            txtFooterToggle.setText("Don't have an account? Create one");
        } else {
            // Update Tab Headers
            btnTabSignUp.setBackgroundResource(R.drawable.bg_rounded_card);
            btnTabSignUp.setBackgroundTintList(ColorStateList.valueOf(primaryBgColor));
            btnTabSignUp.setTextColor(activeColor);

            btnTabSignIn.setBackground(null);
            btnTabSignIn.setTextColor(inactiveColor);

            // Update Fields Visibility
            layoutSchoolName.setVisibility(View.VISIBLE);
            layoutTeacherCode.setVisibility(View.VISIBLE);

            // Update Labels
            txtCardTitle.setText("Create Account");
            btnSubmit.setText("Create Account");
            txtFooterToggle.setText("Already have an account? Sign In");
        }
    }

    private void validateAndSubmit() {
        String email = inputEmail.getText().toString().trim();
        String password = inputPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email) || !email.contains("@")) {
            showError("Please enter a valid email address");
            return;
        }

        if (TextUtils.isEmpty(password) || password.length() < 6) {
            showError("Password must be at least 6 characters");
            return;
        }

        if (!isSignInMode) {
            String school = inputSchool.getText().toString().trim();
            String code = inputCode.getText().toString().trim();

            if (TextUtils.isEmpty(school)) {
                showError("Please enter your school name");
                return;
            }

            if (TextUtils.isEmpty(code)) {
                showError("Please enter your registration code");
                return;
            }
        }

        // Successfully authenticated!
        txtError.setVisibility(View.GONE);

        // Save school name to SharedPreferences
        String schoolName = isSignInMode ? "Greenwood High School" : inputSchool.getText().toString().trim();
        SharedPreferences prefs = getSharedPreferences("OMRScannerPrefs", MODE_PRIVATE);
        prefs.edit().putString("school_name", schoolName).apply();

        // Route to Onboarding walkthrough
        Intent intent = new Intent(LoginActivity.this, OnboardingActivity.class);
        startActivity(intent);
        finish();
    }

    private void showError(String message) {
        txtError.setText(message);
        txtError.setVisibility(View.VISIBLE);
    }
}
