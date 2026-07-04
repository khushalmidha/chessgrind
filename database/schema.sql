CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS app_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(40) NOT NULL UNIQUE,
    email VARCHAR(160) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    streak_days INTEGER NOT NULL DEFAULT 0,
    total_completed INTEGER NOT NULL DEFAULT 0,
    profile_report_json TEXT,
    profile_report_fingerprint VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS training_puzzles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mode VARCHAR(40) NOT NULL,
    difficulty VARCHAR(24) NOT NULL,
    title VARCHAR(120) NOT NULL,
    fen TEXT NOT NULL,
    target_mate_moves INTEGER NOT NULL,
    daily_challenge BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS training_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES app_users(id) ON DELETE CASCADE,
    puzzle_id UUID REFERENCES training_puzzles(id) ON DELETE SET NULL,
    mode VARCHAR(40) NOT NULL,
    difficulty VARCHAR(24) NOT NULL,
    timer_mode VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL,
    start_fen TEXT NOT NULL,
    current_fen TEXT NOT NULL,
    time_limit_seconds INTEGER NOT NULL DEFAULT 0,
    increment_seconds INTEGER NOT NULL DEFAULT 0,
    remaining_seconds INTEGER NOT NULL DEFAULT 0,
    hints_enabled BOOLEAN NOT NULL DEFAULT true,
    takebacks_enabled BOOLEAN NOT NULL DEFAULT false,
    hints_used INTEGER NOT NULL DEFAULT 0,
    mistakes INTEGER NOT NULL DEFAULT 0,
    accuracy DOUBLE PRECISION NOT NULL DEFAULT 100,
    report_json TEXT,
    report_fingerprint VARCHAR(120),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS training_moves (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES training_sessions(id) ON DELETE CASCADE,
    ply INTEGER NOT NULL,
    uci VARCHAR(16) NOT NULL,
    san VARCHAR(24) NOT NULL,
    fen_after TEXT NOT NULL,
    engine_move BOOLEAN NOT NULL DEFAULT false,
    optimal BOOLEAN NOT NULL DEFAULT false,
    reason VARCHAR(260) NOT NULL,
    played_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(session_id, ply)
);

CREATE TABLE IF NOT EXISTS favorite_positions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    fen TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_sessions_user_started ON training_sessions(user_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_moves_session_ply ON training_moves(session_id, ply);
CREATE INDEX IF NOT EXISTS idx_puzzles_mode_difficulty ON training_puzzles(mode, difficulty);
