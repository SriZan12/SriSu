package com.srisu.srisu.core.data.network

import app.cash.paging.PagingSource
import app.cash.paging.PagingState
import com.srisu.srisu.core.logger.AppLogger


class BasePagingSource<T : Any>(
    private val fetchData: suspend (page: Int) -> List<T?>?
) : PagingSource<Int, T>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        return try {
            val page = params.key ?: 1
            val rawData = fetchData(page)

            AppLogger.log("RAW DATA = $rawData")

            val data = rawData?.filterNotNull() ?: emptyList()

            LoadResult.Page(
                data = data,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (data.isEmpty()) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, T>): Int? {
        return state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
    }
}
