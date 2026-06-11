# Geo-awareness Certification TODO

## Minimum Remaining Items

- Implement the mandatory approach-warning rule explicitly: warn early enough that the UA has at least 3 seconds before UGZ boundary breach.
- Log approach-warning evidence: distance to boundary, ground speed, calculated time to boundary, required warning time, and threshold used.
- Decide and document whether UGZ time applicability is supported. If supported, parse and enforce `applicability` windows; if not, document it as unsupported.
- Implement authoritative UGZ retrieval/update service integration when an approved endpoint/interface is available. Manual JSON import remains valid for testing and operator-provided official files.
