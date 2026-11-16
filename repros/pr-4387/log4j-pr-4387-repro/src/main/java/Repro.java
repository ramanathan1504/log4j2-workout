import java.io.StringWriter;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.PageContext;
import org.apache.logging.log4j.taglib.DumpTag;
import org.springframework.mock.web.MockJspWriter;
import org.springframework.mock.web.MockPageContext;

public final class Repro {

    static final class NullToString {
        @Override
        public String toString() {
            return null;
        }
    }

    static String dump(final String name, final Object value) throws Exception {
        final StringWriter body = new StringWriter();
        final MockJspWriter writer = new MockJspWriter(body);
        final MockPageContext context = new MockPageContext() {
            @Override
            public JspWriter getOut() {
                return writer;
            }
        };
        context.setAttribute(name, value, PageContext.PAGE_SCOPE);
        final DumpTag tag = new DumpTag();
        tag.setPageContext(context);
        tag.setScope("page");
        tag.doEndTag();
        writer.flush();
        return body.toString();
    }

    static void report(final String name, final boolean pass, final String observed) {
        System.out.println((pass ? "PASS " : "FAIL ") + name + " observed=" + observed);
    }

    public static void main(final String[] args) throws Exception {
        final String xss = dump("<name>", "<script>alert('xss')</script>");
        report("escaped-name-and-value", !xss.contains("<script>") && !xss.contains("<name>"), xss);
        final String plain = dump("user", "alice");
        report("control-plain-attribute", plain.contains("<dt><code>user</code></dt><dd><code>alice</code></dd>"), plain);
        try {
            final String nullString = dump("odd", new NullToString());
            report("null-toString-value", nullString.contains("<dd><code>null</code></dd>"), nullString);
        } catch (final Throwable error) {
            report("null-toString-value", false, error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }
}
