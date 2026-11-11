#!/bin/sh
# Sendet genau eine Nachricht und protokolliert HTTP-Status und Antwort
i=$1; prefix=$2
code=$(curl -s -o "/tmp/body.$i" -w '%{http_code}' -X POST http://backend:8080/messages \
  -H 'Content-Type: application/json' \
  -d "{\"roomId\":\"11111111-1111-1111-1111-111111111111\",\"content\":\"$prefix-$i\",\"sender\":\"Tester\"}")
echo "$code $i" >> /tmp/codes.txt