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
package org.apache.logging.log4j.samples.bridges;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractOutputStreamAppender;
import org.junit.jupiter.api.Test;

/**
 * Routing other logging APIs <em>into</em> Log4j.
 *
 * <p>A large application rarely uses one logging API. Libraries pick SLF4J,
 * Commons Logging or {@code java.util.logging} independently of the application,
 * and the goal is one configuration and one destination for all of them.
 *
 * <p>Each bridge works differently, and the differences are where the surprises
 * live:
 *
 * <dl>
 *   <dt>SLF4J</dt>
 *   <dd>{@code log4j-slf4j2-impl} is a <em>provider</em>. SLF4J finds it through
 *       {@code ServiceLoader}, so nothing needs to be configured — but two
 *       providers on the classpath is a coin toss, and SLF4J only warns.</dd>
 *
 *   <dt>Commons Logging</dt>
 *   <dd>{@code log4j-jcl} works the same way, through JCL's own discovery.</dd>
 *
 *   <dt>java.util.logging</dt>
 *   <dd>{@code log4j-jul} needs {@code java.util.logging.manager} set
 *       <strong>before the JUL classes initialise</strong>. Setting it from
 *       {@code main} is already too late: the property is read once, when
 *       {@code LogManager} first loads. There is no error — JUL simply keeps its
 *       own handlers, and nothing says the bridge is inert. It cannot be set
 *       from Java at all; this module sets it as a JVM argument through
 *       surefire, and a real deployment must do the same.</dd>
 * </dl>
 */
class BridgesTest {

    private static final Path FILE = Path.of("target", "bridged.log");

    // There is deliberately no code here setting java.util.logging.manager.
    //
    // It cannot work from Java. JUL reads that property once, when LogManager
    // first loads, and by the time any test code runs surefire has already
    // logged through JUL. An earlier draft set it in @BeforeAll and this test
    // failed - which is precisely the bug the sample exists to demonstrate,
    // except that in production nothing fails: JUL keeps its own handlers and
    // the bridge is inert with no error anywhere.
    //
    // It is set as a JVM argument in this module's pom.xml instead.

    @Test
    void slf4j_events_reach_the_log4j_appender() throws IOException {
        final org.slf4j.Logger slf4j = org.slf4j.LoggerFactory.getLogger("sample.slf4j");
        slf4j.info("via-slf4j");

        assertThat(lines())
                .as("log4j-slf4j2-impl is discovered by ServiceLoader; nothing to configure")
                .anyMatch(l -> l.contains("via-slf4j"));
    }

    @Test
    void commons_logging_events_reach_the_log4j_appender() throws IOException {
        final org.apache.commons.logging.Log jcl =
                org.apache.commons.logging.LogFactory.getLog("sample.jcl");
        jcl.info("via-jcl");

        assertThat(lines()).anyMatch(l -> l.contains("via-jcl"));
    }

    @Test
    void jul_events_reach_the_log4j_appender_once_the_manager_is_set() throws IOException {
        final java.util.logging.Logger jul = java.util.logging.Logger.getLogger("sample.jul");
        jul.info("via-jul");

        assertThat(lines())
                .as("java.util.logging.manager must be set before JUL initialises; "
                        + "set it too late and the bridge is silently inert")
                .anyMatch(l -> l.contains("via-jul"));
    }

    @Test
    void the_originating_logger_name_is_preserved() throws IOException {
        org.slf4j.LoggerFactory.getLogger("sample.named.logger").info("named");

        // The bridge does not rewrite the logger name, so routing rules and
        // per-logger levels keep working against the library's own names.
        assertThat(lines())
                .anyMatch(l -> l.contains("named") && l.contains("s.n.logger"));
    }

    private static List<String> lines() throws IOException {
        ((LoggerContext) LogManager.getContext(false)).getConfiguration()
                .getAppenders().values().forEach(appender -> {
                    if (appender instanceof AbstractOutputStreamAppender) {
                        ((AbstractOutputStreamAppender<?>) appender).getManager().flush();
                    }
                });
        return Files.readAllLines(FILE, StandardCharsets.UTF_8);
    }
}
