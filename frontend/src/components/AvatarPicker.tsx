import { useUIStore } from "../stores/useUIStore";

export const AVATARS = ["⚡", "🚀", "🦊", "🐼", "🦄", "🐝", "🍕", "🎮", "🌊", "🔥", "⭐", "🤖"];

/** Local profile glyph — shown on your own card, never sent to the server. */
export function AvatarPicker() {
    const avatar = useUIStore((s) => s.avatar);
    const setAvatar = useUIStore((s) => s.setAvatar);
    return (
        <div className="flex flex-wrap gap-2" role="radiogroup" aria-label="Pick an avatar">
            {AVATARS.map((a) => (
                <button
                    key={a}
                    type="button"
                    role="radio"
                    aria-checked={avatar === a}
                    aria-label={`Avatar ${a}`}
                    onClick={() => setAvatar(a)}
                    className="min-h-[44px] min-w-[44px] text-xl rounded-[10px] border transition-colors"
                    style={
                        avatar === a
                            ? { borderColor: "var(--oq-accent)", background: "var(--oq-accent-tint)" }
                            : { borderColor: "var(--oq-border)" }
                    }
                >
                    {a}
                </button>
            ))}
        </div>
    );
}
