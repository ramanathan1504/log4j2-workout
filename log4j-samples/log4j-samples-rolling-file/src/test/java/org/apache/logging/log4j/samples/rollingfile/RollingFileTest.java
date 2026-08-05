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
package org.apache.logging.log4j.samples.rollingfile;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

/**
 * Rollover, compression, and the {@code Delete} action that keeps the directory
 * from growing forever.
 *
 * <p>Two things here are easy to get wrong and produce no error either way.
 *
 * <p><strong>{@code max} is not a retention count.</strong> On
 * {@code DefaultRolloverStrategy} it caps the {@code %i} counter. Once the
 * counter wraps, the oldest file is overwritten — so a configuration with
 * {@code max="10"} and no {@code Delete} keeps ten files by accident, not by
 * policy, and changing the pattern silently changes the retention.
 *
 * <p><strong>{@code Delete}'s sibling conditions are order-sensitive</strong>,
 * because {@code IfAccumulatedFileCount} is stateful — it counts every file it is
 * asked about. Placed before {@code IfFileName} it also counts the active log
 * file, so the same policy keeps one archive fewer than it appears to. Nesting
 * the counter inside the name match, as this sample does, is the only
 * formulation that means what it reads like.
 */
class RollingFileTest {

    private static final Logger LOG = LogManager.getLogger(RollingFileTest.class);
    private static final Path DIR = Path.of("target", "rolling");

    @Test
    void rollover_compresses_and_delete_bounds_the_directory() throws IOException {
        // Enough volume to cross the 1 KB threshold several times over.
        for (int i = 0; i < 400; i++) {
            LOG.info("rollover line {} — {}", i, "x".repeat(80));
        }

        // Rollover, and the compression that follows it, run on a background
        // executor. Stopping the context is what guarantees they have finished;
        // sleeping instead makes the test flaky on a loaded machine.
        LogManager.shutdown();

        assertThat(DIR).as("the rolling directory").exists();

        final List<Path> archives = listArchives();

        assertThat(archives)
                .as("rollover produced compressed archives")
                .isNotEmpty();

        assertThat(archives)
                .as("every archive is gzipped, per the .gz in filePattern")
                .allSatisfy(p -> assertThat(p.getFileName().toString()).endsWith(".log.gz"));

        // Delete keeps at most 3 archives. Because the counter is nested inside
        // IfFileName it counts only archives, never the active app.log - so the
        // bound is exactly what the configuration says.
        assertThat(archives)
                .as("IfAccumulatedFileCount exceeds=3, nested inside IfFileName")
                .hasSizeLessThanOrEqualTo(3);
    }

    @Test
    void the_archives_really_are_gzip() throws IOException {
        for (int i = 0; i < 400; i++) {
            LOG.info("gzip check {} — {}", i, "y".repeat(80));
        }
        LogManager.shutdown();

        final List<Path> archives = listArchives();
        assertThat(archives).isNotEmpty();

        // 0x1f 0x8b is the gzip magic number. Checking the extension only
        // proves the file was named correctly, not that it was compressed.
        final byte[] head = new byte[2];
        try (var in = Files.newInputStream(archives.get(0))) {
            assertThat(in.read(head)).isEqualTo(2);
        }
        assertThat(head[0] & 0xff).isEqualTo(0x1f);
        assertThat(head[1] & 0xff).isEqualTo(0x8b);
    }

    private static List<Path> listArchives() throws IOException {
        if (!Files.isDirectory(DIR)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(DIR)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".log.gz")).toList();
        }
    }
}
