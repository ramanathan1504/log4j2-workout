package org.apache.logging.log4j.repro;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.util.FilteredObjectInputStream;

public class Repro {

    public static final class AllowListedHandler implements InvocationHandler, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public Object invoke(final Object proxy, final Method method, final Object[] args) {
            return 0;
        }
    }

    private static byte[] serialize(final Object value) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static String read(final byte[] bytes, final java.util.Collection<String> extra) {
        try (FilteredObjectInputStream in = new FilteredObjectInputStream(new ByteArrayInputStream(bytes), extra)) {
            final Object value = in.readObject();
            return "accepted:" + (Proxy.isProxyClass(value.getClass()) ? "proxy " + value.getClass().getName() : value.getClass().getName());
        } catch (final Exception e) {
            return "rejected:" + e.getClass().getSimpleName() + " " + e.getMessage();
        }
    }

    private static void report(final String name, final String observed, final boolean pass) {
        System.out.println((pass ? "PASS " : "FAIL ") + name + " observed=" + observed);
    }

    public static void main(final String[] args) throws Exception {
        System.out.println("java.version=" + System.getProperty("java.version"));
        final Map<String, Object> map = new HashMap<>();
        map.put("k", 1);
        final String control = read(serialize(map), Collections.<String>emptyList());
        report("control-hashmap-accepted", control, control.startsWith("accepted"));
        final String file = read(serialize(new File("x")), Collections.<String>emptyList());
        report("control-file-rejected", file, file.startsWith("rejected"));
        final Object proxy = Proxy.newProxyInstance(
                Repro.class.getClassLoader(), new Class<?>[] {Comparator.class}, new AllowListedHandler());
        final String proxyResult = read(serialize(proxy), Collections.<String>emptyList());
        report("proxy-rejected", proxyResult, proxyResult.startsWith("rejected"));
        final String proxyExtra = read(serialize(proxy), Collections.singletonList(proxy.getClass().getName()));
        report("proxy-rejected-even-when-named-in-allowlist", proxyExtra, proxyExtra.startsWith("rejected"));
    }
}
