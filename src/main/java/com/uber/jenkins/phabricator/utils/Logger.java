package com.uber.jenkins.phabricator.utils;

import java.io.PrintStream;

/**
 * Logger utility.
 */
public class Logger {
    private static final String LOG_FORMAT = "[%s] %s";

    private PrintStream logger;

    /**
     * Logger constructor.
     * @param logger The logger.
     */
    public Logger(PrintStream logger) {
        this.logger = logger;
    }

    /**
     * Logs the message to the logger.
     * @param tag The tag for the message.
     * @param message The message to log.
     */
    public void info(String tag, String message) {
        logger.println(String.format(LOG_FORMAT, tag, message));
    }
}
