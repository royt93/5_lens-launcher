package com.mckimquyen.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class IcuPinyinProviderTest {
    @Test
    fun `Han labels produce normalized pinyin syllables`() {
        assertEquals(listOf("wei", "xin", "du", "shu"), IcuPinyinProvider.syllables("微信读书"))
        assertEquals(listOf("zhi", "fu", "bao"), IcuPinyinProvider.syllables("支付宝"))
        // Heteronym limitation: ICU picks one reading, so 乐 is "le" here although in 音乐 it is read "yue".
        assertEquals(listOf("wang", "yi", "yun", "yin", "le"), IcuPinyinProvider.syllables("网易云音乐"))
    }

    @Test
    fun `non Han label has no pinyin`() {
        assertTrue(IcuPinyinProvider.syllables("Camera Pro").isEmpty())
    }
}
