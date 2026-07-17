# ============================================================
# Script de REPRISE du nettoyage FBO+ (apres le blocage lecture seule)
# A executer depuis la racine du repo : D:\ANDROID_SDK\FBO
# ============================================================

$ErrorActionPreference = "Stop"

$base = "app\src\main\java\com\benjo\fboplus"
$themeDir = "$base\ui\theme"
$screensDir = "$base\ui\screens"
$navDir = "$base\ui\navigation"

Write-Host "0) Suppression de l'attribut lecture seule sur tous les fichiers du projet..." -ForegroundColor Cyan
Get-ChildItem -Path "$base" -Recurse -File | ForEach-Object {
    if ($_.IsReadOnly) {
        $_.IsReadOnly = $false
        Write-Host "   deverrouille : $($_.FullName)"
    }
}

Write-Host "1) Correction de HomeScreen.kt (deja deplace, package pas encore corrige)..." -ForegroundColor Cyan
$homeScreenPath = "$screensDir\HomeScreen.kt"
if (Test-Path $homeScreenPath) {
    (Get-Content $homeScreenPath) -replace `
        'package com\.benjo\.fboplus\.ui\.theme', `
        'package com.benjo.fboplus.ui.screens' | `
        Set-Content $homeScreenPath
    Write-Host "   package corrige : HomeScreen.kt"
} else {
    Write-Host "   ATTENTION : HomeScreen.kt introuvable dans ui/screens, verifie manuellement" -ForegroundColor Red
}

Write-Host "2) Deplacement des ecrans restants vers ui/screens..." -ForegroundColor Cyan
$remainingScreens = @(
    "VentesScreen.kt",
    "StatsScreen.kt",
    "ProfilScreen.kt",
    "PathologieScreen.kt",
    "HistoriqueScreen.kt",
    "NouvelleCommandeScreen.kt",
    "ModifierCommandeScreen.kt"
)
foreach ($f in $remainingScreens) {
    $src = "$themeDir\$f"
    $dst = "$screensDir\$f"
    if (Test-Path $src) {
        Move-Item $src $dst -Force
        (Get-Content $dst) -replace `
            'package com\.benjo\.fboplus\.ui\.theme', `
            'package com.benjo.fboplus.ui.screens' | `
            Set-Content $dst
        Write-Host "   deplace + package corrige : $f"
    } elseif (Test-Path $dst) {
        Write-Host "   deja deplace, on saute : $f" -ForegroundColor DarkGray
    } else {
        Write-Host "   ATTENTION : $f introuvable ni dans theme ni dans screens" -ForegroundColor Red
    }
}

Write-Host "3) Deplacement de Navigation.kt vers ui/navigation..." -ForegroundColor Cyan
$navSrc = "$themeDir\Navigation.kt"
$navDst = "$navDir\Navigation.kt"
if (Test-Path $navSrc) {
    Move-Item $navSrc $navDst -Force
} elseif (Test-Path $navDst) {
    Write-Host "   Navigation.kt deja deplace, on saute" -ForegroundColor DarkGray
} else {
    Write-Host "   ATTENTION : Navigation.kt introuvable" -ForegroundColor Red
}

if (Test-Path $navDst) {
    $navContent = Get-Content $navDst
    # On ne corrige que si ce n'est pas deja fait (evite les doublons d'import si on relance)
    if ($navContent -join "`n" -notmatch 'package com\.benjo\.fboplus\.ui\.navigation') {
        $navContent = $navContent -replace 'package com\.benjo\.fboplus\.ui\.theme', 'package com.benjo.fboplus.ui.navigation'

        $screenImports = @(
            "import com.benjo.fboplus.ui.screens.HomeScreen",
            "import com.benjo.fboplus.ui.screens.VentesScreen",
            "import com.benjo.fboplus.ui.screens.StatsScreen",
            "import com.benjo.fboplus.ui.screens.ProfilScreen",
            "import com.benjo.fboplus.ui.screens.PathologieScreen",
            "import com.benjo.fboplus.ui.screens.HistoriqueScreen",
            "import com.benjo.fboplus.ui.screens.NouvelleCommandeScreen",
            "import com.benjo.fboplus.ui.screens.ModifierCommandeScreen"
        )

        $packageLineIndex = ($navContent | Select-String -Pattern '^package ').LineNumber[0] - 1
        $before = $navContent[0..$packageLineIndex]
        $after = $navContent[($packageLineIndex + 1)..($navContent.Length - 1)]
        $newNavContent = $before + "" + $screenImports + $after
        Set-Content $navDst $newNavContent
        Write-Host "   package corrige + imports des ecrans ajoutes dans Navigation.kt"
    } else {
        Write-Host "   Navigation.kt deja corrige, on ne touche a rien" -ForegroundColor DarkGray
    }
}

Write-Host "4) Correction de l'import dans MainActivity.kt..." -ForegroundColor Cyan
$mainActivity = "$base\MainActivity.kt"
$mainContent = Get-Content $mainActivity
if ($mainContent -join "`n" -match 'import com\.benjo\.fboplus\.ui\.theme\.FBONavigation') {
    $mainContent -replace `
        'import com\.benjo\.fboplus\.ui\.theme\.FBONavigation', `
        'import com.benjo.fboplus.ui.navigation.FBONavigation' | `
        Set-Content $mainActivity
    Write-Host "   import FBONavigation corrige"
} else {
    Write-Host "   MainActivity.kt deja corrige, on ne touche a rien" -ForegroundColor DarkGray
}

Write-Host ""
Write-Host "5) Etat final des dossiers :" -ForegroundColor Cyan
Write-Host "--- ui/theme ---"
Get-ChildItem $themeDir -Name
Write-Host "--- ui/screens ---"
Get-ChildItem $screensDir -Name
Write-Host "--- ui/navigation ---"
Get-ChildItem $navDir -Name

Write-Host ""
Write-Host "Nettoyage termine. Verifie que le projet compile dans Android Studio," -ForegroundColor Green
Write-Host "puis fais :" -ForegroundColor Green
Write-Host "   git add -A" -ForegroundColor Yellow
Write-Host "   git commit -m 'Nettoyage : suppression doublons images + reorganisation ui/screens et ui/navigation'" -ForegroundColor Yellow
Write-Host "   git push" -ForegroundColor Yellow
