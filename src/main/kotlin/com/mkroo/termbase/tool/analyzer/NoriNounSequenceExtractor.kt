package com.mkroo.termbase.tool.analyzer

import com.mkroo.termbase.domain.service.NounSequence
import com.mkroo.termbase.domain.service.NounSequenceExtractor
import com.mkroo.termbase.domain.service.TokenWithOffset
import org.apache.lucene.analysis.ko.KoreanAnalyzer
import org.apache.lucene.analysis.ko.KoreanTokenizer
import org.apache.lucene.analysis.ko.POS
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute
import org.springframework.stereotype.Component
import java.io.StringReader

/**
 * Lucene Nori 기반 NounSequenceExtractor 구현체.
 *
 * Elasticsearch의 Nori 분석기와 동일한 엔진을 사용합니다.
 * C++ 기반 MeCab-ko-dic을 사용하므로 Komoran보다 빠릅니다.
 *
 * 후처리 필터:
 * - 한글이 포함되지 않은 토큰 제거
 * - 1글자 토큰 제거
 * - HTML 엔티티 제거
 */
@Component
class NoriNounSequenceExtractor : NounSequenceExtractor {
    // 명사 태그만 추출하도록 stopTags 설정 (명사가 아닌 것들을 제외)
    private val analyzer =
        KoreanAnalyzer(
            null, // userDict
            KoreanTokenizer.DecompoundMode.NONE, // 복합어 분해하지 않음 (주차장 -> 주차장 유지)
            NON_NOUN_TAGS, // 명사가 아닌 태그들을 stop tags로 설정
            false, // outputUnknownUnigrams
        )

    override fun extractWithOffsets(content: String): List<NounSequence> {
        if (content.isBlank()) return emptyList()

        val tokens = mutableListOf<TokenWithOffset>()

        analyzer.tokenStream("content", StringReader(content)).use { tokenStream ->
            val termAttr = tokenStream.addAttribute(CharTermAttribute::class.java)
            val offsetAttr = tokenStream.addAttribute(OffsetAttribute::class.java)

            tokenStream.reset()

            while (tokenStream.incrementToken()) {
                val term = termAttr.toString()
                val startOffset = offsetAttr.startOffset()
                val endOffset = offsetAttr.endOffset()

                // 후처리 필터 적용
                if (isValidToken(term)) {
                    tokens.add(TokenWithOffset(term, startOffset, endOffset))
                }
            }

            tokenStream.end()
        }

        // 인접한 토큰(이전 토큰의 endOffset과 현재 토큰의 startOffset 차이가 작은 경우)을 시퀀스로 그룹화
        val result = mutableListOf<NounSequence>()
        val buffer = mutableListOf<TokenWithOffset>()
        var lastEndOffset = -1

        for (token in tokens) {
            // 토큰 사이의 간격이 MAX_GAP 이하면 연속된 것으로 간주
            val gap = token.startOffset - lastEndOffset
            val isConsecutive = lastEndOffset == -1 || gap <= MAX_GAP

            if (isConsecutive) {
                buffer.add(token)
            } else {
                if (buffer.size >= MIN_SEQUENCE_LENGTH) {
                    result.add(NounSequence(buffer.toList()))
                }
                buffer.clear()
                buffer.add(token)
            }
            lastEndOffset = token.endOffset
        }

        if (buffer.size >= MIN_SEQUENCE_LENGTH) {
            result.add(NounSequence(buffer.toList()))
        }

        return result
    }

    /**
     * 유효한 토큰인지 확인
     * - 한글이 포함되어야 함
     * - 2글자 이상이어야 함
     * - HTML 엔티티가 아니어야 함
     */
    private fun isValidToken(term: String): Boolean {
        // 2글자 미만 제외
        if (term.length < 2) return false

        // 한글이 포함되어 있어야 함
        if (!term.any { it in '\uAC00'..'\uD7A3' || it in '\u3131'..'\u318E' }) return false

        // HTML 엔티티 패턴 제외 (ldquo, rdquo, nbsp 등)
        if (term in HTML_ENTITIES) return false

        return true
    }

    companion object {
        private const val MIN_SEQUENCE_LENGTH = 2

        // 토큰 사이 최대 허용 간격 (조사 "의" + 공백 = 2자, 여유분 포함 3자)
        private const val MAX_GAP = 3

        // 명사가 아닌 품사 태그들 (이것들을 제외하면 명사만 남음)
        private val NON_NOUN_TAGS =
            setOf(
                // 동사, 형용사
                POS.Tag.VV,
                POS.Tag.VA,
                POS.Tag.VX,
                POS.Tag.VCP,
                POS.Tag.VCN,
                // 부사
                POS.Tag.MAG,
                POS.Tag.MAJ,
                // 조사
                POS.Tag.JKS,
                POS.Tag.JKC,
                POS.Tag.JKG,
                POS.Tag.JKO,
                POS.Tag.JKB,
                POS.Tag.JKV,
                POS.Tag.JKQ,
                POS.Tag.JX,
                POS.Tag.JC,
                // 어미
                POS.Tag.EP,
                POS.Tag.EF,
                POS.Tag.EC,
                POS.Tag.ETN,
                POS.Tag.ETM,
                // 접사
                POS.Tag.XPN,
                POS.Tag.XSN,
                POS.Tag.XSV,
                POS.Tag.XSA,
                // 기호
                POS.Tag.SF,
                POS.Tag.SE,
                POS.Tag.SSO,
                POS.Tag.SSC,
                POS.Tag.SC,
                POS.Tag.SY,
                // 기타
                POS.Tag.IC,
                POS.Tag.UNA,
                POS.Tag.UNKNOWN,
                POS.Tag.SP, // 공백
            )

        // 제외할 HTML 엔티티
        private val HTML_ENTITIES =
            setOf(
                "ldquo",
                "rdquo",
                "lsquo",
                "rsquo",
                "nbsp",
                "amp",
                "lt",
                "gt",
                "quot",
                "mdash",
                "ndash",
                "hellip",
            )
    }
}
