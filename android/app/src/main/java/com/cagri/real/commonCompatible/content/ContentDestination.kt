package com.cagri.real.content

import com.cagri.real.boards.BoardTransform
import org.json.JSONObject
import java.util.UUID

/** A content result has exactly one destination. No implicit fallback from a stale board to a card. */
sealed interface ContentDestination {
    data class Card(val reminderId: Long) : ContentDestination
    data class Board(val boardId: String, val insertion: BoardTransform) : ContentDestination
}

sealed interface ContentRouteResult {
    data object ChooseCard : ContentRouteResult
    data object Discard : ContentRouteResult
    data class Deliver(val request: ContentRequest) : ContentRouteResult
}

object ContentRouting {
    fun sharedResult(hadPending: Boolean, request: ContentRequest?, now: Long, uid: String, kind: String): ContentRouteResult = when {
        !hadPending -> ContentRouteResult.ChooseCard
        request == null || !request.isCurrent(now, uid, kind) -> ContentRouteResult.Discard
        else -> ContentRouteResult.Deliver(request)
    }
    fun cancel(request: ContentRequest?, token: String): ContentRequest? = request?.takeUnless { it.token == token }
}

data class ContentRequest(
    val destination: ContentDestination,
    val uid: String,
    val kind: String,
    val startedAt: Long,
    val token: String = UUID.randomUUID().toString()
) {
    fun isCurrent(now: Long, currentUid: String, expectedKind: String): Boolean =
        uid == currentUid && kind == expectedKind && now - startedAt in 0..MAX_AGE

    fun json(): JSONObject = JSONObject().put("token", token).put("uid", uid)
        .put("kind", kind).put("startedAt", startedAt).apply {
            when (val target = destination) {
                is ContentDestination.Card -> put("target", "CARD").put("reminderId", target.reminderId)
                is ContentDestination.Board -> put("target", "BOARD").put("boardId", target.boardId)
                    .put("insertion", target.insertion.json())
            }
        }

    companion object {
        const val MAX_AGE = 3_600_000L
        fun from(raw: String): ContentRequest {
            val o = JSONObject(raw)
            val target = when (o.getString("target")) {
                "CARD" -> ContentDestination.Card(o.getLong("reminderId").also { require(it > 0) })
                "BOARD" -> ContentDestination.Board(o.getString("boardId").also { require(it.isNotBlank()) },
                    BoardTransform.from(o.getJSONObject("insertion")))
                else -> error("Unknown content destination")
            }
            return ContentRequest(target, o.getString("uid"), o.getString("kind"), o.getLong("startedAt"), o.getString("token"))
        }
    }
}
