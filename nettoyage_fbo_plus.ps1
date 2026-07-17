# ============================================================
# Script de nettoyage du projet FBO+ (etape 1 de la roadmap)
# A executer depuis la RACINE du repo (le dossier qui contient "app")
# ============================================================

$ErrorActionPreference = "Stop"

$base = "app\src\main\java\com\benjo\fboplus"
$themeDir = "$base\ui\theme"
$screensDir = "$base\ui\screens"
$navDir = "$base\ui\navigation"

Write-Host "1) Suppression des images en doublon dans app/src/main..." -ForegroundColor Cyan
$strayFiles = @(
    "app\src\main\aloe_heat_lotion.jpg",
    "app\src\main\aloe_moisturizing_lotion.jpg",
    "app\src\main\nature_min.jpg",
    "app\src\main\ic_launcher-playstore.png"
)
foreach ($f in $strayFiles) {
    if (Test-Path $f) {
        Remove-Item $f -Force
        Write-Host "   supprime : $f"
    } else {
        Write-Host "   deja absent : $f" -ForegroundColor DarkGray
    }
}

Write-Host "2) Creation des nouveaux dossiers ui/screens et ui/navigation..." -ForegroundColor Cyan
New-Item -ItemType Directory -Force -Path $screensDir | Out-Null
New-Item -ItemType Directory -Force -Path $navDir | Out-Null

Write-Host "3) Deplacement des ecrans vers ui/screens..." -ForegroundColor Cyan
$screenFiles = @(
    "HomeScreen.kt",
    "VentesScreen.kt",
    "StatsScreen.kt",
    "ProfilScreen.kt",
    "PathologieScreen.kt",
    "HistoriqueScreen.kt",
    "NouvelleCommandeScreen.kt",
    "ModifierCommandeScreen.kt"
)
foreach ($f in $screenFiles) {
    Move-Item "$themeDir\$f" "$screensDir\$f" -Force
    (Get-Content "$screensDir\$f") -replace `
        'package com\.benjo\.fboplus\.ui\.theme', `
        'package com.benjo.fboplus.ui.screens' | `
        Set-Content "$screensDir\$f"
    Write-Host "   deplace + package corrige : $f"
}

Write-Host "4) Deplacement de Navigation.kt vers ui/navigation..." -ForegroundColor Cyan
Move-Item "$themeDir\Navigation.kt" "$navDir\Navigation.kt" -Force

# Corrige le package de Navigation.kt et ajoute les imports vers les ecrans deplaces
$navContent = Get-Content "$navDir\Navigation.kt"
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
Set-Content "$navDir\Navigation.kt" $newNavContent
Write-Host "   package corrige + imports des ecrans ajoutes dans Navigation.kt"

Write-Host "5) Correction de l'import dans MainActivity.kt..." -ForegroundColor Cyan
$mainActivity = "$base\MainActivity.kt"
(Get-Content $mainActivity) -replace `
    'import com\.benjo\.fboplus\.ui\.theme\.FBONavigation', `
    'import com.benjo.fboplus.ui.navigation.FBONavigation' | `
    Set-Content $mainActivity
Write-Host "   import FBONavigation corrige"

Write-Host ""
Write-Host "Nettoyage termine. Verifie que le projet compile dans Android Studio," -ForegroundColor Green
Write-Host "puis fais :" -ForegroundColor Green
Write-Host "   git add -A" -ForegroundColor Yellow
Write-Host "   git commit -m 'Nettoyage : suppression doublons images + reorganisation ui/screens et ui/navigation'" -ForegroundColor Yellow
Write-Host "   git push" -ForegroundColor Yellow
