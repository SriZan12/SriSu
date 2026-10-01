package com.srisu.srisu.features.chat

import androidx.paging.PagingSource
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.data.remote.ResultHandler
import com.srisu.srisu.features.chat.data.paging.PartnerInvitationsPagingSource
import com.srisu.srisu.features.home.connection.data.remote.response.CoupleConnectionRequestResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PartnerInvitationsPagingSourceTest {
    @Test fun followsNextAndStopsOnNonEmptyFinalPage() = runTest {
        val requested = mutableListOf<Int>()
        val source = PartnerInvitationsPagingSource { page ->
            requested += page
            ResultHandler(NetworkAPIResult.Success(CoupleConnectionRequestResponse(
                next = if (page == 1) "https://example.test/invitations?page=2" else null,
                results = listOf(CoupleConnectionRequestResponse.Result(id = page.toLong())),
            )))
        }
        val first = source.load(PagingSource.LoadParams.Refresh(null, 20, false))
        val firstPage = assertIs<PagingSource.LoadResult.Page<Int, CoupleConnectionRequestResponse.Result>>(first)
        assertEquals(2, firstPage.nextKey)
        val last = source.load(PagingSource.LoadParams.Append(2, 20, false))
        val lastPage = assertIs<PagingSource.LoadResult.Page<Int, CoupleConnectionRequestResponse.Result>>(last)
        assertNull(lastPage.nextKey)
        assertEquals(listOf(1, 2), requested)
    }

    @Test fun emptyListWithNextStillAdvancesAndFiltersNullEntries() = runTest {
        val source = PartnerInvitationsPagingSource {
            ResultHandler(NetworkAPIResult.Success(CoupleConnectionRequestResponse(next = "?page=2", results = listOf(null))))
        }
        val page = assertIs<PagingSource.LoadResult.Page<Int, CoupleConnectionRequestResponse.Result>>(
            source.load(PagingSource.LoadParams.Refresh(null, 20, false)))
        assertTrue(page.data.isEmpty())
        assertEquals(2, page.nextKey)
    }

    @Test fun reportsErrorInsteadOfTreatingFailureAsEmptyList() = runTest {
        var fail = true
        val source = PartnerInvitationsPagingSource {
            if (fail) ResultHandler(NetworkAPIResult.Error("Offline", NetworkAPIResult.ErrorType.NETWORK))
            else ResultHandler(NetworkAPIResult.Success(CoupleConnectionRequestResponse(results = emptyList())))
        }
        assertIs<PagingSource.LoadResult.Error<Int, CoupleConnectionRequestResponse.Result>>(
            source.load(PagingSource.LoadParams.Refresh(null, 20, false)))
        fail = false
        val page = assertIs<PagingSource.LoadResult.Page<Int, CoupleConnectionRequestResponse.Result>>(
            source.load(PagingSource.LoadParams.Refresh(null, 20, false)))
        assertNull(page.nextKey)
    }

    @Test fun nullEnvelopeIsAnErrorAndCancellationIsPropagated() = runTest {
        val empty = PartnerInvitationsPagingSource { ResultHandler(NetworkAPIResult.Success(null)) }
        assertIs<PagingSource.LoadResult.Error<Int, CoupleConnectionRequestResponse.Result>>(
            empty.load(PagingSource.LoadParams.Refresh(null, 20, false)))
        val cancelled = PartnerInvitationsPagingSource { throw CancellationException() }
        assertFailsWith<CancellationException> { cancelled.load(PagingSource.LoadParams.Refresh(null, 20, false)) }
    }
}
