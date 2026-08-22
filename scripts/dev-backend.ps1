# Start the SprintJudge backend in dev mode (native executor, no WSL required).
$root = Split-Path $PSScriptRoot -Parent
& "$root\mvnw.cmd" spring-boot:run "-Dspring-boot.run.profiles=dev" `
  "-Dspring-boot.run.jvmArguments=-Xms256m -Xmx256m -XX:+UseStringDeduplication -XX:+PerfDisableSharedMem" @args
