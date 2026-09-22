package com.mckimquyen.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.mckimquyen.R
import com.mckimquyen.enums.DrawType
import com.mckimquyen.itf.LensInterface
import com.mckimquyen.util.LensPhysicsPreset
import com.mckimquyen.util.UtilSettings
import com.mckimquyen.views.LensView
import java.util.Locale

class FrmLens : Fragment(), LensInterface {
    companion object {
        fun newInstance() = FrmLens()
    }

    private var lensViewsSettings: LensView? = null
    private var sbMinIconSize: Slider? = null
    private var tvValueMinIconSize: TextView? = null
    private var sbDistortionFactor: Slider? = null
    private var tvValueDistortionFactor: TextView? = null
    private var sbScaleFactor: Slider? = null
    private var tvValueScaleFactor: TextView? = null
    private var sbAnimationTime: Slider? = null
    private var tvValueAnimationTime: TextView? = null
    private var btnPresetGentle: MaterialButton? = null
    private var btnPresetStandard: MaterialButton? = null
    private var btnPresetSnappy: MaterialButton? = null
    private var utilSettings: UtilSettings? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.frm_lens, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        utilSettings = UtilSettings(requireContext())
        setupViews(view)
        assignValues()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        (context as? ActSettings)?.setLensInterface(this)
    }

    private fun setupViews(view: View) {
        lensViewsSettings = view.findViewById(R.id.lensViewsSettings)
        sbMinIconSize = view.findViewById(R.id.sbMinIconSize)
        tvValueMinIconSize = view.findViewById(R.id.tvValueMinIconSize)
        sbDistortionFactor = view.findViewById(R.id.sbDistortionFactor)
        tvValueDistortionFactor = view.findViewById(R.id.tvValueDistortionFactor)
        sbScaleFactor = view.findViewById(R.id.sbScaleFactor)
        tvValueScaleFactor = view.findViewById(R.id.tvValueScaleFactor)
        sbAnimationTime = view.findViewById(R.id.sbAnimationTime)
        tvValueAnimationTime = view.findViewById(R.id.tvValueAnimationTime)
        btnPresetGentle = view.findViewById(R.id.btnPresetGentle)
        btnPresetStandard = view.findViewById(R.id.btnPresetStandard)
        btnPresetSnappy = view.findViewById(R.id.btnPresetSnappy)

        // Empty click listeners to prevent parent click events
        view.findViewById<View>(R.id.sbMinIconSizeParent).setOnClickListener(null)
        view.findViewById<View>(R.id.rlSbDistortionFactorParent).setOnClickListener(null)
        view.findViewById<View>(R.id.rlSbScaleFactorParent).setOnClickListener(null)
        view.findViewById<View>(R.id.rlSbAnimationTimeParent).setOnClickListener(null)

        lensViewsSettings?.setDrawType(DrawType.CIRCLES)

        sbMinIconSize?.addOnChangeListener { _, value, fromUser ->
            tvValueMinIconSize?.text = getString(R.string.unit_dp_format, value.toInt())
            if (fromUser) {
                utilSettings?.save(UtilSettings.KEY_ICON_SIZE, value)
                lensViewsSettings?.invalidate()
            }
        }

        sbDistortionFactor?.addOnChangeListener { _, value, fromUser ->
            tvValueDistortionFactor?.text = String.format(Locale.US, "%.1f", value)
            if (fromUser) {
                utilSettings?.save(UtilSettings.KEY_DISTORTION_FACTOR, value)
                lensViewsSettings?.invalidate()
            }
        }

        sbScaleFactor?.addOnChangeListener { _, value, fromUser ->
            tvValueScaleFactor?.text = String.format(Locale.US, "%.1f", value)
            if (fromUser) {
                utilSettings?.save(UtilSettings.KEY_SCALE_FACTOR, value)
                lensViewsSettings?.invalidate()
            }
        }

        sbAnimationTime?.addOnChangeListener { _, value, fromUser ->
            tvValueAnimationTime?.text = getString(R.string.unit_ms_format, value.toLong())
            if (fromUser) {
                utilSettings?.save(UtilSettings.KEY_ANIMATION_TIME, value.toLong())
            }
        }

        btnPresetGentle?.setOnClickListener { applyPreset(LensPhysicsPreset.GENTLE) }
        btnPresetStandard?.setOnClickListener { applyPreset(LensPhysicsPreset.STANDARD) }
        btnPresetSnappy?.setOnClickListener { applyPreset(LensPhysicsPreset.SNAPPY) }
    }

    /**
     * FISH-004: applies a named preset to the 3 sliders above in one tap - safe/instant, and
     * fully reversible (the sliders remain fine-tunable afterward, same as after Reset to
     * Default, which is itself just the STANDARD preset applied from a different entry point).
     */
    private fun applyPreset(preset: LensPhysicsPreset) {
        utilSettings?.let { us ->
            us.save(UtilSettings.KEY_DISTORTION_FACTOR, preset.distortionFactor)
            us.save(UtilSettings.KEY_SCALE_FACTOR, preset.scaleFactor)
            us.save(UtilSettings.KEY_ANIMATION_TIME, preset.animationTimeMs)
        }
        assignValues()
        lensViewsSettings?.invalidate()
    }

    @SuppressLint("SetTextI18n")
    private fun assignValues() {
        utilSettings?.let { us ->
            val iconSize = us.getFloat(UtilSettings.KEY_ICON_SIZE)
            val minIcon = UtilSettings.MIN_ICON_SIZE
            val maxIcon = UtilSettings.MAX_ICON_SIZE.toFloat() + UtilSettings.MIN_ICON_SIZE
            val validIcon = iconSize.coerceIn(minIcon, maxIcon)
            sbMinIconSize?.value = validIcon
            tvValueMinIconSize?.text = getString(R.string.unit_dp_format, validIcon.toInt())

            val distortion = us.getFloat(UtilSettings.KEY_DISTORTION_FACTOR)
            val validDistortion = distortion.coerceIn(0.5f, 5.0f)
            sbDistortionFactor?.value = validDistortion
            tvValueDistortionFactor?.text = String.format(Locale.US, "%.1f", validDistortion)

            val scale = us.getFloat(UtilSettings.KEY_SCALE_FACTOR)
            val validScale = scale.coerceIn(1.0f, 2.0f)
            sbScaleFactor?.value = validScale
            tvValueScaleFactor?.text = String.format(Locale.US, "%.1f", validScale)

            val animTime = us.getLong(UtilSettings.KEY_ANIMATION_TIME).toFloat()
            val validAnim = animTime.coerceIn(100.0f, 400.0f)
            sbAnimationTime?.value = validAnim
            tvValueAnimationTime?.text = getString(R.string.unit_ms_format, validAnim.toLong())
        }
    }

    override fun onDefaultsReset() {
        resetToDefault()
        assignValues()
    }

    private fun resetToDefault() {
        utilSettings?.let { us ->
            us.save(UtilSettings.KEY_ICON_SIZE, us.autoDefaultIconSize)
            us.save(UtilSettings.KEY_DISTORTION_FACTOR, UtilSettings.DEFAULT_DISTORTION_FACTOR)
            us.save(UtilSettings.KEY_SCALE_FACTOR, UtilSettings.DEFAULT_SCALE_FACTOR)
            us.save(UtilSettings.KEY_ANIMATION_TIME, UtilSettings.DEFAULT_ANIMATION_TIME)
        }
    }

    /**
     * Fix BUG-14: Null toàn bộ view references và utilSettings trong onDestroyView()
     * để tránh Fragment giữ context sau khi view bị destroy.
     */
    override fun onDestroyView() {
        lensViewsSettings = null
        sbMinIconSize = null
        tvValueMinIconSize = null
        sbDistortionFactor = null
        tvValueDistortionFactor = null
        sbScaleFactor = null
        tvValueScaleFactor = null
        sbAnimationTime = null
        tvValueAnimationTime = null
        btnPresetGentle = null
        btnPresetStandard = null
        btnPresetSnappy = null
        utilSettings = null
        super.onDestroyView()
    }
}
