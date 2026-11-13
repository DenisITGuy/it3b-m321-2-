# Live-Zustellung (Bewertung 2): S2-S8 in vorgegebener Reihenfolge auf demselben Stack.
# Ohne Override-Datei, Messung von innen im Netz chat-net, Delta-Messungen, kein Aufraeumen dazwischen.
# Jede chat-service-Instanz wird einzeln ueber ihren Containernamen angesprochen.
# S1 laeuft separat: mvn clean test im Wurzelverzeichnis.
Set-Location (Split-Path $PSScriptRoot -Parent)
$NET  = "it3b-m321_chat-net"
$CURL = "curlimages/curl:latest"
$I1   = "it3b-m321-chat-service-1"
$I2   = "it3b-m321-chat-service-2"
$RA   = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
$RB   = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
$R6   = "cccccccc-cccc-cccc-cccc-cccccccccccc"
$R7   = "dddddddd-dddd-dddd-dddd-dddddddddddd"

$e = @{}
Get-Content .env | ForEach-Object { if ($_ -match '^\s*([A-Z_]+)\s*=\s*(.+)$') { $e[$matches[1]] = $matches[2].Trim() } }
$PGU = $e['POSTGRES_USER']; $PGD = $e['POSTGRES_DB']

function dc { docker compose -f docker-compose.yml @args }
function Sql($q) { (dc exec -T postgres psql -U $PGU -d $PGD -t -A -c $q | Select-Object -First 1).Trim() }
function Cnt($q) { [int](Sql $q) }
function Send($ziel,$n,$prefix,$raum,$par) {
  docker run --rm --network $NET -v "${PWD}\tools:/w" $CURL sh /w/live-send.sh $ziel $n $prefix $raum $par
}
function StartSse($name,$inst,$raum,$lastId) {
  $a = @("run","-d","--rm","--name",$name,"--network",$NET,$CURL,"-N","-s")
  if ($lastId) { $a += @("-H","Last-Event-ID: $lastId") }
  $a += "http://${inst}:8080/stream?roomId=$raum"
  docker @a | Out-Null
}
function StopSse($name) { docker rm -f $name 2>&1 | Out-Null }
function Logs($name) { docker logs $name 2>&1 }
function DataCount($name) { (Logs $name | Select-String "^data:").Count }
# "id:" hat drei Zeichen; Substring(4) schneidet das erste ID-Zeichen ab, und
# UUID.fromString fuellt die fehlende Stelle still mit einer Null auf.
function IdLines($name) { Logs $name | Select-String "^id:" | ForEach-Object { $_.Line.Substring(3).Trim() } }
function WaitConnected($name) {
  $sw = [Diagnostics.Stopwatch]::StartNew()
  while ($sw.Elapsed.TotalSeconds -lt 20) {
    if (Logs $name | Select-String ":connected") { return $true }
    Start-Sleep -Milliseconds 500
  }
  return $false
}
function WaitData($name,$target,$max) {
  $sw = [Diagnostics.Stopwatch]::StartNew(); $c = 0
  while ($sw.Elapsed.TotalSeconds -lt $max) {
    $c = DataCount $name
    if ($c -ge $target) { break }
    Start-Sleep -Milliseconds 500
  }
  $sw.Stop(); @{ c=$c; s=[math]::Round($sw.Elapsed.TotalSeconds,1) }
}
function WaitHttp($inst) {
  $sw = [Diagnostics.Stopwatch]::StartNew()
  while ($sw.Elapsed.TotalSeconds -lt 90) {
    $code = docker run --rm --network $NET $CURL -s -m 3 -o /dev/null -w "%{http_code}" "http://${inst}:8080/api/health"
    if ($code -eq "200") { return $true }
    Start-Sleep -Seconds 2
  }
  return $false
}
function LiveQueues { dc exec -T rabbitmq rabbitmqctl list_queues name consumers --silent | Where-Object { $_ -match "^chat\.live\." } }
function OrderOk($name) {
  $last = @{}
  $lines = Logs $name | Select-String "^data:" | ForEach-Object { $_.Line.Substring(5) }
  foreach ($l in $lines) {
    $ev = $l | ConvertFrom-Json
    $num = [int]($ev.content.Split('-')[-1])
    if ($last.ContainsKey($ev.sender)) {
      if ($num -le $last[$ev.sender]) { return $false }
    }
    $last[$ev.sender] = $num
  }
  return $true
}
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
WaitHttp $I1 | Out-Null

# S2: kein Dienst veroeffentlicht einen Port; vom Host abgelehnt, von innen erreichbar
$pub = (dc config | Select-String "published").Count
$hostCode = curl.exe -s -m 3 -o NUL -w "%{http_code}" "http://localhost:8080/api/health"
$innen = docker run --rm --network $NET $CURL -s -m 5 -o /dev/null -w "%{http_code}" "http://${I1}:8080/api/health"
$res += [pscustomobject]@{S="S2";Erwartet="0 published, Host abgelehnt, innen 200";Gemessen="$pub published, Host $hostCode, innen $innen";OK=($pub -eq 0 -and $hostCode -ne "200" -and $innen -eq "200")}

# S3: zwei Instanzen, je ein A-Client pro Instanz, ein B-Client; je 200 ueber jede Instanz
dc up -d --scale chat-service=2 | Out-Null
WaitHttp $I2 | Out-Null
StartSse "sse-a1" $I1 $RA $null
StartSse "sse-a2" $I2 $RA $null
StartSse "sse-b1" $I1 $RB $null
WaitConnected "sse-a1" | Out-Null
WaitConnected "sse-a2" | Out-Null
WaitConnected "sse-b1" | Out-Null
Send $I1 200 "eins" $RA 1 | Out-Null
Send $I2 200 "zwei" $RA 1 | Out-Null
WaitData "sse-a1" 400 30 | Out-Null
WaitData "sse-a2" 400 30 | Out-Null
Start-Sleep -Seconds 2
$c1 = DataCount "sse-a1"; $c2 = DataCount "sse-a2"; $cb = DataCount "sse-b1"
$u1 = (IdLines "sse-a1" | Sort-Object -Unique).Count
$u2 = (IdLines "sse-a2" | Sort-Object -Unique).Count
$o1 = OrderOk "sse-a1"; $o2 = OrderOk "sse-a2"
$res += [pscustomobject]@{S="S3";Erwartet="je 400, 400 ids, Reihenfolge, B=0";Gemessen="a1=$c1/$u1 a2=$c2/$u2 Reihenfolge=$o1/$o2 b1=$cb";OK=($c1 -eq 400 -and $c2 -eq 400 -and $u1 -eq 400 -and $u2 -eq 400 -and $o1 -and $o2 -and $cb -eq 0)}
StopSse "sse-a1"; StopSse "sse-a2"; StopSse "sse-b1"

# S4: zwei Live-Queues mit je einem Consumer; Scale-down auf eine binnen 30 s
$l4 = LiveQueues
$zwei = ($l4.Count -eq 2)
$cons1 = ($l4 | Where-Object { $_ -match "\s1$" }).Count
dc up -d --scale chat-service=1 | Out-Null
$sw4 = [Diagnostics.Stopwatch]::StartNew(); $eine = $false
while ($sw4.Elapsed.TotalSeconds -lt 30) {
  if ((LiveQueues).Count -eq 1) { $eine = $true; break }
  Start-Sleep -Seconds 2
}
$d4 = [math]::Round($sw4.Elapsed.TotalSeconds,1)
$namen = dc exec -T rabbitmq rabbitmqctl list_queues name --silent
$persist = ($namen | Select-String "^chat\.persist").Count
$dlq = ($namen | Select-String "^chat\.dlq").Count
$res += [pscustomobject]@{S="S4";Erwartet="2->1 in <=30s, persist+dlq bleiben";Gemessen="vorher $zwei (Consumer=$cons1), nachher eine=$eine in ${d4}s, persist=$persist dlq=$dlq";OK=($zwei -and $cons1 -eq 2 -and $eine -and $persist -eq 1 -and $dlq -eq 1)}

# S5: postgres und batch-writer gestoppt, 100 Nachrichten live in <=5 s, danach DB-Delta +100
$b5 = Cnt "SELECT COUNT(*) FROM message;"
dc stop postgres batch-writer | Out-Null
StartSse "sse-c5" $I1 $RA $null
WaitConnected "sse-c5" | Out-Null
$t5 = Get-Date
Send $I1 100 "s5" $RA 1 | Out-Null
$w5 = WaitData "sse-c5" 100 5
$d5 = [math]::Round(((Get-Date)-$t5).TotalSeconds,1)
dc start postgres batch-writer | Out-Null
$r5 = WaitCnt ($b5+100) 30
$res += [pscustomobject]@{S="S5";Erwartet="100 in <=5s, DB-Delta +100";Gemessen="$($w5.c) in ${d5}s, DB-Delta $($r5.c-$b5)";OK=($w5.c -eq 100 -and $d5 -le 5 -and ($r5.c-$b5) -eq 100)}
StopSse "sse-c5"

# S6: eine Instanz pausieren, 5000 senden, Queue nie >1000; andere Instanz bekommt alle; nach unpause wieder live
dc up -d --scale chat-service=2 | Out-Null
WaitHttp $I2 | Out-Null
StartSse "sse-c6" $I2 $RA $null
WaitConnected "sse-c6" | Out-Null
docker pause $I1 | Out-Null
$sendArgs = @("run","--rm","--network",$NET,"-v","${PWD}\tools:/w",$CURL,"sh","/w/live-send.sh",$I2,"5000","s6",$RA,"20")
$p6 = Start-Process docker -ArgumentList $sendArgs -RedirectStandardOutput "tools\s6-send.txt" -NoNewWindow -PassThru
$maxMsg = 0; $maxUn = 0
while (!$p6.HasExited) {
  $rows = dc exec -T rabbitmq rabbitmqctl list_queues name messages messages_ready messages_unacknowledged --silent | Where-Object { $_ -match "^chat\.live\." }
  foreach ($r in $rows) {
    $f = $r -split "\s+"
    $m = [int]$f[1]; $u = [int]$f[3]
    if ($m -gt $maxMsg) { $maxMsg = $m }
    if ($u -gt $maxUn) { $maxUn = $u }
  }
  Start-Sleep -Milliseconds 500
}
$p6.WaitForExit()
$w6 = WaitData "sse-c6" 5000 60
docker unpause $I1 | Out-Null
Start-Sleep -Seconds 3
StartSse "sse-c6n" $I1 $R6 $null
WaitConnected "sse-c6n" | Out-Null
Send $I1 10 "s6n" $R6 1 | Out-Null
$w6n = WaitData "sse-c6n" 10 20
$res += [pscustomobject]@{S="S6";Erwartet="max <=1000, unacked <=10, 5000 live, +10 nach unpause";Gemessen="max=$maxMsg unacked=$maxUn, Instanz2=$($w6.c), nach unpause=$($w6n.c)";OK=($maxMsg -le 1000 -and $maxUn -le 10 -and $w6.c -eq 5000 -and $w6n.c -eq 10)}
StopSse "sse-c6"; StopSse "sse-c6n"

# S7: eine Nachricht empfangen, trennen, 50 senden, 10 s warten, mit Last-Event-ID genau 50 nachholen, danach live
StartSse "sse-c7" $I1 $R7 $null
WaitConnected "sse-c7" | Out-Null
Send $I1 1 "s7" $R7 1 | Out-Null
WaitData "sse-c7" 1 15 | Out-Null
$xid = IdLines "sse-c7" | Select-Object -First 1
StopSse "sse-c7"
Send $I1 50 "s7b" $R7 1 | Out-Null
Start-Sleep -Seconds 10
StartSse "sse-c7b" $I1 $R7 $xid
WaitConnected "sse-c7b" | Out-Null
$w7 = WaitData "sse-c7b" 50 15
Start-Sleep -Seconds 2
$ids7 = IdLines "sse-c7b"
$uniq7 = ($ids7 | Sort-Object -Unique).Count
$hasX = $ids7 -contains $xid
$nums = Logs "sse-c7b" | Select-String "^data:" | ForEach-Object { [int](($_.Line.Substring(5) | ConvertFrom-Json).content.Split('-')[-1]) }
$seq = ($nums.Count -eq 50)
for ($i = 0; $i -lt $nums.Count; $i++) {
  if ($nums[$i] -ne ($i+1)) { $seq = $false }
}
Send $I1 1 "s7c" $R7 1 | Out-Null
$w7live = WaitData "sse-c7b" 51 15
$res += [pscustomobject]@{S="S7";Erwartet="genau 50, Reihenfolge, keine doppelt, danach 51";Gemessen="$($w7.c) nachgeholt, uniq=$uniq7, Xdabei=$hasX, Reihenfolge=$seq, live=$($w7live.c)";OK=($w7.c -eq 50 -and $uniq7 -eq 50 -and !$hasX -and $seq -and $w7live.c -eq 51)}
StopSse "sse-c7b"

# S8: keine Streams im chat-service-Quelltext, .env nicht im Repo
$st = (Get-ChildItem chat-service\src -Filter *.java -Recurse | Select-String "\.stream\(\)|\.collect\(").Count
$envf = (git ls-files | Select-String "^\.env$").Count
$res += [pscustomobject]@{S="S8";Erwartet="0 Streams, .env nicht im Repo";Gemessen="$st Streams, $envf .env";OK=($st -eq 0 -and $envf -eq 0)}

Write-Host "`n===== ERGEBNIS (S1 separat: mvn clean test) =====" -ForegroundColor Cyan
$res | Format-Table -AutoSize
$res | ForEach-Object { if ($_.OK) { Write-Host "PASS $($_.S)" -ForegroundColor Green } else { Write-Host "FAIL $($_.S)" -ForegroundColor Red } }
$res | Format-Table -AutoSize | Out-File tools\live-ergebnis.txt