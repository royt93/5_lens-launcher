package com.mckimquyen.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.fragment.app.Fragment
import com.mckimquyen.R
import com.mckimquyen.enums.DrawType
import com.mckimquyen.itf.LensInterface
import com.mckimquyen.util.UtilSettings
import com.mckimquyen.views.LensView

class FrmLens : Fragment(), LensInterface {
    companion object {
        fun newInstance() = FrmLens()
    }

    private var lensViewsSettings: LensView? = null
    private var sbMinIconSize: AppCompatSeekBar? = null
    private var tvValueMinIconSize: TextView? = null
    private var sbDistortionFactor: AppCompatSeekBar? = null
    private var tvValueDistortionFactor: TextView? = null
    private var sbScaleFactor: AppCompatSeekBar? = null
    private var tvValueScaleFactor: TextView? = null
    private var sbAnimationTime: AppCompatSeekBar? = null
    private var tvValueAnimationTime: TextView? = null
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

        // Empty click listeners to prevent parent click events
        view.findViewById<View>(R.id.sbMinIconSizeParent).setOnClickListener(null)
        view.findViewById<View>(R.id.rlSbDistortionFactorParent).setOnClickListener(null)
        view.findViewById<View>(R.id.rlSbScaleFactorParent).setOnClickListener(null)
        view.findViewById<View>(R.id.rlSbAnimationTimeParent).setOnClickListener(null)

        lensViewsSettings?.setDrawType(DrawType.CIRCLES)

        sbMinIconSize?.apply {
            max = UtilSettings.MAX_ICON_SIZE
            setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                @SuppressLint("SetTextI18n")
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    val value = progress + UtilSettings.MIN_ICON_SIZE.toInt()
                    tvValueMinIconSize?.text = "${value}dp"
                    utilSettings?.save(UtilSettings.KEY_ICON_SIZE, value.toFloat())
                    lensViewsSettings?.invalidate()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }

        sbDistortionFactor?.apply {
            max = UtilSettings.MAX_DISTORTION_FACTOR
            setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    val value = progress / 2.0f + UtilSettings.MIN_DISTORTION_FACTOR
                    tvValueDistortionFactor?.text = value.toString()
                    utilSettings?.save(UtilSettings.KEY_DISTORTION_FACTOR, value)
                    lensViewsSettings?.invalidate()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }

        sbScaleFactor?.apply {
            max = UtilSettings.MAX_SCALE_FACTOR
            setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    val value = progress / 5.0f + UtilSettings.MIN_SCALE_FACTOR
                    tvValueScaleFactor?.text = value.toString()
                    utilSettings?.save(UtilSettings.KEY_SCALE_FACTOR, value)
                    lensViewsSettings?.invalidate()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }

        sbAnimationTime?.apply {
            max = UtilSettings.MAX_ANIMATION_TIME
            setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                @SuppressLint("SetTextI18n")
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    val value = progress / 2L + UtilSettings.MIN_ANIMATION_TIME
                    tvValueAnimationTime?.text = "${value}ms"
                    utilSettings?.save(UtilSettings.KEY_ANIMATION_TIME, value)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }
    }

    @SuppressLint("SetTextI18n")
    private fun assignValues() {
        utilSettings?.let { us ->
            val iconSize = us.getFloat(UtilSettings.KEY_ICON_SIZE).toInt()
            sbMinIconSize?.progress = iconSize - UtilSettings.MIN_ICON_SIZE.toInt()
            tvValueMinIconSize?.text = "${iconSize}dp"

            val distortion = us.getFloat(UtilSettings.KEY_DISTORTION_FACTOR)
            sbDistortionFactor?.progress = (2.0f * (distortion - UtilSettings.MIN_DISTORTION_FACTOR)).toInt()
            tvValueDistortionFactor?.text = distortion.toString()

            val scale = us.getFloat(UtilSettings.KEY_SCALE_FACTOR)
            sbScaleFactor?.progress = (5.0f * (scale - UtilSettings.MIN_SCALE_FACTOR)).toInt()
            tvValueScaleFactor?.text = scale.toString()

            val animTime = us.getLong(UtilSettings.KEY_ANIMATION_TIME)
            sbAnimationTime?.progress = (2 * (animTime - UtilSettings.MIN_ANIMATION_TIME)).toInt()
            tvValueAnimationTime?.text = "${animTime}ms"
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
        utilSettings = null
        super.onDestroyView()
    }
}
