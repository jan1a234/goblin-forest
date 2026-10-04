#!/usr/bin/env bash
# Befehle aus probe/cmds.txt: "grep REGEX" | "javap KLASSE [REGEX]" | "src KLASSE [REGEX]"
set -u
CP=$(grep '^CP=' cp.txt | cut -c4-)
: > classes.txt
for j in ${CP//:/ }; do case "$j" in *.jar) unzip -Z1 "$j" 2>/dev/null | grep '\.class$' >> classes.txt;; esac; done
sed -i 's/\.class$//' classes.txt; sort -u -o classes.txt classes.txt
wc -l classes.txt
SRCJARS=$(find "$HOME/.gradle" .gradle -name '*minecraft*sources.jar' 2>/dev/null | sort -u)
echo "SRCJARS: $SRCJARS"
mkdir -p src-x
for s in $SRCJARS; do unzip -qo "$s" -d src-x; done
echo "=====PROBE-START"
while IFS= read -r line; do
  [ -z "$line" ] && continue
  cmd=${line%% *}; rest=${line#* }; cls=${rest%% *}; re=""; [ "$rest" != "$cls" ] && re=${rest#* }
  echo "## $line"
  case "$cmd" in
    grep) grep -E "$rest" classes.txt | head -80;;
    javap) if [ -n "$re" ]; then javap -protected -cp "$CP" "$cls" 2>&1 | grep -E "$re"; else javap -protected -cp "$CP" "$cls" 2>&1 | grep -v '^Compiled from'; fi;;
    src) f="src-x/${cls//.//}.java"; if [ -f "$f" ]; then if [ -n "$re" ]; then grep -nE "$re" "$f"; else cat -n "$f"; fi; else echo "keine Quelle: $f"; fi;;
  esac
done < probe/cmds.txt
echo "=====PROBE-END"
