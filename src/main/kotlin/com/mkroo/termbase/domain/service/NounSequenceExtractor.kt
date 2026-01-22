package com.mkroo.termbase.domain.service

/**
 * 토큰과 원문 위치 정보
 */
data class TokenWithOffset(
    val term: String,
    val startOffset: Int,
    val endOffset: Int,
)

/**
 * 연속된 명사 시퀀스와 원문 정보
 */
data class NounSequence(
    val tokens: List<TokenWithOffset>,
) {
    /**
     * 토큰 목록을 문자열 리스트로 반환 (하위 호환성)
     */
    fun toTermList(): List<String> = tokens.map { it.term }

    /**
     * 원문에서 특정 토큰 범위의 텍스트를 추출합니다.
     * 토큰 사이의 조사("의" 등)를 보존합니다.
     *
     * @param content 원문 텍스트
     * @param fromIndex 시작 토큰 인덱스 (포함)
     * @param toIndex 끝 토큰 인덱스 (포함)
     * @return 원문에서 추출된 구문
     */
    fun getOriginalPhrase(
        content: String,
        fromIndex: Int,
        toIndex: Int,
    ): String {
        require(fromIndex in tokens.indices) { "fromIndex out of range: $fromIndex" }
        require(toIndex in tokens.indices) { "toIndex out of range: $toIndex" }
        require(fromIndex <= toIndex) { "fromIndex must be <= toIndex" }

        val startOffset = tokens[fromIndex].startOffset
        val endOffset = tokens[toIndex].endOffset

        return content.substring(startOffset, endOffset)
    }

    /**
     * 두 개의 연속된 토큰에 대한 원문 구문을 추출합니다.
     */
    fun getBigramPhrase(
        content: String,
        index: Int,
    ): String = getOriginalPhrase(content, index, index + 1)
}

/**
 * 텍스트에서 연속된 명사 시퀀스를 추출하는 인터페이스.
 *
 * 형태소 분석을 통해 연속된 명사(NNG, NNP, SL) 구간을 추출합니다.
 * 예: "공유 주차장에서 결제를 진행했습니다" → [[공유, 주차장]]
 */
interface NounSequenceExtractor {
    /**
     * 텍스트에서 연속된 명사 시퀀스를 추출합니다.
     *
     * @param content 분석할 텍스트
     * @return 각 명사 시퀀스의 리스트 (길이 2 이상인 시퀀스만 포함)
     */
    fun extractWithOffsets(content: String): List<NounSequence>

    /**
     * 텍스트에서 연속된 명사 시퀀스를 추출합니다 (하위 호환성).
     *
     * @param content 분석할 텍스트
     * @return 각 명사 시퀀스의 리스트 (토큰 문자열만 포함)
     */
    fun extract(content: String): List<List<String>> = extractWithOffsets(content).map { it.toTermList() }
}
