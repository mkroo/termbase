package com.mkroo.termbase.tool.extraction

import com.mkroo.termbase.application.service.DefaultTermCandidateExtractor
import com.mkroo.termbase.application.service.TermCandidateFilter
import com.mkroo.termbase.application.service.TermCandidateScoreCalculator
import com.mkroo.termbase.domain.service.DictionaryCandidateStat
import com.mkroo.termbase.domain.service.TermExtractionConfig
import com.mkroo.termbase.domain.service.TermExtractionResult
import com.mkroo.termbase.tool.analyzer.NoriNounSequenceExtractor
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 독립 실행 가능한 용어 후보 추출 스크립트
 *
 * Spring, Elasticsearch 없이 순수 도메인 로직 + Nori로 실행됩니다.
 *
 * 실행 방법:
 * ./gradlew runStandaloneExtraction
 */
fun main() {
    println("\n" + "=".repeat(60))
    println("용어 후보 추출 (Standalone) - Nori")
    println("=".repeat(60))

    // 1. Nori 초기화 시간 측정
    var start = System.currentTimeMillis()
    val nounSequenceExtractor = NoriNounSequenceExtractor()
    println("[TIMING] Nori 초기화: ${System.currentTimeMillis() - start}ms")

    // 2. 나머지 컴포넌트 (거의 즉시)
    start = System.currentTimeMillis()
    val scoreCalculator = TermCandidateScoreCalculator()
    val termCandidateFilter = TermCandidateFilter()
    val extractor =
        DefaultTermCandidateExtractor(
            nounSequenceExtractor = nounSequenceExtractor,
            scoreCalculator = scoreCalculator,
            termCandidateFilter = termCandidateFilter,
        )
    println("[TIMING] 기타 컴포넌트 초기화: ${System.currentTimeMillis() - start}ms")

    val runner = StandaloneRunner(extractor)
    runner.run()

    println("\n" + "=".repeat(60))
    println("완료")
    println("=".repeat(60))
}

class StandaloneRunner(
    private val extractor: DefaultTermCandidateExtractor,
) {
    private val objectMapper: ObjectMapper =
        JsonMapper
            .builder()
            .addModule(KotlinModule.Builder().build())
            .build()

    fun run() {
        // 1. 데이터 로드
        val testDataFile = File("src/test/resources/testdata/elasticsearch-source-documents.json")
        if (!testDataFile.exists()) {
            println("테스트 데이터 파일이 없습니다: ${testDataFile.absolutePath}")
            return
        }

        val allDocuments = loadDocuments(testDataFile)
        val documents = allDocuments // 전체 문서 처리
        println("로드된 문서 수: ${documents.size}")

        // 2. 추출 설정
        val config =
            TermExtractionConfig(
                minCount = 3,
                npmiThreshold = BigDecimal("0.1"),
                relevanceThreshold = BigDecimal("0.3"),
                stopwords =
                    setOf(
                        "제이콥스",
                        "프레디",
                        "찬스",
                        "가버",
                        "신어",
                        "비",
                        "버드",
                        "구어",
                    ),
            )

        // 3. 순수 도메인 로직으로 추출
        println("\n문서 분석 중...")
        val startTime = System.currentTimeMillis()
        val extractionResult = extractor.extract(documents, config)
        println("추출 완료: ${System.currentTimeMillis() - startTime}ms")

        // 4. 사전 추천 용어 탐지
        println("\n사전 후보 탐지 중...")
        val dictStart = System.currentTimeMillis()
        val dictionaryCandidates = detectDictionaryCandidates(extractionResult)
        println("탐지 완료: ${System.currentTimeMillis() - dictStart}ms")

        // 5. 최종 결과 (사전 추천 포함)
        val result =
            extractionResult.copy(
                dictionaryCandidates = dictionaryCandidates,
            )

        // 6. 결과 출력
        printResults(result)
    }

    private fun detectDictionaryCandidates(result: TermExtractionResult): List<DictionaryCandidateStat> {
        val detector = DictionaryCandidateDetector()

        val candidateInfos =
            result.candidates.map { c ->
                DictionaryCandidateDetector.CandidateInfo(
                    term = c.term,
                    count = c.count,
                    npmi = c.npmi,
                )
            }

        val unigramCounts = result.unigrams.associate { it.term to it.count }
        val detected = detector.detect(candidateInfos, unigramCounts)

        return detected.map { dc ->
            DictionaryCandidateStat(
                originalTerm = dc.originalTerm,
                suggestedTerm = dc.suggestedTerm,
                npmi = dc.npmi,
                reasons = dc.reasons,
                confidence = dc.confidence,
            )
        }
    }

    private fun loadDocuments(file: File): List<String> {
        val root = objectMapper.readTree(file)
        val hits = root.path("hits").path("hits")

        val documents = mutableListOf<String>()
        for (hit in hits) {
            try {
                val content = hit.path("_source").path("content").asText()
                if (content.isNotBlank()) {
                    documents.add(content)
                }
            } catch (_: Exception) {
                // skip
            }
        }
        return documents
    }

    private fun printResults(result: TermExtractionResult) {
        // 용어 후보
        println("\n========== 추출된 용어 후보 (관련성 점수순, 상위 30개) ==========")
        result.candidates.take(30).forEachIndexed { i, c ->
            println(
                "${i + 1}. ${c.term} | 빈도: ${c.count} | 문서수: ${c.docCount} | " +
                    "NPMI: ${c.npmi.setScale(4, RoundingMode.HALF_UP)} | " +
                    "관련성: ${c.relevanceScore.setScale(4, RoundingMode.HALF_UP)}",
            )
        }

        // 사전 추천 용어
        val highConfidence = result.dictionaryCandidates.filter { it.confidence >= 0.8 }
        println("\n========== 사전 추가 권장 용어 (신뢰도 80%+, ${highConfidence.size}개) ==========")
        highConfidence.take(20).forEachIndexed { i, dc ->
            println(
                "${i + 1}. ${dc.originalTerm} → ${dc.suggestedTerm} | " +
                    "신뢰도: ${"%.0f".format(dc.confidence * 100)}% | " +
                    "이유: ${dc.reasons.take(2).joinToString(", ")}",
            )
        }

        // 통계 요약
        println("\n========== 통계 요약 ==========")
        println("총 문서: ${result.totalDocuments}개")
        println("총 Unigram: ${result.unigrams.size}개")
        println("총 Ngram: ${result.ngrams.size}개")
        println("총 용어 후보: ${result.candidates.size}개")
        println("사전 추천 용어: ${result.dictionaryCandidates.size}개 (신뢰도 80%+: ${highConfidence.size}개)")
    }
}
