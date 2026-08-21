# Start the OpenQuiz backend in dev mode (native executor, no WSL required).
$root = Split-Path $PSScriptRoot -Parent
& "$root\mvnw.cmd" spring-boot:run -Dspring-boot.run.profiles=dev @args
