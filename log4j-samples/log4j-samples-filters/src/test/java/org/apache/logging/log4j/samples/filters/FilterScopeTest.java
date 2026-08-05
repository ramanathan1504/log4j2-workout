/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.logging.log4j.samples.filters;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractOutputStreamAppender;
import org.junit.jupiter.api.Test;

/**
 * The same filter at different <em>scopes</em>, which is what people get wrong
 * far more often than the filter's own configuration.
 *
 * <p>A filter can sit in four places, and each sees a different set of events:
 *
 * <dl>
 *   <dt>Context</dt><dd>every event in the context, before any logger is consulted</dd>
 *   <dt>Logger</dt><dd>events on that logger, before they reach any appender</dd>
 *   <dt>Appender</dt><dd>only events routed to that appender, from any logger</dd>
 *   <dt>Appender-ref</dt><dd>only that one route, leaving the appender itself open
 *       to other loggers</dd>
 * </dl>
 *
 * <p>The distinction that catches people is the last pair. A filter on the
 * appender applies to everyone using it; the same filter on an appender-ref
 * applies only to that logger's route. Moving it changes which events other
 * loggers see, and nothing warns you.
 */
class FilterScopeTest {

    private static final Logger LOG = LogManager.getLogger(FilterScopeTest.class);
    private static final org.apache.logging.log4j.Marker AUDIT =
            MarkerManager.getMarker("AUDIT");

    // The two messages deliberately share no substring. An earlier draft used
    // "audited" and "not audited", and every noneMatch(contains("audited"))
    // matched the negative case too - the filters were right, the assertion was
    // not.
    @Test
    void appender_scope_filter_admits_only_matching_events() throws IOException {
        LOG.info(AUDIT, "marked-event");
        LOG.info("plain-event");

        // MarkerFilter on the appender: ACCEPT on AUDIT, DENY otherwise.
        final List<String> lines = read("filter-appender.log");
        assertThat(lines).anyMatch(l -> l.contains("marked-event"));
        assertThat(lines)
                .as("an unmarked event is denied at the appender")
                .noneMatch(l -> l.contains("plain-event"));
    }

    @Test
    void appender_ref_scope_filters_one_route_only() throws IOException {
        LOG.info(AUDIT, "marked-event");
        LOG.info("plain-event");

        // The same MarkerFilter, inverted, on the appender-ref: it denies AUDIT
        // and accepts everything else. The appender itself carries no filter, so
        // another logger referencing it would be unaffected.
        final List<String> lines = read("filter-ref.log");
        assertThat(lines).anyMatch(l -> l.contains("plain-event"));
        assertThat(lines)
                .as("AUDIT is denied on this route, not at the appender")
                .noneMatch(l -> l.contains("marked-event"));
    }

    @Test
    void logger_scope_filter_applies_before_any_appender() throws IOException {
        LOG.trace("trace is below the range");
        LOG.info("info is inside the range");
        LOG.error("error is inside the range");

        // LevelRangeFilter minLevel=ERROR maxLevel=INFO. The names read
        // backwards: minLevel is the *most severe* end, because Log4j orders
        // levels by decreasing severity. ERROR..INFO therefore means
        // "ERROR, WARN, INFO" - and TRACE is outside it.
        final List<String> lines = read("filter-logger.log");
        assertThat(lines).anyMatch(l -> l.contains("info is inside"));
        assertThat(lines).anyMatch(l -> l.contains("error is inside"));
        assertThat(lines)
                .as("denied at the logger, so it reaches no appender at all")
                .noneMatch(l -> l.contains("trace is below"));
    }

    private static List<String> read(final String name) throws IOException {
        ((LoggerContext) LogManager.getContext(false)).getConfiguration()
                .getAppenders().values().forEach(appender -> {
                    if (appender instanceof AbstractOutputStreamAppender) {
                        ((AbstractOutputStreamAppender<?>) appender).getManager().flush();
                    }
                });
        return Files.readAllLines(Path.of("target", name), StandardCharsets.UTF_8);
    }
}
