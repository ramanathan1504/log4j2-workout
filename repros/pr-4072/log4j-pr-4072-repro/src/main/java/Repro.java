import java.io.File;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;

public class Repro {
    public static void main(String[] args) {
        File logs = new File(System.getProperty("outdir"), "logs");
        File onDemandDir = new File(logs, "ondemand");
        File onDemandFile = new File(onDemandDir, "app.log");
        File directDir = new File(logs, "direct");
        File eagerFile = new File(logs, "eager/app.log");
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        boolean onDemandDirAtStart = onDemandDir.exists();
        boolean onDemandFileAtStart = onDemandFile.exists();
        boolean directDirAtStart = directDir.exists();
        boolean eagerFileAtStart = eagerFile.exists();
        LogManager.getLogger("ondemand").info("first ondemand event");
        LogManager.getLogger("direct").info("first direct event");
        boolean onDemandFileAfterEvent = onDemandFile.isFile() && onDemandFile.length() > 0;
        String[] directFiles = directDir.list();
        boolean directFileAfterEvent = directFiles != null && directFiles.length == 1;
        context.stop();
        System.out.println((onDemandDirAtStart ? "FAIL" : "PASS") + " createOnDemand=true parent dir before first event exists=" + onDemandDirAtStart);
        System.out.println((onDemandFileAtStart ? "FAIL" : "PASS") + " createOnDemand=true file before first event exists=" + onDemandFileAtStart);
        System.out.println((directDirAtStart ? "FAIL" : "PASS") + " createOnDemand=true direct-write parent dir before first event exists=" + directDirAtStart);
        System.out.println((onDemandFileAfterEvent ? "PASS" : "FAIL") + " createOnDemand=true file after first event written=" + onDemandFileAfterEvent + " bytes=" + onDemandFile.length());
        System.out.println((directFileAfterEvent ? "PASS" : "FAIL") + " createOnDemand=true direct-write file after first event written=" + directFileAfterEvent);
        System.out.println((eagerFileAtStart ? "PASS" : "FAIL") + " control createOnDemand=false file at startup exists=" + eagerFileAtStart);
    }
}
