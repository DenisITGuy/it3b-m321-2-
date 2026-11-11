# S2-S8 wie das Pruefskript: ohne Override, Messung von innen, Deltas, kein Aufraeumen
Set-Location (Split-Path $PSScriptRoot -Parent)
$NET  = "it3b-m321_chat-net"
$CURL = "curlimages/curl:latest"
$ROOM = "11111111-1111-1111-1111-111111111111"

$e = @{}
Get-Content .env | ForEach-Object { if ($_ -match '^\s*([A-Z_]+)\s*=\s*(.+)$') { $e[$matches[1]] = $matches[2].Trim() } }
$PGU = $e['POSTGRES_USER']; $PGD = $e['POSTGRES_DB']
$RU  = $e['RABBITMQ_USER']; $RP  = $e['RABBITMQ_PASSWORD']

function dc { docker compose -f docker-compose.yml @args }
function Sql($q) { (dc exec -T postgres psql -U $PGU -d $PGD -t -A -c $q | Select-Object -First 1).Trim() }
function Cnt($q) { [int](Sql $q) }
function Send($n,$p) { docker run --rm --network $NET -v "${PWD}\tools:/w" $CURL sh /w/send.sh $n $p }
function Q($name) {
  $l = dc exec -T rabbitmq rabbitmqctl list_queues name messages --silent | Where-Object { $_ -match "^$name\s" }
  if ($l) { [int](($l -split "\s+")[-1]) } else { 0 }
}
function Batches($inst) { (docker logs "it3b-m321-batch-writer-$inst" 2>&1 | Select-String "Batch mit").Count }
function WaitCnt($target,$max) {
  $sw = [Diagnostics.Stopwatch]::StartNew(); $c = -1
  while ($sw.Elapsed.TotalSeconds -lt $max) {
    try { $c = Cnt "SELECT COUNT(*) FROM message;" } catch { $c = -1 }
    if ($c -ge $target) { break }
    Start-Sleep -Seconds 2
  }
  $sw.Stop(); @{ c=$c; s=[math]::Round($sw.Elapsed.TotalSeconds,1) }
}
$res = @()

Write-Host "`n[SETUP] Frischer Stack ohne Override..." -ForegroundColor Magenta
dc down -v | Out-Null
dc up -d --build | Out-Null
$sw=[Diagnostics.Stopwatch]::StartNew()
while ($sw.Elapsed.TotalSeconds -lt 90) {
  if ((dc logs backend 2>&1 | Select-String "Started ChatApplication")) { break }
  Start-Sleep -Seconds 2
}
Start-Sleep -Seconds 5

# S2
$pub = (dc config | Select-String "published").Count
$res += [pscustomobject]@{S="S2";Erwartet="0 published";Gemessen=$pub;OK=($pub -eq 0)}

# S3
$b3 = Cnt "SELECT COUNT(*) FROM message;"
$t3 = Get-Date; Send 1000 "S3"
$r3 = WaitCnt ($b3+1000) 60
$d3 = [math]::Round(((Get-Date)-$t3).TotalSeconds,1)
$q3 = Q "chat.persist"
$res += [pscustomobject]@{S="S3";Erwartet="+1000 in <=60s, Queue 0";Gemessen="$($r3.c-$b3) in ${d3}s, Queue $q3";OK=(($r3.c-$b3) -eq 1000 -and $d3 -le 60 -and $q3 -eq 0)}

# S4
$b4 = Cnt "SELECT COUNT(*) FROM message;"
$l4 = Batches 1
$x4 = Cnt "SELECT xact_commit FROM pg_stat_database WHERE datname='$PGD';"
dc stop batch-writer | Out-Null
Send 1000 "S4"
dc start batch-writer | Out-Null
$r4 = WaitCnt ($b4+1000) 120
$tx = (Batches 1) - $l4
$xd = (Cnt "SELECT xact_commit FROM pg_stat_database WHERE datname='$PGD';") - $x4
$res += [pscustomobject]@{S="S4";Erwartet="+1000, <=100 Tx";Gemessen="$($r4.c-$b4) Msg, $tx Batches, $xd xact_commit";OK=(($r4.c-$b4) -eq 1000 -and $tx -le 100)}

# S5
$id = [guid]::NewGuid().ToString()
$ev = @{id=$id;roomId=$ROOM;sender="dupUser";content="Dup";createdAt=1794430756.165} | ConvertTo-Json -Compress
$rq = @{properties=@{content_type="application/json";delivery_mode=2};routing_key="chat.persist";payload=$ev;payload_encoding="string"} | ConvertTo-Json -Compress -Depth 5
[System.IO.File]::WriteAllText("$PWD\tools\s5.json", $rq)
1..2 | ForEach-Object {
  docker run --rm --network $NET -v "${PWD}\tools:/w" $CURL -s -u "${RU}:${RP}" -H "content-type: application/json" -X POST "http://rabbitmq:15672/api/exchanges/%2F/amq.default/publish" -d "@/w/s5.json" | Out-Null
}
Start-Sleep -Seconds 10
$c5 = Cnt "SELECT COUNT(*) FROM message WHERE id='$id';"
$d5 = Q "chat.dlq"
$res += [pscustomobject]@{S="S5";Erwartet="1 Zeile, DLQ 0";Gemessen="$c5 Zeilen, DLQ $d5";OK=($c5 -eq 1 -and $d5 -eq 0)}

# S6
$b6 = Cnt "SELECT COUNT(*) FROM message;"
dc up -d --scale batch-writer=2 | Out-Null
Start-Sleep -Seconds 10
Send 1000 "S6"
$r6 = WaitCnt ($b6+1000) 90
$dup = Cnt "SELECT COUNT(*) FROM (SELECT id FROM message GROUP BY id HAVING COUNT(*)>1) d;"
$i1 = Batches 1; $i2 = Batches 2
$res += [pscustomobject]@{S="S6";Erwartet="+1000, 0 Duplikate, beide aktiv";Gemessen="$($r6.c-$b6) Msg, $dup Dup, Inst1=$i1 Inst2=$i2";OK=(($r6.c-$b6) -eq 1000 -and $dup -eq 0 -and $i2 -gt 0)}

# S7 (weiter mit 2 Instanzen, kein Aufraeumen)
$b7 = Cnt "SELECT COUNT(*) FROM message;"
dc stop postgres | Out-Null
Send 300 "S7"
Start-Sleep -Seconds 15
dc start postgres | Out-Null
$r7 = WaitCnt ($b7+300) 90
$res += [pscustomobject]@{S="S7";Erwartet="+300 in <=90s";Gemessen="$($r7.c-$b7) in $($r7.s)s";OK=(($r7.c-$b7) -eq 300 -and $r7.s -le 90)}

# S8
$st = (Get-ChildItem batch-writer\src\main\java -Filter *.java -Recurse | Select-String "\.stream\(\)|\.collect\(").Count
$envf = (git ls-files | Select-String "^\.env$").Count
$res += [pscustomobject]@{S="S8";Erwartet="0 Streams, .env nicht im Repo";Gemessen="$st Streams, $envf .env";OK=($st -eq 0 -and $envf -eq 0)}

Write-Host "`n===== ERGEBNIS (S1 separat: mvn clean test) =====" -ForegroundColor Cyan
$res | Format-Table -AutoSize
$res | ForEach-Object { if ($_.OK) { Write-Host "PASS $($_.S)" -ForegroundColor Green } else { Write-Host "FAIL $($_.S)" -ForegroundColor Red } }