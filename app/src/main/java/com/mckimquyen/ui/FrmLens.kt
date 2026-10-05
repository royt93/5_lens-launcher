package com.mckimquyen.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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
import com.mckimquyen.model.LensWorkspace
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
    private var btnPresetCustom: MaterialButton? = null
    private var btnSaveCustomPreset: MaterialButton? = null
    private var btnShareLens: MaterialButton? = null
    private var utilSettings: UtilSettings? = null
    private var activeLensId: String = LensWorkspace.DEFAULT_LENS_ID

    /** Test seam: swap this to capture the launched Intent instead of actually starting ActHome -
     *  same pattern as ActHome.lensShareLauncher, which this button ultimately triggers. */
    @androidx.annotation.VisibleForTesting
    internal var lensExportLauncher: (Intent) -> Unit = { intent -> startActivity(intent) }

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
        refreshActiveLens()
        assignValues()
    }

    override fun onResume() {
        super.onResume()
        // FISH-008 Phase 3: the active lens can change while this Fragment stays alive in
        // ActSettings' pager. Re-read on every resume, matching FrmSettings.assignValues().
        refreshActiveLens()
        assignValues()
    }

    private fun refreshActiveLens() {
        activeLensId = utilSettings?.getString(UtilSettings.KEY_ACTIVE_LENS_ID)
            ?: LensWorkspace.DEFAULT_LENS_ID
        lensViewsSettings?.lensId = activeLensId
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
        btnPresetCustom = view.findViewById(R.id.btnPresetCustom)
        btnSaveCustomPreset = view.findViewById(R.id.btnSaveCustomPreset)

        // Empty click listeners to prevent parent click events
        view.findViewById<View>(R.id.sbMinIconSizeParent).setOnClickListener(null)
        view.findViewById<View>(R.id.rlSbDistortionFactorParent).setOnClickListener(null)
        view.findViewById<View>(R.id.rlSbScaleFactorParent).setOnClickListener(null)
        view.findViewById<View>(R.id.rlSbAnimationTimeParent).setOnClickListener(null)

        lensViewsSettings?.setDrawType(DrawType.CIRCLES)

        sbMinIconSize?.addOnChangeListener { _, value, fromUser ->
            tvValueMinIconSize?.text = getString(R.string.unit_dp_format, value.toInt())
            if (fromUser) {
                utilSettings?.saveIconSize(activeLensId, value)
                lensViewsSettings?.invalidate()
            }
        }

        sbDistortionFactor?.addOnChangeListener { _, value, fromUser ->
            tvValueDistortionFactor?.text = String.format(Locale.US, "%.1f", value)
            if (fromUser) {
                utilSettings?.saveDistortionFactor(activeLensId, value)
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

        btnPresetGentle?.setOnClickListener {
            applyPreset(
                LensPhysicsPreset.GENTLE.distortionFactor,
                LensPhysicsPreset.GENTLE.scaleFactor,
                LensPhysicsPreset.GENTLE.animationTimeMs
            )
        }
        btnPresetStandard?.setOnClickListener {
            applyPreset(
                LensPhysicsPreset.STANDARD.distortionFactor,
                LensPhysicsPreset.STANDARD.scaleFactor,
                LensPhysicsPreset.STANDARD.animationTimeMs
            )
        }
        btnPresetSnappy?.setOnClickListener {
            applyPreset(
                LensPhysicsPreset.SNAPPY.distortionFactor,
                LensPhysicsPreset.SNAPPY.scaleFactor,
                LensPhysicsPreset.SNAPPY.animationTimeMs
            )
        }
        btnPresetCustom?.setOnClickListener {
            utilSettings?.let { us ->
                applyPreset(
                    us.getCustomDistortionFactor(activeLensId),
                    us.getCustomScaleFactor(),
                    us.getCustomAnimationTime()
                )
            }
        }
        btnSaveCustomPreset?.setOnClickListener { saveCurrentAsCustomPreset() }
        btnShareLens = view.findViewById(R.id.btnShareLens)
        btnShareLens?.setOnClickListener { shareLensImage() }
    }

    private fun shareLensImage() {
        // FISH-013: persist pending flag before dispatching Intent so the request survives a
        // process kill between this tap and exportAsync completing in ActHome.
        utilSettings?.setPendingAutoExportLens(true)
        val intent = Intent(requireContext(), ActHome::class.java).apply {
            putExtra(ActHome.EXTRA_AUTO_EXPORT_LENS, true)
        }
        lensExportLauncher(intent)
    }

    /**
     * FISH-004/FISH-015: applies a (distortion, scale, animation-time) triple to the 3 sliders
     * above in one tap - safe/instant, and fully reversible (the sliders remain fine-tunable
     * afterward, same as after Reset to Default). Takes raw values rather than a
     * [LensPhysicsPreset] so the user-saved Custom preset (not a compile-time constant) can reuse
     * this exact same path.
     */
    private fun applyPreset(distortion: Float, scale: Float, animationTimeMs: Long) {
        utilSettings?.let { us ->
            us.saveDistortionFactor(activeLensId, distortion)
            us.save(UtilSettings.KEY_SCALE_FACTOR, scale)
            us.save(UtilSettings.KEY_ANIMATION_TIME, animationTimeMs)
        }
        assignValues()
        lensViewsSettings?.invalidate()
    }

    /** FISH-015: captures the 3 sliders' current live values into the single Custom slot. */
    private fun saveCurrentAsCustomPreset() {
        utilSettings?.let { us ->
            val distortion = sbDistortionFactor?.value ?: return
            val scale = sbScaleFactor?.value ?: return
            val animationTimeMs = sbAnimationTime?.value?.toLong() ?: return
            us.saveCustomDistortionFactor(activeLensId, distortion)
            us.saveCustomScaleFactor(scale)
            us.saveCustomAnimationTime(animationTimeMs)
        }
        refreshCustomPresetButtonState()
        android.widget.Toast.makeText(
            requireContext(),
            R.string.lens_physics_custom_preset_saved,
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    /** FISH-015: Custom is only usable once at least one save has ever happened. */
    private fun refreshCustomPresetButtonState() {
        btnPresetCustom?.isEnabled = utilSettings?.hasCustomPreset() == true
    }

    @SuppressLint("SetTextI18n")
    private fun assignValues() {
        utilSettings?.let { us ->
            val iconSize = us.getIconSize(activeLensId)
            val minIcon = UtilSettings.MIN_ICON_SIZE
            val maxIcon = UtilSettings.MAX_ICON_SIZE.toFloat() + UtilSettings.MIN_ICON_SIZE
            val validIcon = iconSize.coerceIn(minIcon, maxIcon)
            sbMinIconSize?.value = validIcon
            tvValueMinIconSize?.text = getString(R.string.unit_dp_format, validIcon.toInt())

            val distortion = us.getDistortionFactor(activeLensId)
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
        refreshCustomPresetButtonState()
    }

    override fun onDefaultsReset() {
        resetToDefault()
        assignValues()
        lensViewsSettings?.invalidate()
    }

    private fun resetToDefault() {
        utilSettings?.let { us ->
            us.saveIconSize(activeLensId, us.autoDefaultIconSize)
            us.saveDistortionFactor(activeLensId, UtilSettings.DEFAULT_DISTORTION_FACTOR)
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
        btnShareLens = null
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
        btnPresetCustom = null
        btnSaveCustomPreset = null
        utilSettings = null
        super.onDestroyView()
    }
}
