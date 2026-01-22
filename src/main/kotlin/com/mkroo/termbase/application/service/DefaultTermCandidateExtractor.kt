package com.mkroo.termbase.application.service

import com.mkroo.termbase.domain.service.CandidateStat
import com.mkroo.termbase.domain.service.NgramStat
import com.mkroo.termbase.domain.service.NounSequenceExtractor
import com.mkroo.termbase.domain.service.TermCandidateExtractor
import com.mkroo.termbase.domain.service.TermExtractionConfig
import com.mkroo.termbase.domain.service.TermExtractionResult
import com.mkroo.termbase.domain.service.UnigramStat
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 용어 후보 추출 순수 도메인 로직 구현체.
 *
 * NounSequenceExtractor만 의존하며, DB/ES 등 인프라에 의존하지 않습니다.
 */
@Component
class DefaultTermCandidateExtractor(
    private val nounSequenceExtractor: NounSequenceExtractor,
    private val scoreCalculator: TermCandidateScoreCalculator,
    private val termCandidateFilter: TermCandidateFilter,
) : TermCandidateExtractor {
    override fun extract(
        documents: List<String>,
        config: TermExtractionConfig,
    ): TermExtractionResult {
        // 1. 문서 분석 및 빈도 계산
        var start = System.currentTimeMillis()
        val analysisResult = analyzeDocuments(documents)
        println("[TIMING] 문서 분석 (형태소): ${System.currentTimeMillis() - start}ms")

        // 2. 통계 생성
        start = System.currentTimeMillis()
        val unigrams =
            analysisResult.unigramCounts.map { (term, count) ->
                UnigramStat(term, count, analysisResult.unigramDocCounts[term] ?: 0)
            }

        val ngrams =
            analysisResult.ngramCounts.map { (bigram, count) ->
                NgramStat(bigram.first, bigram.second, count, analysisResult.ngramDocCounts[bigram] ?: 0)
            }
        println("[TIMING] 통계 생성: ${System.currentTimeMillis() - start}ms")

        // 3. 후보 생성
        start = System.currentTimeMillis()
        val candidates =
            generateCandidates(
                analysisResult = analysisResult,
                totalDocuments = documents.size,
                config = config,
            )
        println("[TIMING] 후보 생성 (점수 계산): ${System.currentTimeMillis() - start}ms")

        return TermExtractionResult(
            totalDocuments = documents.size,
            unigrams = unigrams,
            ngrams = ngrams,
            candidates = candidates,
        )
    }

    private data class AnalysisResult(
        val unigramCounts: Map<String, Int>,
        val ngramCounts: Map<Pair<String, String>, Int>,
        val unigramDocCounts: Map<String, Int>,
        val ngramDocCounts: Map<Pair<String, String>, Int>,
        // 원문 구문 빈도: (정규화된 토큰쌍) -> (원문 구문 -> 빈도)
        val originalPhraseCounts: Map<Pair<String, String>, Map<String, Int>>,
    )

    private fun analyzeDocuments(documents: List<String>): AnalysisResult {
        val unigramCounts = ConcurrentHashMap<String, AtomicInteger>()
        val ngramCounts = ConcurrentHashMap<Pair<String, String>, AtomicInteger>()
        val unigramDocCounts = ConcurrentHashMap<String, AtomicInteger>()
        val ngramDocCounts = ConcurrentHashMap<Pair<String, String>, AtomicInteger>()
        // 원문 구문 빈도 추적
        val originalPhraseCounts = ConcurrentHashMap<Pair<String, String>, ConcurrentHashMap<String, AtomicInteger>>()

        val processedCount = AtomicInteger(0)
        val totalDocs = documents.size

        // 병렬 처리
        documents.parallelStream().forEach { content ->
            val count = processedCount.incrementAndGet()
            if (count % 100 == 0) {
                print("\r처리 중: $count/$totalDocs")
            }

            val sequences = nounSequenceExtractor.extractWithOffsets(content)
            val docUnigrams = mutableSetOf<String>()
            val docNgrams = mutableSetOf<Pair<String, String>>()

            for (sequence in sequences) {
                val tokens = sequence.tokens
                for (token in tokens) {
                    val normalized = token.term.lowercase()
                    unigramCounts.computeIfAbsent(normalized) { AtomicInteger(0) }.incrementAndGet()
                    docUnigrams.add(normalized)
                }

                for (i in 0 until tokens.size - 1) {
                    val bigram = tokens[i].term.lowercase() to tokens[i + 1].term.lowercase()
                    ngramCounts.computeIfAbsent(bigram) { AtomicInteger(0) }.incrementAndGet()
                    docNgrams.add(bigram)

                    // 원문 구문 추출 및 빈도 추적
                    val originalPhrase = sequence.getBigramPhrase(content, i)
                    originalPhraseCounts
                        .computeIfAbsent(bigram) { ConcurrentHashMap() }
                        .computeIfAbsent(originalPhrase) { AtomicInteger(0) }
                        .incrementAndGet()
                }
            }

            for (unigram in docUnigrams) {
                unigramDocCounts.computeIfAbsent(unigram) { AtomicInteger(0) }.incrementAndGet()
            }
            for (ngram in docNgrams) {
                ngramDocCounts.computeIfAbsent(ngram) { AtomicInteger(0) }.incrementAndGet()
            }
        }
        println("\r처리 완료: $totalDocs/$totalDocs")

        // AtomicInteger를 Int로 변환
        return AnalysisResult(
            unigramCounts = unigramCounts.mapValues { it.value.get() },
            ngramCounts = ngramCounts.mapValues { it.value.get() },
            unigramDocCounts = unigramDocCounts.mapValues { it.value.get() },
            ngramDocCounts = ngramDocCounts.mapValues { it.value.get() },
            originalPhraseCounts =
                originalPhraseCounts.mapValues { (_, phraseCounts) ->
                    phraseCounts.mapValues { it.value.get() }
                },
        )
    }

    private fun generateCandidates(
        analysisResult: AnalysisResult,
        totalDocuments: Int,
        config: TermExtractionConfig,
    ): List<CandidateStat> {
        val ngramCounts = analysisResult.ngramCounts
        val unigramCounts = analysisResult.unigramCounts
        val ngramDocCounts = analysisResult.ngramDocCounts
        val originalPhraseCounts = analysisResult.originalPhraseCounts

        val totalBigrams = ngramCounts.values.sum().toLong()
        val totalUnigrams = unigramCounts.values.sum().toLong()

        val filteredNgrams = ngramCounts.filter { it.value >= config.minCount }

        val candidateScores =
            filteredNgrams.mapNotNull { (bigram, count) ->
                // 가장 많이 등장한 원문 구문을 용어로 사용
                val term = getMostCommonPhrase(bigram, originalPhraseCounts)
                val components = listOf(bigram.first, bigram.second)

                // 제외 용어 체크 (정규화된 형태와 공백 제거 형태 모두)
                val normalizedTerm = term.lowercase()
                val noSpaceTerm = term.replace(" ", "").lowercase()
                if (config.excludedTerms.contains(normalizedTerm) ||
                    config.excludedTerms.contains(noSpaceTerm)
                ) {
                    return@mapNotNull null
                }

                // 필터 체크
                if (termCandidateFilter.shouldExclude(term, components, config.stopwords)) {
                    return@mapNotNull null
                }

                val unigram1Count = unigramCounts[bigram.first] ?: 0
                val unigram2Count = unigramCounts[bigram.second] ?: 0
                val docCount = ngramDocCounts[bigram] ?: 0

                val pmi =
                    scoreCalculator.calculatePMI(
                        bigramCount = count,
                        unigram1Count = unigram1Count,
                        unigram2Count = unigram2Count,
                        totalBigrams = totalBigrams,
                        totalUnigrams = totalUnigrams,
                    )

                val npmi = scoreCalculator.calculateNPMI(pmi, count, totalBigrams)

                if (npmi < config.npmiThreshold) {
                    return@mapNotNull null
                }

                val idf = scoreCalculator.calculateIDF(docCount, totalDocuments.toLong())
                val avgTfidf = scoreCalculator.calculateAvgTFIDF(count, totalDocuments.toLong(), idf)

                CandidateScoreInternal(term, components, count, pmi, npmi, docCount, idf, avgTfidf)
            }

        val maxAvgTfidf = candidateScores.maxOfOrNull { it.avgTfidf } ?: BigDecimal.ZERO

        // relevance score 계산 및 임계값 필터링
        val scoredCandidates =
            candidateScores.mapNotNull { score ->
                val relevanceScore =
                    scoreCalculator.calculateRelevanceScore(
                        npmi = score.npmi,
                        avgTfidf = score.avgTfidf,
                        maxAvgTfidf = maxAvgTfidf,
                    )

                if (relevanceScore < config.relevanceThreshold) {
                    return@mapNotNull null
                }

                score.copy(relevanceScore = relevanceScore)
            }

        // 중복 후보 제거
        val deduplicatedCandidates =
            termCandidateFilter.removeDuplicateCandidates(
                candidates = scoredCandidates,
                termExtractor = { it.term },
                scoreExtractor = { it.relevanceScore },
            )

        return deduplicatedCandidates
            .map { score ->
                CandidateStat(
                    term = score.term,
                    components = score.components,
                    count = score.count,
                    docCount = score.docCount,
                    pmi = score.pmi,
                    npmi = score.npmi,
                    idf = score.idf,
                    avgTfidf = score.avgTfidf,
                    relevanceScore = score.relevanceScore,
                )
            }.sortedByDescending { it.relevanceScore }
    }

    /**
     * 주어진 bigram에 대해 가장 많이 등장한 원문 구문을 반환합니다.
     * 원문 구문이 없으면 토큰을 공백으로 연결한 기본 형태를 반환합니다.
     */
    private fun getMostCommonPhrase(
        bigram: Pair<String, String>,
        originalPhraseCounts: Map<Pair<String, String>, Map<String, Int>>,
    ): String {
        val phraseCounts = originalPhraseCounts[bigram]
        if (phraseCounts.isNullOrEmpty()) {
            return "${bigram.first} ${bigram.second}"
        }

        return phraseCounts.maxByOrNull { it.value }?.key ?: "${bigram.first} ${bigram.second}"
    }

    private data class CandidateScoreInternal(
        val term: String,
        val components: List<String>,
        val count: Int,
        val pmi: BigDecimal,
        val npmi: BigDecimal,
        val docCount: Int,
        val idf: BigDecimal,
        val avgTfidf: BigDecimal,
        val relevanceScore: BigDecimal = BigDecimal.ZERO,
    )
}
