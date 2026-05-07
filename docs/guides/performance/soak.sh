#!/bin/bash
SID=$(cat /tmp/sid.txt)
ENDPOINT=localhost:8080/mcp
DURATION=$1
PAR=$2
OUT=/tmp/soak-$3.log
> "$OUT"
END=$(( $(date +%s) + DURATION ))
TOOLS=(hello rot-13-tool.encode)
CALLS=0
while [ $(date +%s) -lt $END ]; do
  TOOL="${TOOLS[$((RANDOM % ${#TOOLS[@]}))]}"
  ID=$((RANDOM))
  if [ "$TOOL" = "hello" ]; then
    BODY="{\"jsonrpc\":\"2.0\",\"id\":$ID,\"method\":\"tools/call\",\"params\":{\"name\":\"hello\",\"arguments\":{\"name\":\"soak-$ID\"}}}"
  else
    BODY="{\"jsonrpc\":\"2.0\",\"id\":$ID,\"method\":\"tools/call\",\"params\":{\"name\":\"rot-13-tool.encode\",\"arguments\":{\"text\":\"hello-soak\"}}}"
  fi
  START=$(date +%s%N)
  RESP=$(curl -s -m 10 -o /dev/null -w "%{http_code}" -X POST -H 'Content-Type: application/json' -H 'Accept: application/json,text/event-stream' -H "MCP-Session-Id: $SID" -H 'MCP-Protocol-Version: 2025-11-25' -d "$BODY" "$ENDPOINT")
  END_T=$(date +%s%N)
  MS=$(( (END_T - START) / 1000000 ))
  echo "$TOOL $RESP $MS" >> "$OUT"
  CALLS=$((CALLS+1))
done
echo "$CALLS" > /tmp/soak-count-$3.txt
