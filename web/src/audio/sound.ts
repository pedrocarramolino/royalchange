import { useSettings } from '@/data/settings';

/** Efectos de sonido, sintetizados (sin archivos de audio que descargar). */
export type Sound = 'chip' | 'card' | 'spin' | 'reelStop' | 'dice' | 'win' | 'bigWin' | 'lose';

const SAMPLE_RATE = 44_100;

class Mixer {
  samples = new Float32Array(0);

  private ensure(length: number) {
    if (length > this.samples.length) {
      const next = new Float32Array(length);
      next.set(this.samples);
      this.samples = next;
    }
  }

  private envelope(t: number, attack: number, decay: number) {
    return Math.min(1, t / Math.max(attack, 1e-6)) * Math.exp(-t * decay);
  }

  tone(frequency: number, start: number, length: number, decay: number, gain: number, attack = 0.002) {
    const from = Math.floor(start * SAMPLE_RATE);
    const count = Math.floor(length * SAMPLE_RATE);
    this.ensure(from + count);
    for (let i = 0; i < count; i++) {
      const t = i / SAMPLE_RATE;
      this.samples[from + i]! += Math.sin(2 * Math.PI * frequency * t) * this.envelope(t, attack, decay) * gain;
    }
  }

  bell(frequency: number, start: number, length: number, gain: number, decay = 7) {
    this.tone(frequency, start, length, decay, gain);
    this.tone(frequency * 2, start, length * 0.7, decay * 1.6, gain * 0.35);
    this.tone(frequency * 3, start, length * 0.4, decay * 2.5, gain * 0.15);
  }

  noise(start: number, length: number, decay: number, gain: number, seed: number, attack = 0.001, lowPass = 1) {
    const from = Math.floor(start * SAMPLE_RATE);
    const count = Math.floor(length * SAMPLE_RATE);
    this.ensure(from + count);
    let state = (seed * 7919 + 12_345) & 0x7fffffff;
    let smoothed = 0;
    for (let i = 0; i < count; i++) {
      state = (Math.imul(state, 1_103_515_245) + 12_345) & 0x7fffffff;
      const white = (state / 0x7fffffff) * 2 - 1;
      // Paso bajo de un polo: más grave cuanto menor es lowPass.
      smoothed += (white - smoothed) * lowPass;
      const t = i / SAMPLE_RATE;
      this.samples[from + i]! += smoothed * this.envelope(t, attack, decay) * gain;
    }
  }

  render(volume: number): Float32Array {
    let peak = 0;
    for (const s of this.samples) peak = Math.max(peak, Math.abs(s));
    if (peak === 0) return this.samples;
    const scale = volume / peak;
    // Fundido de 5 ms al final para que no haya chasquido al cortar.
    const fade = Math.min(this.samples.length, SAMPLE_RATE / 200);
    const out = new Float32Array(this.samples.length);
    for (let i = 0; i < out.length; i++) {
      const tail = out.length - i;
      out[i] = this.samples[i]! * scale * (tail < fade ? tail / fade : 1);
    }
    return out;
  }
}

function synth(sound: Sound): Float32Array {
  const m = new Mixer();
  switch (sound) {
    case 'chip':
      m.tone(2_400, 0, 0.05, 70, 0.5);
      m.tone(3_300, 0.012, 0.05, 80, 0.35);
      m.noise(0, 0.02, 200, 0.25, 1);
      return m.render(0.45);
    case 'card':
      m.noise(0, 0.11, 35, 0.9, 2, 0.012, 0.35);
      m.tone(180, 0.05, 0.05, 60, 0.15);
      return m.render(0.35);
    case 'spin': {
      // Bola que traquetea y se va frenando: clics cada vez más espaciados.
      let time = 0;
      let gap = 0.035;
      let index = 0;
      while (time < 3.6) {
        m.tone(1_800 + (index % 3) * 150, time, 0.025, 160, 0.4 * Math.max(0.25, 1 - time / 5));
        m.noise(time, 0.012, 300, 0.25, 10 + index);
        time += gap;
        gap *= 1.06;
        index++;
      }
      return m.render(0.3);
    }
    case 'reelStop':
      m.tone(120, 0, 0.14, 30, 0.8);
      m.tone(240, 0, 0.08, 45, 0.3);
      m.noise(0, 0.02, 250, 0.4, 3);
      return m.render(0.45);
    case 'dice':
      [0, 0.07, 0.15, 0.21, 0.32, 0.38, 0.5, 0.62].forEach((time, index) => {
        m.noise(time, 0.03, 140, 0.6 - index * 0.05, 20 + index, 0.001, 0.5);
        m.tone(900 + ((index * 137) % 500), time, 0.03, 150, 0.2);
      });
      return m.render(0.4);
    case 'win':
      [523.25, 659.25, 783.99, 1_046.5].forEach((f, i) => m.bell(f, i * 0.085, 0.4, 0.55));
      return m.render(0.3);
    case 'bigWin':
      [523.25, 659.25, 783.99, 1_046.5, 783.99, 1_046.5, 1_318.5].forEach((f, i) => m.bell(f, i * 0.08, 0.45, 0.5));
      // Acorde final sostenido.
      [523.25, 659.25, 783.99, 1_046.5].forEach((f) => m.bell(f, 0.6, 1.1, 0.3, 3));
      return m.render(0.3);
    case 'lose':
      m.tone(392, 0, 0.18, 12, 0.4, 0.01);
      m.tone(329.6, 0.16, 0.28, 9, 0.4, 0.01);
      return m.render(0.25);
  }
}

let context: AudioContext | null = null;
const buffers = new Map<Sound, AudioBuffer>();

function audioContext(): AudioContext | null {
  if (context) return context;
  const Ctor = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
  if (!Ctor) return null;
  context = new Ctor();
  return context;
}

// Safari solo deja sonar tras un gesto del jugador: el contexto se crea y se reanuda en el primero.
const unlock = () => {
  const ctx = audioContext();
  if (ctx?.state === 'suspended') void ctx.resume();
};
window.addEventListener('pointerdown', unlock, { capture: true, passive: true });
window.addEventListener('keydown', unlock, { capture: true });

/** Reproduce un efecto si el sonido está activado en Ajustes. */
export function play(sound: Sound, delayMs = 0): void {
  if (!useSettings.getState().soundEnabled) return;
  const ctx = audioContext();
  if (!ctx) return;
  try {
    if (ctx.state === 'suspended') void ctx.resume();
    let buffer = buffers.get(sound);
    if (!buffer) {
      const samples = synth(sound);
      buffer = ctx.createBuffer(1, samples.length, SAMPLE_RATE);
      buffer.copyToChannel(samples as Float32Array<ArrayBuffer>, 0);
      buffers.set(sound, buffer);
    }
    const source = ctx.createBufferSource();
    source.buffer = buffer;
    source.connect(ctx.destination);
    source.start(ctx.currentTime + delayMs / 1000);
  } catch {
    // Sin Web Audio o bloqueado: no suena.
  }
}

/** Sonido del resultado de una ronda: premio grande, premio o pérdida. */
export function resultSound(net: number, big: boolean): Sound | null {
  if (net > 0) return big ? 'bigWin' : 'win';
  if (net < 0) return 'lose';
  return null;
}

/** Desde 10 veces la apuesta se celebra a lo grande. */
export const BIG_WIN_MULTIPLIER = 10;
