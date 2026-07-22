package com.srisu.srisu.core.data.remote

import app.cash.paging.PagingSourceLoadParamsRefresh
import app.cash.paging.PagingSourceLoadResultPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BasePagingSourceTest {

    @Test
    fun cancellationIsPropagatedToTheCaller() = runTest {
        val source = BasePagingSource<String> {
            throw CancellationException("cancel test")
        }

        assertFailsWith<CancellationException> {
            source.load(refreshParams())
        }
    }

    @Test
    fun nullItemsAreFilteredFromSuccessfulPages() = runTest {
        val source = BasePagingSource<String> {
            listOf("first", null, "second")
        }

        val result = source.load(refreshParams()) as PagingSourceLoadResultPage<Int, String>

        assertEquals(listOf("first", "second"), result.data)
        assertEquals(2, result.nextKey)
    }

    private fun refreshParams() = PagingSourceLoadParamsRefresh<Int>(
        key = null,
        loadSize = 20,
        placeholdersEnabled = false,
    )
}
