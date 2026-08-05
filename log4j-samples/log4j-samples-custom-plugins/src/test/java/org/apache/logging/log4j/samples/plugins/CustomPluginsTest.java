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
package org.apache.logging.log4j.samples.plugins;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.Test;

/**
 * Asserts the two halves of plugin authoring, which fail independently.
 *
 * <p>The build-time half is whether {@code log4j-plugin-processor} ran and wrote
 * the descriptor. The run-time half is whether the plugins resolve from a
 * configuration. Either can pass while the other fails, so testing only that the
 * log looks right proves neither: with the descriptor missing, Log4j falls back
 * to scanning packages and the sample still works here, while the same code
 * inside a shaded jar silently finds nothing.
 */
class CustomPluginsTest {

    private static final String DESCRIPTOR =
            "META-INF/org/apache/logging/log4j/core/config/plugins/Log4j2Plugins.dat";

    /**
     * Build time: the annotation processor must have produced a descriptor, and
     * one of them must be this module's own.
     *
     * <p>Finding <em>a</em> descriptor is not enough — log4j-core ships its own,
     * so a build where the processor never ran still finds one on the classpath.
     * The assertion is that more than the core descriptor is present.
     */
    @Test
    void descriptor_is_generated_for_this_module() throws Exception {
        final Enumeration<URL> found =
                getClass().getClassLoader().getResources(DESCRIPTOR);
        final List<URL> all = Collections.list(found);

        assertThat(all)
                .as("plugin descriptors on the classpath")
                .isNotEmpty();

        final List<URL> fromThisModule = new ArrayList<>();
        for (final URL url : all) {
            final String path = url.toString();
            // log4j-core's own copy arrives from a jar; ours is written into
            // this module's classes directory by the annotation processor.
            if (!path.contains("log4j-core") && !path.contains(".jar!")) {
                fromThisModule.add(url);
            }
        }
        assertThat(fromThisModule)
                .as("descriptor generated for this module by log4j-plugin-processor; "
                        + "if empty, the processor did not run and Log4j will fall back "
                        + "to package scanning, which fails inside a shaded jar")
                .isNotEmpty();

        try (InputStream in = fromThisModule.get(0).openStream()) {
            assertThat(in.readAllBytes()).as("descriptor content").isNotEmpty();
        }
    }

    /**
     * Run time: the plugins resolve from a configuration, and the appender
     * actually receives the events the filter lets through.
     *
     * <p>The count is the assertion. Log4j catches appender exceptions and keeps
     * going, so an appender that threw on every event would leave the log looking
     * ordinary and the exit code at zero.
     */
    @Test
    void plugins_resolve_and_receive_events() {
        final LoggerContext context = (LoggerContext) LogManager.getContext(false);
        final Logger log = LogManager.getLogger(CustomPluginsTest.class);

        final CountingAppender appender = context.getConfiguration()
                .getAppender("Counting");
        assertThat(appender)
                .as("the Counting appender resolved from log4j2-test.xml")
                .isNotNull();

        final int before = appender.getCount();
        for (int i = 0; i < 6; i++) {
            log.info("event {} of 6", i + 1);
        }

        // EveryNthFilter is configured with n=2 and accepts one event in two,
        // so half of the six reach the appender.
        assertThat(appender.getCount() - before)
                .as("events accepted by EveryNthFilter(n=2)")
                .isEqualTo(3);
    }
}
