package com.mkroo.termbase.domain.model.confluence

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
@Table(name = "confluence_workspaces")
class ConfluenceWorkspace(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(nullable = false, unique = true)
    val siteId: String,
    @Column(nullable = false)
    var siteName: String,
    @OneToMany(mappedBy = "workspace", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    private val _spaces: MutableList<ConfluenceSpace> = mutableListOf(),
    @Column(nullable = false, updatable = false)
    val connectedAt: Instant = Instant.now(),
) {
    val spaces: List<ConfluenceSpace> get() = _spaces.toList()

    val selectedSpaces: List<ConfluenceSpace>
        get() = _spaces.filter { it.isSelected }

    fun addSpace(
        spaceId: String,
        spaceKey: String,
        name: String,
    ): ConfluenceSpace {
        require(_spaces.none { it.spaceId == spaceId }) { "Space already exists: $spaceKey" }
        val space = ConfluenceSpace(spaceId = spaceId, spaceKey = spaceKey, name = name, workspace = this)
        _spaces.add(space)
        return space
    }

    fun syncSpaces(remoteSpaces: List<RemoteSpace>) {
        _spaces.removeIf { local -> remoteSpaces.none { it.id == local.spaceId } }

        remoteSpaces.forEach { remote ->
            val existing = _spaces.find { it.spaceId == remote.id }
            if (existing != null) {
                existing.name = remote.name
            } else {
                _spaces.add(
                    ConfluenceSpace(
                        spaceId = remote.id,
                        spaceKey = remote.key,
                        name = remote.name,
                        workspace = this,
                    ),
                )
            }
        }
    }

    fun selectSpace(spaceKey: String) {
        val space =
            _spaces.find { it.spaceKey == spaceKey }
                ?: throw IllegalArgumentException("Space not found: $spaceKey")
        space.isSelected = true
    }

    fun deselectSpace(spaceKey: String) {
        val space =
            _spaces.find { it.spaceKey == spaceKey }
                ?: throw IllegalArgumentException("Space not found: $spaceKey")
        space.isSelected = false
    }
}

data class RemoteSpace(
    val id: String,
    val key: String,
    val name: String,
)
