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

describe("WebSocketService connect-open-join-drop-rejoin sagas", () => {
    test("full saga preserves join-then-submit ordering across a drop", () => {
        const svc = makeService();
        svc.connect("ws://host/game");
        const first = lastSocket();
        svc.send({ type: "JOIN", pin: "1234" });
        first.readyState = FakeSocket.OPEN;
        first.onopen?.();
        expect(first.sent).toEqual([JSON.stringify({ type: "JOIN", pin: "1234" })]);
        svc.send({ type: "SUBMIT", n: 1 });
        expect(first.sent).toHaveLength(2);
        first.readyState = FakeSocket.CLOSED;
        first.onclose?.();
        vi.advanceTimersByTime(1000);
        expect(FakeSocket.created).toHaveLength(2);
        const second = lastSocket();
        svc.send({ type: "JOIN", pin: "1234", rejoinToken: "tok" });
        second.readyState = FakeSocket.OPEN;
        second.onopen?.();
        expect(second.sent).toEqual([JSON.stringify({ type: "JOIN", pin: "1234", rejoinToken: "tok" })]);
        svc.send({ type: "SUBMIT", n: 2 });
        expect(second.sent).toHaveLength(2);
    });

    test("queued pre-drop submits flush only after the rejoin on the new socket", () => {
        const svc = makeService();
        const order: string[] = [];
        svc.onStatus().subscribe((s) => {
            if (s === "open") order.push("open");
        });
        svc.connect("ws://host/game");
        const first = lastSocket();
        first.readyState = FakeSocket.CLOSED;
        svc.send({ type: "STALE_SUBMIT" });
        first.onclose?.();
        vi.advanceTimersByTime(1000);
        const second = lastSocket();
        second.readyState = FakeSocket.OPEN;
        const orig = second.send.bind(second);
        second.send = (d: string) => {
            order.push(d);
            orig(d);
        };
        second.onopen?.();
        expect(order[0]).toBe("open");
        expect(order.slice(1)).toEqual([JSON.stringify({ type: "STALE_SUBMIT" })]);
    });

    test("two consecutive drops each schedule exactly one reconnect", () => {
        const svc = makeService();
        svc.connect("ws://host/game");
        lastSocket().onclose?.();
        vi.advanceTimersByTime(1000);
        expect(FakeSocket.created).toHaveLength(2);
        lastSocket().onclose?.();
        vi.advanceTimersByTime(2000);
        expect(FakeSocket.created).toHaveLength(3);
    });

    test("open resets the backoff so the next drop waits the base delay again", () => {
        const svc = makeService();
        svc.connect("ws://host/game");
        lastSocket().onclose?.();
        vi.advanceTimersByTime(1000);
        const second = lastSocket();
        second.readyState = FakeSocket.OPEN;
        second.onopen?.();
        second.readyState = FakeSocket.CLOSED;
        second.onclose?.();
        vi.advanceTimersByTime(999);
        expect(FakeSocket.created).toHaveLength(2);
        vi.advanceTimersByTime(1);
        expect(FakeSocket.created).toHaveLength(3);
    });

    test("messages sent on the fresh socket bypass the stale queue", () => {
        const svc = makeService();
        svc.connect("ws://host/game");
        const first = lastSocket();
        first.readyState = FakeSocket.OPEN;
        first.onopen?.();
        svc.send({ type: "A" });
        first.readyState = FakeSocket.CLOSED;
        first.onclose?.();
        vi.advanceTimersByTime(1000);
        const second = lastSocket();
        second.readyState = FakeSocket.OPEN;
        second.onopen?.();
        svc.send({ type: "B" });
        expect(second.sent).toEqual([JSON.stringify({ type: "B" })]);
    });
});

describe("WebSocketService backlog cap flows", () => {
    test("exactly 100 queued messages all flush in order", () => {
        const svc = makeService();
        svc.connect("ws://host/game");
        for (let i = 0; i < 100; i++) svc.send({ type: "M", i });
        const sock = lastSocket();
        sock.readyState = FakeSocket.OPEN;
        sock.onopen?.();
        expect(sock.sent).toHaveLength(100);
        expect(sock.sent[0]).toBe(JSON.stringify({ type: "M", i: 0 }));
        expect(sock.sent[99]).toBe(JSON.stringify({ type: "M", i: 99 }));
    });

    test("101st queued message is dropped while the first 100 flush", () => {
        const svc = makeService();
        svc.connect("ws://host/game");
        for (let i = 0; i < 101; i++) svc.send({ type: "M", i });
        const sock = lastSocket();
        sock.readyState = FakeSocket.OPEN;
        sock.onopen?.();
        expect(sock.sent).toHaveLength(100);
        expect(sock.sent).not.toContain(JSON.stringify({ type: "M", i: 100 }));
    });

    test("cap applies on the CLOSED-with-url queue path too", () => {
        const svc = makeService();
        svc.connect("ws://host/game");
        const sock = lastSocket();
        sock.readyState = FakeSocket.CLOSED;
        for (let i = 0; i < 120; i++) svc.send({ type: "C", i });
        sock.onclose?.();
        vi.advanceTimersByTime(1000);
        const next = lastSocket();
        next.readyState = FakeSocket.OPEN;
        next.onopen?.();
        expect(next.sent).toHaveLength(100);
    });

    test("backlog survives a drop then flushes on reconnect in original order", () => {
        const svc = makeService();
        svc.connect("ws://host/game");
        const first = lastSocket();
        svc.send({ type: "ONE" });
        svc.send({ type: "TWO" });
        first.readyState = FakeSocket.CLOSED;
        first.onclose?.();
        vi.advanceTimersByTime(1000);
        const second = lastSocket();
        second.readyState = FakeSocket.OPEN;
        second.onopen?.();
        expect(second.sent).toEqual([JSON.stringify({ type: "ONE" }), JSON.stringify({ type: "TWO" })]);
    });

    test("flush clears the queue so a second open sends nothing twice", () => {
        const svc = makeService();
        svc.connect("ws://host/game");
        const sock = lastSocket();
        svc.send({ type: "ONCE" });
        sock.readyState = FakeSocket.OPEN;
        sock.onopen?.();
        expect(sock.sent).toHaveLength(1);
        sock.onopen?.();
        expect(sock.sent).toHaveLength(1);
    });
});

describe("WebSocketService rapid connect-disconnect cycles", () => {
    test("connect-disconnect-connect opens two sockets and fires no reconnect", () => {
        const svc = makeService();
        svc.connect("ws://host/game");
        svc.disconnect();
        svc.connect("ws://host/game");
        expect(FakeSocket.created).toHaveLength(2);
        vi.advanceTimersByTime(60_000);
        expect(FakeSocket.created).toHaveLength(2);
    });

    test("three rapid cycles leave three sockets and a clean pending queue", () => {
        const svc = makeService();
        for (let i = 0; i < 3; i++) {
            svc.connect("ws://host/game");
            svc.send({ type: "PING", i });
            svc.disconnect();
        }
        svc.connect("ws://host/game");
        const sock = lastSocket();
        sock.readyState = FakeSocket.OPEN;
        sock.onopen?.();
        expect(sock.sent).toHaveLength(0);
        vi.advanceTimersByTime(60_000);
        expect(FakeSocket.created).toHaveLength(4);
    });

    test("disconnect then immediate reconnect reopens and delivers", () => {
        const svc = makeService();
        svc.connect("ws://host/game");
        lastSocket().onclose?.();
        svc.disconnect();
        vi.advanceTimersByTime(60_000);
        expect(FakeSocket.created).toHaveLength(1);
        svc.connect("ws://host/game");
        const sock = lastSocket();
        sock.readyState = FakeSocket.OPEN;
        sock.onopen?.();
        svc.send({ type: "HELLO" });
        expect(sock.sent).toEqual([JSON.stringify({ type: "HELLO" })]);
    });

    test("open after disconnect is ignored by the dead service", () => {
        const svc = makeService();
        const seen: string[] = [];
        svc.onStatus().subscribe((s) => seen.push(s));
        svc.connect("ws://host/game");
        const sock = lastSocket();
        svc.disconnect();
        sock.readyState = FakeSocket.OPEN;
        sock.onopen?.();
        expect(seen).toEqual(["closed"]);
    });
});

describe("WebSocketService close-code variants", () => {
    test("close with a normal payload still schedules a reconnect", () => {
        const svc = makeService();
        svc.connect("ws://host/game");
        (lastSocket().onclose as unknown as (ev: unknown) => void)?.({ code: 1000, reason: "done" });
        vi.advanceTimersByTime(1000);
        expect(FakeSocket.created).toHaveLength(2);
    });

    test("close with an abnormal code still schedules a reconnect", () => {
        const svc = makeService();
        svc.connect("ws://host/game");
        (lastSocket().onclose as unknown as (ev: unknown) => void)?.({ code: 1006, reason: "" });
        vi.advanceTimersByTime(1000);
        expect(FakeSocket.created).toHaveLength(2);
    });

    test("close after intentional disconnect never reschedules even with code 1006", () => {
        const svc = makeService();
        svc.connect("ws://host/game");
        const sock = lastSocket();
        svc.disconnect();
        (sock.onclose as unknown as (ev: unknown) => void)?.({ code: 1006 });
        vi.advanceTimersByTime(60_000);
        expect(FakeSocket.created).toHaveLength(1);
    });

    test("policy-violation close still reconnects since the client cannot know better", () => {
        const svc = makeService();
        const seen: string[] = [];
        svc.onStatus().subscribe((s) => seen.push(s));
        svc.connect("ws://host/game");
        (lastSocket().onclose as unknown as (ev: unknown) => void)?.({ code: 1008, reason: "policy" });
        expect(seen).toContain("closed");
        vi.advanceTimersByTime(1000);
        expect(FakeSocket.created).toHaveLength(2);
    });
});

describe("WebSocketService never-connected and exhaustion flows", () => {
    test("five sends on a never-connected socket vanish then connect starts clean", () => {
        const svc = makeService();
        for (let i = 0; i < 5; i++) svc.send({ type: "GHOST", i });
        svc.connect("ws://host/game");
        const sock = lastSocket();
        sock.readyState = FakeSocket.OPEN;
        sock.onopen?.();
        expect(sock.sent).toHaveLength(0);
        svc.send({ type: "REAL" });
        expect(sock.sent).toEqual([JSON.stringify({ type: "REAL" })]);
    });

    test("ten consecutive drops exhaust retries then report failed", () => {
        const svc = makeService();
        const seen: string[] = [];
        svc.onStatus().subscribe((s) => seen.push(s));
        svc.connect("ws://host/game");
        for (let i = 0; i < 10; i++) {
            lastSocket().onclose?.();
            vi.advanceTimersByTime(30_000);
        }
        expect(FakeSocket.created).toHaveLength(11);
        lastSocket().onclose?.();
        expect(seen[seen.length - 1]).toBe("failed");
        vi.advanceTimersByTime(120_000);
        expect(FakeSocket.created).toHaveLength(11);
    });

    test("failed service stays failed and never opens new sockets", () => {
        const svc = makeService();
        const seen: string[] = [];
        svc.onStatus().subscribe((s) => seen.push(s));
        svc.connect("ws://host/game");
        for (let i = 0; i < 11; i++) {
            lastSocket().onclose?.();
            vi.advanceTimersByTime(30_000);
        }
        expect(seen).toContain("failed");
        const count = FakeSocket.created.length;
        vi.advanceTimersByTime(300_000);
        expect(FakeSocket.created).toHaveLength(count);
    });

    test("fresh connect after exhaustion starts over with a clean retry budget", () => {
        const svc = makeService();
        svc.connect("ws://host/game");
        for (let i = 0; i < 11; i++) {
            lastSocket().onclose?.();
            vi.advanceTimersByTime(30_000);
        }
        const before = FakeSocket.created.length;
        lastSocket().readyState = FakeSocket.CLOSED;
        svc.connect("ws://host/game");
        expect(FakeSocket.created).toHaveLength(before + 1);
        lastSocket().onclose?.();
        vi.advanceTimersByTime(1000);
        expect(FakeSocket.created).toHaveLength(before + 2);
    });
});

describe("WebSocketService late subscribers and malformed mid-stream frames", () => {
    test("late subscriber after a drop replays closed", () => {
        const svc = makeService();
        svc.connect("ws://host/game");
        lastSocket().onclose?.();
        const seen: string[] = [];
        svc.onStatus().subscribe((s) => seen.push(s));
        expect(seen).toEqual(["closed"]);
    });

    test("late subscriber after exhaustion replays failed", () => {
        const svc = makeService();
        svc.connect("ws://host/game");
        for (let i = 0; i < 11; i++) {
            lastSocket().onclose?.();
            vi.advanceTimersByTime(30_000);
        }
        const seen: string[] = [];
        svc.onStatus().subscribe((s) => seen.push(s));
        expect(seen).toEqual(["failed"]);
    });

    test("multiple late subscribers each replay the current status", () => {
        const svc = makeService();
        svc.connect("ws://host/game");
        lastSocket().readyState = FakeSocket.OPEN;
        lastSocket().onopen?.();
        const a: string[] = [];
        const b: string[] = [];
        svc.onStatus().subscribe((s) => a.push(s));
        svc.onStatus().subscribe((s) => b.push(s));
        expect(a).toEqual(["open"]);
        expect(b).toEqual(["open"]);
    });

    test("valid, malformed and empty frames interleave without breaking the stream", () => {
        const svc = makeService();
        const got: unknown[] = [];
        svc.onMessage().subscribe((m) => got.push(m));
        svc.connect("ws://host/game");
        const sock = lastSocket();
        sock.onmessage?.({ data: '{"type":"ONE"}' });
        sock.onmessage?.({ data: "garbage{{{" });
        sock.onmessage?.({ data: "" });
        sock.onmessage?.({ data: '{"type":"TWO"}' });
        sock.onmessage?.({ data: "[unclosed" });
        sock.onmessage?.({ data: '{"type":"THREE","n":3}' });
        expect(got).toEqual([{ type: "ONE" }, { type: "TWO" }, { type: "THREE", n: 3 }]);
    });

    test("coercible frames pass through while undefined is ignored mid-stream", () => {
        const svc = makeService();
        const got: unknown[] = [];
        svc.onMessage().subscribe((m) => got.push(m));
        svc.connect("ws://host/game");
        const sock = lastSocket();
        sock.onmessage?.({ data: '{"type":"A"}' });
        sock.onmessage?.({ data: 42 });
        sock.onmessage?.({ data: null });
        sock.onmessage?.({ data: undefined });
        sock.onmessage?.({ data: '{"type":"B"}' });
        expect(got).toEqual([{ type: "A" }, 42, null, { type: "B" }]);
    });

    test("array and primitive JSON frames pass through untouched mid-stream", () => {
        const svc = makeService();
        const got: unknown[] = [];
        svc.onMessage().subscribe((m) => got.push(m));
        svc.connect("ws://host/game");
        const sock = lastSocket();
        sock.onmessage?.({ data: '{"type":"A"}' });
        sock.onmessage?.({ data: "[1,2]" });
        sock.onmessage?.({ data: '"just a string"' });
        sock.onmessage?.({ data: '{"type":"B"}' });
        expect(got).toEqual([{ type: "A" }, [1, 2], "just a string", { type: "B" }]);
    });
});
