package com.example.droneservicesapp.domain.geoawareness.verification

object GeoAwarenessVerificationChecklist {
    val cases: List<GeoAwarenessVerificationCase> = listOf(
        GeoAwarenessVerificationCase(
            id = "GA-001",
            title = "Dataset load - bundled dummy",
            purpose = "Verify bundled fallback geo-zone dataset loads.",
            preconditions = listOf("No imported datasets active."),
            steps = listOf(
                "Reset to bundled dummy dataset.",
                "Open Geo-awareness menu."
            ),
            expectedResult = "Bundled Rethymno dummy dataset active. Zones loaded = 8. Validation has 0 errors.",
            evidenceToCapture = listOf(
                "Dataset status screenshot.",
                "Exported DATASET_LOADED log."
            ),
            category = "Dataset"
        ),
        GeoAwarenessVerificationCase(
            id = "GA-002",
            title = "Dataset import - valid ED-style JSON",
            purpose = "Verify user can import a valid geo-zone JSON file.",
            preconditions = listOf("Valid ED-style JSON file available."),
            steps = listOf(
                "Tap Import geo-zone JSON.",
                "Select valid file."
            ),
            expectedResult = "Import succeeds. Dataset appears in loaded dataset list. Validation has 0 errors.",
            evidenceToCapture = listOf(
                "Loaded dataset screenshot.",
                "DATASET_IMPORT_SUCCEEDED log."
            ),
            category = "Dataset"
        ),
        GeoAwarenessVerificationCase(
            id = "GA-003",
            title = "Dataset import - invalid JSON rejection",
            purpose = "Verify malformed JSON is rejected without corrupting active dataset.",
            preconditions = listOf("Invalid JSON file available."),
            steps = listOf("Import invalid JSON."),
            expectedResult = "Import fails with readable error. Previous dataset remains active.",
            evidenceToCapture = listOf(
                "Error dialog screenshot.",
                "DATASET_IMPORT_FAILED log."
            ),
            category = "Dataset"
        ),
        GeoAwarenessVerificationCase(
            id = "GA-004",
            title = "Dataset validation warnings",
            purpose = "Verify warnings are visible but non-blocking.",
            preconditions = listOf("Dataset with warnings, such as duplicate IDs or unclosed rings."),
            steps = listOf(
                "Import dataset with warnings.",
                "Open validation details."
            ),
            expectedResult = "Import succeeds. Warnings visible. Errors = 0.",
            evidenceToCapture = listOf(
                "Validation details screenshot.",
                "DATASET_VALIDATED log."
            ),
            category = "Dataset"
        ),
        GeoAwarenessVerificationCase(
            id = "GA-005",
            title = "Geo-zone map rendering",
            purpose = "Verify loaded zones render on map.",
            preconditions = emptyList(),
            steps = listOf(
                "Enable Show geo-zones.",
                "Inspect map."
            ),
            expectedResult = "Zones visible and styled by restriction.",
            evidenceToCapture = listOf(
                "Map screenshot.",
                "GEO_LAYER_SHOWN log."
            ),
            category = "Map display"
        ),
        GeoAwarenessVerificationCase(
            id = "GA-006",
            title = "Geo-zone toggle",
            purpose = "Verify overlay visibility can be controlled.",
            preconditions = emptyList(),
            steps = listOf(
                "Toggle Show geo-zones OFF.",
                "Toggle ON."
            ),
            expectedResult = "Zones disappear and reappear without duplicate overlays.",
            evidenceToCapture = listOf(
                "Screenshots.",
                "GEO_LAYER_HIDDEN/GEO_LAYER_SHOWN logs."
            ),
            category = "Map display"
        ),
        GeoAwarenessVerificationCase(
            id = "GA-007",
            title = "Zone detail dialog",
            purpose = "Verify tapping a zone shows readable details.",
            preconditions = emptyList(),
            steps = listOf("Tap visible zone."),
            expectedResult = "Dialog shows zone name, restriction, message, authority, altitude.",
            evidenceToCapture = listOf("Dialog screenshot."),
            category = "Map display"
        ),
        GeoAwarenessVerificationCase(
            id = "GA-008",
            title = "Planning prohibited conflict",
            purpose = "Verify mission planning detects prohibited zone intersection.",
            preconditions = listOf("Dataset includes known prohibited zone or use bundled Rethymno Hospital/Fortezza."),
            steps = listOf(
                "Draw/plan mission over prohibited zone."
            ),
            expectedResult = "Planning GEO status shows PROHIBITED. Conflict details list prohibited zone.",
            evidenceToCapture = listOf(
                "Planning chip screenshot.",
                "PLANNING_CONFLICT_DETECTED log."
            ),
            category = "Planning"
        ),
        GeoAwarenessVerificationCase(
            id = "GA-009",
            title = "Planning clear mission",
            purpose = "Verify mission outside zones is clear.",
            preconditions = emptyList(),
            steps = listOf("Draw/plan mission outside all loaded zones."),
            expectedResult = "Planning GEO status shows CLEAR.",
            evidenceToCapture = listOf(
                "Planning screenshot.",
                "PLANNING_CHECKED log."
            ),
            category = "Planning"
        ),
        GeoAwarenessVerificationCase(
            id = "GA-010",
            title = "Upload guard - prohibited blocked",
            purpose = "Verify prohibited conflict blocks upload.",
            preconditions = emptyList(),
            steps = listOf(
                "Plan mission over prohibited zone.",
                "Tap upload."
            ),
            expectedResult = "Upload blocked dialog. MAVLink upload does not start.",
            evidenceToCapture = listOf(
                "Dialog screenshot.",
                "UPLOAD_BLOCKED log."
            ),
            category = "Upload guard"
        ),
        GeoAwarenessVerificationCase(
            id = "GA-011",
            title = "Upload guard - authorization acknowledgement",
            purpose = "Verify authorization-required zone requires acknowledgement.",
            preconditions = emptyList(),
            steps = listOf(
                "Plan mission over authorization zone.",
                "Tap upload.",
                "Cancel, then retry and Proceed."
            ),
            expectedResult = "Cancel stops upload. Proceed continues upload.",
            evidenceToCapture = listOf(
                "Dialog screenshots.",
                "UPLOAD_ACK_REQUIRED, UPLOAD_CANCELLED, UPLOAD_ACKNOWLEDGED logs."
            ),
            category = "Upload guard"
        ),
        GeoAwarenessVerificationCase(
            id = "GA-012",
            title = "Live geo warning - prohibited",
            purpose = "Verify live drone/test position inside prohibited zone triggers live warning.",
            preconditions = listOf("Geo Test mode or real drone telemetry available."),
            steps = listOf(
                "Enable Geo Test if needed.",
                "Tap/place drone inside prohibited zone."
            ),
            expectedResult = "LIVE GEO shows PROHIBITED.",
            evidenceToCapture = listOf(
                "Map screenshot.",
                "LIVE_ZONE_ENTERED log."
            ),
            category = "Live warning"
        ),
        GeoAwarenessVerificationCase(
            id = "GA-013",
            title = "Live geo warning - clear",
            purpose = "Verify live warning clears when drone leaves zone.",
            preconditions = emptyList(),
            steps = listOf("Move/test position outside zones."),
            expectedResult = "LIVE GEO shows CLEAR.",
            evidenceToCapture = listOf(
                "Map screenshot.",
                "LIVE_ZONE_EXITED log."
            ),
            category = "Live warning"
        ),
        GeoAwarenessVerificationCase(
            id = "GA-014",
            title = "Dataset update/replace",
            purpose = "Verify imported dataset can be updated without losing previous valid data on failure.",
            preconditions = emptyList(),
            steps = listOf(
                "Update imported dataset with valid file.",
                "Then try update with invalid file."
            ),
            expectedResult = "Valid update succeeds. Invalid update fails and old dataset remains active.",
            evidenceToCapture = listOf(
                "Dataset row screenshot.",
                "DATASET_UPDATE_SUCCEEDED and DATASET_UPDATE_FAILED logs."
            ),
            category = "Dataset"
        ),
        GeoAwarenessVerificationCase(
            id = "GA-015",
            title = "Stale dataset warning",
            purpose = "Verify stale datasets are identified.",
            preconditions = emptyList(),
            steps = listOf(
                "Use simulated old metadata or lowered threshold in debug.",
                "Refresh status."
            ),
            expectedResult = "Dataset marked stale. Health shows STALE or equivalent.",
            evidenceToCapture = listOf(
                "Health screenshot.",
                "DATASET_MARKED_STALE log."
            ),
            category = "Dataset"
        ),
        GeoAwarenessVerificationCase(
            id = "GA-016",
            title = "Event log export",
            purpose = "Verify geo-awareness logs can be exported.",
            preconditions = emptyList(),
            steps = listOf(
                "Trigger several geo events.",
                "Export logs."
            ),
            expectedResult = "JSON export created/share sheet opens. Export contains expected event types.",
            evidenceToCapture = listOf("Exported JSON file."),
            category = "Logs/evidence"
        ),
        GeoAwarenessVerificationCase(
            id = "GA-017",
            title = "Diagnostics test screen",
            purpose = "Verify diagnostics tests run without drone.",
            preconditions = emptyList(),
            steps = listOf(
                "Open Diagnostics.",
                "Run geo-awareness tests."
            ),
            expectedResult = "Failed count = 0. GEO_TEST_RUN logged.",
            evidenceToCapture = listOf(
                "Diagnostics screenshot.",
                "Exported log."
            ),
            category = "Diagnostics"
        ),
        GeoAwarenessVerificationCase(
            id = "GA-018",
            title = "Geo-awareness unavailable/degraded status",
            purpose = "Verify user is warned when geo-awareness data is unavailable or degraded.",
            preconditions = emptyList(),
            steps = listOf(
                "Simulate unavailable/invalid dataset or use validation error case."
            ),
            expectedResult = "Health shows UNAVAILABLE/DEGRADED. Upload requires acknowledgement or blocks according to policy.",
            evidenceToCapture = listOf(
                "Health screenshot.",
                "Upload warning screenshot."
            ),
            category = "Dataset"
        )
    )
}
