# Geo-awareness Certification TODO

## Minimum Remaining Items

- Decide and document whether UGZ time applicability is supported. If supported, parse and enforce `applicability` windows; if not, document it as unsupported.
- Implement authoritative UGZ retrieval/update service integration when an approved endpoint/interface is available. Manual JSON import remains valid for testing and operator-provided official files.

## Implemented

- Explicit 3-second approach-warning logic for live UGZ proximity.
- Encrypted approach-warning evidence logs with distance to boundary, ground speed, calculated time to boundary, required warning time, configured threshold, and effective threshold.
