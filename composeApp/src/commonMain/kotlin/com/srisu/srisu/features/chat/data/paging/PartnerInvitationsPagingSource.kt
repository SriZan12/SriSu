package com.srisu.srisu.features.chat.data.paging

import app.cash.paging.PagingSource
import app.cash.paging.PagingState
import com.srisu.srisu.core.data.remote.NetworkAPIResult
import com.srisu.srisu.core.data.remote.ResultHandler
import com.srisu.srisu.features.home.connection.data.remote.response.CoupleConnectionRequestResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** The API supplies numbered pages and an authoritative `next` link. */
class PartnerInvitationsPagingSource(
    private val fetch: suspend (Int) -> ResultHandler<CoupleConnectionRequestResponse?>,
) : PagingSource<Int, CoupleConnectionRequestResponse.Result>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CoupleConnectionRequestResponse.Result> {
        val page = params.key ?: 1
        return try {
            when (val result =
                fetch(page).result.also { currentCoroutineContext().ensureActive() }) {
                is NetworkAPIResult.Success -> {
                    val response = result.response ?: return LoadResult.Error(
                        throwable = IllegalStateException("Invitations could not be loaded. Please try again.")
                    )
                    LoadResult.Page(
                        data = response.results.orEmpty().filterNotNull(),
                        prevKey = if (page > 1) page - 1 else null,
                        nextKey = if (response.next.isNullOrBlank()) null else page + 1,
                    )
                }

                is NetworkAPIResult.Error -> LoadResult.Error(
                    throwable = IllegalStateException(
                        result.error ?: "Invitations could not be loaded. Please try again."
                    )
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            LoadResult.Error(error)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, CoupleConnectionRequestResponse.Result>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.let { it.prevKey?.plus(1) ?: it.nextKey?.minus(1) }
        }
}
