package com.mkroo.termbase.domain.service

import java.math.BigDecimal

/**
 * 문서 목록에서 용어 후보를 추출하는 순수 도메인 로직.
 *
 * DB, Elasticsearch 등 외부 의존성 없이 동작합니다.
 * 저장은 별도의 서비스에서 담당합니다.
 */
interface TermCandidateExtractor {
    /**
     * 문서 목록에서 용어 후보를 추출합니다.
     *
     * @param documents 분석할 문서 내용 목록
     * @param config 추출 설정
     * @return 추출 결과 (통계 + 후보 목록)
     */
    fun extract(
        documents: List<String>,
        config: TermExtractionConfig,
    ): TermExtractionResult
}

/**
 * 용어 추출 설정
 */
data class TermExtractionConfig(
    val minCount: Int = 3,
    val npmiThreshold: BigDecimal = BigDecimal("0.2"),
    val relevanceThreshold: BigDecimal = BigDecimal("0.3"),
    val stopwords: Set<String> = emptySet(),
    val excludedTerms: Set<String> = emptySet(),
)

/**
 * 용어 추출 결과 (순수 데이터)
 */
data class TermExtractionResult(
    val totalDocuments: Int,
    val unigrams: List<UnigramStat>,
    val ngrams: List<NgramStat>,
    val candidates: List<CandidateStat>,
    val dictionaryCandidates: List<DictionaryCandidateStat> = emptyList(),
)

/**
 * Unigram 통계
 */
data class UnigramStat(
    val term: String,
    val count: Int,
    val docCount: Int,
)

/**
 * Ngram 통계
 */
data class NgramStat(
    val term1: String,
    val term2: String,
    val count: Int,
    val docCount: Int,
)

/**
 * 용어 후보 통계
 */
data class CandidateStat(
    val term: String,
    val components: List<String>,
    val count: Int,
    val docCount: Int,
    val pmi: BigDecimal,
    val npmi: BigDecimal,
    val idf: BigDecimal,
    val avgTfidf: BigDecimal,
    val relevanceScore: BigDecimal,
)

/**
 * 사용자 사전 추가 추천 용어
 */
data class DictionaryCandidateStat(
    val originalTerm: String, // 분리된 형태: "정산 역서"
    val suggestedTerm: String, // 제안 형태: "정산역서"
    val npmi: BigDecimal,
    val reasons: List<String>, // 탐지 이유
    val confidence: Double, // 신뢰도 0.0 ~ 1.0
)
