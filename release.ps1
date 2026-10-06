param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^\d+\.\d+\.\d+$')]
    [string]$Version
)

$ErrorActionPreference = "Stop"
$repo = "Hacer599/PlasmoVoice---Simple-Voice-Chat-Integration"
$tag = "v$Version"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$git = "C:\Program Files\Git\cmd\git.exe"
$gradle = Join-Path $root "gradlew.bat"
$dist = Join-Path $root "dist"
$stage = Join-Path $env:TEMP ("pv-svc-bridge-release-" + [guid]::NewGuid().ToString("N"))

if (-not (Test-Path $git)) {
    throw "Git for Windows was not found at $git"
}
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw "GitHub CLI is not installed. Install it with: winget install --id GitHub.cli -e"
}

Push-Location $root
try {
    & gh auth status
    if ($LASTEXITCODE -ne 0) {
        throw "Sign in to GitHub CLI first by running: gh auth login"
    }

    $changes = & $git status --porcelain
    if ($LASTEXITCODE -ne 0) {
        throw "Could not inspect the Git working tree"
    }
    if ($changes) {
        throw "Commit and push all project changes before creating a release."
    }

    & $git fetch origin
    if ($LASTEXITCODE -ne 0) {
        throw "Could not fetch the GitHub repository"
    }
    $localCommit = (& $git rev-parse HEAD).Trim()
    $remoteCommit = (& $git rev-parse origin/main).Trim()
    if ($LASTEXITCODE -ne 0 -or $localCommit -ne $remoteCommit) {
        throw "Local main must match origin/main. Push the latest commit before creating a release."
    }

    $releaseList = & gh release list --repo $repo --json tagName --limit 1000
    if ($LASTEXITCODE -ne 0) {
        throw "Could not check existing GitHub releases"
    }
    $existingReleases = @($releaseList | ConvertFrom-Json)
    if (@($existingReleases | Where-Object { $_.tagName -eq $tag }).Count -gt 0) {
        throw "Release $tag already exists. Choose a new version."
    }

    $profiles = @(Get-ChildItem (Join-Path $root "versions") -Filter "*.properties")
    $modJars = @(Get-ChildItem $dist -Filter "pv-svc-bridge-*.jar")
    if ($modJars.Count -ne $profiles.Count) {
        throw "Expected $($profiles.Count) mod JARs in dist, found $($modJars.Count). Run build-all.bat first."
    }

    New-Item -ItemType Directory -Path $stage | Out-Null
    foreach ($profile in $profiles) {
        $minecraftVersion = $profile.BaseName
        Write-Host "Building source JAR for Minecraft $minecraftVersion"

        & $gradle clean build "-Pmc=$minecraftVersion"
        if ($LASTEXITCODE -ne 0) {
            throw "Build failed for Minecraft $minecraftVersion"
        }

        $sourceJar = Get-ChildItem (Join-Path $root "build\libs") `
            -Filter "*+mc$minecraftVersion-sources.jar" |
            Select-Object -First 1
        if (-not $sourceJar) {
            throw "Source JAR was not produced for Minecraft $minecraftVersion"
        }
        Copy-Item $sourceJar.FullName $stage
    }

    $projectZip = Join-Path $stage "pv-svc-bridge-$tag-project-source.zip"
    & $git archive --format=zip "--output=$projectZip" HEAD
    if ($LASTEXITCODE -ne 0) {
        throw "Could not create the project source ZIP"
    }

    $sourceJars = @(Get-ChildItem $stage -Filter "*-sources.jar")
    if ($sourceJars.Count -ne $profiles.Count) {
        throw "Expected $($profiles.Count) source JARs, found $($sourceJars.Count)"
    }

    $notes = @"
## Installation
Download the mod JAR matching your Minecraft version and place it in the `mods` folder with Plasmo Voice.

## Release files
- Mod JARs for each supported Minecraft version
- Matching `-sources.jar` files
- ZIP archive of the project source

## Requirements
- Fabric Loader and Fabric API
- Plasmo Voice 2.1.16 or newer
- The Simple Voice Chat client mod is not required

## Compatibility
Runtime audio testing has been completed on Minecraft 1.21.11. Other versions are build-checked and still need in-game testing with matching servers.
"@

    $assets = @($modJars | ForEach-Object { $_.FullName })
    $assets += @($sourceJars | ForEach-Object { $_.FullName })
    $assets += $projectZip
    $arguments = @(
        "release", "create", $tag
    ) + $assets + @(
        "--repo", $repo,
        "--target", "main",
        "--title", "Plasmo Voice SVC Bridge $Version",
        "--notes", $notes
    )

    & gh @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "GitHub release creation failed. Staged files were kept at $stage"
    }

    Remove-Item -LiteralPath $stage -Recurse -Force
    Write-Host "Published release $tag"
}
finally {
    Pop-Location
}
