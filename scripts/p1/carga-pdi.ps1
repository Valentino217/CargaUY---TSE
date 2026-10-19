# Ejecutar con PowerShell 7. No mide el volumen de tracking exigido por la letra.
param([string]$Base='http://localhost:8080/CargaUY-web',[string]$Uid='martin.admin',
      [int]$Cantidad=100,[int]$Concurrencia=12)
$ErrorActionPreference='Stop'
if($PSVersionTable.PSVersion.Major -lt 7){throw 'Requiere PowerShell 7'}
if($Cantidad -lt 1 -or $Cantidad -gt 10000 -or $Concurrencia -lt 1 -or $Concurrencia -gt 64){throw 'Carga fuera del rango de esta prueba'}
$secure=Read-Host 'Contraseña LDAP de CargaUY' -AsSecureString
$ptr=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
$login=$null
try{
 $password=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
 $login=Invoke-RestMethod "$Base/api/auth/login" -Method Post -ContentType 'application/json' -Body (@{uid=$Uid;password=$password}|ConvertTo-Json)
 $token=$login.token
 $resultados=1..$Cantidad|ForEach-Object -ThrottleLimit $Concurrencia -Parallel {
  $reloj=[Diagnostics.Stopwatch]::StartNew()
  try{
   $r=Invoke-RestMethod "$using:Base/api/ciudadanos/12345678/pdi" -Headers @{Authorization='Bearer '+$using:token}
   [pscustomobject]@{estado=$r.estado;duracionMs=$reloj.Elapsed.TotalMilliseconds}
  }catch{[pscustomobject]@{estado='ERROR_HTTP';duracionMs=$reloj.Elapsed.TotalMilliseconds}}
 }
 $ordenados=@($resultados.duracionMs|Sort-Object)
 $indice=[Math]::Max(0,[Math]::Ceiling(0.95*$ordenados.Count)-1)
 $resultados|Group-Object estado|Select-Object Name,Count
 Write-Output ('P95_ms='+[Math]::Round($ordenados[$indice],2))
 $destino=Join-Path (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path '.local-p1/pdi-carga.csv'
 $resultados|Export-Csv -LiteralPath $destino -NoTypeInformation
 Write-Output 'Guardar estos resultados como evidencia de la configuración ensayada; no extrapolarlos a toda la plataforma.'
}finally{
 if($login){try{Invoke-RestMethod "$Base/api/auth/logout" -Headers @{Authorization='Bearer '+$login.token} -Method Post|Out-Null}catch{}}
 [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
 $password=$null;$login=$null;$token=$null
}