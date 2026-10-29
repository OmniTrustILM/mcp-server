package com.otilm.mcp.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/** Collects what a class logs at WARN or above, and fails on a line that carries an exception. */
final class Warnings implements AutoCloseable {
    private final Logger source;
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();

    Warnings(Class<?> source) {
        this.source = (Logger) LoggerFactory.getLogger(source);
        events.start();
        this.source.addAppender(events);
    }

    List<String> messages() {
        List<ILoggingEvent> warnings = events.list
                .stream()
                .filter(event -> event.getLevel().isGreaterOrEqual(Level.WARN))
                .toList();
        assertThat(warnings).filteredOn(event -> event.getThrowableProxy() != null).isEmpty();
        return warnings.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Override
    public void close() {
        source.detachAppender(events);
        events.stop();
    }
}
