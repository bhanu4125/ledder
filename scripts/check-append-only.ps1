$ErrorActionPreference = 'Stop'
$envFile = Join-Path (Get-Location) '.env'
if (-not (Test-Path $envFile)) {
    throw 'Copy .env.example to .env and set DATABASE_PASSWORD first.'
}
$passwordLine = Get-Content $envFile | Where-Object { $_ -match '^DATABASE_PASSWORD=' } | Select-Object -First 1
if (-not $passwordLine) {
    throw 'DATABASE_PASSWORD is missing from .env.'
}
$password = $passwordLine.Substring('DATABASE_PASSWORD='.Length).Trim().Trim('"')
$checks = @(
    'UPDATE ledger_entries SET amount = amount WHERE id = 1',
    'DELETE FROM ledger_entries WHERE id = 1',
    'UPDATE audit_log SET description = description WHERE id = 1',
    'DELETE FROM audit_log WHERE id = 1'
)
foreach ($sql in $checks) {
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $output = docker compose exec -T -e "PGPASSWORD=$password" db psql -U ledgerline_runtime -d ledgerline -v ON_ERROR_STOP=1 -c $sql 2>&1 | Out-String
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousPreference
    if ($exitCode -eq 0 -or $output -notmatch 'permission denied') {
        throw "Expected a database permission denial for: $sql`n$output"
    }
    $tokens = $sql -split '\s+'
    $table = if ($tokens[0] -eq 'DELETE') { $tokens[2] } else { $tokens[1] }
    Write-Output "PASS: runtime role cannot execute $($tokens[0]) on $table."
}
