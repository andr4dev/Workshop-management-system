@echo off
setlocal
REM ============================================================================
REM  RD MOTORS - Acceso directo del POS en modo kiosco de impresion
REM
REM  Crea en el Escritorio "RD MOTORS POS": abre el sistema con Microsoft Edge y
REM  --kiosk-printing, asi al cobrar el comprobante sale solo por la ticketera.
REM
REM  NO instala el driver ni pone la ticketera como predeterminada: eso se hace
REM  una vez en Windows. Ver docs\INSTALAR_TICKETERA.md, pasos 1 y 2.
REM ============================================================================

echo.
echo  RD MOTORS - acceso directo del POS
echo.
echo  Escribe la direccion de RD MOTORS en la tienda.
echo  Si el servidor corre en este computador, deja vacio y se usa http://localhost:5174
echo.
set "URL="
set /p "URL=  Direccion: "
if "%URL%"=="" set "URL=http://localhost:5174"

REM --- Buscar Edge en las rutas de siempre --------------------------------------
set "EDGE="
if exist "%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe" set "EDGE=%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe"
if not defined EDGE if exist "%ProgramFiles%\Microsoft\Edge\Application\msedge.exe" set "EDGE=%ProgramFiles%\Microsoft\Edge\Application\msedge.exe"
if not defined EDGE if exist "%LocalAppData%\Microsoft\Edge\Application\msedge.exe" set "EDGE=%LocalAppData%\Microsoft\Edge\Application\msedge.exe"

if not defined EDGE (
  echo.
  echo  [ERROR] No se encontro Microsoft Edge.
  echo  Crea el acceso directo a mano: docs\INSTALAR_TICKETERA.md, Paso 3, opcion B.
  echo.
  pause
  exit /b 1
)

REM --- Crear el acceso directo en el Escritorio real ------------------------------
REM  GetFolderPath('Desktop') respeta el Escritorio movido a OneDrive, que es la causa
REM  habitual de que el acceso directo "no aparezca". Todo en una linea de PowerShell
REM  para no pelear con la continuacion de lineas del .bat.
REM  --user-data-dir: perfil propio, para que el modo kiosco aplique aunque haya otras
REM  ventanas de Edge abiertas.
powershell -NoProfile -ExecutionPolicy Bypass -Command "try { $escritorio=[Environment]::GetFolderPath('Desktop'); $ruta=Join-Path $escritorio 'RD MOTORS POS.lnk'; $ws=New-Object -ComObject WScript.Shell; $a=$ws.CreateShortcut($ruta); $a.TargetPath=$env:EDGE; $a.Arguments='--kiosk-printing --user-data-dir=' + $env:PUBLIC + '\RDMotorsPOS --no-first-run --no-default-browser-check --app=' + $env:URL; $a.IconLocation=$env:EDGE + ',0'; $a.Description='RD MOTORS POS'; $a.Save(); Write-Host ''; Write-Host '  [OK] Acceso directo creado en:'; Write-Host ('       ' + $ruta) } catch { Write-Host ('  [ERROR] No se pudo crear: ' + $_.Exception.Message); exit 1 }"

echo.
echo  Falta, una sola vez en Windows (docs\INSTALAR_TICKETERA.md):
echo    1. Driver de la ticketera y pagina de prueba.
echo    2. Papel de 80 mm, margenes en 0, y la ticketera como PREDETERMINADA.
echo    3. Abrir RD MOTORS SIEMPRE desde "RD MOTORS POS".
echo.
pause
endlocal
