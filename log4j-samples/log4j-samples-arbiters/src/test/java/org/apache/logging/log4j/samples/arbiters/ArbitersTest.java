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
package org.apache.logging.log4j.samples.arbiters;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractOutputStreamAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.Test;

/**
 * Arbiters: one configuration file that resolves differently per environment.
 *
 * <p>The property that matters is <em>when</em> they run. An arbiter is evaluated
 * once, while the configuration is being built — not per event, and not again
 * afterwards. Three consequences follow, and all three catch people:
 *
 * <ol>
 *   <li><strong>The branch not taken is never built.</strong> A typo inside it is
 *       never reported, because Log4j never constructs those elements. A
 *       configuration can be broken for production and perfectly quiet in
 *       development.</li>
 *   <li><strong>Changing the property later changes nothing.</strong> The
 *       decision is baked into the built configuration; only a reconfiguration
 *       re-evaluates it.</li>
 *   <li><strong>Two branches may declare the same appender name.</strong> That is
 *       the point — exactly one of them exists after arbitration, so
 *       {@code AppenderRef} can name it unconditionally.</li>
 * </ol>
 *
 * <p>Note that the properties configuration format supports no arbiters at all.
 * The nearest spelling fails with {@code No name attribute provided for
 * Appender}, which names neither arbiters nor the format.
 */
class ArbitersTest {

    private static final Logger LOG = LogManager.getLogger(ArbitersTest.class);

    @Test
    void the_matching_branch_is_the_one_that_exists() throws IOException {
        withProperty("sample.env", "prod", () -> LOG.info("hello"));

        assertThat(read("arbiter-prod.log"))
                .as("SystemPropertyArbiter matched prod, so that File appender was built")
                .anyMatch(l -> l.startsWith("PROD"));

        // The dev branch was never constructed, so its file does not exist at
        // all - not merely empty.
        assertThat(Path.of("target", "arbiter-dev.log"))
                .as("the branch not taken is never built")
                .doesNotExist();
    }

    @Test
    void default_arbiter_supplies_the_fallback() throws IOException {
        // sample.tier is not set, so the Select falls through to DefaultArbiter.
        withProperty("sample.env", "prod", () -> LOG.info("tiered"));

        assertThat(read("arbiter-tier.log"))
                .as("no SystemPropertyArbiter in the Select matched, so DefaultArbiter won")
                .anyMatch(l -> l.startsWith("STANDARD"));
    }

    /**
     * Runs work with a system property set, reconfiguring Log4j around it.
     *
     * <p>The reconfiguration is the point: setting the property without it would
     * change nothing, because arbitration already happened.
     */
    private static void withProperty(final String key, final String value, final Runnable work) {
        final String previous = System.getProperty(key);
        System.setProperty(key, value);
        try {
            final LoggerContext context = (LoggerContext) LogManager.getContext(false);
            context.reconfigure();
            work.run();
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }

    private static List<String> read(final String name) throws IOException {
        ((LoggerContext) LogManager.getContext(false)).getConfiguration()
                .getAppenders().values().forEach(appender -> {
                    if (appender instanceof AbstractOutputStreamAppender) {
                        ((AbstractOutputStreamAppender<?>) appender).getManager().flush();
                    }
                });
        final Path file = Path.of("target", name);
        assertThat(file).as("appender file %s", name).exists();
        return Files.readAllLines(file, StandardCharsets.UTF_8);
    }
}
