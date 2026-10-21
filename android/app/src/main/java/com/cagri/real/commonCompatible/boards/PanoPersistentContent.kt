package com.cagri.real.boards

/** Exactly the fields written by BoardStore; transient network/UI state never forces disk writes. */
internal fun PanoState.samePersistentContent(other: PanoState): Boolean =
    boards == other.boards && items == other.items && queue == other.queue &&
    cursors == other.cursors && viewports == other.viewports && backgrounds == other.backgrounds
