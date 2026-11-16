import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;

public class Repro {

    static final class NoClassDefFoundLoader extends ClassLoader {
        NoClassDefFoundLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("com.missing.")) {
                throw new NoClassDefFoundError(name.replace('.', '/'));
            }
            if (name.equals("Hidden")) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        byte[] bytes = readClassBytes(name);
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    }
                    return loaded;
                }
            }
            return super.loadClass(name, resolve);
        }

        private byte[] readClassBytes(String name) throws ClassNotFoundException {
            try (InputStream in = getParent().getResourceAsStream(name + ".class")) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                }
                return out.toByteArray();
            } catch (Exception e) {
                throw new ClassNotFoundException(name, e);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        NoClassDefFoundLoader loader = new NoClassDefFoundLoader(Repro.class.getClassLoader());
        Runnable hidden = (Runnable) loader.loadClass("Hidden").getDeclaredConstructor().newInstance();
        hidden.run();
        ((LoggerContext) LogManager.getContext(false)).stop();
        String log = new String(Files.readAllBytes(Paths.get(System.getProperty("outdir"), "app.log")), StandardCharsets.UTF_8);
        boolean eventWritten = log.contains("event-with-unloadable-frame");
        boolean frameRendered = log.contains("com.missing.Gone.vanish(Gone.java:7)");
        boolean controlWritten = log.contains("event-control");
        System.out.println((eventWritten ? "PASS" : "FAIL") + " event-with-unloadable-frame written=" + eventWritten);
        System.out.println((frameRendered ? "PASS" : "FAIL") + " missing-class frame rendered=" + frameRendered);
        System.out.println((controlWritten ? "PASS" : "FAIL") + " control event-control written=" + controlWritten);
        System.out.println("log-bytes=" + log.length());
    }
}
