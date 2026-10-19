param([string]$Base='http://localhost:8080/CargaUY-web',[string]$Uid='martin.admin')
$ErrorActionPreference='Stop'
$secure=Read-Host 'Contraseña LDAP de CargaUY' -AsSecureString
$ptr=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
$login=$null
try{
 $password=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
 $body=@{uid=$Uid;password=$password}|ConvertTo-Json
 $login=Invoke-RestMethod "$Base/api/auth/login" -Method Post -ContentType 'application/json' -Body $body
 $headers=@{Authorization='Bearer '+$login.token}
 $me=Invoke-RestMethod "$Base/api/auth/me" -Headers $headers
 if($me.roles -notcontains 'ADMINISTRADOR'){throw 'La cuenta no tiene rol administrador'}
 $ok=Invoke-RestMethod "$Base/api/ciudadanos/12345678/pdi" -Headers $headers
 $no=Invoke-RestMethod "$Base/api/ciudadanos/11111111/pdi" -Headers $headers
 if($ok.estado -ne 'DISPONIBLE' -or $no.estado -ne 'NO_ENCONTRADO'){throw 'PDI no respondió según el contrato nuevo'}
 Invoke-RestMethod "$Base/api/auth/logout" -Headers $headers -Method Post|Out-Null
 $revocado=$false
 try{Invoke-RestMethod "$Base/api/auth/me" -Headers $headers|Out-Null}
 catch{if([int]$_.Exception.Response.StatusCode -eq 401){$revocado=$true}else{throw}}
 if(-not $revocado){throw 'El token no quedó revocado'}
 Write-Output 'API_OK: LDAP, bearer, PDI, ciudadano inexistente y logout.'
}finally{
 if($login){try{Invoke-RestMethod "$Base/api/auth/logout" -Headers @{Authorization='Bearer '+$login.token} -Method Post|Out-Null}catch{}}
 [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
 $password=$null;$body=$null;$login=$null
}
