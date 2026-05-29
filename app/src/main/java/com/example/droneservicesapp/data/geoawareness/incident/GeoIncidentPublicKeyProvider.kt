package com.example.droneservicesapp.data.geoawareness.incident

import android.util.Base64
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec

object GeoIncidentPublicKeyProvider {
    const val KEY_ID = "company-geo-incident-key-v1"

    private const val PUBLIC_KEY_PEM = """
    -----BEGIN PUBLIC KEY-----
    MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqAoOYlFTux1thwl59Eqa
    qqJSOA8ZyaIMBaOo+iYfl7A0zPnRwKk3802PGr9QWnyBOHBjoU4yR90HtzaeNqxM
    lftLLUQoh3jt4+TRC5ZAqSMUUQxjtNmXEYS+OBJEEOgeIGGxN+dBsr2MH2/xiBDF
    Q6NR+Dh7Hzp6T5/Ux1njTs+LXcOjDW3htIL61IGff6r1qR4KkUnhYPjurufUrNsg
    UawL2elt0nuCrCSrkEdV/c8Q0ohzCCxGhX1V9pRL3BtEsaGaeRs9KzgsuR9UO8IW
    x1TFOANfQw26wBhB44l/qpwGc04QxOahaGmBhj3FLMrW8auPlT5SdwGvEUTbF+AU
    +wIDAQAB
    -----END PUBLIC KEY-----
    """

    fun getPublicKey(): PublicKey {
        val sanitized = PUBLIC_KEY_PEM
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
        val keyBytes = Base64.decode(sanitized, Base64.DEFAULT)
        val keySpec = X509EncodedKeySpec(keyBytes)
        return KeyFactory.getInstance("RSA").generatePublic(keySpec)
    }
}
