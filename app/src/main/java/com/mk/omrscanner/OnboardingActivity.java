package com.mk.omrscanner;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import java.util.ArrayList;
import java.util.List;

public class OnboardingActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private LinearLayout layoutIndicator;
    private AppCompatButton btnNext;
    private TextView btnSkip;
    private List<OnboardingPage> onboardingPages;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        // Bind Views
        viewPager = findViewById(R.id.viewPager);
        layoutIndicator = findViewById(R.id.layoutIndicator);
        btnNext = findViewById(R.id.btnNext);
        btnSkip = findViewById(R.id.btnSkip);

        // Populate Pages Data
        onboardingPages = new ArrayList<>();
        onboardingPages.add(new OnboardingPage(
                "Generate Answer Sheets",
                "Create custom OMR bubble sheets in seconds.\nChoose questions count, columns, and add your institute name — then download as PDF, ready to print.",
                R.drawable.ic_sheet,
                R.color.color_page_1,
                R.color.color_page_1_bg,
                1
        ));
        onboardingPages.add(new OnboardingPage(
                "Set Answer Key",
                "Tap bubbles to mark correct answers — it's that easy.\nOr scan a pre-filled master sheet to auto-create the key.",
                R.drawable.ic_key,
                R.color.color_page_2,
                R.color.color_page_2_bg,
                2
        ));
        onboardingPages.add(new OnboardingPage(
                "Scan & Grade Instantly",
                "Point your camera at a filled sheet for live one-by-one grading.\n\nHave hundreds of sheets? Use a document scanner to digitize them all at once, then load the images into Batch Scanner — the app grades every sheet automatically, saving you hours of manual work.",
                R.drawable.ic_scan,
                R.color.color_page_3,
                R.color.color_page_3_bg,
                3
        ));
        onboardingPages.add(new OnboardingPage(
                "View & Export Results",
                "See each student's score with a color-coded annotated sheet. Export results as CSV, PDF report, or ZIP — share with staff or parents easily.",
                R.drawable.ic_chart,
                R.color.color_page_4,
                R.color.color_page_4_bg,
                4
        ));

        // Set ViewPager Adapter
        OnboardingAdapter adapter = new OnboardingAdapter(onboardingPages);
        viewPager.setAdapter(adapter);

        // Initialize Indicators
        setupIndicators();
        setCurrentIndicator(0);

        // Page change listener
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                setCurrentIndicator(position);
                updateThemeForPage(position);
            }
        });

        // Click Actions
        btnNext.setOnClickListener(v -> {
            int current = viewPager.getCurrentItem();
            if (current < onboardingPages.size() - 1) {
                viewPager.setCurrentItem(current + 1);
            } else {
                navigateToDashboard();
            }
        });

        btnSkip.setOnClickListener(v -> navigateToDashboard());
    }

    private void setupIndicators() {
        layoutIndicator.removeAllViews();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(8, 0, 8, 0);

        for (int i = 0; i < onboardingPages.size(); i++) {
            ImageView indicator = new ImageView(this);
            indicator.setLayoutParams(params);
            layoutIndicator.addView(indicator);
        }
    }

    private void setCurrentIndicator(int index) {
        int childCount = layoutIndicator.getChildCount();
        int activeColor = ContextCompat.getColor(this, onboardingPages.get(index).accentColorRes);

        for (int i = 0; i < childCount; i++) {
            ImageView indicator = (ImageView) layoutIndicator.getChildAt(i);
            if (i == index) {
                indicator.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.dot_active));
                indicator.setImageTintList(ColorStateList.valueOf(activeColor));
            } else {
                indicator.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.dot_inactive));
                indicator.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.text_muted)));
            }
        }
    }

    private void updateThemeForPage(int position) {
        OnboardingPage page = onboardingPages.get(position);
        int pageColor = ContextCompat.getColor(this, page.accentColorRes);

        // Update button background tint
        btnNext.setBackgroundTintList(ColorStateList.valueOf(pageColor));

        if (position == onboardingPages.size() - 1) {
            // Last Page
            btnNext.setText("Get Started");
            btnSkip.setVisibility(View.GONE);
        } else {
            // Middle Pages
            btnNext.setText("Next");
            btnSkip.setVisibility(View.VISIBLE);
        }
    }

    private void navigateToDashboard() {
        Intent intent = new Intent(OnboardingActivity.this, DashboardActivity.class);
        startActivity(intent);
        finish();
    }

    // Onboarding Data Model
    private static class OnboardingPage {
        String title;
        String desc;
        int iconRes;
        int accentColorRes;
        int accentBgColorRes;
        int pageNumber;

        OnboardingPage(String title, String desc, int iconRes, int accentColorRes, int accentBgColorRes, int pageNumber) {
            this.title = title;
            this.desc = desc;
            this.iconRes = iconRes;
            this.accentColorRes = accentColorRes;
            this.accentBgColorRes = accentBgColorRes;
            this.pageNumber = pageNumber;
        }
    }

    // ViewPager2 Adapter
    private class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.OnboardingViewHolder> {

        private final List<OnboardingPage> pages;

        OnboardingAdapter(List<OnboardingPage> pages) {
            this.pages = pages;
        }

        @NonNull
        @Override
        public OnboardingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_onboarding, parent, false);
            return new OnboardingViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull OnboardingViewHolder holder, int position) {
            OnboardingPage page = pages.get(position);
            holder.bind(page);
        }

        @Override
        public int getItemCount() {
            return pages.size();
        }

        class OnboardingViewHolder extends RecyclerView.ViewHolder {
            private final View imgCircleBg;
            private final ImageView imgIcon;
            private final TextView txtPageBadge;
            private final TextView txtTitle;
            private final TextView txtDescription;

            OnboardingViewHolder(@NonNull View itemView) {
                super(itemView);
                imgCircleBg = itemView.findViewById(R.id.imgCircleBg);
                imgIcon = itemView.findViewById(R.id.imgIcon);
                txtPageBadge = itemView.findViewById(R.id.txtPageBadge);
                txtTitle = itemView.findViewById(R.id.txtTitle);
                txtDescription = itemView.findViewById(R.id.txtDescription);
            }

            void bind(OnboardingPage page) {
                txtTitle.setText(page.title);
                txtDescription.setText(page.desc);
                imgIcon.setImageResource(page.iconRes);
                txtPageBadge.setText(String.valueOf(page.pageNumber));

                // Bind programmatically themed colors
                int accentColor = ContextCompat.getColor(itemView.getContext(), page.accentColorRes);
                int accentBgColor = ContextCompat.getColor(itemView.getContext(), page.accentBgColorRes);

                imgCircleBg.setBackgroundTintList(ColorStateList.valueOf(accentBgColor));
                imgIcon.setImageTintList(ColorStateList.valueOf(accentColor));
                txtPageBadge.setBackgroundTintList(ColorStateList.valueOf(accentColor));
            }
        }
    }
}
