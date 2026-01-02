package com.mkroo.termbase.domain.model.slack

import com.mkroo.termbase.domain.model.document.SlackMetadata
import com.mkroo.termbase.domain.model.document.SourceDocument
import java.time.Instant

data class SlackMessage(
    val workspaceId: String,
    val channelId: String,
    val messageTs: String,
    val userId: String,
    val text: String,
    val timestamp: Instant,
) {
    fun toSourceDocument(): SourceDocument =
        SourceDocument(
            id = null,
            content = text,
            metadata =
                SlackMetadata(
                    workspaceId = workspaceId,
                    channelId = channelId,
                    messageId = messageTs,
                    userId = userId,
                ),
            timestamp = timestamp,
        )

    companion object {
        fun fromSlackTs(ts: String): Instant {
            val seconds = ts.substringBefore(".").toLong()
            val micros = ts.substringAfter(".").padEnd(6, '0').take(6).toLong()
            return Instant.ofEpochSecond(seconds, micros * 1000)
        }
    }
}
