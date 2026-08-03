<%@ page import="java.net.URI" %>
<%@ page import="org.apache.logging.log4j.core.LoggerContext" %>
<%@ page import="org.apache.logging.log4j.core.config.Configurator" %>
<%@ page import="org.apache.logging.log4j.web.WebLoggerContextUtils" %>
<%
  String expectedServletContextName = application.getServletContextName();
  String reproLogPath = System.getProperty("catalina.base") + "/logs/log4j2-web-test-repro.log";
  URI configUri = application.getResource("/WEB-INF/classes/log4j2-single.xml").toURI();
  LoggerContext controlContext = Configurator.initialize(
      "issue2351-single-control",
      Thread.currentThread().getContextClassLoader(),
      configUri,
      WebLoggerContextUtils.createExternalEntry(application));
  try {
    controlContext.getLogger("com.example.log4j2webtest").info(
        "Request URI={} expectedServletContextName={} activeLog4jConfiguration={}",
        request.getRequestURI(), expectedServletContextName, "log4j2-single.xml");
  } finally {
    Configurator.shutdown(controlContext);
  }
%>
<html>
<body>
<h2>Single config control</h2>
<p>Expected ServletContext name from JSP API: <strong><%= expectedServletContextName %></strong></p>
<p>Control configuration: <strong>log4j2-single.xml</strong></p>
<p>This page creates an isolated LoggerContext with the single-file config and writes one <code>SINGLE-CTX=...</code> line to <code><%= reproLogPath %></code>.</p>
<p><a href="index.jsp">Back to chooser</a> | <a href="composite.jsp">Open composite repro</a></p>
</body>
</html>

