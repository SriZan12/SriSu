@echo off
py -3 "%~dp0run.py" %*
set "srisuTestExit=%errorlevel%"
if "%~1"=="" pause
exit /b %srisuTestExit%
