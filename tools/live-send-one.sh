#!/bin/sh
# Sendet genau eine Nachricht an eine bestimmte Instanz, mit Timeout gegen haengende Instanzen
host=$1; i=$2; prefix=$3; raum=$4
code=$(curl -s -m 5 -o /dev/null -w '%{http_code}' -X POST "http://$host:8080/messages" \
  -H 'Content-Type: application/json' \
  -d "{\"roomId\":\"$raum\",\"content\":\"$prefix-$i\",\"sender\":\"$prefix\"}")
echo "$code $i" >> /tmp/live-codes.txt