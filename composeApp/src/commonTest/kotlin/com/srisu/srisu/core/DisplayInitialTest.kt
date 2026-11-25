package com.srisu.srisu.core

import com.srisu.srisu.core.identity.displayInitial
import kotlin.test.Test
import kotlin.test.assertEquals

class DisplayInitialTest {
    @Test fun emojiNicknameAndOrdinaryNamesHaveValidAvatarInitials() {
        assertEquals("🌙", displayInitial(" 🌙 Moon "))
        assertEquals("A", displayInitial(" Aatithi Rai"))
        assertEquals("", displayInitial("   "))
    }
}
