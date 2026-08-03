<%@ page import="org.apache.logging.log4j.LogManager" %>
<%@ page import="org.apache.logging.log4j.Logger" %>
<%
  Logger logger = LogManager.getLogger("com.example.log4j2webtest");
  String expectedServletContextName = application.getServletContextName();
  String activeConfiguration = application.getInitParameter("log4jConfiguration");
  String reproLogPath = System.getProperty("catalina.base") + "/logs/log4j2-web-test-repro.log";
  logger.info("Request URI={} expectedServletContextName={} activeLog4jConfiguration={}",
      request.getRequestURI(), expectedServletContextName, activeConfiguration);
%>
<html>
<body>
<h2>Composite config repro</h2>
<p>Expected ServletContext name from JSP API: <strong><%= expectedServletContextName %></strong></p>
<p>Active log4jConfiguration from <code>web.xml</code>: <strong><%= activeConfiguration %></strong></p>
<p>Open <code><%= reproLogPath %></code> and compare the <code>COMPOSITE-CTX=...</code> value with the expected ServletContext name above.</p>
<p><a href="index.jsp">Back to chooser</a> | <a href="single.jsp">Open single-config control</a></p>
</body>
</html>

