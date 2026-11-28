-- Optional player profiles. Joining a room stays anonymous; a profile is an
-- opt-in capability-token account used only to keep a nickname and progress.
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS player_profiles (
    id           TEXT PRIMARY KEY,
    nickname     TEXT NOT NULL,
    link_token   TEXT NOT NULL UNIQUE,
    created_at   INTEGER NOT NULL
);

-- A player can return under a new game identity (fresh browser session), so a
-- profile may hold several uuids. Each one is claimed once with its rejoin token.
CREATE TABLE IF NOT EXISTS profile_links (
    profile_id   TEXT NOT NULL REFERENCES player_profiles(id) ON DELETE CASCADE,
    player_uuid  TEXT NOT NULL UNIQUE,
    linked_at    INTEGER NOT NULL,
    PRIMARY KEY (profile_id, player_uuid)
);

CREATE INDEX IF NOT EXISTS idx_profile_links_owner ON profile_links(profile_id);
