package com.mkroo.termbase.application.service

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class TermCandidateFilterTest :
    DescribeSpec({
        val filter = TermCandidateFilter()

        describe("shouldExclude") {
            describe("URL 패턴 필터링") {
                it("http 포함 시 제외한다") {
                    filter.shouldExclude("https socarcorp", listOf("https", "socarcorp")) shouldBe true
                }

                it("atlassian 포함 시 제외한다") {
                    filter.shouldExclude("socarcorp atlassian", listOf("socarcorp", "atlassian")) shouldBe true
                }

                it("jira 포함 시 제외한다") {
                    filter.shouldExclude("jira software", listOf("jira", "software")) shouldBe true
                }

                it("wiki 포함 시 제외한다") {
                    filter.shouldExclude("wiki spaces", listOf("wiki", "spaces")) shouldBe true
                }

                it("google 포함 시 제외한다") {
                    filter.shouldExclude("google com", listOf("google", "com")) shouldBe true
                }
            }

            describe("기본 불용어 필터링") {
                it("daily 포함 시 제외한다") {
                    filter.shouldExclude("daily 비", listOf("daily", "비")) shouldBe true
                }

                it("action item 제외한다") {
                    filter.shouldExclude("action item", listOf("action", "item")) shouldBe true
                }

                it("projects 포함 시 제외한다") {
                    filter.shouldExclude("projects newpk", listOf("projects", "newpk")) shouldBe true
                }
            }

            describe("커스텀 불용어 필터링") {
                it("커스텀 불용어 포함 시 제외한다") {
                    val stopwords = setOf("제이콥스", "프레디")
                    filter.shouldExclude("제이콥스 공유", listOf("제이콥스", "공유"), stopwords) shouldBe true
                }

                it("커스텀 불용어가 없으면 포함한다") {
                    filter.shouldExclude("공유 내용", listOf("공유", "내용")) shouldBe false
                }
            }

            describe("단일 문자 필터링") {
                it("단일 ASCII 문자 포함 시 제외한다") {
                    filter.shouldExclude("software c", listOf("software", "c")) shouldBe true
                }

                it("한글 단일 문자는 허용한다") {
                    filter.shouldExclude("주차 장", listOf("주차", "장")) shouldBe false
                }

                it("숫자 단일 문자는 허용한다") {
                    filter.shouldExclude("버전 2", listOf("버전", "2")) shouldBe false
                }
            }

            describe("유효한 용어") {
                it("일반 복합 명사는 포함한다") {
                    filter.shouldExclude("공유 주차장", listOf("공유", "주차장")) shouldBe false
                }

                it("비즈니스 용어는 포함한다") {
                    filter.shouldExclude("엑셀 다운로드", listOf("엑셀", "다운로드")) shouldBe false
                }

                it("기술 용어는 포함한다") {
                    filter.shouldExclude("웹 앱", listOf("웹", "앱")) shouldBe false
                }
            }

            describe("에코 패턴 필터링") {
                it("컴포넌트가 접두사로 반복되면 제외한다") {
                    filter.shouldExclude("공휴일 공휴", listOf("공휴일", "공휴")) shouldBe true
                }

                it("컴포넌트가 접미사로 반복되면 제외한다") {
                    filter.shouldExclude("담당자 담당", listOf("담당자", "담당")) shouldBe true
                }

                it("자치구 자치 패턴 제외") {
                    filter.shouldExclude("자치구 자치", listOf("자치구", "자치")) shouldBe true
                }

                it("예비군 예비 패턴 제외") {
                    filter.shouldExclude("예비군 예비", listOf("예비군", "예비")) shouldBe true
                }

                it("서로 다른 용어는 허용한다") {
                    filter.shouldExclude("공유 주차장", listOf("공유", "주차장")) shouldBe false
                }
            }

            describe("기술적 노이즈 패턴 필터링") {
                it("mermaid 패턴 포함 시 제외한다") {
                    filter.shouldExclude("fcmermaid diagramf", listOf("fcmermaid", "diagramf")) shouldBe true
                }

                it("diagram 패턴 포함 시 제외한다") {
                    filter.shouldExclude("diagramxen macrotruemermaid", listOf("diagramxen", "macrotruemermaid")) shouldBe true
                }

                it("cdn jsdelivr 패턴 제외한다") {
                    filter.shouldExclude("cdn jsdelivr", listOf("cdn", "jsdelivr")) shouldBe true
                }

                it("static mermaid 패턴 제외한다") {
                    filter.shouldExclude("static mermaid", listOf("static", "mermaid")) shouldBe true
                }

                it("png 패턴 포함 시 제외한다") {
                    filter.shouldExclude("logo pngdisplay", listOf("logo", "pngdisplay")) shouldBe true
                }

                it("configs 패턴 포함 시 제외한다") {
                    filter.shouldExclude("sync configs", listOf("sync", "configs")) shouldBe true
                }
            }

            describe("코드 문법 패턴 필터링") {
                it("left join 제외한다") {
                    filter.shouldExclude("left join", listOf("left", "join")) shouldBe true
                }

                it("note right 제외한다") {
                    filter.shouldExclude("note right", listOf("note", "right")) shouldBe true
                }

                it("end subgraph 제외한다") {
                    filter.shouldExclude("end subgraph", listOf("end", "subgraph")) shouldBe true
                }

                it("read write 제외한다") {
                    filter.shouldExclude("read write", listOf("read", "write")) shouldBe true
                }
            }

            describe("해시값/ID 패턴 필터링") {
                it("16진수 패턴 포함 시 제외한다") {
                    filter.shouldExclude("df bfa", listOf("df", "bfa")) shouldBe true
                }

                it("긴 해시값 제외한다") {
                    filter.shouldExclude("abcdef 123456", listOf("abcdef", "123456")) shouldBe true
                }

                it("일반 영문 단어는 허용한다") {
                    filter.shouldExclude("api server", listOf("api", "server")) shouldBe false
                }
            }

            describe("한글 단일 음절 패턴 필터링") {
                it("형태소 분리 오류 '버 젼' 제외한다") {
                    filter.shouldExclude("버 젼", listOf("버", "젼")) shouldBe true
                }

                it("형태소 분리 오류 '그루 밍' 제외한다") {
                    filter.shouldExclude("그루 밍", listOf("그루", "밍")) shouldBe true
                }

                it("형태소 분리 오류 '뤼 버' 제외한다") {
                    filter.shouldExclude("뤼 버", listOf("뤼", "버")) shouldBe true
                }

                it("의미있는 한글 1글자(수사)는 허용한다") {
                    filter.shouldExclude("일 차", listOf("일", "차")) shouldBe false
                }

                it("의미있는 한글 1글자(단위)는 허용한다") {
                    filter.shouldExclude("월 별", listOf("월", "별")) shouldBe false
                }

                it("의미있는 한글 1글자(위치)는 허용한다") {
                    filter.shouldExclude("상 반기", listOf("상", "반기")) shouldBe false
                }
            }
        }

        describe("removeDuplicateCandidates") {
            data class TestCandidate(
                val term: String,
                val score: BigDecimal,
            )

            describe("정규화 중복 제거") {
                it("같은 정규화 형태의 후보 중 점수가 높은 것만 유지한다") {
                    val candidates =
                        listOf(
                            TestCandidate("주차장 주차", BigDecimal("0.9")),
                            TestCandidate("주차 장주차", BigDecimal("0.7")),
                        )

                    val result =
                        filter.removeDuplicateCandidates(
                            candidates = candidates,
                            termExtractor = { it.term },
                            scoreExtractor = { it.score },
                        )

                    result.map { it.term } shouldContainExactlyInAnyOrder listOf("주차장 주차")
                }

                it("서로 다른 정규화 형태는 모두 유지한다") {
                    val candidates =
                        listOf(
                            TestCandidate("공유 주차장", BigDecimal("0.9")),
                            TestCandidate("엑셀 다운로드", BigDecimal("0.8")),
                        )

                    val result =
                        filter.removeDuplicateCandidates(
                            candidates = candidates,
                            termExtractor = { it.term },
                            scoreExtractor = { it.score },
                        )

                    result.map { it.term } shouldContainExactlyInAnyOrder listOf("공유 주차장", "엑셀 다운로드")
                }
            }

            describe("부분 문자열 중복 제거") {
                it("다른 용어의 접두사인 경우 제거한다") {
                    val candidates =
                        listOf(
                            TestCandidate("마이그레이션", BigDecimal("0.9")),
                            TestCandidate("마이그레이", BigDecimal("0.8")),
                        )

                    val result =
                        filter.removeDuplicateCandidates(
                            candidates = candidates,
                            termExtractor = { it.term },
                            scoreExtractor = { it.score },
                        )

                    result.map { it.term } shouldContainExactlyInAnyOrder listOf("마이그레이션")
                }

                it("다른 용어의 접미사인 경우 제거한다") {
                    val candidates =
                        listOf(
                            TestCandidate("공휴일", BigDecimal("0.9")),
                            TestCandidate("휴일", BigDecimal("0.7")),
                        )

                    val result =
                        filter.removeDuplicateCandidates(
                            candidates = candidates,
                            termExtractor = { it.term },
                            scoreExtractor = { it.score },
                        )

                    result.map { it.term } shouldContainExactlyInAnyOrder listOf("공휴일")
                }

                it("부분 문자열이 아닌 경우 모두 유지한다") {
                    val candidates =
                        listOf(
                            TestCandidate("주차장", BigDecimal("0.9")),
                            TestCandidate("주차비", BigDecimal("0.8")),
                        )

                    val result =
                        filter.removeDuplicateCandidates(
                            candidates = candidates,
                            termExtractor = { it.term },
                            scoreExtractor = { it.score },
                        )

                    result.map { it.term } shouldContainExactlyInAnyOrder listOf("주차장", "주차비")
                }
            }

            describe("빈 목록 처리") {
                it("빈 목록은 그대로 반환한다") {
                    val result =
                        filter.removeDuplicateCandidates(
                            candidates = emptyList<TestCandidate>(),
                            termExtractor = { it.term },
                            scoreExtractor = { it.score },
                        )

                    result shouldBe emptyList()
                }
            }
        }

        describe("detectDictionaryCandidate") {
            describe("외래어 분리 패턴 감지") {
                it("높은 NPMI + 한글 2음절+2음절 조합은 사전 등록 후보로 판단한다") {
                    val result =
                        filter.detectDictionaryCandidate(
                            components = listOf("버네", "티스"),
                            npmi = BigDecimal("0.98"),
                        )

                    result.needsDictionary shouldBe true
                    result.reason shouldBe DictionaryNeedReason.LOANWORD_SPLIT
                    result.confidence shouldBe DictionaryConfidence.HIGH
                }

                it("마이그레이션 분리 패턴을 감지한다") {
                    val result =
                        filter.detectDictionaryCandidate(
                            components = listOf("마이", "그레이"),
                            npmi = BigDecimal("0.96"),
                        )

                    result.needsDictionary shouldBe true
                    result.confidence shouldBe DictionaryConfidence.HIGH
                }
            }

            describe("높은 동시 출현율 감지") {
                it("고유어 접미사가 있으면 HIGH_COOCCURRENCE로 판단한다") {
                    // "하다" 접미사가 있으면 고유어로 판단하여 외래어 분리가 아님
                    val result =
                        filter.detectDictionaryCandidate(
                            components = listOf("개발", "하다"),
                            npmi = BigDecimal("0.97"),
                        )

                    result.needsDictionary shouldBe true
                    result.reason shouldBe DictionaryNeedReason.HIGH_COOCCURRENCE
                    result.confidence shouldBe DictionaryConfidence.MEDIUM
                }

                it("4음절 이상 컴포넌트가 포함된 경우 사전 등록 후보가 아니다") {
                    // "비즈니스"는 4음절이므로 외래어 분리 패턴이 아님
                    val result =
                        filter.detectDictionaryCandidate(
                            components = listOf("비즈니스", "로직"),
                            npmi = BigDecimal("0.97"),
                        )

                    result.needsDictionary shouldBe false
                }
            }

            describe("사전 등록이 필요없는 경우") {
                it("낮은 NPMI는 사전 등록 후보가 아니다") {
                    val result =
                        filter.detectDictionaryCandidate(
                            components = listOf("공유", "주차장"),
                            npmi = BigDecimal("0.80"),
                        )

                    result.needsDictionary shouldBe false
                    result.reason shouldBe null
                }

                it("영문 컴포넌트는 사전 등록 후보가 아니다") {
                    val result =
                        filter.detectDictionaryCandidate(
                            components = listOf("cloud", "ecosystem"),
                            npmi = BigDecimal("0.98"),
                        )

                    result.needsDictionary shouldBe false
                }
            }
        }
    })
