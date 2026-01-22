package com.mkroo.termbase.application.service

import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class TermCandidateFilter {
    companion object {
        private val URL_PATTERNS =
            listOf(
                "http",
                "https",
                "www",
                ".com",
                ".net",
                ".org",
                ".io",
                ".kr",
                "atlassian",
                "socarcorp",
                "google",
                "slack",
                "jira",
                "confluence",
                "github",
                "wiki",
            )

        private val TECHNICAL_NOISE_PATTERNS =
            listOf(
                // Mermaid 다이어그램 관련
                "mermaid",
                "diagram",
                "subgraph",
                // 이미지/미디어 관련
                "png",
                "jpg",
                "jpeg",
                "gif",
                "svg",
                "display",
                "logo",
                // CDN/웹 리소스 관련
                "cdn",
                "jsdelivr",
                "fetch",
                "static",
                // 코드/마크업 관련
                "macro",
                "extension",
                "containerid",
                "cloudid",
                "gid",
                // 기타 기술 패턴
                "outbound",
                "inbound",
                "configs",
                "instructions",
            )

        private val CODE_SYNTAX_PATTERNS =
            listOf(
                // SQL 문법
                "left join",
                "right join",
                "inner join",
                "outer join",
                "group by",
                "order by",
                // 프로그래밍 문법
                "end subgraph",
                "note over",
                "note right",
                "note left",
                "right of",
                "left of",
                "read write",
            )

        private val DEFAULT_STOPWORDS =
            setOf(
                // 일반적인 불용어
                "daily",
                "item",
                "action",
                // 자주 사용되는 불필요한 패턴
                "spaces",
                "boards",
                "projects",
                "software",
                // 기술적 노이즈
                "err",
                "akc",
                "bfa",
                "inc",
            )

        // 해시값/ID 패턴 (16진수 문자로만 구성된 3글자 이상 문자열)
        private val HEX_PATTERN = Regex("^[a-f0-9]{3,}$")

        // 의미있는 한글 1글자 (수사, 단위, 위치, 일반 명사 등)
        private val MEANINGFUL_SINGLE_CHARS =
            setOf(
                // 수사
                "일",
                "이",
                "삼",
                "사",
                "오",
                "육",
                "칠",
                "팔",
                "구",
                "십",
                "백",
                "천",
                "만",
                "억",
                // 시간/단위
                "월",
                "년",
                "시",
                "분",
                "초",
                "개",
                "명",
                "건",
                "원",
                "회",
                "차",
                "번",
                "권",
                // 위치/방향
                "상",
                "중",
                "하",
                "전",
                "후",
                "내",
                "외",
                "좌",
                "우",
                // 일반 명사 (장소, 역할 등)
                "장",
                "실",
                "과",
                "부",
                "팀",
                "비",
                "앱",
                "웹",
                "폰",
                // 기타 의미있는 1글자
                "별",
                "용",
                "형",
                "화",
            )

        /**
         * 사전 등록 후보로 판단하기 위한 NPMI 임계값
         * NPMI가 이 값 이상이면 두 컴포넌트가 거의 항상 함께 출현함을 의미
         */
        val DICTIONARY_NPMI_THRESHOLD: BigDecimal = BigDecimal("0.95")
    }

    fun shouldExclude(
        term: String,
        components: List<String>,
        stopwords: Set<String> = emptySet(),
    ): Boolean {
        val allStopwords = DEFAULT_STOPWORDS + stopwords
        val lowerTerm = term.lowercase()
        val lowerComponents = components.map { it.lowercase() }

        // URL 패턴 포함 여부 확인
        if (containsUrlPattern(lowerTerm)) {
            return true
        }

        // 기술적 노이즈 패턴 포함 여부 확인
        if (containsTechnicalNoisePattern(lowerTerm)) {
            return true
        }

        // 코드 문법 패턴 포함 여부 확인
        if (isCodeSyntaxPattern(lowerTerm)) {
            return true
        }

        // 불용어 포함 여부 확인
        if (lowerComponents.any { it in allStopwords }) {
            return true
        }

        // 단일 ASCII 문자로 구성된 경우 제외 (한글은 허용)
        if (lowerComponents.any { it.length == 1 && it.first().code < 128 && !it.first().isDigit() }) {
            return true
        }

        // 해시값/ID 패턴 제외 (16진수로만 구성된 컴포넌트)
        if (lowerComponents.any { isHexPattern(it) }) {
            return true
        }

        // 한글 단일 음절 + 다른 단어 조합 제외 (형태소 분리 오류)
        // 예: "버 젼", "그루 밍", "스크린 샷"
        if (hasKoreanSingleSyllablePattern(lowerComponents)) {
            return true
        }

        // 에코 패턴 제외: 한 컴포넌트가 다른 컴포넌트의 일부인 경우
        // 예: "공휴일 공휴", "담당자 담당", "자치구 자치"
        if (hasEchoPattern(lowerComponents)) {
            return true
        }

        return false
    }

    /**
     * 에코 패턴 감지: 한 컴포넌트가 다른 컴포넌트를 포함하거나 포함되는 경우
     * 예: ["공휴일", "공휴"] → true (공휴일이 공휴를 포함)
     */
    private fun hasEchoPattern(components: List<String>): Boolean {
        if (components.size < 2) return false

        for (i in components.indices) {
            for (j in components.indices) {
                if (i != j) {
                    val a = components[i]
                    val b = components[j]
                    // a가 b를 포함하고, 둘이 같지 않으면 에코 패턴
                    if (a.length > b.length && (a.startsWith(b) || a.endsWith(b))) {
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * 형태소 분리로 인한 중복 후보를 제거합니다.
     *
     * 예시:
     * - "주차장 주차"와 "주차 장" → 동일한 정규화 형태이므로 점수 높은 것만 유지
     * - "마이 그레이"와 "그레이 션" → "마이그레이션"의 부분이므로 제거
     */
    fun <T> removeDuplicateCandidates(
        candidates: List<T>,
        termExtractor: (T) -> String,
        scoreExtractor: (T) -> BigDecimal,
    ): List<T> {
        if (candidates.isEmpty()) return candidates

        // 1단계: 정규화된 형태(공백 제거)로 그룹화하여 중복 제거
        val normalizedGroups =
            candidates.groupBy { candidate ->
                termExtractor(candidate).replace(" ", "").lowercase()
            }

        val deduplicatedByNormalization =
            normalizedGroups.values.map { group ->
                // 같은 정규화 형태 중 점수가 가장 높은 것 선택
                group.maxByOrNull { scoreExtractor(it) }!!
            }

        // 2단계: 부분 문자열 관계에 있는 후보 제거
        val normalizedForms =
            deduplicatedByNormalization
                .map { candidate ->
                    termExtractor(candidate).replace(" ", "").lowercase()
                }.toSet()

        return deduplicatedByNormalization.filter { candidate ->
            val normalized = termExtractor(candidate).replace(" ", "").lowercase()
            !isRedundantDecomposition(normalized, normalizedForms)
        }
    }

    /**
     * 해당 용어가 다른 용어의 불필요한 분해 형태인지 확인합니다.
     *
     * 예: "공휴일"이 있을 때 "공휴"는 불필요한 분해
     */
    private fun isRedundantDecomposition(
        normalized: String,
        allNormalized: Set<String>,
    ): Boolean {
        // 다른 정규화된 용어의 접두사이면서 그 용어가 존재하는 경우
        for (other in allNormalized) {
            if (other != normalized && other.length > normalized.length) {
                // normalized가 other의 시작 부분이거나 끝 부분인 경우
                if (other.startsWith(normalized) || other.endsWith(normalized)) {
                    return true
                }
            }
        }
        return false
    }

    private fun containsUrlPattern(term: String): Boolean = URL_PATTERNS.any { pattern -> term.contains(pattern) }

    private fun containsTechnicalNoisePattern(term: String) = TECHNICAL_NOISE_PATTERNS.any { it in term }

    private fun isCodeSyntaxPattern(term: String): Boolean = CODE_SYNTAX_PATTERNS.any { pattern -> term == pattern }

    private fun isHexPattern(component: String): Boolean = HEX_PATTERN.matches(component)

    /**
     * 한글 단일 음절 패턴 감지: 형태소 분석 오류로 인해 분리된 외래어/복합어
     * 예: ["버", "젼"] → true (버전의 잘못된 분리)
     * 예: ["스크린", "샷"] → true (스크린샷의 잘못된 분리)
     *
     * 규칙:
     * 1. 한글 1글자 컴포넌트가 있는 경우
     * 2. 단, 의미있는 한글 1글자(수사, 관형사 등)는 제외
     */
    private fun hasKoreanSingleSyllablePattern(components: List<String>): Boolean {
        // 의미있는 한글 1글자 (수사, 단위, 위치, 일반 명사 등)
        val meaningfulSingleChars = MEANINGFUL_SINGLE_CHARS

        for (component in components) {
            // 한글 1글자인지 확인
            if (component.length == 1 && isKoreanChar(component.first())) {
                // 의미있는 1글자가 아니면 분리 오류로 판단
                if (component !in meaningfulSingleChars) {
                    return true
                }
            }
        }
        return false
    }

    private fun isKoreanChar(char: Char): Boolean = char.code in 0xAC00..0xD7A3 || char.code in 0x3131..0x3163

    /**
     * 사전 등록이 필요할 수 있는 후보인지 판별합니다.
     *
     * 형태소 분석기가 외래어나 복합어를 잘못 분리한 경우를 감지합니다.
     * 이러한 용어는 Nori 사용자 사전에 등록하면 품질이 개선됩니다.
     *
     * @param components 용어의 컴포넌트 목록
     * @param npmi 용어의 NPMI 값
     * @return 사전 등록 필요 여부와 사유
     */
    fun detectDictionaryCandidate(
        components: List<String>,
        npmi: BigDecimal,
    ): DictionaryCandidateResult {
        // 기준 1: 매우 높은 NPMI (거의 항상 함께 출현)
        val highNpmi = npmi >= DICTIONARY_NPMI_THRESHOLD

        // 기준 2: 모든 컴포넌트가 한글인지 확인
        val allKorean = components.all { component -> component.all { isKoreanChar(it) } }

        // 기준 3: 한글 2-3음절 + 2-3음절 패턴 (외래어 분리 패턴)
        val hasLoanwordSplitPattern =
            components.size == 2 &&
                components.all { it.length in 2..3 } &&
                allKorean

        // 기준 4: 영문 외래어가 한글로 잘못 분리된 패턴 감지
        // 예: "마이 그레이" (migration), "쿠버 네티스" (kubernetes)
        val combinedTerm = components.joinToString("")
        val looksLikeLoanword =
            allKorean &&
                combinedTerm.length >= 4 &&
                !isLikelyNativeKorean(combinedTerm)

        return when {
            highNpmi && hasLoanwordSplitPattern && looksLikeLoanword ->
                DictionaryCandidateResult(
                    needsDictionary = true,
                    reason = DictionaryNeedReason.LOANWORD_SPLIT,
                    confidence = DictionaryConfidence.HIGH,
                )
            highNpmi && hasLoanwordSplitPattern ->
                DictionaryCandidateResult(
                    needsDictionary = true,
                    reason = DictionaryNeedReason.HIGH_COOCCURRENCE,
                    confidence = DictionaryConfidence.MEDIUM,
                )
            highNpmi && allKorean && components.any { it.length == 1 } ->
                DictionaryCandidateResult(
                    needsDictionary = true,
                    reason = DictionaryNeedReason.SINGLE_SYLLABLE_SPLIT,
                    confidence = DictionaryConfidence.MEDIUM,
                )
            else ->
                DictionaryCandidateResult(
                    needsDictionary = false,
                    reason = null,
                    confidence = null,
                )
        }
    }

    /**
     * 고유어/한자어처럼 보이는지 확인합니다.
     * 외래어는 특정 음절 패턴을 가지는 경향이 있습니다.
     */
    private fun isLikelyNativeKorean(term: String): Boolean {
        // 일반적인 한국어 단어 접미사
        val koreanSuffixes = listOf("하다", "되다", "시키다", "화", "성", "적", "자", "가")
        if (koreanSuffixes.any { term.endsWith(it) }) return true

        // 일반적인 한국어 단어 접두사
        val koreanPrefixes = listOf("대", "소", "신", "구", "재", "비", "불", "무")
        if (koreanPrefixes.any { term.startsWith(it) }) return true

        return false
    }
}

/**
 * 사전 등록 필요 여부 판별 결과
 */
data class DictionaryCandidateResult(
    val needsDictionary: Boolean,
    val reason: DictionaryNeedReason?,
    val confidence: DictionaryConfidence?,
)

/**
 * 사전 등록이 필요한 사유
 */
enum class DictionaryNeedReason(
    val description: String,
) {
    LOANWORD_SPLIT("외래어 분리 오류 - 형태소 분석기가 외래어를 잘못 분리"),
    HIGH_COOCCURRENCE("높은 동시 출현율 - 두 단어가 거의 항상 함께 사용됨"),
    SINGLE_SYLLABLE_SPLIT("단일 음절 분리 - 복합어가 잘못 분리되어 1음절 조각 발생"),
}

/**
 * 사전 등록 필요성의 확신도
 */
enum class DictionaryConfidence {
    HIGH,
    MEDIUM,
    LOW,
}
