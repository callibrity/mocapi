// k6 throughput / saturation load for the mocapi Streamable HTTP transport.
//
// Portable: point it at any mocapi server with TARGET (host:port). Drives a full
// MCP `tools/call` round trip on the example server's `hello` tool — envelope
// parse, routing-header validation, dispatch, argument schema-validation, handler,
// structured-result + serverInfo _meta serialization. Not a trivial /ping.
//
//   TARGET=192.168.1.50:8080 VUS=250 DURATION=30s k6 run throughput.js
//
// See throughput-testing.md for the two-box setup, OS tuning, and how to read the
// results honestly (the load generator must NOT share a host with the server).

import http from 'k6/http';
import { check } from 'k6';

const URL = `http://${__ENV.TARGET || 'localhost:8080'}/mcp`;
const TOOL = __ENV.TOOL || 'hello';

const META = {
  'io.modelcontextprotocol/protocolVersion': '2026-07-28',
  'io.modelcontextprotocol/clientCapabilities': {},
};
const BODY = JSON.stringify({
  jsonrpc: '2.0',
  id: 1,
  method: 'tools/call',
  params: { _meta: META, name: TOOL, arguments: { name: 'x' } },
});
const PARAMS = {
  headers: {
    'Content-Type': 'application/json',
    'Accept': 'application/json,text/event-stream',
    'MCP-Protocol-Version': '2026-07-28',
    'Mcp-Method': 'tools/call',
    'Mcp-Name': TOOL,
  },
};

export const options = {
  scenarios: {
    saturation: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 250),
      duration: __ENV.DURATION || '30s',
    },
  },
};

export default function () {
  check(http.post(URL, BODY, PARAMS), { 'status 200': (r) => r.status === 200 });
}
