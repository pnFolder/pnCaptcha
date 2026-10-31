package ru.privatenull.pncaptcha.integration

import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticConfiguration
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticContainer
import org.pnfolder.pnlibrary.api.plugin.ModuleContext
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration
import org.pnfolder.pnlibrary.api.remote.PolicyErrorAction
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.api.runtime.PnLibraryProvider
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import org.slf4j.Logger
import java.nio.file.Path

/** Подключение pnCaptcha к общему рантайму pnLibrary: политика, метрики, диагностика и обновления. */
class PnLibraryRuntime private constructor(
    private val owner: Any,
    private val logger: Logger,
    private val dataDirectory: Path,
    private val runtime: PnLibrary,
) : AutoCloseable {
    private var registration: PluginRegistration? = null
    private var module: ModuleContext? = null

    fun register(metricsEnabled: Boolean, version: String) {
        check(registration == null) { "pnCaptcha уже зарегистрирован в pnLibrary" }
        val registered = runtime.plugins.register(owner)
        registration = registered
        try {
            module = registered.registerModule(MODULE_ID) { builder ->
                builder
                    // Политику, которую нельзя проверить (нет сети, JRE без компилятора), плагин игнорирует:
                    // его останавливает только явный запрет.
                    .remotePolicy(
                        RemotePolicySettings.builder()
                            .source(POLICY_SOURCE)
                            .onError(PolicyErrorAction.KEEP_RUNNING)
                            .support(PnLibraryIdentity.SUPPORT_URL)
                            .build(),
                    )
                    .diagnostics(
                        dataDirectory,
                        DiagnosticContainer.builder("pncaptcha")
                            .snapshot { mapOf("version" to version) }
                            .configuration(
                                DiagnosticConfiguration.builder("config.yml")
                                    .secretKeyRegex("(?i).*(password|passwd|secret|token|uri|url|user(name)?)$")
                                    .build(),
                            )
                            .build(),
                    )
                    .updates(UPDATE_OWNER, UPDATE_REPOSITORY) { updater ->
                        updater
                            .channel(UpdateChannel.STABLE)
                            .automaticDownload(false)
                            .artifact("(?i)^pnCaptcha-[0-9].*\\.jar$", 17)
                    }
                // bStats-проект 35047; ключа FastStats у pnCaptcha пока нет.
                if (metricsEnabled) builder.metrics { metrics -> metrics.bStats(BSTATS_PROJECT_ID) }
                builder
            }
            module?.lifecycle?.enabled()?.show()
        } catch (failure: RuntimeException) {
            closeRegistration()
            throw failure
        }
    }

    override fun close() {
        val closing = module
        module = null
        if (closing != null && !closing.isClosed) {
            try {
                closing.lifecycle.disabled().show()
            } catch (failure: RuntimeException) {
                logger.debug("Не удалось вывести сообщение об остановке pnCaptcha", failure)
            }
        }
        closeRegistration()
    }

    private fun closeRegistration() {
        val closing = registration
        registration = null
        if (closing != null && !closing.isClosed) closing.close()
    }

    companion object {
        const val MINIMUM_VERSION = "0.0.1-dev.6"
        private const val MODULE_ID = "pncaptcha"
        private const val BSTATS_PROJECT_ID = 35047
        private const val POLICY_SOURCE =
            "https://raw.githubusercontent.com/pnFolder/pnlibrary-policy/main/.pnlibrary/policies/PnCaptchaPolicy.java"
        private const val UPDATE_OWNER = "pnFolder"
        private const val UPDATE_REPOSITORY = "pnCaptcha"

        fun connect(owner: Any, logger: Logger, dataDirectory: Path): PnLibraryRuntime {
            val runtime = PnLibraryProvider.get()
            check(runtime.isAtLeastVersion(MINIMUM_VERSION)) {
                "pnCaptcha требует pnLibrary $MINIMUM_VERSION или новее, установлена ${runtime.version}"
            }
            return PnLibraryRuntime(owner, logger, dataDirectory, runtime)
        }
    }
}
