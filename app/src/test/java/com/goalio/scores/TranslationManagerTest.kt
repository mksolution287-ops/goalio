package com.goalio.scores

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationManagerTest {
    @Test
    fun userVisibleTextIsTranslated() {
        assertTrue(TranslationManager.isTranslatable("Match Summary"))
        assertTrue(TranslationManager.isTranslatable("SETTINGS"))
    }

    @Test
    fun technicalValuesAreProtected() {
        assertFalse(TranslationManager.isTranslatable("https://goalio.app/privacy"))
        assertFalse(TranslationManager.isTranslatable("user@example.com"))
        assertFalse(TranslationManager.isTranslatable("{player_name}"))
        assertFalse(TranslationManager.isTranslatable("\${match_id}"))
        assertFalse(TranslationManager.isTranslatable("match_event_id"))
        assertFalse(TranslationManager.isTranslatable("abc123"))
        assertFalse(TranslationManager.isTranslatable("Real Madrid", "teamName"))
    }
}
