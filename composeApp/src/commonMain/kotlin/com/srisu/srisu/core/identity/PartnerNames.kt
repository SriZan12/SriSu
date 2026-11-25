package com.srisu.srisu.core.identity

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.lifecycle.ApplicationLifetime
import com.srisu.srisu.core.session.SessionCoordinator
import com.srisu.srisu.core.session.SessionStamp
import com.srisu.srisu.features.settings.SettingsContext
import com.srisu.srisu.features.settings.SettingsSource
import com.srisu.srisu.navigation.CoupleAccess
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Display-only alias. Canonical identity and user-authored content are never rewritten. */
@Immutable
data class PartnerNameSnapshot(
    val accountId: Long? = null,
    val coupleId: Long? = null,
    val partnerId: Long? = null,
    val connectionId: Long? = null,
    val nickname: String? = null,
    val revision: Long? = null,
    val canonicalName: String? = null,
) {
    fun resolve(userId: Long?, fallback: String, coupleId: Long? = null): String {
        if (userId == null || userId != partnerId || userId == accountId ||
            (coupleId != null && coupleId != this.coupleId)) return fallback
        return nickname?.takeIf { it.isNotBlank() } ?: canonicalName?.takeIf { it.isNotBlank() } ?: fallback
    }
}

val LocalPartnerNames = staticCompositionLocalOf { PartnerNameSnapshot() }

@Composable
fun partnerDisplayName(userId: Long?, fallback: String, coupleId: Long? = null): String =
    LocalPartnerNames.current.resolve(userId, fallback, coupleId)

/** One account/membership-scoped projection, refreshed on foreground and explicit Home refresh. */
class PartnerNames(
    private val sessions: SessionCoordinator,
    private val access: StateFlow<CoupleAccess>,
    private val source: SettingsSource,
    private val lifetime: ApplicationLifetime,
) {
    private data class Scope(val stamp: SessionStamp, val couple: Long, val partner: Long)
    private val mutable = MutableStateFlow(PartnerNameSnapshot())
    val state = mutable.asStateFlow()
    private var scope: Scope? = null
    private var work: Job? = null
    init {
        lifetime.scope.launch {
            combine(sessions.state, access, lifetime.foreground) { _, _, foreground -> currentScope() to foreground }
                .distinctUntilChanged().collect { (next, foreground) ->
                    if (scope != next) {
                        work?.cancel(); scope = next; mutable.value = PartnerNameSnapshot()
                    }
                    if (next != null && foreground) refresh()
                }
        }
    }
    private fun currentScope(): Scope? {
        val stamp = sessions.stamp()
        val ready = (access.value as? CoupleAccess.Ready)?.takeIf { it.stamp == stamp } ?: return null
        val me = stamp.accountId ?: return null
        val couple = ready.coupleId ?: return null
        if (ready.members.distinct().size != 2 || me !in ready.members) return null
        return Scope(stamp, couple, ready.members.first { it != me })
    }
    fun refresh() {
        val expected = currentScope() ?: return
        if (work?.isActive == true && scope == expected) return
        if (scope != expected) {
            work?.cancel(); scope = expected; mutable.value = PartnerNameSnapshot()
        }
        work = lifetime.scope.launch {
            val result = source.load()
            if (currentScope() != expected || scope != expected) return@launch
            when (result) {
                is NetworkAPIResult.Success -> result.response?.let { accept(it, expected.stamp) }
                is NetworkAPIResult.Error -> if (result.failure.status in listOf(401, 403, 409)) mutable.value = PartnerNameSnapshot()
            }
        }
    }
    /** Used by Settings immediately after an authoritative load/save/disconnect. */
    fun accept(context: SettingsContext, stamp: SessionStamp) {
        val expected = currentScope()?.takeIf { it.stamp == stamp } ?: return
        if (context.contract != "settings-1") return
        val partner = context.partner
        if (partner == null) {
            work?.cancel(); scope = null; mutable.value = PartnerNameSnapshot(); return
        }
        if (partner.userId != expected.partner || partner.coupleId != expected.couple) {
            work?.cancel(); scope = null; mutable.value = PartnerNameSnapshot(); return
        }
        val previous = mutable.value
        if (previous.connectionId == partner.connectionId && (previous.revision ?: 0) > (partner.nicknameRevision ?: 0)) return
        scope = expected
        mutable.value = PartnerNameSnapshot(stamp.accountId, partner.coupleId, partner.userId,
            partner.connectionId, partner.nickname, partner.nicknameRevision, partner.name)
    }
}

/** Count Unicode code points (including emoji) consistently with the server's 40-character limit. */
fun nicknameLength(value: String): Int {
    var count = 0; var index = 0
    while (index < value.length) {
        val high = value[index++].code
        if (high in 0xD800..0xDBFF && index < value.length && value[index].code in 0xDC00..0xDFFF) index++
        count++
    }
    return count
}

fun nicknameValidation(value: String): String? = when {
    nicknameLength(value.trim()) > 40 -> "Use 40 characters or fewer."
    value.any { it.code < 32 || it.code in 127..159 || it.code in 0x202A..0x202E || it.code in 0x2066..0x2069 } ->
        "Use a single line without control characters."
    else -> null
}
