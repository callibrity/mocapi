/** Recursively truncate long string values (e.g. a 520KB ui:// bundle) for display. */
function truncateStrings(value: unknown, max: number): unknown {
  if (typeof value === "string") {
    return value.length > max ? `${value.slice(0, max)}… [truncated ${value.length - max} chars]` : value;
  }
  if (Array.isArray(value)) {
    return value.map((v) => truncateStrings(v, max));
  }
  if (value && typeof value === "object") {
    return Object.fromEntries(
      Object.entries(value as Record<string, unknown>).map(([k, v]) => [k, truncateStrings(v, max)]),
    );
  }
  return value;
}

/** Pretty-print JSON with long strings truncated so huge payloads stay readable. */
export function prettyJson(value: unknown, max = 800): string {
  return JSON.stringify(truncateStrings(value, max), null, 2);
}
