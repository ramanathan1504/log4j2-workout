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

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

/**
 * A minimal custom appender, written the way the plugin system expects.
 *
 * <p>Three things make it discoverable, and all three are required:
 *
 * <ol>
 *   <li>{@code @Plugin} with a {@code name} and a {@code category}. The category
 *       is what a configuration element is looked up under; {@code Core} is the
 *       one that appenders, layouts and filters live in.</li>
 *   <li>A {@code @PluginFactory} static method. Log4j never calls a constructor
 *       directly, so a plugin without one builds nothing and reports only that
 *       the element is unknown.</li>
 *   <li>{@code log4j-plugin-processor} on the compile classpath, so the
 *       annotations are written into {@code Log4j2Plugins.dat} at build time.
 *       Without it Log4j falls back to scanning packages by name, which happens
 *       to work when classes sit loose on a classpath and finds nothing inside
 *       a shaded or modular jar.</li>
 * </ol>
 *
 * <p>The count is exposed so a test can assert that events actually arrived,
 * rather than inferring it from output that may have been swallowed. Log4j
 * catches appender exceptions and reports them through {@code StatusLogger}
 * while the application carries on, so "no error" is not evidence of success.
 */
// category is "Core", NOT "appender". Every configuration element lives under
// the Core category; elementType is what says this one is an appender. Getting
// them the wrong way round registers the plugin under a category nothing looks
// in, and Log4j reports only that the element is unknown.
@Plugin(name = "Counting", category = Node.CATEGORY, elementType = Appender.ELEMENT_TYPE)
public final class CountingAppender extends AbstractAppender {

    private final AtomicInteger count = new AtomicInteger();

    private CountingAppender(
            final String name,
            final Filter filter,
            final Layout<? extends Serializable> layout,
            final boolean ignoreExceptions) {
        super(name, filter, layout, ignoreExceptions, Property.EMPTY_ARRAY);
    }

    @PluginFactory
    public static CountingAppender createAppender(
            @PluginAttribute("name") final String name,
            @PluginAttribute(value = "ignoreExceptions", defaultBoolean = true) final boolean ignoreExceptions,
            @PluginElement("Layout") final Layout<? extends Serializable> layout,
            @PluginElement("Filter") final Filter filter) {
        if (name == null) {
            LOGGER.error("No name provided for CountingAppender");
            return null;
        }
        return new CountingAppender(name, filter, layout, ignoreExceptions);
    }

    @Override
    public void append(final LogEvent event) {
        count.incrementAndGet();
    }

    /** Events this appender has accepted. */
    public int getCount() {
        return count.get();
    }
}
