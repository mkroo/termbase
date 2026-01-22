package com.mkroo.termbase.application.service

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class TermCandidateScoreCalculatorTest :
    DescribeSpec({
        val calculator = TermCandidateScoreCalculator()

        describe("calculatePMI") {
            it("함께 자주 등장하는 단어 쌍은 양수 PMI를 가진다") {
                // 예: "공유", "주차장"이 자주 함께 등장
                val pmi =
                    calculator.calculatePMI(
                        bigramCount = 50,
                        unigram1Count = 100,
                        unigram2Count = 100,
                        totalBigrams = 1000,
                        totalUnigrams = 2000,
                    )

                pmi shouldBeGreaterThan BigDecimal.ZERO
            }

            it("독립적으로 등장하는 단어 쌍은 낮은 PMI를 가진다") {
                // 예: 각각 많이 등장하지만 함께는 드물게 등장
                val pmi =
                    calculator.calculatePMI(
                        bigramCount = 1,
                        unigram1Count = 500,
                        unigram2Count = 500,
                        totalBigrams = 1000,
                        totalUnigrams = 2000,
                    )

                pmi shouldBeLessThan BigDecimal.ZERO
            }

            it("bigram 등장이 0이면 0을 반환한다") {
                val pmi =
                    calculator.calculatePMI(
                        bigramCount = 0,
                        unigram1Count = 100,
                        unigram2Count = 100,
                        totalBigrams = 1000,
                        totalUnigrams = 2000,
                    )

                pmi shouldBe BigDecimal.ZERO
            }

            it("unigram1 등장이 0이면 0을 반환한다") {
                val pmi =
                    calculator.calculatePMI(
                        bigramCount = 50,
                        unigram1Count = 0,
                        unigram2Count = 100,
                        totalBigrams = 1000,
                        totalUnigrams = 2000,
                    )

                pmi shouldBe BigDecimal.ZERO
            }

            it("unigram2 등장이 0이면 0을 반환한다") {
                val pmi =
                    calculator.calculatePMI(
                        bigramCount = 50,
                        unigram1Count = 100,
                        unigram2Count = 0,
                        totalBigrams = 1000,
                        totalUnigrams = 2000,
                    )

                pmi shouldBe BigDecimal.ZERO
            }

            it("totalBigrams가 0이면 0을 반환한다") {
                val pmi =
                    calculator.calculatePMI(
                        bigramCount = 50,
                        unigram1Count = 100,
                        unigram2Count = 100,
                        totalBigrams = 0,
                        totalUnigrams = 2000,
                    )

                pmi shouldBe BigDecimal.ZERO
            }

            it("totalUnigrams가 0이면 0을 반환한다") {
                val pmi =
                    calculator.calculatePMI(
                        bigramCount = 50,
                        unigram1Count = 100,
                        unigram2Count = 100,
                        totalBigrams = 1000,
                        totalUnigrams = 0,
                    )

                pmi shouldBe BigDecimal.ZERO
            }
        }

        describe("calculateNPMI") {
            it("NPMI는 -1과 1 사이의 값을 가진다") {
                val pmi =
                    calculator.calculatePMI(
                        bigramCount = 50,
                        unigram1Count = 100,
                        unigram2Count = 100,
                        totalBigrams = 1000,
                        totalUnigrams = 2000,
                    )

                val npmi = calculator.calculateNPMI(pmi, bigramCount = 50, totalBigrams = 1000)

                npmi shouldBeGreaterThan BigDecimal("-1.0")
                npmi.compareTo(BigDecimal("1.0")) shouldBe 0 // 완전 동시출현의 경우 1.0이 됨
            }

            it("bigram 등장이 0이면 0을 반환한다") {
                val npmi = calculator.calculateNPMI(BigDecimal("2.0"), bigramCount = 0, totalBigrams = 1000)

                npmi shouldBe BigDecimal.ZERO
            }

            it("totalBigrams가 0이면 0을 반환한다") {
                val npmi = calculator.calculateNPMI(BigDecimal("2.0"), bigramCount = 50, totalBigrams = 0)

                npmi shouldBe BigDecimal.ZERO
            }
        }

        describe("calculateIDF") {
            it("적은 문서에서만 등장하는 용어는 높은 IDF를 가진다") {
                val idf = calculator.calculateIDF(docCount = 5, totalDocs = 1000)

                idf shouldBeGreaterThan BigDecimal.ZERO
            }

            it("많은 문서에서 등장하는 용어는 낮은 IDF를 가진다") {
                val idfRare = calculator.calculateIDF(docCount = 5, totalDocs = 1000)
                val idfCommon = calculator.calculateIDF(docCount = 500, totalDocs = 1000)

                idfRare shouldBeGreaterThan idfCommon
            }

            it("docCount가 0이면 0을 반환한다") {
                val idf = calculator.calculateIDF(docCount = 0, totalDocs = 1000)

                idf shouldBe BigDecimal.ZERO
            }

            it("totalDocs가 0이면 0을 반환한다") {
                val idf = calculator.calculateIDF(docCount = 5, totalDocs = 0)

                idf shouldBe BigDecimal.ZERO
            }
        }

        describe("calculateAvgTFIDF") {
            it("평균 TF-IDF를 계산한다") {
                val idf = calculator.calculateIDF(docCount = 10, totalDocs = 100)
                val avgTfidf = calculator.calculateAvgTFIDF(count = 50, totalDocs = 100, idf = idf)

                avgTfidf shouldBeGreaterThan BigDecimal.ZERO
            }

            it("count가 0이면 0을 반환한다") {
                val idf = calculator.calculateIDF(docCount = 10, totalDocs = 100)
                val avgTfidf = calculator.calculateAvgTFIDF(count = 0, totalDocs = 100, idf = idf)

                avgTfidf shouldBe BigDecimal.ZERO
            }

            it("totalDocs가 0이면 0을 반환한다") {
                val idf = calculator.calculateIDF(docCount = 10, totalDocs = 100)
                val avgTfidf = calculator.calculateAvgTFIDF(count = 50, totalDocs = 0, idf = idf)

                avgTfidf shouldBe BigDecimal.ZERO
            }
        }

        describe("calculateRelevanceScore") {
            it("NPMI와 TF-IDF를 조합하여 관련성 점수를 계산한다") {
                val relevance =
                    calculator.calculateRelevanceScore(
                        npmi = BigDecimal("0.5"),
                        avgTfidf = BigDecimal("0.3"),
                        maxAvgTfidf = BigDecimal("1.0"),
                    )

                relevance shouldBeGreaterThan BigDecimal.ZERO
                relevance shouldBeLessThan BigDecimal.ONE
            }

            it("maxAvgTfidf가 0이면 TF-IDF 부분은 0으로 처리한다") {
                val relevance =
                    calculator.calculateRelevanceScore(
                        npmi = BigDecimal("0.5"),
                        avgTfidf = BigDecimal("0.3"),
                        maxAvgTfidf = BigDecimal.ZERO,
                    )

                // NPMI 부분만 반영됨: (0.5 + 1) / 2 * 0.6 = 0.45
                relevance shouldBeGreaterThan BigDecimal.ZERO
            }

            it("커스텀 가중치를 사용할 수 있다") {
                val relevanceDefault =
                    calculator.calculateRelevanceScore(
                        npmi = BigDecimal("0.5"),
                        avgTfidf = BigDecimal("0.5"),
                        maxAvgTfidf = BigDecimal("1.0"),
                    )

                val relevanceCustom =
                    calculator.calculateRelevanceScore(
                        npmi = BigDecimal("0.5"),
                        avgTfidf = BigDecimal("0.5"),
                        maxAvgTfidf = BigDecimal("1.0"),
                        npmiWeight = BigDecimal("0.3"),
                        tfidfWeight = BigDecimal("0.7"),
                    )

                relevanceDefault shouldBe BigDecimal("0.650000")
                relevanceCustom shouldBe BigDecimal("0.575000")
            }
        }
    })
