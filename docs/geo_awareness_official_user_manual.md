# Aigaio Skytech Drone Services App
# Geo-awareness Official User Manual

Document owner: Aigaio Skytech  
Document type: Operator user manual  
Feature: Geo-awareness  
Applies to: Drone Services App, Android application  
Prepared on: 2026-06-04

## 1. Purpose

The Geo-awareness section helps the operator load, review, display, and monitor geographic flight restriction data before and during drone operations.

The feature supports:

- Importing geo-zone JSON datasets.
- Reviewing dataset health, validation, and freshness.
- Displaying loaded geo-zones on the mission map.
- Checking planned mission geometry against loaded geo-zones.
- Checking the live drone position against loaded geo-zones.
- Warning, blocking, or requiring acknowledgement during mission upload when a geo-zone conflict is detected.
- Exporting internal encrypted geo incident logs when available.

Geo-awareness is an operational aid. It does not replace official airspace checks, authority approvals, pilot judgement, or legal compliance. Operators must verify current official restrictions with DAGR/HCAA or the responsible authority before flight.

## 2. Accessing Geo-awareness

1. Open the Drone Services App.
2. Open the main navigation drawer.
3. Select **Geo-awareness**.

The Geo-awareness screen contains the following main areas:

- **Health**
- **Live status**
- **Zone overlay**
- **Dataset source**
- **Loaded datasets**
- **Dataset validation**
- **Internal**

The map screen also shows geo-awareness status chips while planning and flying.

## 3. Required Dataset

Geo-awareness is enabled only after at least one valid geo-zone JSON dataset has been imported.

If no dataset is loaded, the screen shows:

- **Current source: No dataset loaded**
- **Loaded datasets: 0**
- **Total zones: 0**
- Health state **UNAVAILABLE**

In this state, the app cannot provide geo-zone protection from loaded data. Import a valid dataset before relying on geo-awareness.

### Dataset File Requirements

The imported file must be valid UTF-8 JSON and must not exceed 5 MB.

The dataset must include a root `features` array. Each parsed zone must contain usable geometry. The app validates imported data and rejects datasets with validation errors.

Warnings may still allow import, but the dataset health may become **DEGRADED** and mission upload may require acknowledgement.

## 4. Importing a Geo-zone Dataset

1. Go to **Geo-awareness**.
2. Select **Import geo-zone JSON**.
3. Choose the JSON file from the Android file picker.
4. Wait for the import result.

If import succeeds, the app displays **Import complete** and reports the number of loaded zones. If validation warnings exist, the dialog also reports the warning count.

If import fails, the app displays **Import failed** with validation error details where available. Correct the file and import again.

After import, confirm the following:

- Health is **AVAILABLE** or otherwise understood and acknowledged.
- **Loaded datasets** is greater than 0.
- **Total zones** matches the expected dataset size.
- **Dataset validation** does not show errors.
- The dataset contents validate successfully and match the expected operational source.

## 5. Updating a Dataset

Imported datasets appear under **Loaded datasets**.

To update one dataset:

1. Locate the dataset record.
2. Select **Update**.
3. Choose the replacement JSON file.
4. Confirm that the updated dataset loads successfully.

The app validates the replacement file before saving it. If validation errors are found, the existing dataset is not replaced.

## 6. Removing Datasets

To remove one imported dataset:

1. Locate the dataset record under **Loaded datasets**.
2. Select **Remove**.
3. Confirm removal.

To remove all imported datasets:

1. Select **Remove all imported datasets**.
2. Confirm **Remove all**.

After all imported datasets are removed, geo-awareness returns to **UNAVAILABLE** until a new valid dataset is imported.

## 7. Refreshing Status

Select **Refresh status** to reload the current imported datasets and recalculate:

- Dataset source state.
- Loaded dataset count.
- Total zone count.
- Validation counts.
- Health state.
- Stale dataset status.

Use this after updating files, troubleshooting, or before an operational check.

## 8. Understanding Health States

The **Health** chip summarizes whether geo-awareness data can be used normally.

| Health state | Meaning | Operator action |
| --- | --- | --- |
| **AVAILABLE** | A valid dataset is loaded and current. | Continue normal planning, while still verifying official restrictions. |
| **DEGRADED** | Data has validation warnings. | Review validation details and confirm whether the dataset is acceptable. Upload may require acknowledgement. |
| **STALE** | One or more datasets may be older than the configured freshness period. | Update the dataset before flight or acknowledge the risk only when operationally justified. |
| **UNAVAILABLE** | No usable geo-awareness dataset is loaded, or loading failed. | Import a valid dataset before relying on geo-awareness. |

The app treats stale, degraded, and unavailable data as health conditions that require operator attention. Dummy or test data is shown as a validation warning and dataset type, but it does not by itself change the health state.

## 9. Reviewing Dataset Validation

The **Dataset validation** area shows:

- **Validation: OK**
- **Validation: Warnings**
- **Validation: Errors**

It also shows counts for errors, warnings, and information items.

Select **View validation details** to inspect validation issues. The app may report issues such as:

- Missing `features` array.
- Invalid latitude or longitude.
- Invalid circle radius.
- Invalid polygon ring.
- Missing zone geometry.
- Invalid altitude range.
- Unknown altitude unit.
- Unknown lower or upper vertical reference.
- Unknown restriction type.
- Missing metadata such as version or country.
- Duplicate zone IDs.

Datasets with validation errors are rejected during import or update. Datasets with warnings may load, but the operator must review the warnings before flight.

## 10. Showing Geo-zones on the Map

The **Zone overlay** switch controls whether loaded geo-zones appear on the map.

To show zones:

1. Go to **Geo-awareness**.
2. Enable **Show geo-zones on map**.
3. Return to the map.

The map renders supported zone geometry as polygons or circles.

Zone colors:

| Restriction | Map color |
| --- | --- |
| **PROHIBITED** | Red |
| **REQ_AUTHORISATION** | Orange |
| **CONDITIONAL** | Yellow |
| **INFORMATION** | Blue |
| **UNKNOWN** | Gray |

Tap a displayed zone on the map to view details including:

- Zone name.
- Restriction.
- Message.
- Authority, when available.
- Altitude limits, when available.

If several zones overlap at the tapped location, the app shows a list ordered by restriction priority and area.

## 11. Planning Status on the Map

During mission planning, the map shows a planning geo-awareness chip.

Planning chip labels:

| Label | Meaning |
| --- | --- |
| **GEO: CLEAR** | No conflicts detected for the current mission plan. |
| **GEO: PROHIBITED** | The mission intersects a prohibited zone. |
| **GEO: AUTH REQUIRED** | The mission intersects an authorization-required zone. |
| **GEO: CONDITIONAL** | The mission intersects a conditional zone. |
| **GEO: INFO** | The mission intersects an information zone. |
| **GEO: DEGRADED** | Dataset health is degraded. |
| **GEO: STALE** | Dataset may be stale. |
| **GEO: UNAVAILABLE** | Geo-awareness data is unavailable. |

Tap the planning chip to view details. Details include the highest restriction, upload status, acknowledgement requirement, affected zones, conflict type, and zone messages.

The app checks:

- Mission area intersection with zones.
- Survey path intersection with zones.
- Route waypoint position inside zones.
- Mission altitude overlap with zone altitude limits, when altitude limits are present.

### Vertical Limits

Geo-zone vertical limits may use either:

- **AGL**: height above ground level.
- **AMSL**: altitude above mean sea level.

The app preserves the dataset vertical reference and altitude unit for each zone geometry. Meter values are used directly and foot values are converted to meters for checks.

During planning, the app compares the mission height with AGL limits. If a zone uses AMSL limits and no mission AMSL altitude is available, the app treats the vertical filter conservatively and does not clear the conflict only because AMSL is missing.

During live monitoring, the app uses drone relative altitude for AGL-style checks and MAVLink `GLOBAL_POSITION_INT.alt` telemetry for AMSL checks when available.

## 12. Upload Guard Behavior

When the operator uploads a mission, the app evaluates the current mission plan against loaded geo-zones.

### Prohibited Zone

If the mission intersects a **PROHIBITED** zone:

- Upload is blocked.
- The app shows **Geo-awareness upload blocked**.
- The operator must cancel the upload and revise the plan or verify official restrictions outside the app.

### Authorization-required Zone

If the mission intersects a **REQ_AUTHORISATION** zone:

- Upload is allowed only after pilot confirmation.
- The app shows **Confirm UGZ authorization**.
- The dialog lists the affected UGZ identifiers, zone names, and available authority information.
- Select **Confirm authorization** only after the required authorization or notification has been completed with the responsible authority.
- Select **Cancel** to stop the upload.

The confirmation scope is the current flight only. The app logs the affected UGZ identifiers, the pilot declaration, and the current-flight scope. The confirmation is reset automatically when the drone is disarmed at the end of the flight.

### Conditional or Information Zone

If the mission intersects **CONDITIONAL** or **INFORMATION** zones:

- The app shows a geo-awareness notice.
- Select **Continue** only after reviewing the restriction message.
- Select **Cancel** to stop the upload.

### Data Health Warning

If there are no plan conflicts but geo-awareness health is stale, degraded, or unavailable, the app may show a health warning before upload.

Select **Continue** only when the operator has verified official restrictions independently.

## 13. Live Geo-awareness

The **Live status** chip monitors the drone position using telemetry.

Live status labels:

| Label | Meaning |
| --- | --- |
| **LIVE GEO: NO POS** | No usable drone position is available. |
| **LIVE GEO: CLEAR** | Drone is not inside any loaded geo-zone and no near-zone warning applies. |
| **LIVE GEO: PROHIBITED** | Drone is inside a prohibited zone. |
| **LIVE GEO: AUTH ZONE** | Drone is inside an authorization-required zone. |
| **LIVE GEO: CONDITIONAL** | Drone is inside a conditional zone. |
| **LIVE GEO: INFO** | Drone is inside an information zone. |
| **LIVE GEO: NEAR PROHIBITED** | Drone is outside but within the near-zone threshold of a prohibited zone. |
| **LIVE GEO: NEAR AUTH ZONE** | Drone is outside but within the near-zone threshold of an authorization-required zone. |
| **LIVE GEO: NEAR CONDITIONAL** | Drone is outside but within the near-zone threshold of a conditional zone. |
| **LIVE GEO: NEAR UNKNOWN** | Drone is outside but within the near-zone threshold of an unknown zone. |

The near-zone warning threshold is the greater of 100 meters or the current ground speed multiplied by 3 seconds. This provides an explicit 3-second approach-warning rule for live UGZ boundary proximity when ground speed telemetry is available.

Tap the live status chip to see details. If the drone is inside zones, the app lists up to five zones and their restrictions. If a near-zone warning applies, the app shows the nearest zone, restriction, distance, threshold, dataset, and message.

If live status shows **NO POS**, verify telemetry and GPS before relying on live geo-awareness.

## 14. Event and Incident Records

The app records geo-awareness events internally, including dataset import, update, removal, stale dataset detection, map layer changes, upload guard actions, UGZ authorization confirmations, authorization resets, and live geo-awareness changes.

The current operator screen exposes an **Internal** section for encrypted geo incident logs.

Encrypted geo-awareness event logs are retained in app-private internal storage for at least 90 days. They are rotated by date, stored encrypted, and are not deleted by export. The app cleanup process removes only logs older than 90 days.

To export encrypted incident logs:

1. Go to **Geo-awareness**.
2. Scroll to **Internal**.
3. Select **Export encrypted geo incident logs**.
4. Choose the share destination.

If no encrypted incident logs exist, the app reports that no encrypted geo incident logs are stored on the device.

Encrypted incident logs are intended for internal/debug handling by authorized personnel. Normal user-facing actions do not clear retained logs.

## 15. Recommended Pre-flight Procedure

Before every operation:

1. Import or update the latest official geo-zone JSON dataset.
2. Open **Geo-awareness**.
3. Select **Refresh status**.
4. Confirm health is **AVAILABLE**.
5. Confirm the dataset is valid, current, and from the expected operational source.
6. Review validation details if any warnings appear.
7. Enable **Show geo-zones on map**.
8. Review the mission area, route, and survey path on the map.
9. Tap any relevant displayed zones and read their messages.
10. Confirm the planning chip state.
11. If upload warnings appear, follow the app prompt and company authorization procedure.
12. During flight, monitor **Live geo-awareness** and telemetry.

## 16. Operator Responsibilities

The operator is responsible for:

- Using current official restriction data.
- Verifying DAGR/HCAA or responsible authority requirements before flight.
- Confirming required authorization before proceeding in authorization-required zones.
- Avoiding upload and flight in prohibited zones unless legally permitted by the responsible authority and company procedure.
- Investigating stale, degraded, or unavailable data states.
- Preserving exported incident evidence when required by company procedure.

## 17. Troubleshooting

| Problem | Likely cause | Action |
| --- | --- | --- |
| Health is **UNAVAILABLE** | No valid dataset is loaded. | Import a valid geo-zone JSON file. |
| Import fails | File is invalid, empty, larger than 5 MB, missing `features`, or has validation errors. | Review the error dialog, correct the file, and import again. |
| Dataset shows **STALE** | Imported dataset update time is older than the freshness threshold. | Import or update with the latest official dataset. |
| Dataset shows **DEGRADED** | Dataset has validation warnings. | Review validation details and verify the source. |
| Zones do not appear on the map | Overlay switch is off or no zones are loaded. | Enable **Show geo-zones on map** and confirm datasets are loaded. |
| Live status shows **NO POS** | No usable telemetry position is available. | Verify drone connection, GPS fix, and telemetry. |
| Upload is blocked | Mission intersects a prohibited zone. | Revise mission or verify official authority requirements outside the app. |
| Upload requires acknowledgement | Mission intersects an authorization-required zone or data health warning applies. | Confirm only after official authorization or restriction verification. |

## 18. Important Limitations

- Geo-awareness depends on the imported dataset. Missing, stale, invalid, or incorrect source data affects results.
- The app validates structure and geometry but cannot guarantee legal completeness of the dataset.
- The app uses available mission height, drone relative altitude, AMSL telemetry, and zone altitude limits where present.
- True terrain-derived AGL requires a terrain source and is not currently calculated beyond mission height or drone relative altitude.
- Live geo-awareness depends on valid drone telemetry and GPS position.
- The near-zone threshold is the greater of 100 meters or current ground speed multiplied by 3 seconds.
- Automatic authoritative DAGR/API retrieval is not implemented.
- Operators must verify official restrictions before flight regardless of app status.
