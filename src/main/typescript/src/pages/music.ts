// pages/music.ts — white noise generator using Web Audio API.

let audioCtx: AudioContext | null = null;
let gainNode: GainNode | null = null;
let source: AudioNode | null = null;
let playing = false;

const TRACK_PRESETS: Record<string, { label: string; setup: () => void }> = {
    rain:   { label: '雨声',   setup: () => { source = makeNoise(0.5); } },
    forest: { label: '森林',   setup: () => { source = makeForest(); } },
    ocean:  { label: '海浪',   setup: () => { source = makeOcean(); } },
    fire:   { label: '壁炉',   setup: () => { source = makeNoise(0.3); } },
    cafe:   { label: '咖啡馆', setup: () => { source = makeCafe(); } },
};

function makeNoise(vol: number): AudioNode {
    const c = audioCtx!;
    const bufferSize = 2 * c.sampleRate;
    const noiseBuffer = c.createBuffer(1, bufferSize, c.sampleRate);
    const output = noiseBuffer.getChannelData(0);
    for (let i = 0; i < bufferSize; i++) output[i] = Math.random() * 2 - 1;
    const src = c.createBufferSource();
    src.buffer = noiseBuffer;
    src.loop = true;
    const filter = c.createBiquadFilter();
    filter.type = 'lowpass';
    filter.frequency.value = 4000;
    src.connect(filter);
    filter.connect(c.destination);
    gainNode = c.createGain();
    gainNode.gain.value = vol;
    filter.connect(gainNode);
    gainNode.connect(c.destination);
    return src;
}

function makeForest(): AudioNode {
    const n = makeNoise(0.3);
    const filter = audioCtx!.createBiquadFilter();
    filter.type = 'bandpass';
    filter.frequency.value = 1500;
    n.connect(filter);
    return filter;
}

function makeOcean(): AudioNode {
    const n = makeNoise(0.4);
    const lfo = audioCtx!.createOscillator();
    const lfoGain = audioCtx!.createGain();
    lfo.frequency.value = 0.1;
    lfoGain.gain.value = 0.15;
    lfo.connect(lfoGain).connect((n as unknown as AudioParam));
    lfo.start();
    return n;
}

function makeCafe(): AudioNode {
    const n = makeNoise(0.25);
    const filter = audioCtx!.createBiquadFilter();
    filter.type = 'bandpass';
    filter.frequency.value = 800;
    n.connect(filter);
    return filter;
}

function stop(): void {
    if (source) try { (source as unknown as AudioScheduledSourceNode).stop(); } catch { /* */ }
    source = null;
    if (audioCtx) { try { audioCtx.close(); } catch { /* */ } }
    audioCtx = null;
    gainNode = null;
    playing = false;
}

function play(track: string): void {
    if (!TRACK_PRESETS[track]) return;
    stop();
    audioCtx = new AudioContext();
    TRACK_PRESETS[track].setup();
    playing = true;
}

document.addEventListener('DOMContentLoaded', () => {
    const playBtn = document.getElementById('play-btn');
    const select = document.getElementById('track-select') as HTMLSelectElement | null;
    const status = document.getElementById('track-status');
    const title = document.getElementById('track-title');

    function setStatus(): void {
        const t = select?.value ?? 'rain';
        if (title) title.textContent = TRACK_PRESETS[t]?.label ?? t;
        if (status) status.textContent = playing ? '▶ 播放中' : '点击 ▶ 播放';
    }

    playBtn?.addEventListener('click', () => {
        const t = select?.value ?? 'rain';
        if (!playing) { play(t); } else { stop(); }
        setStatus();
    });
    select?.addEventListener('change', () => {
        if (playing) play(select.value);
        setStatus();
    });
    setStatus();
});