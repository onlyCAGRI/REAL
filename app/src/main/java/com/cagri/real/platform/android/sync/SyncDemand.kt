package com.cagri.real.platform.android.sync

internal class SyncDemand {
    private var requested = 0L
    private var completed = 0L
    @Synchronized fun request(): Long = ++requested
    @Synchronized fun current(): Long = requested
    @Synchronized fun isPending(generation: Long): Boolean = generation > completed
    @Synchronized fun complete(generation: Long) { completed = maxOf(completed, generation) }
}
