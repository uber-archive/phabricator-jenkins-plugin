package com.uber.jenkins.phabricator.utils;

import java.io.PrintStream;

/**
 * Logger utility.
 */
public class Logger {
    private static final String LOG_FORMAT = "[%s] %s";

    private PrintStream stream;

    /**
     * Logger constructor.
     * @param stream The stream.
     */
    public Logger(PrintStream stream) {
        this.stream = stream;
    }

    /**
     * Gets the stream.
     * @return The stream where logs go to.
     */
    public PrintStream getStream() {
        return stream;
    }

    /**
     * Logs the message to the stream.
     * @param tag The tag for the message.
     * @param message The message to log.
     */
    public void info(String tag, String message) {
        stream.println(String.format(LOG_FORMAT, tag, message));
    }
}
