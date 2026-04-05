package com.mckimquyen.ui;

import android.animation.Animator;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.WindowInsetsController;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.mckimquyen.BuildConfig;
import com.mckimquyen.R;
import com.mckimquyen.sdkadbmob.UIUtils;
import com.mckimquyen.services.AppEventManager;

import java.util.Objects;

//2023.03.19 tried to convert kotlin but failed
public class ActAbout extends ActBase {
    TextView tvAbout;
    TextView tvVersion;
    ImageView backdrop;
    CollapsingToolbarLayout collapsingToolbar;
    Toolbar toolbar;

    View cardFeatures, cardAbout, cardCredits;
    View headerFeatures, headerAbout, headerCredits;
    View contentFeatures, contentAbout, contentCredits;
    TextView iconFeatures, iconAbout, iconCredits;

    private Animator animator;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        UIUtils.INSTANCE.setupEdgeToEdge1(getWindow());
        setContentView(R.layout.act_about);
        UIUtils.INSTANCE.setupEdgeToEdge2(findViewById(R.id.rootLayout), true, true);

        // Set status bar icon tint to light (white icons)
        setupStatusBarIconTint();

        findViews();

        setupViews();

        // Setup expandable cards
        setupExpandableCards();

        // Animate backdrop
        backdrop.postDelayed(this::circularRevealAboutImage, 150);

        // Animate cards
        animateCards();

        // Observe night mode changes using LiveData
        AppEventManager.INSTANCE.getNightModeChanged().observe(this, data -> updateNightMode());

        // Note: Intentionally NOT using OnBackPressedCallback here
        // Default back button behavior (finish()) is sufficient
    }

    private void setupStatusBarIconTint() {
        // Set dark status bar icons (black icons for light status bar)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+)
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                // Set light status bar flag to use dark (black) icons
                controller.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6.0+ (API 23-29)
            View decorView = getWindow().getDecorView();
            int flags = decorView.getSystemUiVisibility();
            // Add SYSTEM_UI_FLAG_LIGHT_STATUS_BAR to use dark icons
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            decorView.setSystemUiVisibility(flags);
        }
    }

    private void findViews() {
        tvAbout = findViewById(R.id.tvAbout);
        tvVersion = findViewById(R.id.tvVersion);
        backdrop = findViewById(R.id.backdrop);
        collapsingToolbar = findViewById(R.id.collapsingToolbar);
        toolbar = findViewById(R.id.toolbar);

        // Cards
        cardFeatures = findViewById(R.id.cardFeatures);
        cardAbout = findViewById(R.id.cardAbout);
        cardCredits = findViewById(R.id.cardCredits);

        // Headers
        headerFeatures = findViewById(R.id.headerFeatures);
        headerAbout = findViewById(R.id.headerAbout);
        headerCredits = findViewById(R.id.headerCredits);

        // Contents
        contentFeatures = findViewById(R.id.contentFeatures);
        contentAbout = findViewById(R.id.contentAbout);
        contentCredits = findViewById(R.id.contentCredits);

        // Icons
        iconFeatures = findViewById(R.id.iconFeatures);
        iconAbout = findViewById(R.id.iconAbout);
        iconCredits = findViewById(R.id.iconCredits);
    }

    private void animateCards() {
        View[] cards = { cardFeatures, cardAbout, cardCredits };
        long baseDelay = 300;
        long staggerDelay = 150;

        for (int i = 0; i < cards.length; i++) {
            final View card = cards[i];
            if (card != null) {
                long delay = baseDelay + (i * staggerDelay);
                card.postDelayed(() -> {
                    card.animate()
                            .alpha(1f)
                            .setDuration(500)
                            .start();
                }, delay);
            }
        }
    }

    private void setupExpandableCards() {
        // Features card
        if (headerFeatures != null) {
            headerFeatures.setOnClickListener(v -> toggleCard(contentFeatures, iconFeatures));
        }

        // About card
        if (headerAbout != null) {
            headerAbout.setOnClickListener(v -> toggleCard(contentAbout, iconAbout));
        }

        // Credits card
        if (headerCredits != null) {
            headerCredits.setOnClickListener(v -> toggleCard(contentCredits, iconCredits));
        }
    }

    private void toggleCard(View content, TextView icon) {
        if (content == null || icon == null)
            return;

        if (content.getVisibility() == View.VISIBLE) {
            // Collapse
            content.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction(() -> content.setVisibility(View.GONE))
                    .start();
            icon.animate().rotation(0f).setDuration(200).start();
        } else {
            // Expand
            content.setVisibility(View.VISIBLE);
            content.setAlpha(0f);
            content.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .start();
            icon.animate().rotation(180f).setDuration(200).start();
        }
    }

    private void circularRevealAboutImage() {
        try {
            if (backdrop != null) {
                // Kiểm tra xem view có còn attached không
                if (!backdrop.isAttachedToWindow()) {
                    return;
                }
                // Kiểm tra trạng thái của Activity
                if (this.isDestroyed() || this.isFinishing()) {
                    return; // Không thực hiện animation nếu Activity không hoạt động
                }

                int cx = backdrop.getWidth() / 2;
                int cy = backdrop.getHeight() / 2;
                float finalRadius = (float) Math.hypot(cx, cy);
                animator = ViewAnimationUtils.createCircularReveal(backdrop, cx, cy, 0, finalRadius);
                backdrop.setVisibility(View.VISIBLE);
                if (animator != null) {
                    animator.start();
                }
            }
        } catch (Exception e) {
            // do nothing
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (animator != null) {
            animator.cancel();
        }
    }

    private void setupViews() {
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        collapsingToolbar.setExpandedTitleColor(ContextCompat.getColor(this, R.color.colorTransparent));
        collapsingToolbar.setTitle(getString(R.string.activity_title_about));

        // Set HTML text (minSdk = 25 >= API 24, always use new API)
        tvAbout.setText(Html.fromHtml(getString(R.string.about), Html.FROM_HTML_MODE_LEGACY));
        tvAbout.setMovementMethod(LinkMovementMethod.getInstance());

        // Set version info
        if (tvVersion != null) {
            tvVersion.setText("Version " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.a_slide_in_right, R.anim.a_slide_out_left);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        // Fix BUG-09: Cancel animator trong onDestroy() để tránh window/view leak
        // onPause() đã cancel nhưng nếu Activity bị destroy trực tiếp thì cần xử lý ở đây
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDestroy();
    }
}
