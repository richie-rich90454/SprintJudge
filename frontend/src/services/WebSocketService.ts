import { Subject, Observable } from "rxjs";

export interface WsMessage {
    type: string;
    [key: string]: unknown;
}

/**
 * Thin wrapper around the browser WebSocket with an RxJS subject for inbound
 * messages. One connection per player tab.
 */
export class WebSocketService {
    private static _instance: WebSocketService | null = null;
    private socket: WebSocket | null = null;
    private readonly messages$ = new Subject<WsMessage>();
    private readonly status$ = new Subject<"open" | "closed">();
    // Messages sent while the handshake is still in flight; dropped silently
    // otherwise, which killed the very first JOIN of every session.
    private pending: WsMessage[] = [];

    static get instance(): WebSocketService {
        if (!this._instance) this._instance = new WebSocketService();
        return this._instance;
    }

    connect(url: string): void {
        if (this.socket && this.socket.readyState <= 1) return;
        const socket = new WebSocket(url);
        this.socket = socket;
        socket.onopen = () => {
            const queued = this.pending;
            this.pending = [];
            for (const msg of queued) this.send(msg);
            this.status$.next("open");
        };
        socket.onmessage = (ev) => {
            try {
                this.messages$.next(JSON.parse(ev.data) as WsMessage);
            } catch {
                /* ignore malformed */
            }
        };
        socket.onclose = () => {
            this.pending = [];
            this.status$.next("closed");
        };
    }

    send(msg: WsMessage): void {
        if (
            !this.socket ||
            this.socket.readyState === WebSocket.CLOSING ||
            this.socket.readyState === WebSocket.CLOSED
        )
            return;
        if (this.socket.readyState === WebSocket.OPEN) {
            this.socket.send(JSON.stringify(msg));
        } else if (this.pending.length < 100) {
            this.pending.push(msg);
        }
    }

    onMessage(): Observable<WsMessage> {
        return this.messages$.asObservable();
    }

    onStatus(): Observable<"open" | "closed"> {
        return this.status$.asObservable();
    }

    disconnect(): void {
        this.socket?.close();
        this.socket = null;
    }
}

export const webSocketService = WebSocketService.instance;
