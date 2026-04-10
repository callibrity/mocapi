CREATE TABLE IF NOT EXISTS mocapi_sessions (
  session_id VARCHAR(64) PRIMARY KEY,
  payload JSON NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  INDEX idx_mocapi_sessions_expires_at (expires_at)
);
