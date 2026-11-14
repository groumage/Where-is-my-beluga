package com.whereismybeluga.data

private val TOULOUSE_BLAGNAC = HomeBase("Toulouse-Blagnac", Coordinate(43.6294, 1.3638))

/**
 * Fleet of Airbus Transport International's "Beluga" outsize cargo aircraft,
 * used to ferry aircraft parts (wings, fuselage sections...) between Airbus
 * plants. ICAO24 hex codes were taken from OpenSky's aircraft metadata
 * database, matched on operator = "Airbus Transport International".
 * Only the BelugaXL (A330-743L) in service are tracked; the original
 * BelugaST (A300-600ST) fleet is retired.
 */
val BELUGA_FLEET: List<BelugaAircraft> = listOf(
    BelugaAircraft("395d66", "F-GXLG", "Beluga XL1", TOULOUSE_BLAGNAC),
    BelugaAircraft("395d67", "F-GXLH", "Beluga XL2", TOULOUSE_BLAGNAC),
    BelugaAircraft("395d68", "F-GXLI", "Beluga XL3", TOULOUSE_BLAGNAC),
    BelugaAircraft("395d69", "F-GXLJ", "Beluga XL4", TOULOUSE_BLAGNAC),
    BelugaAircraft("395d6d", "F-GXLN", "Beluga XL5", TOULOUSE_BLAGNAC),
    BelugaAircraft("395d6e", "F-GXLO", "Beluga XL6", TOULOUSE_BLAGNAC),
)
