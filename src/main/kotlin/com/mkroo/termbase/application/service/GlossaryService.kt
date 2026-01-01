package com.mkroo.termbase.application.service

import com.mkroo.termbase.domain.model.term.Term
import com.mkroo.termbase.domain.repository.IgnoredTermRepository
import com.mkroo.termbase.domain.repository.TermRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class GlossaryService(
    private val termRepository: TermRepository,
    private val ignoredTermRepository: IgnoredTermRepository,
    private val reindexingService: ReindexingService,
) {
    fun addTerm(
        name: String,
        definition: String,
    ): TermAddResult {
        if (termRepository.existsByName(name)) {
            return TermAddResult.AlreadyExists(name)
        }

        if (termRepository.existsBySynonymName(name)) {
            return TermAddResult.AlreadyExistsAsSynonym(name)
        }

        if (ignoredTermRepository.existsByName(name)) {
            return TermAddResult.AlreadyExistsAsIgnored(name)
        }

        val conflictingTerms = termRepository.findAll().filter { name.contains(it.name) && it.name != name }
        if (conflictingTerms.isNotEmpty()) {
            return TermAddResult.ConflictWithExistingTerms(
                name = name,
                conflictingTerms = conflictingTerms.map { it.name },
            )
        }

        val term = Term(name = name, definition = definition)
        val savedTerm = termRepository.save(term)
        reindexingService.markReindexingRequired()
        return TermAddResult.Success(savedTerm)
    }

    fun deleteTerm(name: String) {
        val term = findTermByName(name)
        termRepository.delete(term)
        reindexingService.markReindexingRequired()
    }

    fun updateDefinition(
        name: String,
        newDefinition: String,
    ): Term {
        val term = findTermByName(name)
        term.updateDefinition(newDefinition)
        return termRepository.save(term)
    }

    @Transactional(readOnly = true)
    fun findByName(name: String): Term? = termRepository.findByName(name)

    @Transactional(readOnly = true)
    fun findAll(): List<Term> = termRepository.findAll()

    internal fun findTermByName(name: String): Term =
        termRepository.findByName(name)
            ?: throw IllegalArgumentException("존재하지 않는 용어입니다: $name")
}

sealed interface TermAddResult {
    data class Success(
        val term: Term,
    ) : TermAddResult

    data class AlreadyExists(
        val name: String,
    ) : TermAddResult

    data class AlreadyExistsAsSynonym(
        val name: String,
    ) : TermAddResult

    data class AlreadyExistsAsIgnored(
        val name: String,
    ) : TermAddResult

    data class ConflictWithExistingTerms(
        val name: String,
        val conflictingTerms: List<String>,
    ) : TermAddResult
}
