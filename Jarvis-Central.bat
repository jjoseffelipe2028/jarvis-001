@echo off
REM Jarvis 001 - Central (Tickets 014 e 015)
REM Liga a API (se estiver desligada), espera ela responder e abre a Central em "modo app".
REM Tudo em 127.0.0.1: nada sai do PC. Este arquivo nao le nem mostra o token.
setlocal
title Jarvis 001 - Central
set "URL=http://127.0.0.1:8080/"
set "EDGE=%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe"
if not exist "%EDGE%" set "EDGE=%ProgramFiles%\Microsoft\Edge\Application\msedge.exe"
set "BASH=%ProgramFiles%\Git\bin\bash.exe"

REM 1) A API ja esta ligada? Entao so abre a Central.
curl.exe -s -f -o nul --max-time 2 "%URL%acoes/hora" && goto abrir

REM 2) Nao esta: liga a API numa janela minimizada ("Jarvis API").
if not exist "%BASH%" (
  echo [ERRO] Git Bash nao encontrado em "%BASH%".
  pause
  exit /b 1
)
echo Ligando a API do Jarvis... (a janela "Jarvis API" fica minimizada)
start "Jarvis API" /min /d "%~dp0java\bracos" "%BASH%" -c "./iniciar.sh"

REM 3) Espera ate 2 minutos a API responder.
set /a TENTATIVAS=0
:esperar
timeout /t 1 /nobreak >nul
curl.exe -s -f -o nul --max-time 2 "%URL%acoes/hora" && goto abrir
set /a TENTATIVAS+=1
if %TENTATIVAS% lss 120 goto esperar
echo [ERRO] A API nao respondeu em 2 minutos. Abra a janela "Jarvis API" para ver o motivo.
pause
exit /b 1

:abrir
REM 4) Le a chave de USO UNICO da Central (a API cria ao ligar e troca a cada uso).
REM    Ela vai depois do "#": o navegador nao manda essa parte pela rede nem para o log.
set "CHAVE="
set "ARQ_CHAVE=%~dp0java\bracos\.central-chave"
if exist "%ARQ_CHAVE%" set /p CHAVE=<"%ARQ_CHAVE%"
set "DESTINO=%URL%"
if defined CHAVE set "DESTINO=%URL%#k=%CHAVE%"

REM 5) Abre a Central em janela propria, sem barra de endereco.
if not exist "%EDGE%" (
  start "" "%DESTINO%"
) else (
  start "" "%EDGE%" --app=%DESTINO% --window-size=1440,900
)
exit /b 0
