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

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.message.Message;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A custom filter that accepts one event in every {@code n}.
 *
 * <p>Filters are the plugin type most often placed wrongly rather than written
 * wrongly. The same filter behaves differently at each of the four scopes it can
 * occupy — context-wide, on a logger, on an appender, or on an appender-ref —
 * because each sees a different subset of events. This one is deliberately
 * stateful so that difference is visible: at appender scope it counts only what
 * reached that appender, at context scope it counts everything.
 *
 * <p>{@code AbstractFilter} declares many overloads. Only {@code filter(LogEvent)}
 * is overridden here, which is enough for a configuration-driven filter; the
 * others exist for the API paths that pass their arguments unassembled to avoid
 * allocating a {@code LogEvent} when the filter will reject it anyway.
 */
@Plugin(name = "EveryNthFilter", category = "Core", elementType = Filter.ELEMENT_TYPE)
public final class EveryNthFilter extends AbstractFilter {

    private final long n;
    private final AtomicLong seen = new AtomicLong();

    private EveryNthFilter(final long n, final Result onMatch, final Result onMismatch) {
        super(onMatch, onMismatch);
        this.n = n;
    }

    @PluginFactory
    public static EveryNthFilter createFilter(
            @PluginAttribute(value = "n", defaultLong = 2L) final long n,
            @PluginAttribute(value = "onMatch", defaultString = "ACCEPT") final Result onMatch,
            @PluginAttribute(value = "onMismatch", defaultString = "DENY") final Result onMismatch) {
        return new EveryNthFilter(n < 1 ? 1 : n, onMatch, onMismatch);
    }

    @Override
    public Result filter(final LogEvent event) {
        return decide();
    }

    @Override
    public Result filter(final Logger logger, final Level level, final Marker marker,
            final Message msg, final Throwable t) {
        return decide();
    }

    @Override
    public Result filter(final Logger logger, final Level level, final Marker marker,
            final Object msg, final Throwable t) {
        return decide();
    }

    @Override
    public Result filter(final Logger logger, final Level level, final Marker marker,
            final String msg, final Object... params) {
        return decide();
    }

    private Result decide() {
        return seen.incrementAndGet() % n == 0 ? onMatch : onMismatch;
    }
}
