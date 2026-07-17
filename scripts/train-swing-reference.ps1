[CmdletBinding()]
param(
    [string]$InputDirectory = (Join-Path $PSScriptRoot '..\training-data\best-swings'),
    [ValidatePattern('^[a-z0-9][a-z0-9._-]{2,63}$')]
    [string]$ProfileId = 'curated-swings-v1',
    [ValidateSet('prototype-only', 'licensed-commercial-ml')]
    [string]$RightsStatus = 'prototype-only',
    [ValidateRange(1, 100)]
    [int]$ExpectedCount = 10,
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\reference-training-output'),
    [string]$DeviceSerial
)

$ErrorActionPreference = 'Stop'
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$resolvedInput = if (Test-Path -LiteralPath $InputDirectory -PathType Container) {
    (Resolve-Path -LiteralPath $InputDirectory).Path
} else {
    throw "Training directory not found: $InputDirectory"
}

$expectedNames = @(1..$ExpectedCount | ForEach-Object { "swing_$_.mp4" })
$videoFiles = @(Get-ChildItem -LiteralPath $resolvedInput -File -Filter '*.mp4')
$actualNames = @($videoFiles.Name)
$missingNames = @($expectedNames | Where-Object { $_ -notin $actualNames })
$unexpectedNames = @($actualNames | Where-Object { $_ -notin $expectedNames })
if ($missingNames.Count -gt 0 -or $unexpectedNames.Count -gt 0) {
    throw "Expected exactly $ExpectedCount files named swing_1.mp4 through swing_$ExpectedCount.mp4. Missing: $($missingNames -join ', '). Unexpected: $($unexpectedNames -join ', ')."
}
$incompleteFiles = @($videoFiles | Where-Object Length -lt 1024)
if ($incompleteFiles.Count -gt 0) {
    throw "These files are too small to be complete videos: $($incompleteFiles.Name -join ', ')."
}
$videoFiles = @($videoFiles | Sort-Object { [int]($_.BaseName -replace '^swing_', '') })
$totalInputBytes = [int64](($videoFiles | Measure-Object -Property Length -Sum).Sum)
$hostHashes = @{}
foreach ($video in $videoFiles) {
    $hostHashes[$video.Name] = (Get-FileHash -LiteralPath $video.FullName -Algorithm SHA256).Hash
}

$adbCommand = Get-Command adb -ErrorAction SilentlyContinue | Select-Object -First 1
$adbPath = if ($adbCommand) {
    $adbCommand.Source
} else {
    Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
}
if (-not (Test-Path -LiteralPath $adbPath -PathType Leaf)) {
    throw 'adb was not found in PATH or the default local Android SDK.'
}

$deviceLines = @(& $adbPath devices | Select-Object -Skip 1 | Where-Object { $_ -match '^\S+\s+device$' })
if ($DeviceSerial) {
    $selectedDevice = $deviceLines | Where-Object { ($_ -split '\s+')[0] -eq $DeviceSerial }
    if (-not $selectedDevice) {
        throw "Android device $DeviceSerial is not connected and authorized."
    }
} elseif ($deviceLines.Count -eq 1) {
    $DeviceSerial = ($deviceLines[0] -split '\s+')[0]
} else {
    throw "Expected one connected Android device, found $($deviceLines.Count). Use -DeviceSerial when more than one is connected."
}
$adbPrefix = @('-s', $DeviceSerial)

function Invoke-AdbChecked {
    param([string[]]$CommandArguments)

    $commandOutput = @(& $adbPath @adbPrefix @CommandArguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "adb command failed: adb $($CommandArguments -join ' ')`n$($commandOutput -join "`n")"
    }
    return $commandOutput
}

function Export-PrivateTextFile {
    param(
        [string]$PackageName,
        [string]$RelativePath,
        [string]$Destination
    )

    $fileLines = @(& $adbPath @adbPrefix exec-out run-as $PackageName cat $RelativePath 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "Could not read private training output: $RelativePath`n$($fileLines -join "`n")"
    }
    [System.IO.File]::WriteAllText(
        $Destination,
        ($fileLines -join "`n"),
        [System.Text.UTF8Encoding]::new($false)
    )
}

$resolvedOutput = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $resolvedOutput -Force | Out-Null
$artifactPath = Join-Path $resolvedOutput 'swing-reference-profile.json'
$reportPath = Join-Path $resolvedOutput 'swing-reference-training-report.json'
Remove-Item -LiteralPath $artifactPath, $reportPath -Force -ErrorAction SilentlyContinue

$trainingPackage = 'com.example.sportsai.debug'
$testPackage = "$trainingPackage.test"
$privateInputRelative = 'files/reference-training-input'
$privateOutputRelative = 'files/reference-training'
$stagingDirectory = '/data/local/tmp/sportsai-reference-training'
$runFailure = $null
$installedTrainingPackage = $false
$installedTestPackage = $false
$startedDeviceTraining = $false
$originalStayAwakeSetting = $null
try {
    Push-Location $repoRoot
    try {
        & (Join-Path $repoRoot 'gradlew.bat') :app:assembleDebug :app:assembleDebugAndroidTest
        if ($LASTEXITCODE -ne 0) {
            throw 'Could not assemble the debug app and instrumentation APK.'
        }
    } finally {
        Pop-Location
    }

    $deviceAbi = ((Invoke-AdbChecked -CommandArguments @('shell', 'getprop', 'ro.product.cpu.abi')) -join '').Trim()
    $appApkPath = Join-Path $repoRoot "app\build\outputs\apk\debug\app-$deviceAbi-debug.apk"
    if (-not (Test-Path -LiteralPath $appApkPath -PathType Leaf)) {
        $appApkPath = Join-Path $repoRoot 'app\build\outputs\apk\debug\app-universal-debug.apk'
    }
    $testApkPath = Join-Path $repoRoot 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
    if (-not (Test-Path -LiteralPath $appApkPath -PathType Leaf) -or
        -not (Test-Path -LiteralPath $testApkPath -PathType Leaf)) {
        throw 'Expected debug app or instrumentation APK was not assembled.'
    }

    $freeSpaceLine = Invoke-AdbChecked -CommandArguments @('shell', 'df', '-k', '/sdcard') |
        Select-Object -Last 1
    $freeSpaceFields = $freeSpaceLine.Trim() -split '\s+'
    if ($freeSpaceFields.Count -lt 4 -or $freeSpaceFields[3] -notmatch '^\d+$') {
        throw "Could not determine device free space from: $freeSpaceLine"
    }
    $freeDeviceBytes = [int64]$freeSpaceFields[3] * 1KB
    $storageMonitor = Invoke-AdbChecked -CommandArguments @('shell', 'dumpsys', 'devicestoragemonitor')
    $lowStorageLine = $storageMonitor | Where-Object { $_ -match 'lowBytes=\d+' } |
        Select-Object -First 1
    $lowStorageMatch = [regex]::Match([string]$lowStorageLine, 'lowBytes=(\d+)')
    $lowStorageThresholdBytes = if ($lowStorageMatch.Success) {
        [int64]$lowStorageMatch.Groups[1].Value
    } else {
        512MB
    }
    $apkBytes = (Get-Item -LiteralPath $appApkPath).Length +
        (Get-Item -LiteralPath $testApkPath).Length
    $installationAllowance = [int64][Math]::Ceiling($apkBytes * 2.5)
    $requiredDeviceBytes = $totalInputBytes + $installationAllowance + $lowStorageThresholdBytes
    if ($freeDeviceBytes -lt $requiredDeviceBytes) {
        $requiredMiB = [Math]::Ceiling($requiredDeviceBytes / 1MB)
        $freeMiB = [Math]::Floor($freeDeviceBytes / 1MB)
        $inputMiB = [Math]::Ceiling($totalInputBytes / 1MB)
        $thresholdMiB = [Math]::Ceiling($lowStorageThresholdBytes / 1MB)
        throw "Not enough phone storage. Need about $requiredMiB MiB ($inputMiB MiB of videos, training APKs, and Android's $thresholdMiB MiB low-storage reserve); only $freeMiB MiB is free."
    }

    $originalStayAwakeSetting = ((Invoke-AdbChecked -CommandArguments @(
        'shell', 'settings', 'get', 'global', 'stay_on_while_plugged_in'
    )) -join '').Trim()
    if ($originalStayAwakeSetting -match '^\d+$') {
        $usbStayAwakeSetting = [int]$originalStayAwakeSetting -bor 2
        Invoke-AdbChecked -CommandArguments @(
            'shell', 'settings', 'put', 'global',
            'stay_on_while_plugged_in', $usbStayAwakeSetting
        ) | Out-Null
        Invoke-AdbChecked -CommandArguments @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP') | Out-Null
    }

    $existingTrainingPackage = @(& $adbPath @adbPrefix shell pm path $trainingPackage 2>$null) -join ''
    if ($existingTrainingPackage.Trim()) {
        throw "Disposable training package $trainingPackage is already installed. Remove it before training so existing debug data is not overwritten."
    }
    Invoke-AdbChecked -CommandArguments @('install', '-r', $appApkPath) |
        ForEach-Object { Write-Verbose $_ }
    $installedTrainingPackage = $true
    Invoke-AdbChecked -CommandArguments @('install', '-r', $testApkPath) |
        ForEach-Object { Write-Verbose $_ }
    $installedTestPackage = $true

    $appDataDirectory = ((Invoke-AdbChecked -CommandArguments @(
        'shell', 'run-as', $trainingPackage, 'pwd'
    )) -join '').Trim()
    if (-not $appDataDirectory.StartsWith('/data/')) {
        throw "Unexpected debug app data directory: $appDataDirectory"
    }
    $privateInputAbsolute = "$appDataDirectory/$privateInputRelative"
    Invoke-AdbChecked -CommandArguments @(
        'shell', 'run-as', $trainingPackage, 'rm', '-rf',
        $privateInputRelative, $privateOutputRelative
    ) | Out-Null
    Invoke-AdbChecked -CommandArguments @(
        'shell', 'run-as', $trainingPackage, 'mkdir', '-p', $privateInputRelative
    ) | Out-Null
    Invoke-AdbChecked -CommandArguments @('shell', 'rm', '-rf', $stagingDirectory) | Out-Null
    Invoke-AdbChecked -CommandArguments @('shell', 'mkdir', '-p', $stagingDirectory) | Out-Null
    foreach ($video in $videoFiles) {
        $stagedVideo = "$stagingDirectory/$($video.Name)"
        Invoke-AdbChecked -CommandArguments @('push', $video.FullName, $stagedVideo) |
            ForEach-Object { Write-Verbose $_ }
        Invoke-AdbChecked -CommandArguments @(
            'shell', 'run-as', $trainingPackage, 'cp',
            $stagedVideo, "$privateInputRelative/$($video.Name)"
        ) | Out-Null
        Invoke-AdbChecked -CommandArguments @('shell', 'rm', '-f', $stagedVideo) | Out-Null
    }

    $startedDeviceTraining = $true
    $instrumentOutput = [System.Collections.Generic.List[string]]::new()
    & $adbPath @adbPrefix shell am instrument -w -r `
            -e class com.example.sportsai.SwingReferenceTrainingInstrumentedTest `
            -e swingVideoDirectory $privateInputAbsolute `
            -e expectedSwingCount $ExpectedCount `
            -e swingProfileId $ProfileId `
            -e swingRightsStatus $RightsStatus `
            "$testPackage/androidx.test.runner.AndroidJUnitRunner" 2>&1 |
        ForEach-Object {
            $instrumentLine = [string]$_
            $instrumentOutput.Add($instrumentLine)
            Write-Host $instrumentLine
        }
    $instrumentExitCode = $LASTEXITCODE
    $instrumentText = $instrumentOutput -join "`n"
    if ($instrumentExitCode -ne 0 -or
        $instrumentText -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' -or
        $instrumentText -notmatch 'OK \(1 test\)') {
        $runFailure = 'Swing reference instrumentation failed. Inspect the pulled training report for clip-level diagnostics.'
    }
} catch {
    $runFailure = $_.Exception.Message
} finally {
    if ($originalStayAwakeSetting -match '^\d+$') {
        try {
            Invoke-AdbChecked -CommandArguments @(
                'shell', 'settings', 'put', 'global',
                'stay_on_while_plugged_in', $originalStayAwakeSetting
            ) | Out-Null
        } catch {
            Write-Warning "Could not restore the phone's stay-awake setting: $($_.Exception.Message)"
        }
    }
    if ($startedDeviceTraining -and $installedTrainingPackage) {
        try {
            Export-PrivateTextFile `
                -PackageName $trainingPackage `
                -RelativePath "$privateOutputRelative/swing-reference-training-report.json" `
                -Destination $reportPath
        } catch {
            Write-Warning "Could not pull private training report: $($_.Exception.Message)"
        }
        $artifactListing = @(
            & $adbPath @adbPrefix shell run-as $trainingPackage `
                ls "$privateOutputRelative/swing-reference-profile.json" 2>$null
        )
        if ($LASTEXITCODE -eq 0 -and $artifactListing.Count -gt 0) {
            try {
                Export-PrivateTextFile `
                    -PackageName $trainingPackage `
                    -RelativePath "$privateOutputRelative/swing-reference-profile.json" `
                    -Destination $artifactPath
            } catch {
                Write-Warning "Could not pull private reference artifact: $($_.Exception.Message)"
            }
        }
    }
    if ($installedTrainingPackage) {
        try {
            Invoke-AdbChecked -CommandArguments @(
                'shell', 'run-as', $trainingPackage, 'rm', '-rf',
                $privateInputRelative, $privateOutputRelative
            ) | Out-Null
        } catch {
            Write-Warning "Could not remove device training files: $($_.Exception.Message)"
        }
    }
    try {
        Invoke-AdbChecked -CommandArguments @('shell', 'rm', '-rf', $stagingDirectory) | Out-Null
    } catch {
        Write-Warning "Could not remove ADB staging files: $($_.Exception.Message)"
    }
    if ($installedTestPackage) {
        try {
            Invoke-AdbChecked -CommandArguments @('uninstall', $testPackage) | Out-Null
        } catch {
            Write-Warning "Could not uninstall disposable test package: $($_.Exception.Message)"
        }
    }
    if ($installedTrainingPackage) {
        try {
            Invoke-AdbChecked -CommandArguments @('uninstall', $trainingPackage) | Out-Null
        } catch {
            Write-Warning "Could not uninstall disposable training package: $($_.Exception.Message)"
        }
    }
}

$trainingReport = $null
if (Test-Path -LiteralPath $reportPath -PathType Leaf) {
    $trainingReport = Get-Content -LiteralPath $reportPath -Raw | ConvertFrom-Json
    foreach ($clip in $trainingReport.clips) {
        if (-not $hostHashes.ContainsKey($clip.fileName) -or
            $hostHashes[$clip.fileName] -ne $clip.sha256) {
            throw "Training report hash does not match the local source file: $($clip.fileName)."
        }
    }
}
if ($runFailure) {
    throw $runFailure
}
if (-not (Test-Path -LiteralPath $artifactPath -PathType Leaf) -or $null -eq $trainingReport) {
    throw 'Instrumentation passed but the expected reference artifact or training report was not pulled.'
}

Write-Output "Reference profile: $artifactPath"
Write-Output "Private training report: $reportPath"
Write-Output "Accepted clips: $($trainingReport.acceptedClipCount)/$ExpectedCount"
Write-Output "Rights status: $RightsStatus"