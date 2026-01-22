package com.mkroo.termbase.application.service

import com.mkroo.termbase.domain.model.candidate.CandidateBatchHistory
import com.mkroo.termbase.domain.model.candidate.TermCandidate
import com.mkroo.termbase.domain.repository.CandidateBatchHistoryRepository
import com.mkroo.termbase.domain.repository.IgnoredTermRepository
import com.mkroo.termbase.domain.repository.SourceDocumentRepository
import com.mkroo.termbase.domain.repository.TermCandidateRepository
import com.mkroo.termbase.domain.repository.TermRepository
import com.mkroo.termbase.domain.service.TermCandidateExtractor
import com.mkroo.termbase.domain.service.TermExtractionConfig
import com.mkroo.termbase.domain.service.TermExtractionResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal

/**
 * 용어 후보 추출 및 저장 서비스.
 *
 * 순수 도메인 로직은 [TermCandidateExtractor]에 위임하고,
 * 이 서비스는 문서 조회와 결과 저장만 담당합니다.
 */
@Service
@Transactional
class TermCandidateExtractionService(
    private val sourceDocumentRepository: SourceDocumentRepository,
    private val termCandidateRepository: TermCandidateRepository,
    private val batchHistoryRepository: CandidateBatchHistoryRepository,
    private val termRepository: TermRepository,
    private val ignoredTermRepository: IgnoredTermRepository,
    private val termCandidateExtractor: TermCandidateExtractor,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun extractCandidates(config: ExtractionConfig = ExtractionConfig()): ExtractionResult {
        val batchHistory = batchHistoryRepository.save(CandidateBatchHistory())
        val totalStart = System.currentTimeMillis()

        try {
            // 1. 문서 로드
            var start = System.currentTimeMillis()
            val documents = loadAllDocuments(config.pageSize)
            logger.debug("[TIMING] loadAllDocuments: {}ms ({}개 문서)", System.currentTimeMillis() - start, documents.size)

            // 2. 순수 도메인 로직으로 추출 (TermCandidateExtractor에 위임)
            start = System.currentTimeMillis()
            val extractionConfig =
                TermExtractionConfig(
                    minCount = config.minCount,
                    npmiThreshold = config.npmiThreshold,
                    relevanceThreshold = config.relevanceThreshold,
                    stopwords = config.stopwords,
                    excludedTerms = collectExcludedTerms(),
                )
            val result = termCandidateExtractor.extract(documents, extractionConfig)
            logger.debug("[TIMING] extract: {}ms", System.currentTimeMillis() - start)

            // 3. 후보 저장 (TermCandidate만 저장)
            start = System.currentTimeMillis()
            val savedCandidates = saveExtractionResult(result)
            logger.debug("[TIMING] saveExtractionResult: {}ms", System.currentTimeMillis() - start)

            logger.debug("[TIMING] TOTAL: {}ms", System.currentTimeMillis() - totalStart)

            batchHistory.complete(result.totalDocuments, savedCandidates.size)
            batchHistoryRepository.save(batchHistory)

            return ExtractionResult.Success(
                batchId = batchHistory.id!!,
                totalDocuments = result.totalDocuments,
                totalCandidates = savedCandidates.size,
            )
        } catch (e: Exception) {
            batchHistory.fail(e.message ?: "Unknown error")
            batchHistoryRepository.save(batchHistory)

            return ExtractionResult.Failure(
                batchId = batchHistory.id!!,
                errorMessage = e.message ?: "Unknown error",
            )
        }
    }

    private fun loadAllDocuments(pageSize: Int): List<String> {
        val documents = mutableListOf<String>()
        var page = 0

        do {
            val documentPage = sourceDocumentRepository.findAll(page, pageSize)
            documents.addAll(documentPage.documents.map { it.content })
            page++
        } while (documentPage.hasNext)

        return documents
    }

    private fun collectExcludedTerms(): Set<String> {
        val terms = termRepository.findAll()
        val termNames = terms.map { it.name.lowercase() }
        val synonymNames = terms.flatMap { it.synonyms.map { s -> s.name.lowercase() } }
        val ignoredNames = ignoredTermRepository.findAll().map { it.name.lowercase() }

        return (termNames + synonymNames + ignoredNames).toSet()
    }

    private fun saveExtractionResult(result: TermExtractionResult): List<TermCandidate> {
        val existingTerms = termCandidateRepository.findAllTerms().toSet()
        val candidatesToSave =
            result.candidates
                .filter { it.term !in existingTerms }
                .map { c ->
                    TermCandidate(
                        term = c.term,
                        components = objectMapper.writeValueAsString(c.components),
                        count = c.count,
                        pmi = c.pmi,
                        npmi = c.npmi,
                        docCount = c.docCount,
                        idf = c.idf,
                        avgTfidf = c.avgTfidf,
                        relevanceScore = c.relevanceScore,
                    )
                }

        return termCandidateRepository.saveAll(candidatesToSave)
    }
}

data class ExtractionConfig(
    val pageSize: Int = 100,
    val minCount: Int = 3,
    val npmiThreshold: BigDecimal = BigDecimal("0.2"),
    val relevanceThreshold: BigDecimal = BigDecimal("0.3"),
    val stopwords: Set<String> = emptySet(),
)

sealed interface ExtractionResult {
    data class Success(
        val batchId: Long,
        val totalDocuments: Int,
        val totalCandidates: Int,
    ) : ExtractionResult

    data class Failure(
        val batchId: Long,
        val errorMessage: String,
    ) : ExtractionResult
}
