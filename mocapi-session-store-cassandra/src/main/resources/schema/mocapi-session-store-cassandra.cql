-- Caller creates the keyspace themselves; this script assumes
-- the current session is already USE-ing the target keyspace.
CREATE TABLE IF NOT EXISTS mocapi_sessions (
  session_id text PRIMARY KEY,
  payload text
);
