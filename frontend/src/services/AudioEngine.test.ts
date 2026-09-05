import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";

vi.mock("tone", () => {
    class Voice {
        triggerAttackRelease = vi.fn();
        constructor(..._args: unknown[]) {}
        connect(..._args: unknown[]) {
            return this;
        }
    }
    class Gain {
        level: unknown;
        constructor(level: unknown) {
            this.level = level;
        }
        toDestination() {
            return this;
        }
        connect(..._args: unknown[]) {
            return this;
        }
    }
    class Filter {
        constructor(..._args: unknown[]) {}
        connect(dest: unknown) {
            return dest;
        }
    }
    class BitCrusher {
        constructor(..._args: unknown[]) {}
        connect(..._args: unknown[]) {
            return this;
        }
    }
    class Loop {
        cb: (t: unknown) => void;
        interval: unknown;
        start = vi.fn();
        stop = vi.fn();
        dispose = vi.fn();
        constructor(cb: (t: unknown) => void, interval: unknown) {
            this.cb = cb;
            this.interval = interval;
        }
    }
    const transport = { bpm: { value: 0 }, start: vi.fn() };
    const destination = { mute: false };
    return {
        Gain,
        PolySynth: Voice,
        MonoSynth: Voice,
        NoiseSynth: Voice,
        Synth: Voice,
        Filter,
        BitCrusher,
        Loop,
        getTransport: () => transport,
        getDestination: () => destination,
        start: vi.fn(async () => {}),
        now: () => 7.5,
        Frequency: (n: unknown) => ({ toNote: () => `N${String(n)}` }),
    };
});

import * as Tone from "tone";
import { audio } from "./AudioEngine";

type VoiceFake = { triggerAttackRelease: ReturnType<typeof vi.fn> };
type LoopFake = {
    cb: (t: unknown) => void;
    start: ReturnType<typeof vi.fn>;
    stop: ReturnType<typeof vi.fn>;
    dispose: ReturnType<typeof vi.fn>;
};

function priv(): Record<string, unknown> {
    return audio as unknown as Record<string, unknown>;
}

function virgin() {
    const p = priv();
    p.started = false;
    p.musicOn = false;
    p.musicLoop = null;
    p.step = 0;
    p.buzzSynth = null;
    p.lead = undefined;
    p.bass = undefined;
    p.noise = undefined;
    p.musicGain = undefined;
    p.sfxGain = undefined;
}

function lead(): VoiceFake {
    return priv().lead as VoiceFake;
}
function bass(): VoiceFake {
    return priv().bass as VoiceFake;
}
function noise(): VoiceFake {
    return priv().noise as VoiceFake;
}
function loop(): LoopFake {
    return priv().musicLoop as LoopFake;
}

beforeEach(() => {
    vi.clearAllMocks();
    virgin();
});

afterEach(() => {
    vi.restoreAllMocks();
});

describe("AudioEngine init", () => {
    test("init configures tempo and builds the synth chain", () => {
        audio.init();
        expect(Tone.getTransport().bpm.value).toBe(132);
        expect(priv().started).toBe(true);
        expect(lead().triggerAttackRelease).toBeDefined();
        expect(bass().triggerAttackRelease).toBeDefined();
        expect(noise().triggerAttackRelease).toBeDefined();
        expect(priv().musicOn).toBe(false);
    });

    test("init runs only once", () => {
        audio.init();
        const first = priv().lead;
        audio.init();
        expect(priv().lead).toBe(first);
    });
});

describe("AudioEngine play", () => {
    test("play lazily inits a fresh engine", () => {
        audio.play("click");
        expect(priv().started).toBe(true);
        expect(noise().triggerAttackRelease).toHaveBeenCalledTimes(1);
    });

    test("correct plays a four-note rising arpeggio", () => {
        audio.init();
        audio.play("correct");
        expect(lead().triggerAttackRelease).toHaveBeenCalledTimes(4);
    });

    test("wrong buzzes and reuses the buzz synth", () => {
        audio.init();
        audio.play("wrong");
        const first = priv().buzzSynth;
        audio.play("wrong");
        expect(priv().buzzSynth).toBe(first);
        expect((first as VoiceFake).triggerAttackRelease).toHaveBeenCalledTimes(2);
    });

    test("timer ticks the lead once", () => {
        audio.init();
        audio.play("timer");
        expect(lead().triggerAttackRelease).toHaveBeenCalledTimes(1);
        expect(bass().triggerAttackRelease).not.toHaveBeenCalled();
    });

    test("join pings the lead once", () => {
        audio.init();
        audio.play("join");
        expect(lead().triggerAttackRelease).toHaveBeenCalledTimes(1);
    });

    test("leave pings the lead once", () => {
        audio.init();
        audio.play("leave");
        expect(lead().triggerAttackRelease).toHaveBeenCalledTimes(1);
    });

    test("victory plays a six-note fanfare", () => {
        audio.init();
        audio.play("victory");
        expect(lead().triggerAttackRelease).toHaveBeenCalledTimes(6);
    });

    test("combo plays a three-note burst", () => {
        audio.init();
        audio.play("combo");
        expect(lead().triggerAttackRelease).toHaveBeenCalledTimes(3);
    });

    test("click fires the noise synth", () => {
        audio.init();
        audio.play("click");
        expect(noise().triggerAttackRelease).toHaveBeenCalledTimes(1);
        expect(lead().triggerAttackRelease).not.toHaveBeenCalled();
    });

    test("start plays a four-note intro", () => {
        audio.init();
        audio.play("start");
        expect(lead().triggerAttackRelease).toHaveBeenCalledTimes(4);
    });
});

describe("AudioEngine music", () => {
    test("startMusic inits a fresh engine and starts the loop", () => {
        audio.startMusic();
        expect(priv().started).toBe(true);
        expect(priv().musicOn).toBe(true);
        expect(loop().start).toHaveBeenCalledWith(0);
        expect(Tone.getTransport().start).toHaveBeenCalled();
    });

    test("startMusic is idempotent while playing", () => {
        audio.startMusic();
        const first = priv().musicLoop;
        audio.startMusic();
        expect(priv().musicLoop).toBe(first);
    });

    test("loop callback drives lead every step, bass on evens, hats on quarters", () => {
        audio.startMusic();
        const cb = loop().cb;
        for (let i = 0; i < 5; i++) cb(i);
        expect(lead().triggerAttackRelease).toHaveBeenCalledTimes(5);
        expect(bass().triggerAttackRelease).toHaveBeenCalledTimes(3);
        expect(noise().triggerAttackRelease).toHaveBeenCalledTimes(2);
        expect(priv().step).toBe(5);
    });

    test("stopMusic disposes the loop and allows a restart", () => {
        audio.startMusic();
        const first = priv().musicLoop;
        audio.stopMusic();
        expect((first as LoopFake).stop).toHaveBeenCalled();
        expect((first as LoopFake).dispose).toHaveBeenCalled();
        expect(priv().musicLoop).toBeNull();
        expect(priv().musicOn).toBe(false);
        audio.startMusic();
        expect(priv().musicLoop).not.toBe(first);
        expect(priv().step).toBe(0);
    });

    test("stopMusic without a loop is safe", () => {
        expect(() => audio.stopMusic()).not.toThrow();
        expect(priv().musicOn).toBe(false);
    });
});

describe("AudioEngine mute and resume", () => {
    test("setMuted drives the destination mute flag", () => {
        audio.setMuted(true);
        expect(audio.isMuted()).toBe(true);
        expect(Tone.getDestination().mute).toBe(true);
        audio.setMuted(false);
        expect(audio.isMuted()).toBe(false);
        expect(Tone.getDestination().mute).toBe(false);
    });

    test("resume starts the transport and tone", async () => {
        audio.init();
        await audio.resume();
        expect(Tone.getTransport().start).toHaveBeenCalled();
        expect(Tone.start).toHaveBeenCalled();
    });

    test("resume inits a fresh engine first", async () => {
        await audio.resume();
        expect(priv().started).toBe(true);
        expect(Tone.start).toHaveBeenCalled();
    });

    test("resume swallows tone start rejections", async () => {
        vi.mocked(Tone.start).mockRejectedValueOnce(new Error("autoplay blocked"));
        await expect(audio.resume()).resolves.toBeUndefined();
    });

    test("resume swallows transport start failures", async () => {
        const transport = Tone.getTransport() as unknown as { start: ReturnType<typeof vi.fn> };
        transport.start.mockImplementationOnce(() => {
            throw new Error("no audio");
        });
        await expect(audio.resume()).resolves.toBeUndefined();
    });
});

describe("AudioEngine cold-start SFX matrix", () => {
    test("correct plays from a cold engine without throwing", () => {
        expect(() => audio.play("correct")).not.toThrow();
        expect(priv().started).toBe(true);
        expect(lead().triggerAttackRelease).toHaveBeenCalledTimes(4);
    });

    test("wrong plays from a cold engine without throwing", () => {
        expect(() => audio.play("wrong")).not.toThrow();
        expect(priv().started).toBe(true);
        expect((priv().buzzSynth as VoiceFake).triggerAttackRelease).toHaveBeenCalledTimes(1);
    });

    test("timer plays from a cold engine without throwing", () => {
        expect(() => audio.play("timer")).not.toThrow();
        expect(lead().triggerAttackRelease).toHaveBeenCalledTimes(1);
    });

    test("join plays from a cold engine without throwing", () => {
        expect(() => audio.play("join")).not.toThrow();
        expect(lead().triggerAttackRelease).toHaveBeenCalledTimes(1);
    });

    test("leave plays from a cold engine without throwing", () => {
        expect(() => audio.play("leave")).not.toThrow();
        expect(lead().triggerAttackRelease).toHaveBeenCalledTimes(1);
    });

    test("victory plays from a cold engine without throwing", () => {
        expect(() => audio.play("victory")).not.toThrow();
        expect(lead().triggerAttackRelease).toHaveBeenCalledTimes(6);
    });

    test("combo plays from a cold engine without throwing", () => {
        expect(() => audio.play("combo")).not.toThrow();
        expect(lead().triggerAttackRelease).toHaveBeenCalledTimes(3);
    });

    test("click plays from a cold engine without throwing", () => {
        expect(() => audio.play("click")).not.toThrow();
        expect(noise().triggerAttackRelease).toHaveBeenCalledTimes(1);
    });

    test("start plays from a cold engine without throwing", () => {
        expect(() => audio.play("start")).not.toThrow();
        expect(lead().triggerAttackRelease).toHaveBeenCalledTimes(4);
    });
});

describe("AudioEngine SFX sequences", () => {
    const ALL = ["correct", "wrong", "timer", "join", "leave", "victory", "combo", "click", "start"] as const;

    test("every SfxName plays while muted without throwing", () => {
        audio.init();
        audio.setMuted(true);
        for (const name of ALL) expect(() => audio.play(name)).not.toThrow();
        expect(audio.isMuted()).toBe(true);
        audio.setMuted(false);
        expect(audio.isMuted()).toBe(false);
    });

    test("full fanfare sequence drives exact synth call totals", () => {
        audio.init();
        for (const name of ALL) audio.play(name);
        expect(lead().triggerAttackRelease).toHaveBeenCalledTimes(4 + 1 + 1 + 1 + 6 + 3 + 4);
        expect(noise().triggerAttackRelease).toHaveBeenCalledTimes(1);
        expect((priv().buzzSynth as VoiceFake).triggerAttackRelease).toHaveBeenCalledTimes(1);
    });

    test("mute play unmute play keeps the synths firing", () => {
        audio.init();
        audio.setMuted(true);
        audio.play("correct");
        expect(lead().triggerAttackRelease).toHaveBeenCalledTimes(4);
        audio.setMuted(false);
        audio.play("correct");
        expect(lead().triggerAttackRelease).toHaveBeenCalledTimes(8);
        expect(audio.isMuted()).toBe(false);
    });

    test("start stop start stop lifecycle resets the step", () => {
        audio.startMusic();
        loop().cb(0);
        loop().cb(0);
        expect(priv().step).toBe(2);
        audio.stopMusic();
        audio.stopMusic();
        expect(priv().musicLoop).toBeNull();
        audio.startMusic();
        expect(priv().step).toBe(0);
        audio.startMusic();
        audio.stopMusic();
        expect(priv().musicOn).toBe(false);
    });

    test("stop without start then play then stop is a safe chain", () => {
        audio.stopMusic();
        audio.play("victory");
        expect(lead().triggerAttackRelease).toHaveBeenCalledTimes(6);
        audio.stopMusic();
        expect(priv().musicOn).toBe(false);
        expect(priv().musicLoop).toBeNull();
    });

    test("resume rejection then play then resume recovers silently", async () => {
        vi.mocked(Tone.start).mockRejectedValueOnce(new Error("blocked"));
        await expect(audio.resume()).resolves.toBeUndefined();
        audio.play("click");
        expect(noise().triggerAttackRelease).toHaveBeenCalledTimes(1);
        await expect(audio.resume()).resolves.toBeUndefined();
        expect(Tone.start).toHaveBeenCalled();
    });

    test("buzz synth is shared across an interleaved wrong and correct chain", () => {
        audio.init();
        audio.play("wrong");
        audio.play("correct");
        audio.play("wrong");
        audio.play("wrong");
        expect((priv().buzzSynth as VoiceFake).triggerAttackRelease).toHaveBeenCalledTimes(3);
        expect(lead().triggerAttackRelease).toHaveBeenCalledTimes(4);
    });

    test("sixteen loop steps wrap the patterns with exact voice totals", () => {
        audio.startMusic();
        const cb = loop().cb;
        for (let i = 0; i < 16; i++) cb(i);
        expect(lead().triggerAttackRelease).toHaveBeenCalledTimes(16);
        expect(bass().triggerAttackRelease).toHaveBeenCalledTimes(8);
        expect(noise().triggerAttackRelease).toHaveBeenCalledTimes(4);
        expect(priv().step).toBe(16);
        audio.stopMusic();
    });

    test("play startMusic play stopMusic play lifecycle never throws", () => {
        audio.play("start");
        audio.startMusic();
        audio.play("combo");
        audio.stopMusic();
        audio.play("victory");
        expect(lead().triggerAttackRelease).toHaveBeenCalledTimes(4 + 3 + 6);
        expect(priv().musicOn).toBe(false);
    });

    test("rapid timer ticks queue eleven lead hits in a row", () => {
        audio.init();
        for (let i = 0; i < 11; i++) audio.play("timer");
        expect(lead().triggerAttackRelease).toHaveBeenCalledTimes(11);
        expect(bass().triggerAttackRelease).not.toHaveBeenCalled();
    });
});
