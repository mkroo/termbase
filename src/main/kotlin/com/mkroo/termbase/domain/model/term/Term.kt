package com.mkroo.termbase.domain.model.term

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "terms")
class Term(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(nullable = false, unique = true)
    val name: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    var definition: String,
    @OneToMany(mappedBy = "term", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    private val _synonyms: MutableList<Synonym> = mutableListOf(),
    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    val synonyms: List<Synonym> get() = _synonyms.toList()

    fun addSynonym(name: String): Synonym {
        require(name != this.name) { "동의어는 대표어와 동일할 수 없습니다." }
        require(_synonyms.none { it.name == name }) { "이미 등록된 동의어입니다: $name" }

        val synonym = Synonym(name = name, term = this)
        _synonyms.add(synonym)
        return synonym
    }

    fun removeSynonym(name: String) {
        val synonym =
            _synonyms.find { it.name == name }
                ?: throw IllegalArgumentException("등록되지 않은 동의어입니다: $name")
        _synonyms.remove(synonym)
    }

    fun updateDefinition(newDefinition: String) {
        this.definition = newDefinition
    }
}
