CREATE TABLE IF NOT EXISTS mocapi_sessions (
  session_id VARCHAR(64) PRIMARY KEY,
  payload CLOB NOT NULL,
  expires_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_mocapi_sessions_expires_at
  ON mocapi_sessions (expires_at);
