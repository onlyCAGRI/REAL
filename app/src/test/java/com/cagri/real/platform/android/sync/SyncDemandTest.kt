package com.cagri.real.platform.android.sync

import org.junit.Assert.*
import org.junit.Test

class SyncDemandTest {
    @Test fun `completed foreground request does not need a duplicate scheduled run`() {
        val demand = SyncDemand()
        val first = demand.request()
        demand.complete(first)
        assertFalse(demand.isPending(first))
    }
    @Test fun `edit during network request remains pending`() {
        val demand = SyncDemand()
        val inFlight = demand.request()
        val newEdit = demand.request()
        demand.complete(inFlight)
        assertTrue(demand.isPending(newEdit))
        assertEquals(newEdit, demand.current())
    }
    @Test fun `older completion cannot reopen an already completed request`() {
        val demand = SyncDemand()
        val first = demand.request(); val second = demand.request()
        demand.complete(second); demand.complete(first)
        assertFalse(demand.isPending(first)); assertFalse(demand.isPending(second))
        assertTrue(demand.isPending(demand.request()))
    }
}
