package com.mkroo.termbase.application.service

import com.mkroo.termbase.domain.model.term.Term
import com.mkroo.termbase.domain.repository.IgnoredTermRepository
import com.mkroo.termbase.domain.repository.TermRepository
import com.mkroo.termbase.domain.service.SourceDocumentAnalyzer
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class GlossaryService(
    private val termRepository: TermRepository,
    private val ignoredTermRepository: IgnoredTermRepository,
    private val reindexingService: ReindexingService,
    private val sourceDocumentAnalyzer: SourceDocumentAnalyzer,
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

    @Transactional(readOnly = true)
    fun search(query: String): List<Term> {
        if (query.isBlank()) {
            return termRepository.findAll()
        }
        return termRepository.findAll().filter { term ->
            term.name.contains(query, ignoreCase = true) ||
                term.definition.contains(query, ignoreCase = true) ||
                term.synonyms.any { it.name.contains(query, ignoreCase = true) }
        }
    }

    @Transactional(readOnly = true)
    fun findAllSortedByFrequency(): List<TermWithFrequency> =
        termRepository
            .findAll()
            .map { term ->
                TermWithFrequency(
                    term = term,
                    frequency = sourceDocumentAnalyzer.getTermFrequency(term.name),
                )
            }.sortedByDescending { it.frequency }

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

data class TermWithFrequency(
    val term: Term,
    val frequency: Long,
)
