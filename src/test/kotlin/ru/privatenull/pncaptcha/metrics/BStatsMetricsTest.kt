package ru.privatenull.pncaptcha.metrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class BStatsMetricsTest {
    @Test
    fun usesRegisteredPnCaptchaVelocityService() {
        assertEquals(33698, BStatsMetrics.SERVICE_ID)
    }

    @Test
    fun readsGlobalOptInAndOptOut(@TempDir directory: Path) {
        val config = directory.resolve("config.txt")

        Files.writeString(config, "enabled=true\nserver-uuid=test\n")
        assertEquals(true, BStatsMetrics.readGlobalEnabled(config))

        Files.writeString(config, "enabled=false\nserver-uuid=test\n")
        assertEquals(false, BStatsMetrics.readGlobalEnabled(config))
    }

    @Test
    fun reportsUnknownStateForMissingOrMalformedConfig(@TempDir directory: Path) {
        assertNull(BStatsMetrics.readGlobalEnabled(directory.resolve("missing.txt")))

        val malformed = directory.resolve("config.txt")
        Files.writeString(malformed, "server-uuid=test\n")
        assertNull(BStatsMetrics.readGlobalEnabled(malformed))
    }
}
