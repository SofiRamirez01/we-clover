@echo off
setlocal
set "ROOT=%~dp0"

if not exist "%ROOT%services\pieza-geometria\.venv\Scripts\python.exe" (
  echo [AVISO] No se encontro el entorno virtual de Python en services\pieza-geometria\.venv
  echo Crealo una vez con:
  echo   cd services\pieza-geometria
  echo   python -m venv .venv
  echo   .venv\Scripts\pip install -r requirements.txt
  echo.
)

start "Backend Java - 8080" cmd /k "cd /d "%ROOT%backend" && mvnw.cmd spring-boot:run"
start "Servicio Geometria Python - 8001" cmd /k "cd /d "%ROOT%services\pieza-geometria" && .venv\Scripts\python.exe -m uvicorn app.main:app --port 8001"
start "Frontend Vite" cmd /k "cd /d "%ROOT%frontend" && npm run dev"

echo.
echo Se abrieron 3 ventanas: Backend (8080), Servicio de Geometria (8001) y Frontend.
echo Para apagar todo, cerra cada ventana (o Ctrl+C dentro de cada una).
endlocal
