@echo off
REM Jarvis 001 - abre o chat com 2 cliques
title Jarvis 001
cd /d "%~dp0python"
if not exist ".venv\Scripts\activate.bat" (
  echo [ERRO] Ambiente .venv nao encontrado em python\.venv
  pause
  exit /b 1
)
call ".venv\Scripts\activate.bat"
python chat.py
pause
