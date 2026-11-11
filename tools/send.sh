#!/bin/sh
# Sendet N Nachrichten parallel (Standard 20), damit die Sendezeit unter dem 60s-Budget bleibt
N=$1; PREFIX=$2; PAR=${3:-20}
: > /tmp/codes.txt
seq 1 "$N" | xargs -P "$PAR" -I@ sh /w/send-one.sh @ "$PREFIX"
ok=$(grep -c '^2' /tmp/codes.txt)
fail=$(grep -vc '^2' /tmp/codes.txt)
echo "HTTP_OK=$ok HTTP_FAIL=$fail"
grep -v '^2' /tmp/codes.txt | while read -r code i; do
  echo "FEHLER $code bei Nachricht $i: $(cat /tmp/body.$i)"
done