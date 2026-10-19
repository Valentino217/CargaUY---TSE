param([Parameter(Mandatory=$true)][string]$WildFlyHome)
$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$propsPath=Join-Path $projectRoot '.local-p1/wildfly.properties'
$cliPath=Join-Path $WildFlyHome 'bin/jboss-cli.bat'
if(-not (Test-Path -LiteralPath $cliPath)){throw 'No se encontró jboss-cli.bat'}
$lines=[IO.File]::ReadAllLines($propsPath)
$commands=New-Object 'System.Collections.Generic.List[string]'
foreach($line in $lines){
    if([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')){continue}
    $parts=$line.Split('=',2)
    $key=$parts[0]; $value=$parts[1]
    if($key -notmatch '^[a-zA-Z0-9.]+$' -or $value.Contains('"') -or $value.Contains('\')){
        throw 'Propiedad no admitida por el generador CLI'
    }
    $commands.Add("if (outcome == success) of /system-property=$key" + ':read-resource')
    $commands.Add("/system-property=$key" + ':write-attribute(name=value,value="' + $value + '")')
    $commands.Add('else')
    $commands.Add("/system-property=$key" + ':add(value="' + $value + '")')
    $commands.Add('end-if')
}
$destino=Join-Path $projectRoot '.local-p1/configurar.cli'
[IO.File]::WriteAllLines($destino,$commands,(New-Object Text.UTF8Encoding($false)))
& $cliPath '--connect' "--file=$destino"
if($LASTEXITCODE -ne 0){throw 'WildFly rechazó la configuración; revisar la salida'}
Write-Output 'Propiedades configuradas. Reiniciar WildFly después de aplicar la migración y publicar el EAR.'
