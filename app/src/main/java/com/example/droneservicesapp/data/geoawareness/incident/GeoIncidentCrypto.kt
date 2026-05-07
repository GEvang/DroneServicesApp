package com.example.droneservicesapp.data.geoawareness.incident

import android.util.Base64
import org.json.JSONObject
import java.security.PublicKey
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

object GeoIncidentCrypto {
    fun encryptEvent(
        eventJson: JSONObject,
        publicKey: PublicKey,
        keyId: String
    ): GeoIncidentEncryptedEnvelope {
        val aesKeyGenerator = KeyGenerator.getInstance("AES").apply {
            init(256, SecureRandom())
        }
        val aesKey = aesKeyGenerator.generateKey()
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }

        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        }
        val ciphertext = aesCipher.doFinal(eventJson.toString().toByteArray(Charsets.UTF_8))

        val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding").apply {
            init(Cipher.ENCRYPT_MODE, publicKey)
        }
        val encryptedKey = rsaCipher.doFinal(aesKey.encoded)

        return GeoIncidentEncryptedEnvelope(
            format = "geo-incident-log-v1",
            keyId = keyId,
            algorithm = "RSA-OAEP-SHA256+A256GCM",
            timestampMillis = eventJson.getLong("timestampMillis"),
            encryptedKeyBase64 = Base64.encodeToString(encryptedKey, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        )
    }
}
