# ADR 0001 — Event-Sourced Rooms

Status: accepted
Date: 2026-09-18

## Context

Rooms are currently held in a live in-memory map with direct broadcasts.
Resume, audit, and honest measurement need a durable ordered history.

## Decision

Model each room as an append-only event log. State is a pure fold over
events. The wire carries versioned events after a full snapshot.

## Consequences

- New `room_events` table stores every event with a per-room sequence.
- A pure reducer computes state from events, fully tested.
- Existing `GameRoomManager` stays untouched during dual-write migration.
- Resume replays missed events after the snapshot.
