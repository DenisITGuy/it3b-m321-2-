#!/bin/sh
# Sendet N Nachrichten an einen Host, Standard sequentiell (Reihenfolge pro Absender)
host=$1; n=$2; prefix=$3; raum=$4; par=${5:-1}
: > /tmp/live-codes.txt
seq 1 "$n" | xargs -P "$par" -I@ sh /w/live-send-one.sh "$host" @ "$prefix" "$raum"
ok=$(grep -c '^2' /tmp/live-codes.txt)
fail=$(grep -vc '^2' /tmp/live-codes.txt)
echo "HTTP_OK=$ok HTTP_FAIL=$fail"