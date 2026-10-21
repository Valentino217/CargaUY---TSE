$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$sourcePath=Join-Path $projectRoot 'gub-callback-root/target/gub-callback-root.war'
$targetPath=Join-Path $projectRoot 'target/p1-callback'
New-Item -ItemType Directory -Path $targetPath -Force | Out-Null
$warPath=Join-Path $targetPath 'gub-callback-root.war'
if(Test-Path -LiteralPath $warPath){throw 'El WAR ya existe. Renombrarlo o quitar ese archivo antes de regenerar.'}
Push-Location $projectRoot
try {
    & mvn -B -ntp -pl gub-callback-root -am -DskipTests package
    if($LASTEXITCODE -ne 0){throw 'No se pudo compilar el callback.'}
} finally {Pop-Location}
Copy-Item -LiteralPath $sourcePath -Destination $warPath
Write-Output "WAR creado: $warPath"
Write-Output 'Desplegar este WAR adicional en el mismo WildFly que CargaUY; no modificar el context-root del EAR.'
