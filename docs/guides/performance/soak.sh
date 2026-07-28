#!/bin/bash
# Soak driver for the stateless MCP 2026-07-28 HTTP transport.
#
# 2026-07-28 is stateless: there is no `initialize` handshake and no
# MCP-Session-Id. Every request stands alone and carries:
#   - the MCP-Protocol-Version / Mcp-Method / Mcp-Name routing headers, and
#   - a `_meta` envelope with the required protocolVersion + clientCapabilities.
#
# Args: $1 duration (seconds)  $2 (unused, kept for call-site compatibility)  $3 worker id
ENDPOINT=localhost:8080/mcp
DURATION=$1
PAR=$2
OUT=/tmp/soak-$3.log
> "$OUT"
END=$(( $(date +%s) + DURATION ))
TOOLS=(hello rot-13-tool.encode)
META='"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}}'
CALLS=0
while [ $(date +%s) -lt $END ]; do
  TOOL="${TOOLS[$((RANDOM % ${#TOOLS[@]}))]}"
  ID=$((RANDOM))
  if [ "$TOOL" = "hello" ]; then
    BODY="{\"jsonrpc\":\"2.0\",\"id\":$ID,\"method\":\"tools/call\",\"params\":{$META,\"name\":\"hello\",\"arguments\":{\"name\":\"soak-$ID\"}}}"
  else
    BODY="{\"jsonrpc\":\"2.0\",\"id\":$ID,\"method\":\"tools/call\",\"params\":{$META,\"name\":\"rot-13-tool.encode\",\"arguments\":{\"text\":\"hello-soak\"}}}"
  fi
  START=$(date +%s%N)
  RESP=$(curl -s -m 10 -o /dev/null -w "%{http_code}" -X POST \
    -H 'Content-Type: application/json' \
    -H 'Accept: application/json,text/event-stream' \
    -H 'MCP-Protocol-Version: 2026-07-28' \
    -H 'Mcp-Method: tools/call' \
    -H "Mcp-Name: $TOOL" \
    -d "$BODY" "$ENDPOINT")
  END_T=$(date +%s%N)
  MS=$(( (END_T - START) / 1000000 ))
  echo "$TOOL $RESP $MS" >> "$OUT"
  CALLS=$((CALLS+1))
done
echo "$CALLS" > /tmp/soak-count-$3.txt
