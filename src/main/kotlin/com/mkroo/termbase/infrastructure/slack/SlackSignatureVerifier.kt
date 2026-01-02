package com.mkroo.termbase.infrastructure.slack

import com.mkroo.termbase.infrastructure.config.SlackProperties
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

@Component
class SlackSignatureVerifier(
    private val slackProperties: SlackProperties,
) {
    companion object {
        private const val VERSION = "v0"
        private const val MAX_TIMESTAMP_DIFF_SECONDS = 60 * 5L
    }

    fun verify(
        timestamp: String,
        body: String,
        signature: String,
    ): Boolean {
        if (slackProperties.signingSecret.isBlank()) {
            return false
        }

        val requestTimestamp = timestamp.toLongOrNull() ?: return false
        val currentTimestamp = Instant.now().epochSecond

        if (abs(currentTimestamp - requestTimestamp) > MAX_TIMESTAMP_DIFF_SECONDS) {
            return false
        }

        val baseString = "$VERSION:$timestamp:$body"
        val expectedSignature = "$VERSION=${hmacSha256(slackProperties.signingSecret, baseString)}"

        return MessageDigest.isEqual(
            expectedSignature.toByteArray(),
            signature.toByteArray(),
        )
    }

    private fun hmacSha256(
        secret: String,
        data: String,
    ): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
