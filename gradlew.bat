@echo off

setlocal

set APP_HOME=%~dp0

set WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar

if not exist "%WRAPPER_JAR%" (

  echo Missing gradle wrapper jar at %WRAPPER_JAR%

  exit /b 1

)

java -cp "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*

endlocal

