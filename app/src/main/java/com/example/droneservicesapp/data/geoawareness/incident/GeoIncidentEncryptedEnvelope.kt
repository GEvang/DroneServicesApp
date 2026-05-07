package com.example.droneservicesapp.data.geoawareness.incident

data class GeoIncidentEncryptedEnvelope(
    val format: String,
    val keyId: String,
    val algorithm: String,
    val timestampMillis: Long,
    val encryptedKeyBase64: String,
    val ivBase64: String,
    val ciphertextBase64: String
)
