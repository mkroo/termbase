package com.mkroo.termbase.domain.repository

import com.mkroo.termbase.domain.model.slack.SlackCollectionCheckpoint
import org.springframework.data.repository.Repository

interface SlackCollectionCheckpointRepository : Repository<SlackCollectionCheckpoint, Long> {
    fun save(checkpoint: SlackCollectionCheckpoint): SlackCollectionCheckpoint

    fun findByChannelId(channelId: String): SlackCollectionCheckpoint?

    fun findAll(): List<SlackCollectionCheckpoint>
}
