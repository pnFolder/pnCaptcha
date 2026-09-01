package ru.privatenull.pncaptcha.metrics

import org.bstats.charts.SimplePie
import org.bstats.velocity.Metrics
import org.slf4j.Logger
import ru.privatenull.pncaptcha.PnCaptchaPlugin
import ru.privatenull.pncaptcha.config.CaptchaConfig
import java.nio.file.Files
import java.nio.file.Path

/** Owns the complete bStats lifecycle and keeps telemetry failures isolated from pnCaptcha startup. */
class BStatsMetrics private constructor(
    private val metrics: Metrics,
    private val logger: Logger
) : AutoCloseable {

    override fun close() {
        runCatching { metrics.shutdown() }
            .onFailure { error -> logger.warn("Could not shut down pnCaptcha bStats cleanly", error) }
    }

    companion object {
        const val SERVICE_ID = 33698

        fun start(
            plugin: PnCaptchaPlugin,
            factory: Metrics.Factory,
            dataDirectory: Path,
            logger: Logger,
            config: CaptchaConfig
        ): BStatsMetrics? {
            if (!config.metrics.enabled) {
                logger.info("pnCaptcha bStats disabled by plugins/pncaptcha/config.yml")
                return null
            }

            return try {
                val metrics = factory.make(plugin, SERVICE_ID)
                if (config.metrics.customCharts) registerCharts(metrics, config)

                val globalConfig = dataDirectory.parent?.resolve("bStats")?.resolve("config.txt")
                when (globalConfig?.let(::readGlobalEnabled)) {
                    false -> logger.warn(
                        "pnCaptcha bStats is globally disabled in {}. No statistics will be sent.",
                        globalConfig
                    )
                    true -> logger.info(
                        "pnCaptcha bStats started (service id {}). The first report is sent after 3-6 minutes; " +
                            "the public dashboard can update up to 30 minutes later.",
                        SERVICE_ID
                    )
                    null -> logger.info(
                        "pnCaptcha bStats started with service id {}. Global state could not be read from {}; " +
                            "bStats will report any I/O error according to its global logging settings.",
                        SERVICE_ID,
                        globalConfig
                    )
                }
                BStatsMetrics(metrics, logger)
            } catch (error: Exception) {
                logger.error(
                    "Could not initialize pnCaptcha bStats (service id {}). pnCaptcha will continue without metrics.",
                    SERVICE_ID,
                    error
                )
                null
            }
        }

        internal fun readGlobalEnabled(path: Path): Boolean? {
            if (!Files.isRegularFile(path)) return null
            return runCatching {
                Files.readAllLines(path)
                    .firstOrNull { line -> line.startsWith("enabled=") }
                    ?.substringAfter('=')
                    ?.trim()
                    ?.equals("true", ignoreCase = true)
            }.getOrNull()
        }

        private fun registerCharts(metrics: Metrics, config: CaptchaConfig) {
            metrics.addCustomChart(SimplePie("routing_strategy") { config.routing.strategy.lowercase() })
            metrics.addCustomChart(SimplePie("routing_servers") { config.routing.servers.count { it.enabled }.toString() })
            metrics.addCustomChart(SimplePie("captcha_length") { config.captchaLength.toString() })
            metrics.addCustomChart(SimplePie("font_preset") { config.font.preset.lowercase() })
            metrics.addCustomChart(SimplePie("voxel_depth") { config.geometry.depthBlocks.toString() })
            metrics.addCustomChart(SimplePie("noise_enabled") { config.noise.enabled.toString() })
            metrics.addCustomChart(SimplePie("recovery_enabled") { config.player.recovery.enabled.toString() })
            metrics.addCustomChart(SimplePie("actions_enabled") { config.actions.enabled.toString() })
            metrics.addCustomChart(SimplePie("bossbar_actions") {
                config.actions.triggers.values.flatten()
                    .any { it.type.equals("bossbar", ignoreCase = true) }
                    .toString()
            })
        }
    }
}
