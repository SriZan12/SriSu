package com.srisu.srisu.navigation

/** First linking grants a new space without interrupting acceptance/chat. Revocation or
 * replacement still discards every saved entry owned by the previous couple. Account
 * changes separately replace this tracker and the entire session navigation scope. */
internal class MembershipNavigationScope {
    private var previous: Pair<Long?, List<Long>>? = null
    private var revision = 0
    fun observe(coupleId: Long?, members: List<Long>): Int {
        val next = coupleId to members.sorted()
        val old = previous
        if (old != null && old.first != null && old != next) revision++
        previous = next
        return revision
    }
}
