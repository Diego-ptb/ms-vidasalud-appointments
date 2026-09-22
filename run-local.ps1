# Levanta el microservicio de atenciones leyendo la configuracion de infra\apps\.env
#
# Uso:  cd ms-vidasalud-appointments ;  .\run-local.ps1

$jdk17 = "C:\Program Files\Java\jdk-17"
if (Test-Path $jdk17) {
    $env:JAVA_HOME = $jdk17
    $env:PATH = "$jdk17\bin;$env:PATH"
}

# Carga la configuracion compartida si el repo esta dentro del workspace
# completo. Si clonaste solo este repositorio, define las variables a mano
# (ver README) y el script funciona igual.
$compartido = Join-Path $PSScriptRoot "..\cargar-env.ps1"
if (Test-Path $compartido) {
    . $compartido
} else {
    Write-Host "No encontre ..\cargar-env.ps1: uso las variables ya definidas en la sesion." -ForegroundColor Yellow
}


Write-Host ""
Write-Host "Perfil : $env:SPRING_PROFILES_ACTIVE" -ForegroundColor Cyan
Write-Host "BD     : $env:DB_APPOINTMENTS_URL" -ForegroundColor Cyan
Write-Host "Puerto : 8081" -ForegroundColor Cyan
Write-Host ""

mvn spring-boot:run
