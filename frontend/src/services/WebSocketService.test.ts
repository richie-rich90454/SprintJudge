import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";
import { WebSocketService } from "./WebSocketService";

type Handler = (ev: unknown) => void;

class FakeSocket {
    static OPEN = 1;
    static CONNECTING = 0;
    static CLOSING = 2;
    static CLOSED = 3;
    static created: FakeSocket[] = [];
    readyState = 0;
    sent: string[] = [];
    onopen: (() => void) | null = null;
    onmessage: Handler | null = null;
    onclose: (() => void) | null = null;
    autoClose = false;
    constructor(_url: string) {
        FakeSocket.created.push(this);
    }
    send(data: string) {
        this.sent.push(data);
    }
    close() {
        if (this.autoClose && this.onclose) this.onclose();
    }
}

function lastSocket(): FakeSocket {
    return FakeSocket.created[FakeSocket.created.length - 1];
}

function makeService(): WebSocketService {
    return new WebSocketService();
}

beforeEach(() => {
    vi.useFakeTimers();
    FakeSocket.created = [];
    vi.stubGlobal("WebSocket", FakeSocket);
});

afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
});

describe("WebSocketService connect", () => {
    test("singleton instance is shared", () => {
        expect(WebSocketService.instance).toBe(WebSocketService.instance);
    });
    test("connect opens a socket and records the url", () => {
        const svc = makeService();
        svc.connect("ws://host/game");
        expect(FakeSocket.created).toHaveLength(1);
    });

    test("connect while CONNECTING returns early without new socket", () => {
        const svc = makeService();
        svc.connect("ws://host/game");
        svc.connect("ws://host/game");
        expect(FakeSocket.created).toHaveLength(1);
    });

    test("connect while OPEN returns early", () => {
        const svc = makeService();
        svc.connect("ws://host/game");
        lastSocket().readyState = FakeSocket.OPEN;
        svc.connect("ws://host/game");
        expect(FakeSocket.created).toHaveLength(1);
    });

    test("connect after close reopens and clears the pending reconnect timer", () => {
        const svc = makeService();
        svc.connect("ws://host/game");
        lastSocket().readyState = FakeSocket.CLOSED;
        lastSocket().onclose?.();
        expect(FakeSocket.created).toHaveLength(1);
        svc.connect("ws://host/game");
        expect(FakeSocket.created).toHaveLength(2);
        vi.advanceTimersByTime(60_000);
        expect(FakeSocket.created).toHaveLength(2);
    });
});

describe("WebSocketService open and queue flush", () => {
    test("open emits status and flushes queued messages in order", () => {
        const svc = makeService();
        const seen: string[] = [];
        svc.onStatus().subscribe((s) => seen.push(s));
        svc.connect("ws://host/game");
        const sock = lastSocket();
        svc.send({ type: "JOIN" });
        svc.send({ type: "SUBMIT" });
        expect(sock.sent).toHaveLength(0);
        sock.readyState = FakeSocket.OPEN;
        sock.onopen?.();
        expect(seen).toEqual(["closed", "open"]);
        expect(sock.sent).toEqual([JSON.stringify({ type: "JOIN" }), JSON.stringify({ type: "SUBMIT" })]);
    });

    test("open signal fires before the flushed backlog", () => {
        const svc = makeService();
        const order: string[] = [];
        svc.onStatus().subscribe((s) => {
            if (s === "open") order.push("status-open");
        });
        svc.connect("ws://host/game");
        const sock = lastSocket();
        svc.send({ type: "JOIN" });
        svc.send({ type: "SUBMIT" });
        sock.readyState = FakeSocket.OPEN;
        const origSend = sock.send.bind(sock);
        sock.send = (d: string) => {
            order.push(`sent:${d}`);
            origSend(d);
        };
        sock.onopen?.();
        expect(order).toEqual([
            "status-open",
            `sent:${JSON.stringify({ type: "JOIN" })}`,
            `sent:${JSON.stringify({ type: "SUBMIT" })}`,
        ]);
    });

    test("open on a superseded socket closes it and emits nothing", () => {
        const svc = makeService();
        const seen: string[] = [];
        svc.onStatus().subscribe((s) => seen.push(s));
        svc.connect("ws://host/game");
        const old = lastSocket();
        old.readyState = FakeSocket.CLOSED;
        old.onclose?.();
        vi.advanceTimersByTime(1000);
        expect(FakeSocket.created).toHaveLength(2);
        const closeSpy = vi.spyOn(old, "close");
        old.onopen?.();
        expect(closeSpy).toHaveBeenCalled();
        expect(seen).not.toContain("open");
    });

    test("late status subscribers replay the last status", () => {
        const svc = makeService();
        svc.connect("ws://host/game");
        const sock = lastSocket();
        sock.readyState = FakeSocket.OPEN;
        sock.onopen?.();
        const seen: string[] = [];
        svc.onStatus().subscribe((s) => seen.push(s));
        expect(seen).toEqual(["open"]);
    });
});

describe("WebSocketService inbound messages", () => {
    test("valid JSON is delivered to subscribers", () => {
        const svc = makeService();
        const got: unknown[] = [];
        svc.onMessage().subscribe((m) => got.push(m));
        svc.connect("ws://host/game");
        lastSocket().onmessage?.({ data: '{"type":"PING"}' });
        expect(got).toEqual([{ type: "PING" }]);
    });

    test("malformed JSON is ignored", () => {
        const svc = makeService();
        const got: unknown[] = [];
        svc.onMessage().subscribe((m) => got.push(m));
        svc.connect("ws://host/game");
        lastSocket().onmessage?.({ data: "not-json{{{" });
        expect(got).toHaveLength(0);
    });

    test("message from a stale socket is ignored", () => {
        const svc = makeService();
        const got: unknown[] = [];
        svc.onMessage().subscribe((m) => got.push(m));
        svc.connect("ws://host/game");
        const old = lastSocket();
        old.readyState = FakeSocket.CLOSED;
        old.onclose?.();
        vi.advanceTimersByTime(1000);
        old.onmessage?.({ data: '{"type":"STALE"}' });
        expect(got).toHaveLength(0);
    });

    test("close from a stale socket is ignored", () => {
        const svc = makeService();
        const seen: string[] = [];
        svc.onStatus().subscribe((s) => seen.push(s));
        svc.connect("ws://host/game");
        const old = lastSocket();
        old.readyState = FakeSocket.CLOSED;
        old.onclose?.();
        vi.advanceTimersByTime(1000);
        expect(FakeSocket.created).toHaveLength(2);
        old.onclose?.();
        expect(seen).toEqual(["closed", "closed"]);
    });
});

describe("WebSocketService reconnect", () => {
    test("unintended close schedules a reconnect", () => {
        const svc = makeService();
        svc.connect("ws://host/game");
        lastSocket().onclose?.();
        expect(FakeSocket.created).toHaveLength(1);
        vi.advanceTimersByTime(1000);
        expect(FakeSocket.created).toHaveLength(2);
    });

    test("backoff delay grows then caps at 30s", () => {
        const svc = makeService() as unknown as Record<string, number>;
        svc._maxRetries = 20;
        svc._retryCount = 10;
        (svc as unknown as Record<string, unknown>)._url = "ws://host/game";
        (svc as unknown as { _scheduleReconnect: () => void })._scheduleReconnect();
        vi.advanceTimersByTime(29_999);
        expect(FakeSocket.created).toHaveLength(0);
        vi.advanceTimersByTime(1);
        expect(FakeSocket.created).toHaveLength(1);
    });

    test("max retries flips status to failed without reconnect", () => {
        const svc = makeService() as unknown as Record<string, number> & {
            _scheduleReconnect: () => void;
        };
        const seen: string[] = [];
        (svc as unknown as WebSocketService).onStatus().subscribe((s) => seen.push(s));
        svc._url = "ws://host/game" as unknown as number;
        svc._retryCount = 10;
        svc._maxRetries = 10;
        svc._scheduleReconnect();
        expect(seen).toContain("failed");
        vi.advanceTimersByTime(60_000);
        expect(FakeSocket.created).toHaveLength(0);
    });

    test("scheduled reconnect with cleared url does nothing", () => {
        const svc = makeService() as unknown as Record<string, unknown> & {
            _scheduleReconnect: () => void;
        };
        svc._url = "ws://host/game";
        svc._retryCount = 0;
        svc._scheduleReconnect();
        svc._url = null;
        vi.advanceTimersByTime(2000);
        expect(FakeSocket.created).toHaveLength(0);
    });

    test("intentional disconnect clears queue and never reconnects", () => {
        const svc = makeService();
        svc.connect("ws://host/game");
        svc.send({ type: "JOIN" });
        svc.disconnect();
        vi.advanceTimersByTime(60_000);
        expect(FakeSocket.created).toHaveLength(1);
        svc.send({ type: "LATE" });
        expect(lastSocket().sent).toHaveLength(0);
    });

    test("disconnect clears a scheduled reconnect timer", () => {
        const svc = makeService();
        svc.connect("ws://host/game");
        lastSocket().onclose?.();
        svc.disconnect();
        vi.advanceTimersByTime(60_000);
        expect(FakeSocket.created).toHaveLength(1);
    });

    test("disconnect fires socket close which reports closed without reschedule", () => {
        const svc = makeService();
        const seen: string[] = [];
        svc.onStatus().subscribe((s) => seen.push(s));
        svc.connect("ws://host/game");
        const sock = lastSocket();
        sock.autoClose = true;
        svc.disconnect();
        expect(seen).toContain("closed");
        vi.advanceTimersByTime(60_000);
        expect(FakeSocket.created).toHaveLength(1);
    });
});

describe("WebSocketService send", () => {
    test("send with no socket is a no-op", () => {
        const svc = makeService();
        expect(() => svc.send({ type: "JOIN" })).not.toThrow();
    });

    test("send on OPEN socket writes immediately", () => {
        const svc = makeService();
        svc.connect("ws://host/game");
        const sock = lastSocket();
        sock.readyState = FakeSocket.OPEN;
        svc.send({ type: "PING", n: 1 });
        expect(sock.sent).toEqual([JSON.stringify({ type: "PING", n: 1 })]);
    });

    test("send while CLOSING queues the message", () => {
        const svc = makeService();
        svc.connect("ws://host/game");
        lastSocket().readyState = FakeSocket.CLOSING;
        svc.send({ type: "Q" });
        expect(lastSocket().sent).toHaveLength(0);
        lastSocket().readyState = FakeSocket.OPEN;
        lastSocket().onopen?.();
        expect(lastSocket().sent).toHaveLength(1);
    });

    test("pending queue caps at 100 messages", () => {
        const svc = makeService();
        svc.connect("ws://host/game");
        for (let i = 0; i < 150; i++) svc.send({ type: "M", i });
        const sock = lastSocket();
        sock.readyState = FakeSocket.OPEN;
        sock.onopen?.();
        expect(sock.sent).toHaveLength(100);
    });

    test("send while CLOSED with url queues for the next connection", () => {
        const svc = makeService();
        svc.connect("ws://host/game");
        const sock = lastSocket();
        sock.readyState = FakeSocket.CLOSED;
        svc.send({ type: "AFTER_DROP" });
        sock.onclose?.();
        vi.advanceTimersByTime(1000);
        const next = lastSocket();
        next.readyState = FakeSocket.OPEN;
        next.onopen?.();
        expect(next.sent).toContain(JSON.stringify({ type: "AFTER_DROP" }));
    });

    test("send while CLOSED without url is dropped", () => {
        const svc = makeService();
        const internals = svc as unknown as Record<string, unknown>;
        svc.connect("ws://host/game");
        const sock = lastSocket();
        sock.readyState = FakeSocket.CLOSED;
        internals._url = null;
        internals._intentionalClose = false;
        expect(() => svc.send({ type: "LOST" })).not.toThrow();
        expect(sock.sent).toHaveLength(0);
    });

    test("send while CLOSED after intentional close is dropped", () => {
        const svc = makeService();
        const internals = svc as unknown as Record<string, unknown>;
        svc.connect("ws://host/game");
        const sock = lastSocket();
        svc.disconnect();
        internals.socket = sock;
        sock.readyState = FakeSocket.CLOSED;
        svc.send({ type: "LOST" });
        expect(sock.sent).toHaveLength(0);
    });
});
