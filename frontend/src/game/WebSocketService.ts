import { BehaviorSubject, Subject, Observable } from "rxjs";

export interface WsMessage {
    type: string;
    [key: string]: unknown;
}

export type SocketStatus = "open" | "closed" | "failed";

/**
 * Thin wrapper around the browser WebSocket with an RxJS subject for inbound
 * messages. One connection per player tab.
 */
export class WebSocketService {
    private static _instance: WebSocketService | null = null;
    private socket: WebSocket | null = null;
    private readonly messages$ = new Subject<WsMessage>();
    private readonly status$ = new BehaviorSubject<SocketStatus>("closed");
    // Messages sent while the handshake is still in flight; dropped silently
    // otherwise, which killed the very first JOIN of every session.
    private pending: WsMessage[] = [];

    // Reconnection state
    private _url: string | null = null;
    private _retryCount = 0;
    private _reconnectTimer: ReturnType<typeof setTimeout> | null = null;
    private _maxRetries = 10;
    private _intentionalClose = false;

    static get instance(): WebSocketService {
        if (!this._instance) this._instance = new WebSocketService();
        return this._instance;
    }

    connect(url: string): void {
        if (this.socket && this.socket.readyState <= 1) return;
        this._url = url;
        this._intentionalClose = false;
        this._retryCount = 0;
        if (this._reconnectTimer) {
            clearTimeout(this._reconnectTimer);
            this._reconnectTimer = null;
        }
        this._openSocket(url);
    }

    private _openSocket(url: string): void {
        const socket = new WebSocket(url);
        this.socket = socket;
        socket.onopen = () => {
            if (this.socket !== socket) {
                socket.close();
                return; // superseded by a newer connection
            }
            this._retryCount = 0;
            // Signal open FIRST so the auto-JOIN precedes any flushed backlog:
            // queued pre-drop SUBMITs must never run ahead of the rejoin.
            this.status$.next("open");
            const queued = this.pending;
            this.pending = [];
            for (const msg of queued) this.send(msg);
        };
        socket.onmessage = (ev) => {
            if (this.socket !== socket) return; // stale socket's mail is dead
            try {
                this.messages$.next(JSON.parse(ev.data) as WsMessage);
            } catch {
                /* ignore malformed */
            }
        };
        socket.onclose = () => {
            if (this.socket !== socket) return; // superseded; owner handles state
            // Keep the queue across unintended drops so a JOIN sent during a
            // failed first handshake still flushes on reconnect. Only an
            // intentional disconnect discards queued messages.
            if (this._intentionalClose) this.pending = [];
            this.status$.next("closed");
            if (!this._intentionalClose && this._url) {
                this._scheduleReconnect();
            }
        };
    }

    private _scheduleReconnect(): void {
        if (this._retryCount >= this._maxRetries) {
            this.status$.next("failed");
            return;
        }
        const delay = Math.min(1000 * 2 ** this._retryCount, 30_000);
        this._retryCount++;
        this._reconnectTimer = setTimeout(() => {
            this._reconnectTimer = null;
            if (!this._url) return;
            this._openSocket(this._url);
        }, delay);
    }

    send(msg: WsMessage): void {
        if (!this.socket) return;
        if (this.socket.readyState === WebSocket.OPEN) {
            this.socket.send(JSON.stringify(msg));
        } else if (
            this.socket.readyState === WebSocket.CONNECTING ||
            this.socket.readyState === WebSocket.CLOSING
        ) {
            if (this.pending.length < 100) this.pending.push(msg);
        } else if (this._url && !this._intentionalClose && this.pending.length < 100) {
            // CLOSED but reconnect is scheduled — queue for next connection
            this.pending.push(msg);
        }
    }

    onMessage(): Observable<WsMessage> {
        return this.messages$.asObservable();
    }

    onStatus(): Observable<SocketStatus> {
        return this.status$.asObservable();
    }

    disconnect(): void {
        this._intentionalClose = true;
        this.pending = [];
        if (this._reconnectTimer) {
            clearTimeout(this._reconnectTimer);
            this._reconnectTimer = null;
        }
        this.socket?.close();
        this.socket = null;
    }
}

export const webSocketService = WebSocketService.instance;
