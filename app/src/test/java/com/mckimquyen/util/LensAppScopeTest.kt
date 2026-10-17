package com.mckimquyen.util

import com.mckimquyen.model.App
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class LensAppScopeTest {

    private fun app(pkg: String, name: String) =
        App(id = 1, label = pkg, packageName = pkg, name = name)

    private val mail = app("com.mail", "Main")
    private val chat = app("com.chat", "Main")
    private val apps = listOf(mail, chat)

    @Test
    fun `temporary hiding filters ALL without changing input or global visibility`() {
        val hidden = setOf(LensAppScope.identifierOf(mail), "missing-Activity")
        val result = LensAppScope.filter(apps, LensAppScope.ALL, emptySet(), hidden)
        assertEquals(listOf(chat), result)
        assertEquals(listOf(mail, chat), apps)
        assertEquals(true, mail.isVisible)
        assertEquals(2, hidden.size)
    }

    @Test
    fun `temporary hiding preserves selection and can hide the last selected app`() {
        val selected = setOf(LensAppScope.identifierOf(mail))
        val hidden = setOf(LensAppScope.identifierOf(mail))
        assertEquals(emptyList<App>(), LensAppScope.filter(apps, LensAppScope.SELECTED, selected, hidden))
        assertEquals(setOf(LensAppScope.identifierOf(mail)), selected)
        assertEquals(listOf(mail), LensAppScope.filter(apps, LensAppScope.SELECTED, selected, emptySet()))
    }

    @Test
    fun `ALL returns every app as a new list`() {
        val result = LensAppScope.filter(apps, LensAppScope.ALL, emptySet())
        assertEquals(apps, result)
        assertNotSame(apps, result)
    }

    @Test
    fun `SELECTED keeps only apps whose identifier is selected`() {
        val result = LensAppScope.filter(
            apps, LensAppScope.SELECTED, setOf(LensAppScope.identifierOf(chat))
        )
        assertEquals(listOf(chat), result)
    }

    @Test
    fun `SELECTED with an empty selection returns nothing`() {
        assertEquals(emptyList<App>(), LensAppScope.filter(apps, LensAppScope.SELECTED, emptySet()))
    }

    @Test
    fun `identifiers that match no installed app are ignored`() {
        val result = LensAppScope.filter(
            apps, LensAppScope.SELECTED, setOf("gone-App", LensAppScope.identifierOf(mail))
        )
        assertEquals(listOf(mail), result)
    }

    @Test
    fun `fromStored falls back to ALL for null or unknown values`() {
        assertEquals(LensAppScope.ALL, LensAppScope.fromStored(null))
        assertEquals(LensAppScope.ALL, LensAppScope.fromStored("bogus"))
        assertEquals(LensAppScope.SELECTED, LensAppScope.fromStored("SELECTED"))
    }

    @Test
    fun `filter keeps the input order`() {
        val maps = app("com.maps", "Main")
        val result = LensAppScope.filter(
            listOf(maps, mail, chat), LensAppScope.SELECTED,
            setOf(LensAppScope.identifierOf(chat), LensAppScope.identifierOf(maps))
        )
        assertEquals(listOf(maps, chat), result)
    }

    @Test
    fun `filter never mutates its input`() {
        val input = arrayListOf(mail, chat)
        LensAppScope.filter(input, LensAppScope.SELECTED, setOf(LensAppScope.identifierOf(mail)))
        assertEquals(listOf(mail, chat), input)
    }

    @Test
    fun `the same package with a different activity name is a different app`() {
        val a = app("com.same", "ActA")
        val b = app("com.same", "ActB")
        val result = LensAppScope.filter(listOf(a, b), LensAppScope.SELECTED, setOf(LensAppScope.identifierOf(a)))
        assertEquals(listOf(a), result)
    }
}
