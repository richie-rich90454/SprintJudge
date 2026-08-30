package com.sprintjudge.service.room;

import com.sprintjudge.service.GameRoom;
import com.sprintjudge.util.IntObjectMap;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.IntFunction;

/**
 * Pin-keyed live-room registry at thousands-of-rooms scale.
 *
 * <p>Keys are the raw six-digit integers (no String hashing, no boxing);
 * reads take a cheap read lock, writes are serialized. Backed by the
 * allocation-free {@link IntObjectMap}.
 */
public final class RoomRegistry {

    private final IntObjectMap<GameRoom> rooms = new IntObjectMap<>(2048);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public GameRoom get(int pin) {
        lock.readLock().lock();
        try {
            return rooms.get(pin);
        } finally {
            lock.readLock().unlock();
        }
    }

    public GameRoom computeIfAbsent(int pin, IntFunction<GameRoom> factory) {
        lock.writeLock().lock();
        try {
            GameRoom existing = rooms.get(pin);
            if (existing != null) return existing;
            GameRoom created = factory.apply(pin);
            rooms.put(pin, created);
            return created;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public GameRoom remove(int pin) {
        lock.writeLock().lock();
        try {
            return rooms.remove(pin);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Unconditional insert (caller guarantees the pin is free). */
    public void put(int pin, GameRoom room) {
        lock.writeLock().lock();
        try {
            rooms.put(pin, room);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Inserts only if absent; returns the existing room when one is present. */
    public GameRoom putIfAbsent(int pin, GameRoom room) {
        lock.writeLock().lock();
        try {
            GameRoom existing = rooms.get(pin);
            if (existing != null) return existing;
            rooms.put(pin, room);
            return room;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Snapshot of all live rooms (for the idle sweeper). */
    public java.util.List<GameRoom> snapshot() {
        lock.readLock().lock();
        try {
            java.util.List<GameRoom> out = new java.util.ArrayList<>(rooms.size());
            rooms.forEach((k, v) -> out.add(v));
            return out;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return rooms.size();
        } finally {
            lock.readLock().unlock();
        }
    }
}
