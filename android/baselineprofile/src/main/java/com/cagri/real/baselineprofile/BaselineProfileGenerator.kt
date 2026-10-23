package com.cagri.real.baselineprofile

import android.util.Log
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TAG = "BaselineProfile"
private const val PACKAGE_NAME = "com.cagri.real"
private const val SHORT_WAIT_MS = 3_000L
private const val WAIT_MS = 5_000L
private const val LONG_WAIT_MS = 15_000L

/**
 * Collects the startup + home journey used by the baseline profile.
 *
 * All UI lookups use the test tags exposed as resource ids through
 * `testTagsAsResourceId` on the MainActivity root. Every step is best-effort:
 * if a screen cannot be reached the run logs it and continues, so a profile
 * is still produced for the startup and home screens.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() {
        baselineProfileRule.collect(
            packageName = PACKAGE_NAME,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()

            val homeList = waitFor("home_list", LONG_WAIT_MS)
            if (homeList == null) {
                Log.w(TAG, "home_list not found; keeping the startup part of the profile")
                return@collect
            }

            // Warm the home list, then return towards the top for the first card.
            repeat(3) {
                runCatching { homeList.fling(Direction.DOWN) }
                    .onFailure { Log.w(TAG, "Fling down failed", it) }
                device.waitForIdle()
            }
            runCatching { homeList.fling(Direction.UP) }
                .onFailure { Log.w(TAG, "Fling up failed", it) }
            device.waitForIdle()

            val card = waitFor("home_card", WAIT_MS)
            if (card != null) {
                runCatching {
                    card.click()
                    device.waitForIdle()
                    device.pressBack()
                    device.waitForIdle()
                }.onFailure { Log.w(TAG, "Card detail journey failed", it) }
            } else {
                Log.w(TAG, "home_card not found; skipping the card detail")
            }

            runCatching { openDiscovery() }
                .onFailure { Log.w(TAG, "Discovery journey skipped", it) }

            // The discovery screen and Yaşam both handle back. The home list stays composed
            // under those overlays, so look for their tags instead of home_list.
            var backAttempts = 0
            while (backAttempts < 4) {
                val overlayVisible = device.hasObject(By.res("yasam_tab_discover")) ||
                    device.hasObject(By.res("discovery_deck")) ||
                    device.hasObject(By.res("discovery_start"))
                if (!overlayVisible) break
                runCatching {
                    device.pressBack()
                    device.waitForIdle()
                }.onFailure { Log.w(TAG, "Back to home failed", it) }
                backAttempts++
            }
        }
    }

    private fun MacrobenchmarkScope.openDiscovery() {
        val dock = waitFor("home_dock", WAIT_MS)
        if (dock == null) {
            Log.w(TAG, "home_dock not found; skipping discovery")
            return
        }

        // Expand the dock: start near its bottom edge (the glass bar) and drag up.
        runCatching {
            val bounds = dock.visibleBounds
            val inset = (bounds.height() * 0.1f).toInt()
            device.swipe(
                bounds.centerX(), bounds.bottom - inset,
                bounds.centerX(), bounds.top + inset,
                20,
            )
        }.onFailure { Log.w(TAG, "Dock expansion failed", it) }
        device.waitForIdle()

        val yasam = waitFor("dock_yasam", WAIT_MS)
        if (yasam == null) {
            Log.w(TAG, "dock_yasam not found; skipping discovery")
            return
        }
        yasam.click()
        device.waitForIdle()

        val discoverTab = waitFor("yasam_tab_discover", LONG_WAIT_MS)
        if (discoverTab == null) {
            Log.w(TAG, "yasam_tab_discover not found; skipping discovery")
            return
        }
        discoverTab.click()
        device.waitForIdle()

        val watch = scrollTo("discover_watch", attempts = 4)
        if (watch == null) {
            Log.w(TAG, "discover_watch not found; skipping discovery")
            return
        }
        watch.click()
        device.waitForIdle()

        // İzle opens MediaDiscoveryScreen on the filters page. The filters are a 3-step wizard:
        // step 0 needs one genre, steps 1 and 2 are optional. The action button carries
        // discovery_start on every step; the last tap starts the deck.
        val firstStart = waitFor("discovery_start", WAIT_MS)
        if (firstStart == null) {
            Log.w(TAG, "discovery_start not found; skipping the deck")
            return
        }
        if (!firstStart.isEnabled) selectFirstGenre(firstStart)

        var start: UiObject2? = device.findObject(By.res("discovery_start"))
        if (start?.isEnabled != true) {
            Log.w(TAG, "discovery_start is disabled; skipping the deck")
            return
        }

        var advanced = 0
        while (advanced < 3) {
            val button = start ?: waitFor("discovery_start", WAIT_MS) ?: break
            if (!button.isEnabled) break
            button.click()
            device.waitForIdle()
            start = waitFor("discovery_start", SHORT_WAIT_MS)
            advanced++
        }

        val deck = waitFor("discovery_deck", LONG_WAIT_MS)
        if (deck == null) {
            Log.w(TAG, "discovery_deck not reachable; continuing without deck swipes")
            return
        }
        swipeDeck(deck)
    }

    /**
     * Picks the first genre without a text selector. The genre chips sit above the pinned
     * action button; type chips and headings are harmless no-ops, so probe a few rows and
     * stop as soon as the action button becomes enabled.
     */
    private fun MacrobenchmarkScope.selectFirstGenre(start: UiObject2) {
        val buttonTop = start.visibleBounds.top
        val xFractions = listOf(0.5f, 0.35f, 0.65f)
        val yFractions = listOf(0.55f, 0.50f, 0.60f, 0.45f, 0.65f, 0.40f, 0.70f)
        for (yFraction in yFractions) {
            val y = (buttonTop * yFraction).toInt()
            if (y <= 0) continue
            for (xFraction in xFractions) {
                val x = (device.displayWidth * xFraction).toInt()
                runCatching { device.click(x, y) }
                    .onFailure { Log.w(TAG, "Genre probe tap failed", it) }
                device.waitForIdle()
                if (device.findObject(By.res("discovery_start"))?.isEnabled == true) return
            }
        }
        Log.w(TAG, "Could not enable discovery_start with a genre probe")
    }

    private fun MacrobenchmarkScope.swipeDeck(deck: UiObject2) {
        repeat(3) {
            runCatching { deck.swipe(Direction.LEFT, 0.6f) }
                .onFailure { Log.w(TAG, "Deck swipe failed", it) }
            device.waitForIdle()
        }
    }

    /** Scrolls a vertically scrollable screen until [res] appears or attempts run out. */
    private fun MacrobenchmarkScope.scrollTo(res: String, attempts: Int): UiObject2? {
        repeat(attempts) {
            device.findObject(By.res(res))?.let { return it }
            runCatching {
                val width = device.displayWidth
                val height = device.displayHeight
                device.swipe(width / 2, (height * 0.72f).toInt(), width / 2, (height * 0.35f).toInt(), 20)
            }.onFailure { Log.w(TAG, "Scroll toward $res failed", it) }
            device.waitForIdle()
        }
        return device.findObject(By.res(res))
    }

    private fun MacrobenchmarkScope.waitFor(res: String, timeoutMs: Long): UiObject2? =
        device.wait(Until.findObject(By.res(res)), timeoutMs)
}
