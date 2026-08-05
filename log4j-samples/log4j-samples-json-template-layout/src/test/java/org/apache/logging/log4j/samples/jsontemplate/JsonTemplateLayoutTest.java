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
package org.apache.logging.log4j.samples.jsontemplate;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Shows what {@code JsonTemplateLayout} produces, asserting on the bytes rather
 * than on the fact that nothing threw.
 *
 * <p>That distinction matters here more than usual. A template URI that does not
 * resolve is not an error: the layout falls back to its built-in template, logs
 * one line through the {@code StatusLogger}, and carries on emitting perfectly
 * valid JSON in a shape nobody asked for. Downstream that surfaces days later as
 * "our index mapping stopped matching", so the only useful test reads the field
 * names back off disk.
 */
class JsonTemplateLayoutTest {

    private static final Logger LOG =
            LogManager.getLogger(JsonTemplateLayoutTest.class);

    private static final Path CUSTOM = Path.of("target", "json-custom.log");
    private static final Path ECS = Path.of("target", "json-ecs.log");

    @BeforeEach
    void setUp() {
        ThreadContext.clearAll();
    }

    @AfterEach
    void tearDown() {
        ThreadContext.clearAll();
    }

    @Test
    void custom_template_emits_exactly_the_declared_fields() throws IOException {
        ThreadContext.put("traceId", "abc-123");
        LOG.info("order accepted");

        final String json = lastLine(CUSTOM);

        // Declared in BenchLayout.json, so all of these must be present.
        assertThat(json)
                .contains("\"level\":\"INFO\"")
                .contains("\"message\":\"order accepted\"")
                .contains("\"logger\":\"" + JsonTemplateLayoutTest.class.getName() + "\"")
                .contains("\"service\":\"checkout\"")
                .contains("\"trace.id\":\"abc-123\"");
    }

    @Test
    void mdc_resolver_omits_the_field_when_the_key_is_absent() throws IOException {
        LOG.info("no trace id on this one");

        assertThat(lastLine(CUSTOM))
                .as("an unset MDC key yields no field at all, not an empty one")
                .doesNotContain("trace.id");
    }

    @Test
    void exception_resolver_renders_the_stack_trace() throws IOException {
        LOG.error("payment failed", new IllegalStateException("gateway timeout"));

        assertThat(lastLine(CUSTOM))
                .contains("\"error\"")
                .contains("gateway timeout")
                .contains("IllegalStateException");
    }

    @Test
    void bundled_ecs_template_uses_the_elastic_field_names() throws IOException {
        LOG.info("shipped");

        // EcsLayout.json ships inside log4j-layout-template-json. Its field
        // names are Elastic's, not Log4j's - "@timestamp" and "log.level"
        // rather than "timestamp" and "level" - which is the point of using it.
        assertThat(lastLine(ECS))
                .contains("\"@timestamp\"")
                .contains("\"log.level\":\"INFO\"")
                .contains("\"message\":\"shipped\"");
    }

    /**
     * Reads the newest line written to an appender's file.
     *
     * <p>Flushes the context first: the File appender buffers by default, so
     * reading without this races the writer and fails intermittently, which is
     * worse than failing every time.
     */
    private static String lastLine(final Path file) throws IOException {
        ((LoggerContext) LogManager.getContext(false)).getConfiguration()
                .getAppenders().values().forEach(appender -> {
                    if (appender instanceof org.apache.logging.log4j.core.appender.AbstractOutputStreamAppender) {
                        ((org.apache.logging.log4j.core.appender.AbstractOutputStreamAppender<?>) appender)
                                .getManager().flush();
                    }
                });
        final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertThat(lines).as("lines written to %s", file).isNotEmpty();
        return lines.get(lines.size() - 1);
    }
}
