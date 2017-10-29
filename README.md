# JDI
JDI examples

ShellScriptLauncherDebugger is a simple tool to allow to attach to a running JVM process, and execute a shell script if a user-specified breakpoint is hit.
```
mvn compile
java -classpath target/classes:${JAVA_HOME}/lib/tools.jar org.asasvari.debug.ShellScriptLauncherDebugger localhost:8008 org.apache.oozie.servlet.BaseAdminServlet.doGet "ssh example.test.com ls /tmp > 1"
```

Make sure ``tools.jar`` is set correctly on your platform. I tested only on Mac OSX using JDK 8. 
