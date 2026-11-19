package com.srisu.srisu.navigation

import kotlin.test.*

class MembershipNavigationScopeTest {
    @Test fun initialLinkKeepsAcceptanceAndChatButUnlinkAndReplacementClearPrivateStacks() {
        val scope = MembershipNavigationScope()
        assertEquals(0, scope.observe(null, emptyList()))
        assertEquals(0, scope.observe(10, listOf(2, 1)))
        assertEquals(0, scope.observe(10, listOf(1, 2)))
        assertEquals(1, scope.observe(null, emptyList()))
        assertEquals(1, scope.observe(20, listOf(1, 3)))
        assertEquals(2, scope.observe(21, listOf(1, 4)))
        assertEquals(3, scope.observe(21, listOf(1, 5)))
    }
}
