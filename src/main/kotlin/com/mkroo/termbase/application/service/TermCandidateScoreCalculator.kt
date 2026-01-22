package com.mkroo.termbase.application.service

import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.ln

@Service
class TermCandidateScoreCalculator {
    /**
     * PMI (Pointwise Mutual Information) 계산.
     *
     * PMI(x, y) = log2( P(x,y) / (P(x) × P(y)) )
     *
     * @param bigramCount bigram 등장 빈도
     * @param unigram1Count 첫 번째 단어 등장 빈도
     * @param unigram2Count 두 번째 단어 등장 빈도
     * @param totalBigrams 전체 bigram 수
     * @param totalUnigrams 전체 unigram 수
     * @return PMI 값
     */
    fun calculatePMI(
        bigramCount: Int,
        unigram1Count: Int,
        unigram2Count: Int,
        totalBigrams: Long,
        totalUnigrams: Long,
    ): BigDecimal {
        if (bigramCount == 0 || unigram1Count == 0 || unigram2Count == 0 || totalBigrams == 0L || totalUnigrams == 0L) {
            return BigDecimal.ZERO
        }

        val pXY = bigramCount.toDouble() / totalBigrams.toDouble()
        val pX = unigram1Count.toDouble() / totalUnigrams.toDouble()
        val pY = unigram2Count.toDouble() / totalUnigrams.toDouble()

        val pmi = log2(pXY / (pX * pY))

        return BigDecimal(pmi, MATH_CONTEXT).setScale(SCALE, RoundingMode.HALF_UP)
    }

    /**
     * NPMI (Normalized PMI) 계산.
     *
     * NPMI = PMI / -log2(P(x,y))
     * 범위: -1 ~ 1
     *
     * @param pmi PMI 값
     * @param bigramCount bigram 등장 빈도
     * @param totalBigrams 전체 bigram 수
     * @return NPMI 값 (-1 ~ 1)
     */
    fun calculateNPMI(
        pmi: BigDecimal,
        bigramCount: Int,
        totalBigrams: Long,
    ): BigDecimal {
        if (bigramCount == 0 || totalBigrams == 0L) {
            return BigDecimal.ZERO
        }

        val pXY = bigramCount.toDouble() / totalBigrams.toDouble()
        val denominator = -log2(pXY)

        if (denominator == 0.0) {
            return BigDecimal.ZERO
        }

        val npmi = pmi.toDouble() / denominator

        return BigDecimal(npmi, MATH_CONTEXT).setScale(SCALE, RoundingMode.HALF_UP)
    }

    /**
     * IDF (Inverse Document Frequency) 계산.
     *
     * IDF = log(totalDocs / docCount)
     *
     * @param docCount 해당 용어가 등장한 문서 수
     * @param totalDocs 전체 문서 수
     * @return IDF 값
     */
    fun calculateIDF(
        docCount: Int,
        totalDocs: Long,
    ): BigDecimal {
        if (docCount == 0 || totalDocs == 0L) {
            return BigDecimal.ZERO
        }

        val idf = ln(totalDocs.toDouble() / docCount.toDouble())

        return BigDecimal(idf, MATH_CONTEXT).setScale(SCALE, RoundingMode.HALF_UP)
    }

    /**
     * 평균 TF-IDF 계산.
     *
     * avg_TFIDF = (count / totalDocs) × IDF
     *
     * @param count 용어 등장 빈도
     * @param totalDocs 전체 문서 수
     * @param idf IDF 값
     * @return 평균 TF-IDF 값
     */
    fun calculateAvgTFIDF(
        count: Int,
        totalDocs: Long,
        idf: BigDecimal,
    ): BigDecimal {
        if (count == 0 || totalDocs == 0L) {
            return BigDecimal.ZERO
        }

        val avgTF = count.toDouble() / totalDocs.toDouble()
        val avgTfidf = avgTF * idf.toDouble()

        return BigDecimal(avgTfidf, MATH_CONTEXT).setScale(SCALE, RoundingMode.HALF_UP)
    }

    /**
     * 관련성 점수 계산.
     *
     * relevance_score = NPMI × npmiWeight + normalized_tfidf × tfidfWeight
     *
     * @param npmi NPMI 값
     * @param avgTfidf 평균 TF-IDF 값
     * @param maxAvgTfidf 정규화용 최대 평균 TF-IDF 값
     * @param npmiWeight NPMI 가중치 (기본 0.6)
     * @param tfidfWeight TF-IDF 가중치 (기본 0.4)
     * @return 관련성 점수
     */
    fun calculateRelevanceScore(
        npmi: BigDecimal,
        avgTfidf: BigDecimal,
        maxAvgTfidf: BigDecimal,
        npmiWeight: BigDecimal = DEFAULT_NPMI_WEIGHT,
        tfidfWeight: BigDecimal = DEFAULT_TFIDF_WEIGHT,
    ): BigDecimal {
        val normalizedNpmi = (npmi + BigDecimal.ONE).divide(BigDecimal.TWO, MATH_CONTEXT)

        val normalizedTfidf =
            if (maxAvgTfidf.compareTo(BigDecimal.ZERO) > 0) {
                avgTfidf.divide(maxAvgTfidf, MATH_CONTEXT)
            } else {
                BigDecimal.ZERO
            }

        val relevanceScore = normalizedNpmi * npmiWeight + normalizedTfidf * tfidfWeight

        return relevanceScore.setScale(SCALE, RoundingMode.HALF_UP)
    }

    private fun log2(value: Double): Double = ln(value) / LN_2

    companion object {
        private val MATH_CONTEXT = MathContext(10, RoundingMode.HALF_UP)
        private const val SCALE = 6
        private val LN_2 = ln(2.0)

        val DEFAULT_NPMI_WEIGHT: BigDecimal = BigDecimal("0.6")
        val DEFAULT_TFIDF_WEIGHT: BigDecimal = BigDecimal("0.4")
    }
}
