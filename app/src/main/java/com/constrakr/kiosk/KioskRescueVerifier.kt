package com.constrakr.kiosk

import android.util.Base64
import com.constrakr.BuildConfig
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.json.JSONObject
import java.nio.charset.StandardCharsets

object KioskRescueVerifier {
    private const val PREFIX = "CR1"
    private const val TYPE = "constrakr_rescue"
    private const val VERSION = 1

    data class VerifiedPayload(
        val deviceId: String,
        val nonce: String,
        val jti: String,
        val issuedAtSeconds: Long,
        val expiresAtSeconds: Long
    )

    fun verify(token: String): Result<VerifiedPayload> = runCatching {
        val parts = token.trim().split('.')
        require(parts.size == 3 && parts[0] == PREFIX) { "Invalid rescue token format" }

        val payloadBytes = decodeUrlSafe(parts[1])
        val signature = decodeUrlSafe(parts[2])
        val payloadJson = JSONObject(String(payloadBytes, StandardCharsets.UTF_8))

        require(payloadJson.optInt("v") == VERSION) { "Unsupported token version" }
        require(payloadJson.optString("typ") == TYPE) { "Invalid token type" }

        val publicKeyBytes = decodeUrlSafe(BuildConfig.RESCUE_PUBLIC_KEY_B64)
        val verifier = Ed25519Signer()
        verifier.init(false, Ed25519PublicKeyParameters(publicKeyBytes, 0))
        verifier.update(payloadBytes, 0, payloadBytes.size)
        require(verifier.verifySignature(signature)) { "Invalid rescue token signature" }

        val canonical = canonicalJson(payloadJson)
        require(canonical.toByteArray(StandardCharsets.UTF_8).contentEquals(payloadBytes)) {
            "Rescue token payload is not canonical"
        }

        VerifiedPayload(
            deviceId = payloadJson.getString("device_id"),
            nonce = payloadJson.getString("nonce"),
            jti = payloadJson.getString("jti"),
            issuedAtSeconds = payloadJson.getLong("iat"),
            expiresAtSeconds = payloadJson.getLong("exp")
        )
    }

    private fun decodeUrlSafe(value: String): ByteArray {
        val padding = "=".repeat((4 - value.length % 4) % 4)
        return Base64.decode(value + padding, Base64.URL_SAFE)
    }

    /** Match server-side json.dumps(..., sort_keys=True, separators=(',', ':')) */
    private fun canonicalJson(json: JSONObject): String {
        val keys = json.keys().asSequence().toList().sorted()
        val body = keys.joinToString(",") { key ->
            val raw = json.get(key)
            val encoded = when (raw) {
                is Number -> if (raw is Int || raw is Long) raw.toString() else raw.toString()
                is String -> JSONObject.quote(raw)
                else -> JSONObject.wrap(raw)?.toString() ?: "null"
            }
            JSONObject.quote(key) + ":" + encoded
        }
        return "{$body}"
    }
}
