package com.cagri.real.books

import kotlinx.coroutines.*

/** Ordered, coalesced position writes survive reader disposal; bound to this account's store. */
internal class BookPositionWriter(private val store: BookShelfStore, private val onFailure: () -> Unit) {
    private data class Position(val book: Book, val index: Int, val offset: Int, val paged: Boolean)
    private val lock = Any()
    private val pending = linkedMapOf<String, Position>()
    private var running = false
    fun save(book: Book, index: Int, offset: Int, paged: Boolean) {
        synchronized(lock) {
            pending[book.id] = Position(book, index.coerceAtLeast(0), offset.coerceAtLeast(0), paged)
            if (!running) { running = true; processScope.launch { drain() } }
        }
    }
    fun retry() {
        synchronized(lock) {
            if (!running && pending.isNotEmpty()) { running = true; processScope.launch { drain() } }
        }
    }
    private suspend fun drain() {
        while (true) {
            val batch = synchronized(lock) {
                if (pending.isEmpty()) { running = false; return }
                pending.values.toList().also { pending.clear() }
            }
            for ((batchIndex, p) in batch.withIndex()) {
                var saved = false
                repeat(3) { attempt ->
                    if (!saved) try {
                        if (p.paged) store.savePagedPosition(p.book, p.index, p.offset)
                        else store.saveReadingPosition(p.book, p.index, p.offset)
                        saved = true
                    } catch(e: CancellationException) { throw e }
                    catch (_: Exception) { if (attempt < 2) delay(150L shl attempt) }
                }
                if (!saved) {
                    synchronized(lock) {
                        batch.drop(batchIndex).forEach { remaining ->
                            if (remaining.book.id !in pending) pending[remaining.book.id] = remaining
                        }
                    }
                    synchronized(lock) { running = false }
                    withContext(Dispatchers.Main) { onFailure() }
                    // No suspended worker or endless loop; explicit retry/new scroll restarts it.
                    return
                }
            }
        }
    }
    companion object { private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.IO) }
}
