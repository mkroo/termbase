package com.mkroo.termbase.application.service

import com.mkroo.termbase.domain.model.ignoredterm.IgnoredTerm
import com.mkroo.termbase.domain.repository.IgnoredTermRepository
import com.mkroo.termbase.domain.repository.TermRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class IgnoredTermService(
    private val ignoredTermRepository: IgnoredTermRepository,
    private val termRepository: TermRepository,
) {
    fun addIgnoredTerm(
        name: String,
        reason: String,
    ): IgnoredTermAddResult {
        if (reason.isBlank()) {
            return IgnoredTermAddResult.ReasonRequired
        }

        if (termRepository.existsByName(name)) {
            return IgnoredTermAddResult.AlreadyExistsAsTerm(name)
        }

        if (termRepository.existsBySynonymName(name)) {
            return IgnoredTermAddResult.AlreadyExistsAsSynonym(name)
        }

        if (ignoredTermRepository.existsByName(name)) {
            return IgnoredTermAddResult.AlreadyIgnored(name)
        }

        val ignoredTerm = IgnoredTerm(name = name, reason = reason)
        return IgnoredTermAddResult.Success(ignoredTermRepository.save(ignoredTerm))
    }

    fun removeIgnoredTerm(name: String): IgnoredTerm {
        val ignoredTerm =
            ignoredTermRepository.findByName(name)
                ?: throw IllegalArgumentException("무시 처리되지 않은 단어입니다: $name")
        ignoredTermRepository.delete(ignoredTerm)
        return ignoredTerm
    }

    fun updateReason(
        name: String,
        newReason: String,
    ): IgnoredTerm {
        val ignoredTerm =
            ignoredTermRepository.findByName(name)
                ?: throw IllegalArgumentException("무시 처리되지 않은 단어입니다: $name")
        ignoredTerm.updateReason(newReason)
        return ignoredTermRepository.save(ignoredTerm)
    }

    @Transactional(readOnly = true)
    fun findByName(name: String): IgnoredTerm? = ignoredTermRepository.findByName(name)

    @Transactional(readOnly = true)
    fun findAll(): List<IgnoredTerm> = ignoredTermRepository.findAll()

    @Transactional(readOnly = true)
    fun existsByName(name: String): Boolean = ignoredTermRepository.existsByName(name)
}

sealed interface IgnoredTermAddResult {
    data class Success(
        val ignoredTerm: IgnoredTerm,
    ) : IgnoredTermAddResult

    data class AlreadyExistsAsTerm(
        val name: String,
    ) : IgnoredTermAddResult

    data class AlreadyExistsAsSynonym(
        val name: String,
    ) : IgnoredTermAddResult

    data class AlreadyIgnored(
        val name: String,
    ) : IgnoredTermAddResult

    data object ReasonRequired : IgnoredTermAddResult
}
