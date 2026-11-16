import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.xml.DOMConfigurator;

public class Repro {
    public static void main(final String[] args) {
        DOMConfigurator.configure("log4j-does-not-matter.xml");
        PropertyConfigurator.configure("log4j-does-not-matter.properties");
    }
}
