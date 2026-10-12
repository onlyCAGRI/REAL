package com.cagri.real.books

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serialises one page's read/analysis/edit/commit; entries live only while callers hold/wait. */
internal class BookPageWork {
    private class Entry(val mutex: Mutex = Mutex(), var users: Int = 0)
    private val entries = mutableMapOf<String, Entry>()
    suspend fun <T> withPage(key: String, block: suspend () -> T): T {
        val entry = synchronized(entries) { entries.getOrPut(key) { Entry() }.also { it.users++ } }
        return try { entry.mutex.withLock { block() } }
        finally { synchronized(entries) { if (--entry.users == 0) entries.remove(key) } }
    }
}
