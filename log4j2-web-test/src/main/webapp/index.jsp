<%
  String reproLogPath = System.getProperty("catalina.base") + "/logs/log4j2-web-test-repro.log";
%>
<html>
<body>
<h2>Log4j2 Composite Lookup Test</h2>
<p>This app provides two reproducible comparison paths for issue <code>#2351</code>.</p>
<ul>
  <li><a href="composite.jsp">Composite config repro</a> — uses the web-app startup configuration from <code>web.xml</code>.</li>
  <li><a href="single.jsp">Single config control</a> — uses an isolated control context with <code>log4j2-single.xml</code>.</li>
</ul>
<p>Both pages write one line to <code><%= reproLogPath %></code>.</p>
</body>
</html>
