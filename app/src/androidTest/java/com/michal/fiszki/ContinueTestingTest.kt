package com.michal.fiszki

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.michal.fiszki.model.LearningStatus
import com.michal.fiszki.model.Theme
import com.michal.fiszki.model.Word
import com.michal.fiszki.ui.theme.FiszkiTheme
import com.michal.fiszki.ui.words.WordListScreen
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContinueTestingTest {
    @Test
    fun regularTestIncludesEarlierMissesAndUncheckedWords() {
        assertContinuedWords(isBasket = false, expectedIds = listOf(1, 3, 5))
    }

    @Test
    fun basketTestUsesBasketProgressToIncludeEarlierMissesAndUncheckedWords() {
        assertContinuedWords(isBasket = true, expectedIds = listOf(2, 3, 5))
    }

    private fun assertContinuedWords(isBasket: Boolean, expectedIds: List<Int>) {
        val words = listOf(
            word(1, LearningStatus.NOT_LEARNED, LearningStatus.LEARNED),
            word(2, LearningStatus.LEARNED, LearningStatus.NOT_LEARNED),
            word(3, LearningStatus.UNCHECKED, LearningStatus.UNCHECKED),
            word(4, LearningStatus.LEARNED, LearningStatus.LEARNED),
            word(5, LearningStatus.UNCHECKED, LearningStatus.UNCHECKED)
        )
        val result = AtomicReference<Triple<List<Word>, Boolean, Boolean>>()
        val continued = CountDownLatch(1)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    FiszkiTheme {
                        WordListScreen(
                            theme = Theme(id = if (isBasket) -1 else 1, name = "Test"),
                            words = words,
                            isBasket = isBasket,
                            onBack = {},
                            onPlayFlashcards = { selectedWords, testMode, unlearnedSession ->
                                result.set(Triple(selectedWords, testMode, unlearnedSession))
                                continued.countDown()
                            }
                        )
                    }
                }
            }

            click("Play Flashcards")
            click("Continue Testing")

            assertTrue("Continue Testing did not start a session", continued.await(5, TimeUnit.SECONDS))
            assertEquals(expectedIds, result.get().first.map { it.id })
            assertTrue(result.get().second)
            assertEquals(false, result.get().third)
        }
    }

    private fun click(label: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            var node = findNode(instrumentation.uiAutomation.rootInActiveWindow, label)
            while (node != null && !node.isClickable) node = node.parent
            if (node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return
            SystemClock.sleep(50)
        }
        fail("Could not click $label")
    }

    private fun findNode(node: AccessibilityNodeInfo?, label: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString() == label || node.contentDescription?.toString() == label) return node
        for (index in 0 until node.childCount) {
            findNode(node.getChild(index), label)?.let { return it }
        }
        return null
    }

    private fun word(id: Int, status: LearningStatus, basketStatus: LearningStatus) = Word(
        id = id, themeId = 1, original = "word $id", translation = "translation $id",
        status = status, isInBasket = true, basketStatus = basketStatus
    )
}
