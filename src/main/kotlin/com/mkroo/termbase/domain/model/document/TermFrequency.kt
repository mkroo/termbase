package com.mkroo.termbase.domain.model.document

data class TermFrequency(
    val term: String,
    val count: Long,
    val score: Double,
    val posTag: String? = null,
) {
    fun posTagDisplayName(): String? = posTag?.let { POS_TAG_NAMES[it] ?: it }

    companion object {
        private val POS_TAG_NAMES =
            mapOf(
                // 체언 (명사류)
                "NNG" to "일반명사",
                "NNP" to "고유명사",
                "NNB" to "의존명사",
                "NR" to "수사",
                "NP" to "대명사",
                // 용언 (동사/형용사류)
                "VV" to "동사",
                "VA" to "형용사",
                "VX" to "보조용언",
                "VCP" to "긍정지정사",
                "VCN" to "부정지정사",
                // 관형사/부사
                "MM" to "관형사",
                "MAG" to "일반부사",
                "MAJ" to "접속부사",
                // 감탄사
                "IC" to "감탄사",
                // 조사
                "JKS" to "주격조사",
                "JKC" to "보격조사",
                "JKG" to "관형격조사",
                "JKO" to "목적격조사",
                "JKB" to "부사격조사",
                "JKV" to "호격조사",
                "JKQ" to "인용격조사",
                "JX" to "보조사",
                "JC" to "접속조사",
                // 어미
                "EP" to "선어말어미",
                "EF" to "종결어미",
                "EC" to "연결어미",
                "ETN" to "명사형어미",
                "ETM" to "관형형어미",
                // 접사
                "XPN" to "체언접두사",
                "XSN" to "명사파생접미사",
                "XSV" to "동사파생접미사",
                "XSA" to "형용사파생접미사",
                "XR" to "어근",
                // 부호
                "SF" to "마침표",
                "SE" to "줄임표",
                "SSO" to "여는괄호",
                "SSC" to "닫는괄호",
                "SC" to "구분자",
                "SY" to "기타기호",
                "SL" to "외국어",
                "SH" to "한자",
                "SN" to "숫자",
                "SP" to "공백",
                // 기타
                "UNKNOWN" to "알수없음",
            )
    }
}
