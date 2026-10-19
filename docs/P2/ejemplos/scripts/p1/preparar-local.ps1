param(
    [string]$Uid = 'martin.admin',
    [int]$Ci = 90000001
)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$privatePath = Join-Path $projectRoot '.local-p1'
if ($Uid -notmatch '^[a-zA-Z0-9._-]{1,64}$' -or $Ci -le 0) { throw 'UID o CI inválidos' }
$ignorePath = Join-Path $projectRoot '.gitignore'
if (-not (Test-Path -LiteralPath $ignorePath) -or
    -not ([IO.File]::ReadAllText($ignorePath) -match '(?m)^/?\.local-p1/?\s*$')) {
    throw 'Primero agregar /.local-p1/ a .gitignore'
}
if (Test-Path -LiteralPath (Join-Path $privatePath 'servicios.env')) {
    throw 'Ya existe configuración local. No regenerar: LDAP y Keycloak conservan sus datos.'
}
New-Item -ItemType Directory -Path $privatePath -Force | Out-Null
function New-Secret {
    $bytes = New-Object byte[] 32
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($bytes) } finally { $random.Dispose() }
    return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+','-').Replace('/','_')
}
$ldapRoot = New-Secret
$ldapApp = New-Secret
$kcAdmin = New-Secret
$gubPassword = New-Secret
$clientSecret = New-Secret
$utf8 = New-Object Text.UTF8Encoding($false)
$envText = @"
LDAP_ROOT_PASSWORD=$ldapRoot
LDAP_APP_UID=$Uid
LDAP_APP_PASSWORD=$ldapApp
KC_BOOTSTRAP_ADMIN_USERNAME=gestor-idp
KC_BOOTSTRAP_ADMIN_PASSWORD=$kcAdmin
"@
[IO.File]::WriteAllText((Join-Path $privatePath 'servicios.env'),$envText,$utf8)
$realm = @{
    realm='cargauy-test'; enabled=$true; sslRequired='none'; registrationAllowed=$false
    users=@(@{
        id='11111111-1111-4111-8111-111111111111'
        username='ciudadano.prueba'; enabled=$true; emailVerified=$true
        email='ana@example.invalid'; firstName='Ana'; lastName='Prueba'
        attributes=@{ci=@('80000001'); fechaNacimiento=@('1990-01-01')}
        credentials=@(@{type='password'; value=$gubPassword; temporary=$false})
    })
    clients=@(@{
        clientId='cargauy'; enabled=$true; protocol='openid-connect'
        publicClient=$false; secret=$clientSecret; standardFlowEnabled=$true
        directAccessGrantsEnabled=$false; implicitFlowEnabled=$false
        redirectUris=@('http://localhost:8080/CargaUY-web/gub/callback','uy.cargauy:/oauth/callback')
        webOrigins=@('http://localhost:8080')
        defaultClientScopes=@('profile','email')
        attributes=@{'pkce.code.challenge.method'='S256'; 'id.token.signed.response.alg'='RS256'}
        protocolMappers=@(
            @{
                name='ci'; protocol='openid-connect'; protocolMapper='oidc-usermodel-attribute-mapper'
                config=@{'user.attribute'='ci'; 'claim.name'='ci'; 'jsonType.label'='String';
                    'id.token.claim'='true'; 'access.token.claim'='true'; 'userinfo.token.claim'='true'}
            },
            @{
                name='nacimiento'; protocol='openid-connect'; protocolMapper='oidc-usermodel-attribute-mapper'
                config=@{'user.attribute'='fechaNacimiento'; 'claim.name'='fechaNacimiento'; 'jsonType.label'='String';
                    'id.token.claim'='true'; 'access.token.claim'='true'; 'userinfo.token.claim'='true'}
            }
        )
    })
}
[IO.File]::WriteAllText((Join-Path $privatePath 'gub-realm.json'),
    ($realm | ConvertTo-Json -Depth 30),$utf8)
$props = @"
p1.entorno.local=true
p1.bootstrap=true
bootstrap.admin.uid=$Uid
bootstrap.admin.ci=$Ci
bootstrap.admin.nombre=Administrador de pruebas
bootstrap.admin.mail=admin@example.invalid
bootstrap.admin.nacimiento=1990-01-01
ldap.url=ldap://127.0.0.1:1389
ldap.people.dn=ou=people,dc=cargauy,dc=local
gub.proveedor=keycloak
gub.pkce=true
gub.issuer=http://localhost:8083/realms/cargauy-test
gub.client.id=cargauy
gub.client.secret=$clientSecret
gub.redirect=http://localhost:8080/CargaUY-web/gub/callback
gub.mobile.redirect=uy.cargauy:/oauth/callback
pdi.endpoint=http://localhost:8081/pdi/ws
p1.pruebas=false
"@
[IO.File]::WriteAllText((Join-Path $privatePath 'wildfly.properties'),$props,$utf8)
$cuentas = @"
Administrador de CargaUY (login LDAP)
Usuario: $Uid
Contraseña: $ldapApp
CI de prueba: $Ci

Administrador del directorio (solo herramientas LDAP)
DN: cn=manager,dc=cargauy,dc=local
Contraseña: $ldapRoot

Ciudadano del simulador gub.uy (Keycloak)
Usuario: ciudadano.prueba
Contraseña: $gubPassword
CI de prueba: 80000001

Administrador de Keycloak (solo consola localhost:8083)
Usuario: gestor-idp
Contraseña: $kcAdmin
"@
[IO.File]::WriteAllText((Join-Path $privatePath 'cuentas-locales.txt'),$cuentas,$utf8)
Write-Output 'Archivos privados creados en .local-p1. Abrir cuentas-locales.txt para consultar las credenciales.'
Write-Output 'No regenerar archivos ni subirlos al repositorio.'
