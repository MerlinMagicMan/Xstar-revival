package io.xstarrevival.app.gs

import io.xstarrevival.app.TelemetrySource
import io.xstarrevival.core.model.DiagnosticsState
import io.xstarrevival.core.model.NavigationState
import io.xstarrevival.core.model.ProtocolPacketDisposition
import io.xstarrevival.core.model.ProtocolPacketTrace
import io.xstarrevival.core.model.XStarState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticsReportTest {
    private val state = XStarState(
        navigation = NavigationState(latitudeDeg = 41.123456, longitudeDeg = -93.654321),
        diagnostics = DiagnosticsState(
            source = "test",
            protocolVersion = "TEST/1",
            notes = listOf("serial=PRIVATE"),
            packets = listOf(
                ProtocolPacketTrace(1, "TEST/1", 7, 1, 3, ProtocolPacketDisposition.DECODED, "TEST", "AA BB CC")
            )
        )
    )

    @Test
    fun standardExportRedactsLocationNotesAndRawPackets() {
        val report = buildDiagnosticReport(state, TelemetrySource.SIMULATOR, emptyList(), includeRawPackets = false)

        assertTrue(report.contains("coordinates=REDACTED"))
        assertTrue(report.contains("notes=REDACTED"))
        assertFalse(report.contains("41.123456"))
        assertFalse(report.contains("PRIVATE"))
        assertFalse(report.contains("AA BB CC"))
    }

    @Test
    fun developerExportIncludesExplicitlyRequestedRawPackets() {
        val report = buildDiagnosticReport(state, TelemetrySource.SIMULATOR, emptyList(), includeRawPackets = true)
        assertTrue(report.contains("raw=AA BB CC"))
    }
}
