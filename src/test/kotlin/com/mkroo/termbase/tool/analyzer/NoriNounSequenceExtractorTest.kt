package com.mkroo.termbase.tool.analyzer

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class NoriNounSequenceExtractorTest :
    DescribeSpec({
        val extractor = NoriNounSequenceExtractor()

        describe("NoriNounSequenceExtractor") {
            describe("extract") {
                it("공유 주차장이 포함된 문장에서 명사 토큰을 추출한다") {
                    val content = "공유 주차장 이용 안내"
                    val result = extractor.extract(content)

                    // 명사 토큰들이 추출됨
                    result.flatten() shouldContainAll listOf("공유", "주차장", "이용", "안내")
                }

                it("빈 문자열은 빈 리스트를 반환한다") {
                    val result = extractor.extract("")
                    result.shouldBeEmpty()
                }

                it("공백만 있는 문자열은 빈 리스트를 반환한다") {
                    val result = extractor.extract("   ")
                    result.shouldBeEmpty()
                }

                it("1글자 토큰은 필터링된다") {
                    val content = "나 는 간다"
                    val result = extractor.extract(content)

                    // 1글자 명사는 필터링됨
                    result.shouldBeEmpty()
                }

                it("한글이 포함되지 않은 토큰은 필터링된다") {
                    val content = "ABC 테스트 데이터 분석"
                    val result = extractor.extract(content)

                    // 한글 명사만 추출됨
                    result.flatten() shouldContainAll listOf("테스트", "데이터")
                    result.flatten().none { it == "ABC" } shouldBe true
                }

                it("HTML 엔티티는 필터링된다") {
                    val content = "hello&nbsp;world 테스트 데이터"
                    val result = extractor.extract(content)

                    // HTML 엔티티 패턴은 필터링됨
                    result.flatten().none { it.contains("nbsp") } shouldBe true
                }

                it("토큰 사이 간격이 크면 별도 시퀀스로 분리된다") {
                    // 마침표와 새 문장으로 인한 큰 간격
                    val content = "공유 주차장입니다. 결제 완료되었습니다"
                    val result = extractor.extract(content)

                    // 문장 경계로 인해 별도의 시퀀스로 분리됨
                    result.size shouldBe 2
                    result[0] shouldContainAll listOf("공유", "주차장")
                    result[1] shouldContainAll listOf("결제", "완료")
                }

                it("연속된 명사가 2개 미만이면 시퀀스에 포함되지 않는다") {
                    val content = "가다"
                    val result = extractor.extract(content)

                    // 동사만 있으면 명사 시퀀스가 없음
                    result.shouldBeEmpty()
                }

                it("관형격 조사 '의'가 포함된 구문에서 명사 토큰이 같은 시퀀스에 포함된다") {
                    val content = "모두의 주차장 앱을 소개합니다"
                    val result = extractor.extract(content)

                    // "모두"와 "주차장"이 같은 시퀀스에 있어야 함
                    result.any { it.containsAll(listOf("모두", "주차장")) } shouldBe true
                }

                it("여러 단어가 연속된 경우 하나의 시퀀스로 추출된다") {
                    val content = "인공 지능 기술 발전"
                    val result = extractor.extract(content)

                    // 연속된 명사들이 하나의 시퀀스로 추출됨
                    result shouldHaveSize 1
                    result[0] shouldContainAll listOf("인공", "지능", "기술", "발전")
                }
            }

            describe("extractWithOffsets") {
                it("토큰의 오프셋 정보를 정확하게 반환한다") {
                    val content = "공유 주차장"
                    val result = extractor.extractWithOffsets(content)

                    result shouldHaveSize 1
                    val sequence = result[0]

                    // 첫 번째 토큰 "공유"
                    sequence.tokens[0].term shouldBe "공유"
                    sequence.tokens[0].startOffset shouldBe 0
                    sequence.tokens[0].endOffset shouldBe 2

                    // 두 번째 토큰 "주차장"
                    sequence.tokens[1].term shouldBe "주차장"
                    sequence.tokens[1].startOffset shouldBe 3
                    sequence.tokens[1].endOffset shouldBe 6
                }

                it("관형격 조사 '의'가 포함된 구문에서 원문을 정확히 추출한다") {
                    val content = "모두의 주차장 앱을 소개합니다"
                    val result = extractor.extractWithOffsets(content)

                    result shouldHaveSize 1
                    val sequence = result[0]

                    // "모두"와 "주차장"이 같은 시퀀스에 있어야 함
                    sequence.tokens.map { it.term } shouldContainAll listOf("모두", "주차장")

                    // getBigramPhrase로 원문 추출
                    val originalPhrase = sequence.getBigramPhrase(content, 0)
                    originalPhrase shouldBe "모두의 주차장"
                }

                it("연속된 명사에서 n-gram 원문 구문을 추출한다") {
                    val content = "인공 지능 기술 발전"
                    val result = extractor.extractWithOffsets(content)

                    result shouldHaveSize 1
                    val sequence = result[0]

                    // 2-gram 원문 추출
                    sequence.getBigramPhrase(content, 0) shouldBe "인공 지능"
                    sequence.getBigramPhrase(content, 1) shouldBe "지능 기술"
                    sequence.getBigramPhrase(content, 2) shouldBe "기술 발전"

                    // 3-gram 원문 추출
                    sequence.getOriginalPhrase(content, 0, 2) shouldBe "인공 지능 기술"
                    sequence.getOriginalPhrase(content, 1, 3) shouldBe "지능 기술 발전"

                    // 4-gram 원문 추출
                    sequence.getOriginalPhrase(content, 0, 3) shouldBe "인공 지능 기술 발전"
                }
            }
        }
    })
