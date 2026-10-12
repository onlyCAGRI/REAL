package com.cagri.real.books

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class BookPageWorkTest {
    @Test fun `same page callers observe the cache produced by the first analysis`() = runBlocking {
        withTimeout(5000) {
            val work = BookPageWork()
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            var cache: String? = null; var analyses = 0
            val first = async { work.withPage("book:1") {
                analyses++; entered.complete(Unit); release.await(); cache = "paragraphs"; cache
            } }
            entered.await()
            val second = async(start = CoroutineStart.UNDISPATCHED) { work.withPage("book:1") {
                cache ?: "duplicate".also { analyses++; cache = it }
            } }
            assertFalse(second.isCompleted)
            release.complete(Unit)
            assertEquals("paragraphs", first.await()); assertEquals("paragraphs", second.await())
            assertEquals(1, analyses)
        }
    }
    @Test fun `cancelled waiter does not strand the page lock`() = runBlocking {
        withTimeout(5000) {
            val work = BookPageWork(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val holder = launch { work.withPage("book:2") { entered.complete(Unit); release.await() } }
            entered.await()
            val waiter = launch(start = CoroutineStart.UNDISPATCHED) { work.withPage("book:2") { fail("Cancelled waiter entered") } }
            waiter.cancelAndJoin(); release.complete(Unit); holder.join()
            assertEquals(3, work.withPage("book:2") { 3 })
        }
    }
    @Test fun `independent pages do not share an edit lock`() = runBlocking {
        withTimeout(5000) {
            val work = BookPageWork(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val holder = launch { work.withPage("book:1") { entered.complete(Unit); release.await() } }
            entered.await(); assertEquals("ready", work.withPage("book:2") { "ready" })
            release.complete(Unit); holder.join()
        }
    }
}
