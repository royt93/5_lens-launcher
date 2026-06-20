package com.mckimquyen.feature.vip

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mckimquyen.R
import com.mckimquyen.databinding.ActVipManagementBinding
import com.roy.sdkadbmob.AdManager
import com.roy.sdkadbmob.AppPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ActVipManagement : AppCompatActivity() {

    private lateinit var binding: ActVipManagementBinding

    private var countDownTimer: CountDownTimer? = null
    private var pulseAnimator: ObjectAnimator? = null
    private var shimmerAnimator: ObjectAnimator? = null
    private var countUpAnimator: ValueAnimator? = null
    private var confettiAnimator: ObjectAnimator? = null
    private var entryAnimator: ValueAnimator? = null // BUG-10: store for cancellation

    private lateinit var vipPrefs: VipPrefs
    private var lastMinute: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActVipManagementBinding.inflate(layoutInflater)
        com.mckimquyen.util.UIUtils.setupEdgeToEdge1(window)
        setContentView(binding.root)
        com.mckimquyen.util.UIUtils.setupEdgeToEdge2(binding.rootLayout, true, true)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        vipPrefs = VipPrefs(this)

        setupListeners()
        bindUi()
        playSlideInAnimation()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onResume() {
        super.onResume()
        startAnimators()
        bindUi()
    }

    override fun onPause() {
        super.onPause()
        pauseAnimators()
    }

    private fun setupListeners() {
        binding.edtVipKey.addTextChangedListener(object : android.text.TextWatcher {
            private var lastState = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val isEnabledNow = !s.isNullOrBlank()
                if (isEnabledNow != lastState) {
                    lastState = isEnabledNow
                    binding.btnActivateVipKey.isEnabled = isEnabledNow
                    animateEnableButton(binding.btnActivateVipKey, isEnabledNow)
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.btnActivateVipKey.setOnClickListener {
            val rawKey = binding.edtVipKey.text?.toString()?.trim() ?: ""
            if (rawKey.isEmpty()) return@setOnClickListener
            val normalizedKey = rawKey.uppercase() // BUG-4: normalize to uppercase before SDK call
            val days = VipKeys.lookupDays(normalizedKey)
            if (days != null) {
                val originalSecret = AdManager.adConfig.vipKeySecret
                try { // BUG-5: exception-safe — always restore secret in finally
                    AdManager.adConfig = AdManager.adConfig.copy(vipKeySecret = normalizedKey)
                    val success = AdManager.activateVipByKey(this, normalizedKey, days)
                    if (success) {
                        showMaterialDialog(
                            getString(R.string.vip_success_title),
                            getString(R.string.vip_activation_success_message, days),
                            R.drawable.ic_star_24dp
                        )
                        binding.edtVipKey.text?.clear()
                        binding.edtVipKey.clearFocus()
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                        imm.hideSoftInputFromWindow(binding.edtVipKey.windowToken, 0)
                        handleVipSuccess(days)
                    } else {
                        showMaterialDialog(
                            R.string.vip_failed_title,
                            R.string.vip_activation_failed_message
                        )
                    }
                } finally {
                    AdManager.adConfig = AdManager.adConfig.copy(vipKeySecret = originalSecret)
                }
            } else {
                showMaterialDialog(
                    getString(R.string.vip_error_title),
                    getString(R.string.vip_invalid_key_message)
                )
            }
        }

        binding.btnWatchAdVip.setOnClickListener {
            if (!isNetworkAvailable()) { // BUG-7: fail fast when offline
                Toast.makeText(this, getString(R.string.no_internet), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val fallbackTriggered = java.util.concurrent.atomic.AtomicBoolean(false)
            val showFallbackInterstitial = {
                if (fallbackTriggered.compareAndSet(false, true)) {
                    if (!isFinishing) {
                        AdManager.showInterstitial(this) { success ->
                            if (!isFinishing) {
                                if (success) {
                                    grantViaRewarded()
                                } else {
                                    showMaterialDialog(
                                        R.string.vip_failed_title,
                                        R.string.vip_ad_reward_failed_message
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val originalListener = AdManager.rewardedListener
            val tempRewardedListener = object : com.roy.sdkadbmob.RewardedAdListener {
                override fun onAdLoaded() {}
                override fun onAdFailedToLoad(error: com.roy.sdkadbmob.AdError) {
                    showFallbackInterstitial()
                }
                override fun onAdShowed() {}
                override fun onAdDismissed() {}
                override fun onAdClicked() {}
                override fun onAdFailedToShow(error: com.roy.sdkadbmob.AdError) {
                    showFallbackInterstitial()
                }
                override fun onAdNotAvailable() {
                    showFallbackInterstitial()
                }
                override fun onUserEarnedReward(type: String, amount: Int) {}
            }
            AdManager.rewardedListener = tempRewardedListener

            AdManager.showRewarded(this) { earned ->
                if (AdManager.rewardedListener === tempRewardedListener) {
                    AdManager.rewardedListener = originalListener
                }
                if (isFinishing) return@showRewarded
                if (earned) {
                    grantViaRewarded()
                }
            }
        }

        binding.btnRevokeVip.setOnClickListener {
            MaterialAlertDialogBuilder(this, R.style.MaterialYouDialogTheme) // BUG-9: theme overlay provides colorSurface
                .setTitle(R.string.vip_revoke_all_confirm_title)
                .setMessage(R.string.vip_revoke_all_confirm_message)
                .setPositiveButton(R.string.confirm) { _, _ ->
                    AdManager.clearVipByKey()
                    // If the device is marked VIP via GAID, we also try to remove it for thoroughness
                    AdManager.deleteVIPMember(listOf(AdManager.getCurrentDeviceGAID()))
                    vipPrefs.clearGrantedAtMs()
                    vipPrefs.clearVipDays()
                    bindUi()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        binding.tvPrivacyPolicy.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AdKeys.PRIVACY_POLICY_URL)))
        }
    }

    private fun grantViaRewarded() {
        val originalSecret = AdManager.adConfig.vipKeySecret
        try { // BUG-11: always restore original secret regardless of success/failure/exception
            AdManager.adConfig = AdManager.adConfig.copy(vipKeySecret = VipKeys.VIP_3D_KEY)
            val success = AdManager.activateVipByKey(this, VipKeys.VIP_3D_KEY, 3)
            if (success) {
                showMaterialDialog(
                    getString(R.string.vip_success_title),
                    getString(R.string.vip_activation_success_message, 3),
                    R.drawable.ic_star_24dp
                )
                handleVipSuccess(3)
            }
        } finally {
            AdManager.adConfig = AdManager.adConfig.copy(vipKeySecret = originalSecret)
        }
    }

    private fun handleVipSuccess(days: Int) {
        vipPrefs.saveGrantedAtMs(System.currentTimeMillis())
        vipPrefs.saveVipDays(days)
        vipPrefs.markUserRedeemed()
        bindUi()
        playConfetti()
    }

    private fun bindUi() {
        // SDK truth: VIP active if GAID in whitelist OR VIP by key is active
        val isActive = AdManager.isVIPMember()
        
        binding.btnRevokeVip.isEnabled = isActive

        if (isActive) {
            binding.tvStatusBadge.text = getString(R.string.vip_badge_premium_member)
            binding.tvStatusBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#20000000")))
            binding.tvStatusBadge.setTextColor(android.graphics.Color.parseColor("#1C1C1E"))

            binding.btnWatchAdVip.isEnabled = false
            binding.btnWatchAdVip.text = getString(R.string.vip_ad_disabled_active)
            binding.btnWatchAdVip.alpha = 0.5f

            binding.tvStatusTitle.text = getString(R.string.vip_active)
            binding.layoutStatusHeaderBg.setBackgroundResource(R.drawable.bg_vip_status_header_active)
            binding.imgCrown.setColorFilter(android.graphics.Color.parseColor("#FFFFFF"), android.graphics.PorterDuff.Mode.SRC_IN)
            binding.tvStatusTitle.setTextColor(android.graphics.Color.parseColor("#1C1C1E"))
            binding.tvStatusSubtitle.text = getString(R.string.vip_active_thank_you)
            binding.tvStatusSubtitle.setTextColor(android.graphics.Color.parseColor("#3A3A3C"))
            binding.tvStatusSubtitle.isVisible = true
            binding.layoutActiveVipInfo.isVisible = true
            
            val expiryMs = AdManager.getVipByKeyExpiry()
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            
            if (AdManager.isVipByKeyActive()) {
                val formattedExpiry = dateFormat.format(Date(expiryMs))
                binding.tvStatusSubtitle.text = getString(R.string.vip_until, formattedExpiry)
                binding.tvExpiryDate.text = getString(R.string.vip_expires_at, formattedExpiry)
                binding.tvExpiryDate.isVisible = true
                binding.progressVip.isVisible = true
                binding.tvCountdown.isVisible = true
            } else {
                // VIP by GAID (Lifetime or unspecified)
                binding.tvStatusSubtitle.text = getString(R.string.vip_lifetime_device)
                binding.tvExpiryDate.isVisible = false
                binding.progressVip.isVisible = false
                binding.tvCountdown.isVisible = false
            }
            
            val grantedAtMs = vipPrefs.getGrantedAtMs()
            if (grantedAtMs > 0) {
                binding.tvActivationDate.text = getString(R.string.vip_activated_at, dateFormat.format(Date(grantedAtMs)))
                binding.tvActivationDate.isVisible = true
            } else {
                binding.tvActivationDate.isVisible = false
            }
            
            val isGrace = AppPreferences.getInstance(this).isAddVIPMemberFirstInitSuccess() && !vipPrefs.userRedeemedAtLeastOnce()
            if (isGrace) { // BUG-8: show grace label during active grace period
                binding.tvActiveVipLabel.text = getString(R.string.vip_entry_first_install)
            } else {
                val days = vipPrefs.getVipDays()
                if (days > 0) {
                    binding.tvActiveVipLabel.text = getString(R.string.vip_entry_redeemed, days)
                } else {
                    binding.tvActiveVipLabel.text = getString(R.string.vip_active)
                }
            }
            
            if (AdManager.isVipByKeyActive()) {
                startCountdownTimer(expiryMs, grantedAtMs)
            } else {
                countDownTimer?.cancel()
                countDownTimer = null
            }
        } else {
            binding.tvStatusBadge.text = getString(R.string.vip_badge_free_member)
            binding.tvStatusBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#15FFFFFF")))
            binding.tvStatusBadge.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))

            binding.btnWatchAdVip.isEnabled = true
            binding.btnWatchAdVip.text = getString(R.string.vip_watch_ad_3d)
            binding.btnWatchAdVip.alpha = 1.0f

            binding.tvStatusTitle.text = getString(R.string.vip_free_user)
            binding.layoutStatusHeaderBg.setBackgroundResource(R.drawable.bg_vip_status_header_free)
            binding.imgCrown.setColorFilter(android.graphics.Color.parseColor("#ECEFF1"), android.graphics.PorterDuff.Mode.SRC_IN)
            binding.tvStatusTitle.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
            binding.tvStatusSubtitle.setTextColor(android.graphics.Color.parseColor("#B0BEC5"))
            binding.tvStatusSubtitle.isVisible = false
            binding.layoutActiveVipInfo.isVisible = false
            countDownTimer?.cancel()
            countDownTimer = null
        }
    }

    private fun showMaterialDialog(titleId: Int, messageId: Int, iconRes: Int? = null) {
        showMaterialDialog(getString(titleId), getString(messageId), iconRes)
    }

    private fun showMaterialDialog(title: String, message: String, iconRes: Int? = null) {
        if (isFinishing) return
        val builder = MaterialAlertDialogBuilder(this, R.style.MaterialYouDialogTheme) // BUG-9: theme overlay provides colorSurface
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.vip_dialog_ok, null)
        
        if (iconRes != null) {
            builder.setIcon(iconRes)
        }
        builder.show()
    }

    private fun animateEnableButton(button: android.widget.Button, enabled: Boolean) {
        button.animate().cancel()
        if (enabled) {
            button.setTextColor(android.graphics.Color.BLACK)
            button.animate()
                .scaleX(1.08f)
                .scaleY(1.08f)
                .setDuration(150)
                .withEndAction {
                    button.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .start()
                }
                .start()
        } else {
            button.setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
            button.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(150)
                .start()
        }
    }

    private fun computeElapsedProgress(grantedAtMs: Long, expiresAtMs: Long, nowMs: Long): Int {
        val safeGranted = if (grantedAtMs <= 0) (expiresAtMs - 3L * 24 * 60 * 60 * 1000) else grantedAtMs
        val total = expiresAtMs - safeGranted
        if (total <= 0L) return 100
        val elapsed = nowMs - safeGranted
        return ((elapsed.toDouble() / total.toDouble()) * 100.0).toInt().coerceIn(0, 100)
    }

    private fun startCountdownTimer(expiryMs: Long, grantedAtMs: Long) {
        countDownTimer?.cancel()
        val remainingMs = expiryMs - System.currentTimeMillis()
        if (remainingMs <= 0) {
            binding.tvCountdown.text = getString(R.string.vip_remaining, 0, 0, 0, 0)
            binding.progressVip.progress = 100
            return
        }

        countDownTimer = object : CountDownTimer(remainingMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                if (isFinishing) return
                
                val nowMs = System.currentTimeMillis()
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    binding.progressVip.setProgress(computeElapsedProgress(grantedAtMs, expiryMs, nowMs), true)
                } else {
                    binding.progressVip.progress = computeElapsedProgress(grantedAtMs, expiryMs, nowMs)
                }

                val seconds = (millisUntilFinished / 1000) % 60
                val minutes = (millisUntilFinished / (1000 * 60)) % 60
                val hours = (millisUntilFinished / (1000 * 60 * 60)) % 24
                val days = millisUntilFinished / (1000 * 60 * 60 * 24)

                binding.tvCountdown.text = getString(R.string.vip_remaining, days, hours, minutes, seconds)

                val currentMinute = minutes.toInt()
                if (lastMinute != currentMinute) {
                    if (lastMinute != null) {
                        playCountUpAnimation(lastMinute!!, currentMinute)
                    }
                    lastMinute = currentMinute
                }
            }

            override fun onFinish() {
                if (!isFinishing) {
                    vipPrefs.clearGrantedAtMs()
                    vipPrefs.clearVipDays()
                    bindUi()
                }
            }
        }.start()
    }

    private fun playSlideInAnimation() {
        val s1 = binding.section1
        val s2 = binding.section2
        val s3 = binding.section3

        s1.alpha = 0f
        s1.translationY = 200f
        s2.alpha = 0f
        s2.translationY = 200f
        s3.alpha = 0f
        s3.translationY = 200f

        entryAnimator = ValueAnimator.ofFloat(0f, 1f).apply { // BUG-10: store in field for cancellation
            duration = 1100L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float
                val f1 = ((fraction - 0.0f) / 0.4f).coerceIn(0f, 1f)
                s1.alpha = f1
                s1.translationY = (1f - f1) * 200f

                val f2 = ((fraction - 0.3f) / 0.4f).coerceIn(0f, 1f)
                s2.alpha = f2
                s2.translationY = (1f - f2) * 200f

                val f3 = ((fraction - 0.6f) / 0.4f).coerceIn(0f, 1f)
                s3.alpha = f3
                s3.translationY = (1f - f3) * 200f
            }
        }
        entryAnimator?.start()
    }

    private fun startAnimators() {
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            binding.btnWatchAdVip,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.05f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.05f)
        ).apply {
            duration = 1600L
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            start()
        }

        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.15f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.15f)
        val rotation = PropertyValuesHolder.ofFloat(View.ROTATION, -8f, 8f)
        val translationY = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, -8f, 8f)
        shimmerAnimator = ObjectAnimator.ofPropertyValuesHolder(binding.imgCrown, scaleX, scaleY, rotation, translationY).apply {
            duration = 2000L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun pauseAnimators() {
        pulseAnimator?.cancel()
        shimmerAnimator?.cancel()
    }

    private fun playCountUpAnimation(from: Int, to: Int) {
        countUpAnimator?.cancel()
        countUpAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400L
            addUpdateListener { anim ->
                val fraction = anim.animatedFraction
                val scale = 1.0f + if (fraction < 0.5f) fraction * 0.2f else (1f - fraction) * 0.2f
                binding.tvCountdown.scaleX = scale
                binding.tvCountdown.scaleY = scale
            }
            start()
        }
    }

    private fun playConfetti() {
        shimmerAnimator?.cancel()
        confettiAnimator?.cancel()
        confettiAnimator = ObjectAnimator.ofPropertyValuesHolder(
            binding.imgCrown,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0f, 1.2f, 1.0f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0f, 1.2f, 1.0f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f)
        ).apply {
            duration = 1000L
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!isFinishing) {
                        startAnimators()
                    }
                }
            })
            start()
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            binding.root.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator?.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(50)
            }
        }
    }

    override fun onDestroy() {
        countDownTimer?.cancel()
        countDownTimer = null
        pulseAnimator?.cancel()
        pulseAnimator = null
        shimmerAnimator?.cancel()
        shimmerAnimator = null
        entryAnimator?.cancel() // BUG-10: cancel slide-in animator
        entryAnimator = null
        countUpAnimator?.cancel()
        countUpAnimator = null
        confettiAnimator?.cancel()
        confettiAnimator = null
        super.onDestroy()
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        @Suppress("DEPRECATION")
        val activeNetwork = cm.activeNetworkInfo
        @Suppress("DEPRECATION")
        return activeNetwork?.isConnected == true
    }
}
