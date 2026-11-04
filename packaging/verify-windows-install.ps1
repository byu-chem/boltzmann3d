param(
    [Parameter(Mandatory = $true)][string]$Installer,
    [string]$TestClasses = 'build/test-classes'
)

$ErrorActionPreference = 'Stop'
$installerPath = (Resolve-Path $Installer).Path
$testClassesPath = (Resolve-Path $TestClasses).Path
$installPath = Join-Path ([System.IO.Path]::GetTempPath()) ('Boltzmann3D-package-test-' + [guid]::NewGuid())

function Invoke-Installer([string[]]$InstallerArguments) {
    $process = Start-Process -FilePath $installerPath -ArgumentList $InstallerArguments -PassThru
    if (-not $process.WaitForExit(180000)) {
        $process.Kill()
        throw 'Installer exceeded the three-minute verification timeout'
    }
    if ($process.ExitCode -notin @(0, 3010)) {
        throw "Installer returned exit code $($process.ExitCode)"
    }
}

$installed = $false
try {
    Invoke-Installer @('/quiet', '/norestart', "INSTALLDIR=`"$installPath`"")
    $installed = $true
    $runtime = Join-Path $installPath 'runtime'
    $java = Join-Path $runtime 'bin/java.exe'
    $launcher = Join-Path $installPath 'Boltzmann3D.exe'
    if (-not (Test-Path $java) -or -not (Test-Path $launcher)) {
        throw "Installed launcher or bundled runtime is missing from $installPath"
    }
    $classPath = "$installPath/app/Boltzmann3D.jar;$installPath/app/lib/*;$testClassesPath"
    & $java '-Djava.awt.headless=true' '--enable-native-access=ALL-UNNAMED' '-ea' '-cp' $classPath `
        'edu.byu.chem.boltzmann.tests.PackagedRuntimeSmokeTest' $runtime '25'
    if ($LASTEXITCODE -ne 0) { throw "Installed runtime test failed: $LASTEXITCODE" }
    Write-Output 'PASS: Windows installer and installed bundled runtime'
} finally {
    if ($installed) {
        Invoke-Installer @('uninstall')
        if (Test-Path $launcher) { throw 'Uninstall left the application launcher installed' }
        Write-Output 'PASS: Windows uninstallation'
    }
}
