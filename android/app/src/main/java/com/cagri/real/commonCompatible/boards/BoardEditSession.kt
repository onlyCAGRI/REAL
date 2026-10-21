package com.cagri.real.boards

/** Device-local interaction lock, separate from server membership and sync authorization. */
class BoardEditSession {
    private var board: String? = null
    private val drafts = linkedMapOf<String, () -> Unit>()
    fun allows(boardId: String) = board == boardId
    fun begin(boardId: String) { finish(); board = boardId }
    fun register(id: String, save: () -> Unit) { drafts[id] = save }
    fun unregister(id: String) { drafts.remove(id) }
    fun finish() {
        // Flush user-authorized drafts while the session still allows writes.
        drafts.values.toList().forEach { it() }
        board = null
    }
}

enum class BoardBackgroundColor { BLACK, GRAY, BLUE, GREEN }
enum class BoardBackgroundPattern { NONE, DOTS, GRID }
data class BoardLook(val color: BoardBackgroundColor = BoardBackgroundColor.BLACK, val pattern: BoardBackgroundPattern = BoardBackgroundPattern.NONE) {
    fun supported() = copy(
        color = if (color == BoardBackgroundColor.GRAY) color else BoardBackgroundColor.BLACK,
        pattern = if (pattern == BoardBackgroundPattern.GRID) pattern else BoardBackgroundPattern.DOTS
    )
    fun encode() = "${color.name}:${pattern.name}"
    companion object {
        fun parse(raw: String?): BoardLook {
            val parts = raw.orEmpty().split(':')
            return BoardLook(runCatching { BoardBackgroundColor.valueOf(parts[0]) }.getOrDefault(BoardBackgroundColor.BLACK),
                runCatching { BoardBackgroundPattern.valueOf(parts.getOrElse(1) { "NONE" }) }.getOrDefault(BoardBackgroundPattern.NONE))
        }
    }
}
