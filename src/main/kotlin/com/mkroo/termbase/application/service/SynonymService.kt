package com.mkroo.termbase.application.service

import com.mkroo.termbase.domain.model.term.Term
import com.mkroo.termbase.domain.repository.IgnoredTermRepository
import com.mkroo.termbase.domain.repository.TermRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class SynonymService(
    private val termRepository: TermRepository,
    private val ignoredTermRepository: IgnoredTermRepository,
) {
    fun addSynonym(
        termName: String,
        synonymName: String,
    ): SynonymAddResult {
        val term =
            termRepository.findByName(termName)
                ?: return SynonymAddResult.TermNotFound(termName)

        if (termName == synonymName) {
            return SynonymAddResult.SameAsCanonical(synonymName)
        }

        if (termRepository.existsByName(synonymName)) {
            return SynonymAddResult.AlreadyExistsAsTerm(synonymName)
        }

        if (termRepository.existsBySynonymName(synonymName)) {
            return SynonymAddResult.AlreadyExistsAsSynonym(synonymName)
        }

        if (ignoredTermRepository.existsByName(synonymName)) {
            return SynonymAddResult.AlreadyExistsAsIgnored(synonymName)
        }

        term.addSynonym(synonymName)
        termRepository.save(term)
        return SynonymAddResult.Success(term)
    }

    fun removeSynonym(
        termName: String,
        synonymName: String,
    ): Term {
        val term =
            termRepository.findByName(termName)
                ?: throw IllegalArgumentException("존재하지 않는 용어입니다: $termName")
        term.removeSynonym(synonymName)
        return termRepository.save(term)
    }
}

sealed interface SynonymAddResult {
    data class Success(
        val term: Term,
    ) : SynonymAddResult

    data class TermNotFound(
        val termName: String,
    ) : SynonymAddResult

    data class SameAsCanonical(
        val synonymName: String,
    ) : SynonymAddResult

    data class AlreadyExistsAsTerm(
        val synonymName: String,
    ) : SynonymAddResult

    data class AlreadyExistsAsSynonym(
        val synonymName: String,
    ) : SynonymAddResult

    data class AlreadyExistsAsIgnored(
        val synonymName: String,
    ) : SynonymAddResult
}
