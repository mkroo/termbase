package com.mkroo.termbase.tool.extraction

import java.math.BigDecimal

/**
 * 사용자 사전에 추가할 후보 단어를 탐지합니다.
 *
 * 잘못 분리된 복합어를 rule-base로 찾습니다:
 * 1. NPMI가 매우 높음 (항상 함께 출현)
 * 2. 한쪽 단어가 불완전함 (단독 의미 없음)
 * 3. 결합 시 알려진 패턴 매칭
 */
class DictionaryCandidateDetector {
    data class DictionaryCandidate(
        val originalTerm: String, // 분리된 형태: "정산 역서"
        val suggestedTerm: String, // 제안 형태: "정산역서"
        val npmi: BigDecimal,
        val reasons: List<String>, // 탐지 이유
        val confidence: Double, // 신뢰도 0.0 ~ 1.0
    )

    fun detect(
        candidates: List<CandidateInfo>,
        unigramCounts: Map<String, Int>,
    ): List<DictionaryCandidate> {
        return candidates
            .mapNotNull { candidate ->
                val reasons = mutableListOf<String>()
                var score = 0.0

                val parts = candidate.term.split(" ")
                if (parts.size != 2) return@mapNotNull null

                val (first, second) = parts
                val combined = first + second

                // Rule 1: NPMI가 매우 높음 (0.9 이상)
                if (candidate.npmi >= BigDecimal("0.9")) {
                    reasons.add("NPMI가 매우 높음 (${candidate.npmi})")
                    score += 0.3
                }

                // Rule 2: 한쪽 단어가 불완전한 패턴
                if (isIncompleteWord(first) || isIncompleteWord(second)) {
                    val incomplete = if (isIncompleteWord(first)) first else second
                    reasons.add("불완전한 단어 포함: '$incomplete'")
                    score += 0.3
                }

                // Rule 3: 결합 시 알려진 접미사 패턴
                val suffixMatch = KNOWN_SUFFIXES.find { combined.endsWith(it) }
                if (suffixMatch != null) {
                    reasons.add("알려진 접미사 패턴: -$suffixMatch")
                    score += 0.2
                }

                // Rule 4: 외래어 패턴 (영어 음차)
                if (isLoanwordPattern(first, second)) {
                    reasons.add("외래어 음차 패턴")
                    score += 0.2
                }

                // Rule 5: 단어가 거의 항상 함께 출현 (개별 빈도 대비 결합 빈도)
                val firstCount = unigramCounts[first] ?: 0
                val secondCount = unigramCounts[second] ?: 0
                val minCount = minOf(firstCount, secondCount)
                if (minCount > 0 && candidate.count.toDouble() / minCount >= 0.7) {
                    reasons.add("항상 함께 출현 (${candidate.count}/$minCount)")
                    score += 0.2
                }

                // 최소 2개 이상의 규칙에 매칭되어야 함
                if (reasons.size >= 2 && score >= 0.4) {
                    DictionaryCandidate(
                        originalTerm = candidate.term,
                        suggestedTerm = combined,
                        npmi = candidate.npmi,
                        reasons = reasons,
                        confidence = minOf(score, 1.0),
                    )
                } else {
                    null
                }
            }.sortedByDescending { it.confidence }
    }

    /**
     * 불완전한 단어인지 판단
     * - 한글 1글자
     * - 의미 없는 음절 패턴
     */
    private fun isIncompleteWord(word: String): Boolean {
        // 1글자 한글
        if (word.length == 1 && word[0] in '\uAC00'..'\uD7A3') return true

        // 알려진 불완전 패턴
        if (word in INCOMPLETE_PATTERNS) return true

        // 자주 접미사로 쓰이는 음절로만 구성
        if (word.length <= 2 && SUFFIX_SYLLABLES.any { word.endsWith(it) }) return true

        return false
    }

    /**
     * 외래어 음차 패턴인지 판단
     */
    private fun isLoanwordPattern(
        first: String,
        second: String,
    ): Boolean {
        val combined = first + second

        // 알려진 외래어 접미사
        return LOANWORD_SUFFIXES.any { combined.endsWith(it) } ||
            LOANWORD_PATTERNS.any { combined.contains(it) }
    }

    data class CandidateInfo(
        val term: String,
        val count: Int,
        val npmi: BigDecimal,
    )

    companion object {
        // 알려진 접미사 (복합어에서 자주 나타남)
        private val KNOWN_SUFFIXES =
            setOf(
                "내역서",
                "명세서",
                "신청서",
                "동의서",
                "계약서",
                "주차장",
                "휴게소",
                "관리소",
                "가입",
                "탈퇴",
                "신청",
                "취소",
                "완료",
                "시작",
            )

        // 불완전한 패턴 (단독으로 의미 없음)
        private val INCOMPLETE_PATTERNS =
            setOf(
                "역서",
                "휴주",
                "그레이",
                "포지",
                "버네",
                "토리",
                "티스",
                "워크",
                "션",
                "먼트",
                "링",
                "터",
            )

        // 접미사로 자주 쓰이는 음절
        private val SUFFIX_SYLLABLES =
            setOf(
                "서",
                "장",
                "소",
                "션",
                "트",
                "크",
                "스",
                "터",
                "링",
            )

        // 외래어 접미사
        private val LOANWORD_SUFFIXES =
            setOf(
                "션",
                "먼트",
                "링",
                "터",
                "션",
                "니스",
                "티스",
                "워크",
                "포트",
                "넷",
                "북",
                "톤",
            )

        // 외래어 패턴
        private val LOANWORD_PATTERNS =
            setOf(
                "그레이션",
                "포지토리",
                "프레임워크",
                "쿠버네티스",
                "엔드포인트",
                "마이크로",
                "인프라",
            )
    }
}
