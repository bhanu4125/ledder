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
$encodedPassword = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($password))
$checks = @(
    'UPDATE ledger_entries SET amount = amount WHERE id = 1',
    'DELETE FROM ledger_entries WHERE id = 1',
    'UPDATE audit_log SET description = description WHERE id = 1',
    'DELETE FROM audit_log WHERE id = 1'
)
foreach ($sql in $checks) {
    $output = docker compose exec -T -e "LEDGERLINE_CHECK_PASSWORD=$encodedPassword" db sh -lc 'export PGPASSWORD="$(printf "%s" "$LEDGERLINE_CHECK_PASSWORD" | base64 -d)"; psql -U ledgerline_runtime -d ledgerline -v ON_ERROR_STOP=1 -c "$1"' check $sql 2>&1
    if ($LASTEXITCODE -eq 0 -or ($output -join "`n") -notmatch 'permission denied') {
        throw "Expected a database permission denial for: $sql`n$($output -join "`n")"
    }
    Write-Output "PASS: runtime role cannot execute $($sql.Split(' ')[0]) on $($sql.Split(' ')[1])."
}
