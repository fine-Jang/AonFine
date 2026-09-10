param(
    [string]$MavenCommand = (Join-Path $env:USERPROFILE 'orca\workspaces\AonFine\logperch\target\build-tools\apache-maven-3.9.9\bin\mvn.cmd')
)
$ErrorActionPreference = 'Stop'
$adaProject = [System.IO.Path]::GetFullPath($PSScriptRoot)
$adaWorkspace = [System.IO.Path]::GetFullPath((Join-Path $adaProject '..'))
$adaBuiltWar = Join-Path $adaProject 'target\ADA.war'
$adaLocalWar = Join-Path $adaWorkspace 'ADA.war'
if (-not (Test-Path -LiteralPath $MavenCommand)) { throw 'Maven executable not found. Supply -MavenCommand.' }
Push-Location $adaProject
try {
    & node --check src/main/webapp/js/ada-upload.js
    if ($LASTEXITCODE -ne 0) { throw 'JavaScript syntax check failed.' }
    & node --test src/test/js/ada-upload.test.cjs
    if ($LASTEXITCODE -ne 0) { throw 'JavaScript interaction tests failed.' }
    & $MavenCommand clean package
    if ($LASTEXITCODE -ne 0) { throw 'Maven build/tests failed; workspace WAR was not replaced.' }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $adaArchive = [System.IO.Compression.ZipFile]::OpenRead($adaBuiltWar)
    try {
        foreach ($adaRequired in @('WEB-INF/classes/com/aonfine/ada/upload/UploadCancellation.class',
                'WEB-INF/classes/com/aonfine/ada/analysis/AnalysisState.class', 'js/ada-upload.js')) {
            if ($null -eq $adaArchive.GetEntry($adaRequired)) { throw ('Missing WAR entry: ' + $adaRequired) }
        }
        if ($adaArchive.Entries.FullName -match 'WEB-INF/lib/(h2-|spring-test-|spring-modules-validation-)') {
            throw 'WAR contains an excluded/test dependency.'
        }
    } finally { $adaArchive.Dispose() }
    if (Test-Path -LiteralPath $adaLocalWar) {
        $adaBackup = $adaLocalWar + '.bak.' + (Get-Date -Format 'yyyyMMddHHmmssfff')
        Copy-Item -LiteralPath $adaLocalWar -Destination $adaBackup -ErrorAction Stop
        if ((Get-FileHash -LiteralPath $adaLocalWar).Hash -ne (Get-FileHash -LiteralPath $adaBackup).Hash) {
            throw 'WAR backup verification failed.'
        }
        Write-Output ('Previous WAR backup: ' + $adaBackup)
    }
    Copy-Item -LiteralPath $adaBuiltWar -Destination $adaLocalWar -Force
    $adaHash = (Get-FileHash -LiteralPath $adaLocalWar -Algorithm SHA256).Hash
    if ($adaHash -ne (Get-FileHash -LiteralPath $adaBuiltWar -Algorithm SHA256).Hash) { throw 'WAR copy verification failed.' }
    Write-Output ('Local WAR: ' + $adaLocalWar)
    Write-Output ('SHA256: ' + $adaHash)
    Write-Output 'No DB changes or server deployment were performed.'
} finally { Pop-Location }
