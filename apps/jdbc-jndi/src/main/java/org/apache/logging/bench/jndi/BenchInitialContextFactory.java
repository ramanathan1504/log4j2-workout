package org.apache.logging.bench.jndi;

import java.util.Collections;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.OperationNotSupportedException;
import javax.naming.spi.InitialContextFactory;

/**
 * A JNDI provider that is only a {@link Map}.
 *
 * <p>The JDBC appender's {@code DataSource} connection source resolves its
 * target through JNDI, so exercising it needs a JNDI provider. Every real one —
 * a container's, or an LDAP/RMI client — can reach off the machine. This one
 * cannot: it speaks no protocol, opens no socket, and resolves only names the
 * application itself put in the map below.
 *
 * <p>That is what makes it reasonable for the bench to run with
 * {@code log4j2.enableJndiJdbc=true}. The flag re-enables a subsystem Log4j
 * disabled by default after CVE-2021-44228, and the reason that CVE mattered
 * was remote lookup: an attacker-controlled name resolving to an
 * attacker-controlled server. With this factory there is nowhere for a name to
 * point. The bench never enables {@code log4j2.enableJndiLookup}, which is the
 * flag that governs the {@code ${jndi:}} lookup itself.
 *
 * <p>Selected with {@code -Djava.naming.factory.initial}, which ./bench passes
 * for this app.
 */
public final class BenchInitialContextFactory implements InitialContextFactory {

    /** The entire namespace. Static, because JNDI constructs the factory
     *  reflectively and the application binds before Log4j looks anything up. */
    private static final Map<String, Object> BINDINGS = new ConcurrentHashMap<>();

    /** Binds a name the appender's configuration can then reference. */
    public static void bind(final String name, final Object value) {
        BINDINGS.put(name, value);
    }

    public static Map<String, Object> bindings() {
        return Collections.unmodifiableMap(BINDINGS);
    }

    @Override
    public Context getInitialContext(final Hashtable<?, ?> environment) {
        return new MapContext();
    }

    /**
     * The minimum a {@link Context} can be and still satisfy a lookup: every
     * mutating or federating operation refuses rather than pretending.
     */
    private static final class MapContext implements Context {

        @Override
        public Object lookup(final String name) throws NamingException {
            final Object value = BINDINGS.get(name);
            if (value == null) {
                // The same exception a real provider throws, so Log4j's error
                // handling takes the path it would in production.
                throw new NameNotFoundException("Nothing bound at " + name
                        + " (bound: " + BINDINGS.keySet() + ")");
            }
            return value;
        }

        @Override
        public Object lookup(final Name name) throws NamingException {
            return lookup(name.toString());
        }

        @Override
        public void bind(final String name, final Object obj) {
            BINDINGS.put(name, obj);
        }

        @Override
        public void bind(final Name name, final Object obj) {
            BINDINGS.put(name.toString(), obj);
        }

        @Override
        public void rebind(final String name, final Object obj) {
            BINDINGS.put(name, obj);
        }

        @Override
        public void rebind(final Name name, final Object obj) {
            BINDINGS.put(name.toString(), obj);
        }

        @Override
        public void unbind(final String name) {
            BINDINGS.remove(name);
        }

        @Override
        public void unbind(final Name name) {
            BINDINGS.remove(name.toString());
        }

        @Override
        public Hashtable<?, ?> getEnvironment() {
            return new Hashtable<>();
        }

        @Override
        public void close() {
            // Nothing to release: there is no connection.
        }

        @Override
        public String getNameInNamespace() {
            return "";
        }

        // ── Everything below is deliberately unsupported ────────────────────
        // A partial implementation that silently returned null would make a
        // misconfiguration look like an empty namespace.

        private static NamingException unsupported() {
            return new OperationNotSupportedException(
                    "BenchInitialContextFactory is a flat in-memory map: "
                            + "no subcontexts, no listing, no federation");
        }

        @Override
        public void rename(final String oldName, final String newName) throws NamingException {
            throw unsupported();
        }

        @Override
        public void rename(final Name oldName, final Name newName) throws NamingException {
            throw unsupported();
        }

        @Override
        public NamingEnumeration<javax.naming.NameClassPair> list(final String name)
                throws NamingException {
            throw unsupported();
        }

        @Override
        public NamingEnumeration<javax.naming.NameClassPair> list(final Name name)
                throws NamingException {
            throw unsupported();
        }

        @Override
        public NamingEnumeration<javax.naming.Binding> listBindings(final String name)
                throws NamingException {
            throw unsupported();
        }

        @Override
        public NamingEnumeration<javax.naming.Binding> listBindings(final Name name)
                throws NamingException {
            throw unsupported();
        }

        @Override
        public void destroySubcontext(final String name) throws NamingException {
            throw unsupported();
        }

        @Override
        public void destroySubcontext(final Name name) throws NamingException {
            throw unsupported();
        }

        @Override
        public Context createSubcontext(final String name) throws NamingException {
            throw unsupported();
        }

        @Override
        public Context createSubcontext(final Name name) throws NamingException {
            throw unsupported();
        }

        @Override
        public Object lookupLink(final String name) throws NamingException {
            return lookup(name);
        }

        @Override
        public Object lookupLink(final Name name) throws NamingException {
            return lookup(name);
        }

        @Override
        public javax.naming.NameParser getNameParser(final String name) throws NamingException {
            throw unsupported();
        }

        @Override
        public javax.naming.NameParser getNameParser(final Name name) throws NamingException {
            throw unsupported();
        }

        @Override
        public String composeName(final String name, final String prefix) {
            return prefix.isEmpty() ? name : prefix + "/" + name;
        }

        @Override
        public Name composeName(final Name name, final Name prefix) throws NamingException {
            throw unsupported();
        }

        @Override
        public Object addToEnvironment(final String propName, final Object propVal) {
            return null;
        }

        @Override
        public Object removeFromEnvironment(final String propName) {
            return null;
        }
    }

    /**
     * Public and no-arg, necessarily: JNDI instantiates the factory named by
     * {@code java.naming.factory.initial} reflectively. Making it private — the
     * habit everywhere else in this bench — fails at the first lookup with a
     * NoInitialContextException that blames the class name rather than its
     * visibility.
     */
    public BenchInitialContextFactory() {}
}
