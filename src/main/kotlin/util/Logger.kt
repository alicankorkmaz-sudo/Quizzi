package util

import org.slf4j.LoggerFactory

object Logger {
    private val logger = LoggerFactory.getLogger("FlagQuizServer")

    fun d(message: String) {
        logger.debug("[DEBUG] $message")
    }

    fun i(message: String) {
        logger.info("[INFO] $message")
    }

    fun w(message: String) {
        logger.warn("[WARN] $message")
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            logger.error("[ERROR] $message", throwable)
        } else {
            logger.error("[ERROR] $message")
        }
    }
} 