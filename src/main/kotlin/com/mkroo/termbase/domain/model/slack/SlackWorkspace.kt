package com.mkroo.termbase.domain.model.slack

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "slack_workspaces")
class SlackWorkspace(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(nullable = false, unique = true)
    val teamId: String,
    @Column(nullable = false)
    var teamName: String,
    @OneToMany(mappedBy = "workspace", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    private val _channels: MutableList<SlackChannel> = mutableListOf(),
    @Column(nullable = false, updatable = false)
    val connectedAt: Instant = Instant.now(),
) {
    val channels: List<SlackChannel> get() = _channels.toList()

    val selectedChannels: List<SlackChannel>
        get() = _channels.filter { it.isSelected }

    fun syncChannels(remoteChannels: List<RemoteChannel>) {
        _channels.removeIf { local -> remoteChannels.none { it.id == local.channelId } }

        remoteChannels.forEach { remote ->
            val existing = _channels.find { it.channelId == remote.id }
            if (existing != null) {
                existing.name = remote.name
                existing.isPrivate = remote.isPrivate
            } else {
                _channels.add(
                    SlackChannel(
                        channelId = remote.id,
                        name = remote.name,
                        isPrivate = remote.isPrivate,
                        workspace = this,
                    ),
                )
            }
        }
    }

    fun selectChannel(channelId: String) {
        val channel =
            _channels.find { it.channelId == channelId }
                ?: throw IllegalArgumentException("Channel not found: $channelId")
        channel.isSelected = true
    }

    fun deselectChannel(channelId: String) {
        val channel =
            _channels.find { it.channelId == channelId }
                ?: throw IllegalArgumentException("Channel not found: $channelId")
        channel.isSelected = false
    }
}

data class RemoteChannel(
    val id: String,
    val name: String,
    val isPrivate: Boolean,
)
