package com.mkroo.termbase.infrastructure.slack

import com.mkroo.termbase.infrastructure.config.SlackProperties
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SlackSignatureVerifierTest :
    DescribeSpec({
        val signingSecret = "test-signing-secret"

        fun generateSignature(
            secret: String,
            timestamp: String,
            body: String,
        ): String {
            val baseString = "v0:$timestamp:$body"
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
            val hash = mac.doFinal(baseString.toByteArray()).joinToString("") { "%02x".format(it) }
            return "v0=$hash"
        }

        describe("SlackSignatureVerifier") {
            describe("verify") {
                it("유효한 서명을 검증한다") {
                    val properties = SlackProperties(signingSecret = signingSecret)
                    val verifier = SlackSignatureVerifier(properties)

                    val timestamp = Instant.now().epochSecond.toString()
                    val body = """{"type":"url_verification","challenge":"test"}"""
                    val signature = generateSignature(signingSecret, timestamp, body)

                    verifier.verify(timestamp, body, signature) shouldBe true
                }

                it("잘못된 서명은 검증에 실패한다") {
                    val properties = SlackProperties(signingSecret = signingSecret)
                    val verifier = SlackSignatureVerifier(properties)

                    val timestamp = Instant.now().epochSecond.toString()
                    val body = """{"type":"url_verification","challenge":"test"}"""
                    val wrongSignature = "v0=invalid_signature"

                    verifier.verify(timestamp, body, wrongSignature) shouldBe false
                }

                it("5분 이상 지난 타임스탬프는 검증에 실패한다") {
                    val properties = SlackProperties(signingSecret = signingSecret)
                    val verifier = SlackSignatureVerifier(properties)

                    val oldTimestamp = (Instant.now().epochSecond - 400).toString()
                    val body = """{"type":"url_verification","challenge":"test"}"""
                    val signature = generateSignature(signingSecret, oldTimestamp, body)

                    verifier.verify(oldTimestamp, body, signature) shouldBe false
                }

                it("유효하지 않은 타임스탬프 형식은 검증에 실패한다") {
                    val properties = SlackProperties(signingSecret = signingSecret)
                    val verifier = SlackSignatureVerifier(properties)

                    val invalidTimestamp = "not-a-number"
                    val body = """{"type":"url_verification","challenge":"test"}"""
                    val signature = "v0=any_signature"

                    verifier.verify(invalidTimestamp, body, signature) shouldBe false
                }

                it("signing secret이 비어있으면 검증에 실패한다") {
                    val properties = SlackProperties(signingSecret = "")
                    val verifier = SlackSignatureVerifier(properties)

                    val timestamp = Instant.now().epochSecond.toString()
                    val body = """{"type":"url_verification","challenge":"test"}"""
                    val signature = "v0=any_signature"

                    verifier.verify(timestamp, body, signature) shouldBe false
                }

                it("다른 signing secret으로 생성된 서명은 검증에 실패한다") {
                    val properties = SlackProperties(signingSecret = signingSecret)
                    val verifier = SlackSignatureVerifier(properties)

                    val timestamp = Instant.now().epochSecond.toString()
                    val body = """{"type":"url_verification","challenge":"test"}"""
                    val wrongSecretSignature = generateSignature("wrong-secret", timestamp, body)

                    verifier.verify(timestamp, body, wrongSecretSignature) shouldBe false
                }
            }
        }
    })
