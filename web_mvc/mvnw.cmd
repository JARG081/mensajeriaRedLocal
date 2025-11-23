@echo off
REM Lightweight wrapper delegating to web_mvc_demo\mvnw.cmd
SET THIS_DIR=%~dp0
REM If the demo wrapper exists, forward all args to it and ensure we run against this module's pom
IF EXIST "%THIS_DIR%web_mvc_demo\mvnw.cmd" (
  "%THIS_DIR%web_mvc_demo\mvnw.cmd" -f "%THIS_DIR%pom.xml" %*
  EXIT /B %ERRORLEVEL%
) ELSE (
  ECHO Could not find web_mvc_demo\mvnw.cmd to delegate to. Please install Maven or restore the wrapper.
  EXIT /B 1
)
