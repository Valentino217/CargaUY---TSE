@echo off
setlocal
set "JAVA_HOME=C:\Program Files\Java\jdk-17"
set "M2_HOME=C:\Users\valen\apache-maven-3.9.9"
set "PATH=%M2_HOME%\bin;%JAVA_HOME%\bin;%PATH%"
"%M2_HOME%\bin\mvn.cmd" %*
endlocal
