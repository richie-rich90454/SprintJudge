import * as Tone from "tone";

export type SfxName =
    | "correct"
    | "wrong"
    | "timer"
    | "join"
    | "leave"
    | "victory"
    | "combo"
    | "click"
    | "start";

class AudioEngine {
    private started = false;
    private muted = false;
    private musicOn = false;
    private musicLoop: Tone.Loop | null = null;
    private step = 0;

    private lead!: Tone.PolySynth;
    private bass!: Tone.MonoSynth;
    private noise!: Tone.NoiseSynth;
    private buzzSynth: Tone.Synth | null = null;
    private musicGain!: Tone.Gain;
    private sfxGain!: Tone.Gain;

    private readonly leadPattern = [
        60,
        64,
        67,
        64, // C E G E
        62,
        65,
        69,
        65, // D F A F
        64,
        67,
        71,
        67, // E G B G
        59,
        62,
        67,
        62, // B D G D
    ];
    private readonly bassPattern = [36, 36, 43, 43, 41, 41, 38, 38];

    init() {
        if (this.started) return;
        this.started = true;
        Tone.getTransport().bpm.value = 132;

        this.musicGain = new Tone.Gain(0.18).toDestination();
        this.sfxGain = new Tone.Gain(0.5).toDestination();

        this.lead = new Tone.PolySynth(Tone.Synth, {
            oscillator: { type: "square" },
            envelope: { attack: 0.01, decay: 0.12, sustain: 0.2, release: 0.15 },
        }).connect(this.mfx());
        this.lead.connect(this.musicGain);

        this.bass = new Tone.MonoSynth({
            oscillator: { type: "triangle" },
            envelope: { attack: 0.02, decay: 0.2, sustain: 0.3, release: 0.2 },
        }).connect(this.musicGain);

        this.noise = new Tone.NoiseSynth({
            noise: { type: "white" },
            envelope: { attack: 0.001, decay: 0.08, sustain: 0 },
        }).connect(this.sfxGain);
    }

    // Light chiptune tone-shaping chain.
    private mfx(): Tone.BitCrusher {
        const filter = new Tone.Filter(2600, "lowpass").toDestination();
        const bit = new Tone.BitCrusher(6).connect(filter);
        return bit;
    }

    async resume() {
        if (!this.started) this.init();
        await Tone.getTransport().start();
        await Tone.start();
    }

    setMuted(m: boolean) {
        this.muted = m;
        Tone.getDestination().mute = m;
    }
    isMuted() {
        return this.muted;
    }

    startMusic() {
        if (!this.started) this.init();
        if (this.musicOn) return;
        this.musicOn = true;
        this.step = 0;
        this.musicLoop = new Tone.Loop((time) => {
            const i = this.step % this.leadPattern.length;
            const bi = this.step % this.bassPattern.length;
            this.lead.triggerAttackRelease(
                Tone.Frequency(this.leadPattern[i], "midi").toNote(),
                "16n",
                time,
                0.5,
            );
            if (this.step % 2 === 0) {
                this.bass.triggerAttackRelease(
                    Tone.Frequency(this.bassPattern[bi], "midi").toNote(),
                    "8n",
                    time,
                    0.7,
                );
            }
            if (this.step % 4 === 0) {
                this.noise.triggerAttackRelease("32n", time, 0.2);
            }
            this.step++;
        }, "8n");
        Tone.getTransport().start();
        this.musicLoop.start(0);
    }

    stopMusic() {
        if (this.musicLoop) {
            this.musicLoop.stop();
            this.musicLoop.dispose();
            this.musicLoop = null;
        }
        this.musicOn = false;
    }

    play(name: SfxName) {
        if (!this.started) this.init();
        switch (name) {
            case "correct":
                this.arpeggio([72, 76, 79, 84], 0.06);
                break;
            case "wrong":
                this.buzz();
                break;
            case "timer":
                this.lead.triggerAttackRelease("C6", "32n", undefined, 0.3);
                break;
            case "join":
                this.lead.triggerAttackRelease("G5", "16n", undefined, 0.3);
                break;
            case "leave":
                this.lead.triggerAttackRelease("E5", "16n", undefined, 0.3);
                break;
            case "victory":
                this.arpeggio([72, 76, 79, 84, 88, 91], 0.09);
                break;
            case "combo":
                this.arpeggio([76, 79, 83], 0.05);
                break;
            case "click":
                this.noise.triggerAttackRelease("64n", undefined, 0.15);
                break;
            case "start":
                this.arpeggio([60, 64, 67, 72], 0.07);
                break;
        }
    }

    private arpeggio(notes: number[], dur = 0.06) {
        notes.forEach((n, i) => {
            const t = Tone.now() + i * dur;
            this.lead.triggerAttackRelease(Tone.Frequency(n, "midi").toNote(), "16n", t, 0.6);
        });
    }

    private buzz() {
        if (!this.buzzSynth) {
            this.buzzSynth = new Tone.Synth({
                oscillator: { type: "sawtooth" },
                envelope: { attack: 0.01, decay: 0.2, sustain: 0, release: 0.1 },
            }).connect(this.sfxGain);
        }
        this.buzzSynth.triggerAttackRelease("C3", "8n", undefined, 0.4);
    }
}

export const audio = new AudioEngine();
