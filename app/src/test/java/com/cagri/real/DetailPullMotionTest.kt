package com.cagri.real

import com.cagri.real.ui.shouldCommitDetailPull
import org.junit.Assert.*
import org.junit.Test

class DetailPullMotionTest {
    @Test fun distanceOrIntentionalFlingCanDismiss() {
        assertTrue(shouldCommitDetailPull(110f, 0f, 320f, 700f))
        assertTrue(shouldCommitDetailPull(30f, 800f, 320f, 700f))
    }
    @Test fun shortOrUpwardReleaseReturnsToDetail() {
        assertFalse(shouldCommitDetailPull(3f, 1400f, 320f, 700f))
        assertFalse(shouldCommitDetailPull(60f, -900f, 320f, 700f))
    }
    @Test fun invalidGestureCannotDismiss() {
        assertFalse(shouldCommitDetailPull(Float.NaN, 0f, 320f, 700f))
        assertFalse(shouldCommitDetailPull(80f, Float.POSITIVE_INFINITY, 320f, 700f))
        assertFalse(shouldCommitDetailPull(80f, 800f, 0f, 700f))
    }
}
