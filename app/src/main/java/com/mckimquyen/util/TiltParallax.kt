package com.mckimquyen.util

import android.view.Surface
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2

/**
 * FISH-033: phép tính thuần biến quaternion của `TYPE_GAME_ROTATION_VECTOR` thành độ dịch tâm méo.
 *
 * Quy ước dấu: nghiêng máy sang phải thì tâm méo trôi sang phải, nghiêng đầu máy lên thì trôi lên.
 * Pose đầu tiên sau [Filter.reset] là tư thế tham chiếu, nên cách cầm máy quen thuộc là "tâm".
 * Mọi hằng số có tên để test và smoke dùng chung, không có số nghiệp vụ vô danh trong thân hàm.
 */
object TiltParallax {
    /** Góc nghiêng tối đa được tính (~20°); lớn hơn thì giữ nguyên biên độ tối đa. */
    const val MAX_ANGLE_RADIANS = 0.35f

    /** Biên độ tối đa so với cạnh ngắn của vùng lưới. */
    const val MAX_OFFSET_FRACTION = 0.12f

    /** Rung tay dưới ngưỡng này (~1°) không làm tâm trôi. */
    const val DEAD_ZONE_RADIANS = 0.02f

    /** Bộ lọc thông thấp một cực, 0..1; nhỏ thì mượt nhưng trễ hơn. */
    const val SMOOTHING_FACTOR = 0.15f

    /** Mức méo khi rảnh có tilt; nhỏ hơn mức khi chạm để hiệu ứng nhẹ. */
    const val ANIMATION_MULTIPLIER = 0.6f

    /** Offset đổi dưới ngưỡng này thì không cần vẽ lại. */
    const val INVALIDATE_THRESHOLD_PX = 0.5f

    private const val HALF = 2f
    private const val ANGLE_COUNT = 2

    /**
     * Nghiêng ngang/dọc của máy (rad) theo hướng màn hình, vào [out] (độ dài >= 2): out[0] ngang, out[1] dọc.
     * Quay quanh trục Y của máy là nghiêng ngang, quanh trục X là nghiêng dọc; xoay màn hình hoán đổi hai trục.
     */
    fun anglesOf(qx: Float, qy: Float, qz: Float, qw: Float, rotation: Int, out: FloatArray) {
        require(out.size >= ANGLE_COUNT)
        val roll = atan2(HALF * (qw * qy + qx * qz), 1f - HALF * (qy * qy + qx * qx))
        val pitch = asin((HALF * (qw * qx - qy * qz)).coerceIn(-1f, 1f))
        when (rotation) {
            Surface.ROTATION_90 -> { out[0] = -pitch; out[1] = roll }
            Surface.ROTATION_180 -> { out[0] = -roll; out[1] = -pitch }
            Surface.ROTATION_270 -> { out[0] = pitch; out[1] = -roll }
            else -> { out[0] = roll; out[1] = pitch }
        }
    }

    /** Bộ lọc có trạng thái; không cấp phát trong [update] nên dùng được ở tần số sensor. */
    class Filter {
        var offsetX = 0f
            private set
        var offsetY = 0f
            private set
        private var hasReference = false
        private var referenceX = 0f
        private var referenceY = 0f
        private val angles = FloatArray(ANGLE_COUNT)

        fun reset() {
            offsetX = 0f
            offsetY = 0f
            hasReference = false
        }

        /** True nếu offset đổi quá [INVALIDATE_THRESHOLD_PX]; đầu vào không hợp lệ thì giữ nguyên trạng thái. */
        fun update(qx: Float, qy: Float, qz: Float, qw: Float, rotation: Int, areaPx: Float): Boolean {
            if (!qx.isFinite() || !qy.isFinite() || !qz.isFinite() || !qw.isFinite()) return false
            if (!areaPx.isFinite() || areaPx <= 0f) return false
            anglesOf(qx, qy, qz, qw, rotation, angles)
            if (!angles[0].isFinite() || !angles[1].isFinite()) return false
            if (!hasReference) {
                referenceX = angles[0]
                referenceY = angles[1]
                hasReference = true
                return false
            }
            val maxPx = MAX_OFFSET_FRACTION * areaPx
            val targetX = normalized(angles[0] - referenceX) * maxPx
            val targetY = normalized(angles[1] - referenceY) * maxPx
            val newX = offsetX + (targetX - offsetX) * SMOOTHING_FACTOR
            val newY = offsetY + (targetY - offsetY) * SMOOTHING_FACTOR
            val changed = abs(newX - offsetX) > INVALIDATE_THRESHOLD_PX || abs(newY - offsetY) > INVALIDATE_THRESHOLD_PX
            offsetX = newX
            offsetY = newY
            return changed
        }

        /** Vùng chết rồi clamp về [-1, 1]; giữ dấu của góc. */
        private fun normalized(delta: Float): Float {
            val magnitude = abs(delta)
            if (magnitude <= DEAD_ZONE_RADIANS) return 0f
            val scaled = ((magnitude - DEAD_ZONE_RADIANS) / MAX_ANGLE_RADIANS).coerceAtMost(1f)
            return if (delta < 0f) -scaled else scaled
        }
    }
}
