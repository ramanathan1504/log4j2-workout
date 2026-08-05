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
package org.apache.logging.log4j.samples.threadcontext;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractOutputStreamAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code ThreadContext} — the MDC and NDC — and the one thing about it that
 * costs people real time: it is <em>thread</em> local, so it does not follow work
 * handed to an executor.
 *
 * <p>That is not a bug and it is documented, but the failure mode is quiet. A
 * correlation id set on a request thread simply renders empty on the pool thread
 * that does the work, and an empty MDC value is indistinguishable from a value
 * that is genuinely blank. Nothing is logged to say the context was lost.
 */
class ThreadContextTest {

    private static final Logger LOG = LogManager.getLogger(ThreadContextTest.class);
    private static final Path FILE = Path.of("target", "thread-context.log");

    @BeforeEach
    @AfterEach
    void clear() {
        ThreadContext.clearAll();
    }

    @Test
    void mdc_and_ndc_appear_in_the_pattern() throws IOException {
        ThreadContext.put("orderId", "4711");
        ThreadContext.put("tenant", "acme");
        ThreadContext.push("checkout");

        LOG.info("processing");

        assertThat(lastLine())
                .contains("[4711|acme]")
                .contains("checkout");
    }

    @Test
    void an_unset_key_renders_empty_rather_than_null() throws IOException {
        ThreadContext.put("orderId", "4711");
        // "tenant" deliberately not set.

        LOG.info("half a context");

        // Reads "[4711|]" - the separator survives, the value is empty. Nothing
        // distinguishes this from tenant having been set to "".
        assertThat(lastLine())
                .contains("[4711|]")
                .doesNotContain("null");
    }

    @Test
    void context_does_not_cross_a_thread_boundary_by_itself() throws Exception {
        ThreadContext.put("orderId", "4711");
        ThreadContext.put("tenant", "acme");

        final ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(() -> LOG.info("work on a pool thread")).get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertThat(lastLine())
                .as("the pool thread has its own, empty context")
                .contains("[|]");
    }

    @Test
    void context_crosses_a_thread_boundary_when_it_is_carried_deliberately() throws Exception {
        ThreadContext.put("orderId", "4711");
        ThreadContext.put("tenant", "acme");

        // Capture on the submitting thread, restore on the worker. This is what
        // every "MDC-propagating executor" wrapper does underneath.
        final Map<String, String> carried = ThreadContext.getImmutableContext();

        final ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(() -> {
                ThreadContext.putAll(carried);
                try {
                    LOG.info("work with the context carried across");
                } finally {
                    ThreadContext.clearAll();
                }
            }).get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertThat(lastLine()).contains("[4711|acme]");
    }

    private static String lastLine() throws IOException {
        ((LoggerContext) LogManager.getContext(false)).getConfiguration()
                .getAppenders().values().forEach(appender -> {
                    if (appender instanceof AbstractOutputStreamAppender) {
                        ((AbstractOutputStreamAppender<?>) appender).getManager().flush();
                    }
                });
        final List<String> lines = Files.readAllLines(FILE, StandardCharsets.UTF_8);
        assertThat(lines).as("lines written").isNotEmpty();
        return lines.get(lines.size() - 1);
    }
}
