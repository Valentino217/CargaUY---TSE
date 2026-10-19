$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$sourcePath=Join-Path $projectRoot 'gub-callback-root/src/main/webapp'
$targetPath=Join-Path $projectRoot 'target/p1-callback'
New-Item -ItemType Directory -Path $targetPath -Force | Out-Null
$warPath=Join-Path $targetPath 'gub-callback-root.war'
if(Test-Path -LiteralPath $warPath){throw 'El WAR ya existe. Renombrarlo o quitar ese archivo antes de regenerar.'}
Add-Type -AssemblyName System.IO.Compression.FileSystem
[IO.Compression.ZipFile]::CreateFromDirectory($sourcePath,$warPath)
Write-Output "WAR creado: $warPath"
Write-Output 'Desplegar este WAR adicional en el mismo WildFly que CargaUY; no modificar el context-root del EAR.'

