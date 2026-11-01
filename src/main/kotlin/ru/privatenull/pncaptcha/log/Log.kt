package ru.privatenull.pncaptcha.log

/**
 * Единый логгер pnCaptcha. Пока модуль зарегистрирован в pnLibrary, сообщения идут в логгер модуля
 * (они попадают в диагностику вместе со стеком); иначе - в запасной логгер Velocity. Ничего не бросает.
 */
object Log {
    /** Приёмник сообщений: логгер модуля pnLibrary или запасной логгер платформы. */
    interface Sink {
        fun info(message: String)
        fun success(message: String)
        fun warning(message: String)
        fun error(message: String, error: Throwable?)
    }

    @Volatile
    private var module: Sink? = null

    @Volatile
    var fallback: Sink? = null

    /** Подключает логгер модуля; `null` при закрытии модуля возвращает запасной путь. */
    fun attach(sink: Sink?) {
        module = sink
    }

    fun info(message: String) = deliver { it.info(message) }

    fun success(message: String) = deliver { it.success(message) }

    fun warning(message: String) = deliver { it.warning(message) }

    /** Логгер модуля не принимает исключение в warning, поэтому с исключением пишем как ошибку, чтобы остался стек. */
    fun warning(message: String, error: Throwable?) {
        if (error == null) warning(message) else error(message, error)
    }

    fun error(message: String) = error(message, null)

    fun error(message: String, error: Throwable?) = deliver { it.error(message, error) }

    private inline fun deliver(action: (Sink) -> Unit) {
        val current = module
        if (current != null) {
            try {
                action(current)
                return
            } catch (_: Throwable) {
                // Модуль уже закрыт - уходим в запасной путь.
            }
        }
        try {
            fallback?.let(action)
        } catch (_: Throwable) {
            // Логирование никогда не должно ронять плагин.
        }
    }
}
