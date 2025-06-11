package com.srisu.srisu.core.logger

import io.github.aakira.napier.Napier

object AppLogger {

    fun log(message: String) {
        Napier.v(message)
    }

    fun debug(message: String, tag: String) {
        Napier.d(message = message, tag = tag)
    }

    fun error(error: String, tag: String) {
        Napier.e(message = error, tag = tag)
    }
}