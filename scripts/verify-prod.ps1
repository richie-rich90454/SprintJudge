# Verifies a real production-profile launch of SprintJudge on Windows.
param(
    [int]$Port = 8091,
    [switch]$SkipBuild
)
$ErrorActionPreference = "Continue"
. "$PSScriptRoot\_dotenv.ps1"
$root = Split-Path $PSScriptRoot -Parent