package com.mckimquyen.ui

import android.view.View
import android.widget.LinearLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Widget tests cho `ActAbout` — không có test nào tồn tại cho Activity này trước đây.
 * Thêm khi dọn lint [UselessParent]: mỗi card (`cardFeatures`/`cardAbout`/`cardCredits`)
 * đổi từ `FrameLayout` bọc ngoài một `LinearLayout` con thành một `LinearLayout` duy nhất
 * (background chuyển vào chung). Code Java chỉ khai báo các field này là `View` (không ép
 * kiểu `FrameLayout`), nên đổi kiểu view không ảnh hưởng `findViewById`, nhưng cần chứng
 * minh: (1) Activity vẫn inflate/launch được, (2) mỗi card giờ đúng là `LinearLayout`,
 * (3) hành vi bấm mở rộng từng card vẫn hoạt động sau khi gộp layout.
 */
@RunWith(AndroidJUnit4::class)
class ActAboutWidgetTest {

    @Test
    fun testActAbout_launchesWithoutCrash() {
        val scenario = ActivityScenario.launch(ActAbout::class.java)
        scenario.onActivity { activity ->
            assertFalse("ActAbout must not be finishing after launch", activity.isFinishing)
        }
        scenario.close()
    }

    @Test
    fun testActAbout_cardsAreNowPlainLinearLayouts() {
        val scenario = ActivityScenario.launch(ActAbout::class.java)
        scenario.onActivity { activity ->
            val cardFeatures = activity.findViewById<View>(R.id.cardFeatures)
            val cardAbout = activity.findViewById<View>(R.id.cardAbout)
            val cardCredits = activity.findViewById<View>(R.id.cardCredits)

            assertNotNull("cardFeatures must exist", cardFeatures)
            assertNotNull("cardAbout must exist", cardAbout)
            assertNotNull("cardCredits must exist", cardCredits)

            assertTrue("cardFeatures must be a LinearLayout after the UselessParent merge", cardFeatures is LinearLayout)
            assertTrue("cardAbout must be a LinearLayout after the UselessParent merge", cardAbout is LinearLayout)
            assertTrue("cardCredits must be a LinearLayout after the UselessParent merge", cardCredits is LinearLayout)
        }
        scenario.close()
    }

    @Test
    fun testActAbout_clickingEachHeader_expandsItsContent() {
        val scenario = ActivityScenario.launch(ActAbout::class.java)

        scenario.onActivity { activity ->
            val contentFeatures = activity.findViewById<View>(R.id.contentFeatures)
            val contentAbout = activity.findViewById<View>(R.id.contentAbout)
            val contentCredits = activity.findViewById<View>(R.id.contentCredits)

            assertEquals("contentFeatures starts collapsed", View.GONE, contentFeatures.visibility)
            assertEquals("contentAbout starts collapsed", View.GONE, contentAbout.visibility)
            assertEquals("contentCredits starts collapsed", View.GONE, contentCredits.visibility)

            activity.findViewById<View>(R.id.headerFeatures).performClick()
            activity.findViewById<View>(R.id.headerAbout).performClick()
            activity.findViewById<View>(R.id.headerCredits).performClick()

            // The click listener sets VISIBLE synchronously before starting the fade-in
            // animation, so this is observable immediately without waiting on animation.
            assertEquals("contentFeatures expands on click", View.VISIBLE, contentFeatures.visibility)
            assertEquals("contentAbout expands on click", View.VISIBLE, contentAbout.visibility)
            assertEquals("contentCredits expands on click", View.VISIBLE, contentCredits.visibility)
        }

        scenario.close()
    }
}
