#!/usr/bin/env python3
"""Generates ATR's train profile sounds.

Every file is synthesised here from sine partials, shaped noise and envelopes; no third-party recording is used, so the
whole set is dedicated to the public domain (CC0). Mono 44.1 kHz Vorbis.

Loops (motor, compressor, flange) are rendered with an extra tail and crossfaded back into their head, so the wrap is
seam-free. Motor and compressor loops are long and carry slow load wobble and a slight pitch drift, so a ride does not
repeat a short phrase every couple of seconds. The script ends by decoding every loop and checking the wrap step.

Run from the repository root:

    python3 tools/sounds/generate_profile_sounds.py          # generate, then check
    python3 tools/sounds/generate_profile_sounds.py --check  # check the files already in place

Requires numpy and ffmpeg. oggenc is used when present; otherwise the fallback uses ffmpeg's Vorbis encoder.
"""

import glob
import math
import os
import shutil
import subprocess
import sys
import tempfile

import numpy as np

SAMPLE_RATE = 44100
OUT_DIR = os.path.join("fabric", "src", "main", "resources", "assets", "aurelia", "sounds", "train_profiles")
MOTOR_LOOP_SECONDS = 7.0
COMPRESSOR_LOOP_SECONDS = 6.5
FLANGE_LOOP_SECONDS = 2.5
CROSSFADE_SECONDS = 0.09
SEAM_LIMIT = 1.2


def normalise(samples, peak=0.9, loop=False):
    samples = np.asarray(samples, dtype=np.float64)
    if not loop and len(samples) > 0:
        fade = min(len(samples), int(0.015 * SAMPLE_RATE))
        samples[-fade:] *= np.linspace(1.0, 0.0, fade)
    maximum = np.max(np.abs(samples))
    if maximum > 0:
        samples = samples / maximum * peak
    return samples


def encode_ogg(ogg_path, samples, peak=0.9, loop=False):
    samples = normalise(samples, peak, loop)
    directory = os.path.dirname(ogg_path)
    if directory:
        os.makedirs(directory, exist_ok=True)
    with tempfile.TemporaryDirectory() as temporary:
        wav_path = os.path.join(temporary, "sound.wav")
        import wave
        with wave.open(wav_path, "wb") as handle:
            handle.setnchannels(1)
            handle.setsampwidth(2)
            handle.setframerate(SAMPLE_RATE)
            handle.writeframes((np.clip(samples, -1, 1) * 32767).astype("<i2").tobytes())
        if shutil.which("oggenc"):
            subprocess.run(["oggenc", "-Q", "-q", "4", "-o", ogg_path, wav_path], check=True)
        else:
            subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", wav_path, "-ac", "1", "-c:a", "vorbis", "-strict", "experimental", "-q:a", "4", ogg_path], check=True)


def write_ogg(relative_path, samples, peak=0.9, loop=False):
    """Writes a one-shot. Loops go through write_loop, which searches for a click-free block boundary."""
    encode_ogg(os.path.join(OUT_DIR, relative_path), samples, peak, loop)


def sample_count(seconds):
    return int(round(seconds * SAMPLE_RATE))


def time_axis(seconds):
    return np.arange(sample_count(seconds)) / SAMPLE_RATE


def snap(frequency, loop_seconds):
    """The nearest frequency that completes a whole number of cycles in the loop, for a seamless wrap."""
    return max(1, round(frequency * loop_seconds)) / loop_seconds


def crossfade_loop(rendered, loop_seconds):
    """Blends the extra rendered tail into the loop head, so the last sample leads into the first."""
    loop_samples = sample_count(loop_seconds)
    fade_samples = sample_count(CROSSFADE_SECONDS)
    if len(rendered) < loop_samples + fade_samples:
        raise ValueError("rendered loop is too short for the crossfade")
    out = np.array(rendered[:loop_samples], dtype=np.float64)
    tail = rendered[loop_samples:loop_samples + fade_samples]
    # Complementary weights: correlated content (the loop is mostly periodic) keeps its amplitude through the fade.
    weights = np.linspace(0.0, 1.0, fade_samples)
    out[:fade_samples] = out[:fade_samples] * weights + tail * (1.0 - weights)
    return out


def rotate_to_zero(loop):
    """Starts the loop at a zero crossing, so the encoder's first/last block fades land on near-silence.

    Rotating a loop does not change its wrap: the sample before the new start is still the sample that followed the
    old last one, so the loop stays smooth. It only moves the block boundary, which Vorbis attenuates, to a quiet point.
    """
    magnitudes = np.abs(loop)
    score = magnitudes + np.roll(magnitudes, 1)
    index = int(np.argmin(score))
    return np.roll(loop, -index)


def periodic_noise(loop_seconds, low, high, count, seed, amplitude=1.0, render_seconds=None):
    rng = np.random.default_rng(seed)
    t = time_axis(render_seconds if render_seconds is not None else loop_seconds)
    out = np.zeros_like(t)
    frequencies = np.exp(rng.uniform(math.log(low), math.log(high), count))
    for frequency in frequencies:
        snapped = snap(frequency, loop_seconds)
        out += np.sin(2 * math.pi * snapped * t + rng.uniform(0, 2 * math.pi)) * rng.uniform(0.3, 1.0)
    return out / max(1e-9, np.max(np.abs(out))) * amplitude


def periodic_am(loop_seconds, hertz, depth, phase=0.0, render_seconds=None):
    t = time_axis(render_seconds if render_seconds is not None else loop_seconds)
    return 1.0 - depth + depth * (0.5 + 0.5 * np.sin(2 * math.pi * snap(hertz, loop_seconds) * t + phase))


def slow_wobble(loop_seconds, render_seconds, seed, depth):
    """A slow, periodic load wobble between 1 - depth and 1 + depth."""
    t = time_axis(render_seconds)
    rng = np.random.default_rng(seed)
    signal = np.zeros_like(t)
    for cycles in (1, 2, 4):
        frequency = snap(cycles / loop_seconds, loop_seconds)
        signal += np.sin(2 * math.pi * frequency * t + rng.uniform(0, 2 * math.pi))
    signal /= max(1e-9, np.max(np.abs(signal)))
    return 1.0 + depth * signal


def slow_warp(loop_seconds, render_seconds, seed, depth):
    """A periodic phase warp in seconds, giving a slight pitch drift that returns to its start at the loop wrap."""
    if depth <= 0:
        return np.zeros(sample_count(render_seconds))
    t = time_axis(render_seconds)
    rng = np.random.default_rng(seed)
    warp = np.zeros_like(t)
    for cycles in (1, 2, 3):
        frequency = snap(cycles / loop_seconds, loop_seconds)
        phase = rng.uniform(0, 2 * math.pi)
        # Integral of sin(2*pi*f*t + phase); exactly zero at t=0 and t=loop_seconds.
        warp += -(np.cos(2 * math.pi * frequency * t + phase) - math.cos(phase)) / (2 * math.pi * frequency)
    return depth * warp / 3


def motor_loop(loop_seconds, base, partials, extras, noise_band, noise_level, seed, wobble=0.0, diesel=False):
    """A long periodic motor or engine loop with load wobble and a slight pitch drift."""
    render_seconds = loop_seconds + CROSSFADE_SECONDS
    t = time_axis(render_seconds)
    phase_time = t + slow_warp(loop_seconds, render_seconds, seed + 7, 0.0009)
    load = slow_wobble(loop_seconds, render_seconds, seed + 11, 0.045)
    out = np.zeros_like(t)
    fundamental = snap(base, loop_seconds)

    if diesel:
        rng = np.random.default_rng(seed)
        cycle_count = max(1, int(round(loop_seconds * fundamental)))
        strengths = rng.uniform(0.65, 1.0, cycle_count)
        pulse_length = max(1, int(0.018 * SAMPLE_RATE))
        pulse_time = np.arange(pulse_length) / SAMPLE_RATE
        band = bandpass(rng.standard_normal(pulse_length), 80, 2200)
        band /= max(1e-9, np.max(np.abs(band)))
        exhaust = bandpass(rng.standard_normal(pulse_length), 35, 320)
        exhaust /= max(1e-9, np.max(np.abs(exhaust)))
        attack = np.minimum(1.0, pulse_time / 0.0015)
        for cycle in range(int(math.ceil(render_seconds * fundamental))):
            strength = strengths[cycle % cycle_count]
            pulse = (0.6 * np.sin(2 * math.pi * fundamental * 3 * pulse_time) + 0.45 * band + 0.4 * exhaust) * np.exp(-pulse_time / 0.006) * strength * attack
            start = int(cycle / fundamental * SAMPLE_RATE)
            if start >= len(out):
                break
            end = min(len(out), start + len(pulse))
            out[start:end] += pulse[:end - start] * 0.55

    for harmonic, amplitude in partials:
        frequency = snap(fundamental * harmonic, loop_seconds)
        out += amplitude * np.sin(2 * math.pi * frequency * phase_time + rng_phase(seed, harmonic))
        # A slightly detuned partner gives the slow beating a real gear train has.
        out += amplitude * 0.12 * np.sin(2 * math.pi * snap(frequency * 1.004, loop_seconds) * phase_time + rng_phase(seed, harmonic + 100))
    for frequency, amplitude, am in extras:
        tone = np.sin(2 * math.pi * snap(frequency, loop_seconds) * phase_time)
        out += amplitude * tone * periodic_am(loop_seconds, am, 0.35 if wobble == 0 else wobble, render_seconds=render_seconds)
    if noise_level > 0:
        out += periodic_noise(loop_seconds, noise_band[0], noise_band[1], 300, seed, noise_level, render_seconds)
    return out * load


def rng_phase(seed, index):
    return np.random.default_rng(seed * 1009 + index).uniform(0, 2 * math.pi)


def bell(frequency, seconds, ratios, decays, amplitudes, attack=0.004, seed=0):
    t = time_axis(seconds)
    out = np.zeros_like(t)
    for ratio, decay, amplitude in zip(ratios, decays, amplitudes):
        out += amplitude * np.sin(2 * math.pi * frequency * ratio * t) * np.exp(-t / decay)
    rng = np.random.default_rng(seed + int(frequency))
    strike = bandpass(rng.standard_normal(len(t)), 1800, 9000) * np.exp(-t / 0.006)
    out += 0.18 * strike / max(1e-9, np.max(np.abs(strike)))
    return out * np.minimum(1.0, t / attack)


def chime(notes, ratios, decays, amplitudes, gap):
    parts = []
    for index, frequency in enumerate(notes):
        parts.append(bell(frequency, 0.75, ratios, decays, amplitudes))
        if index < len(notes) - 1:
            parts.append(np.zeros(int(gap * SAMPLE_RATE)))
    return np.concatenate(parts)


def bandpass(samples, low, high):
    spectrum = np.fft.rfft(samples)
    frequencies = np.fft.rfftfreq(len(samples), 1.0 / SAMPLE_RATE)
    mask = np.zeros_like(frequencies)
    mask[(frequencies >= low) & (frequencies <= high)] = 1.0
    edge = max(1, int(150 / (SAMPLE_RATE / len(samples))))
    kernel = np.ones(edge * 2 + 1) / (edge * 2 + 1)
    mask = np.convolve(mask, kernel, mode="same")
    return np.fft.irfft(spectrum * mask, len(samples))


def noise_burst(seconds, low, high, attack, decay, seed, amplitude=1.0):
    t = time_axis(seconds)
    rng = np.random.default_rng(seed)
    noise = bandpass(rng.standard_normal(len(t)), low, high)
    envelope = (1.0 - np.exp(-t / attack)) * np.exp(-t / decay)
    return noise * envelope * amplitude


def thud(seconds, start, frequency, decay, amplitude=1.0):
    out = np.zeros(int(seconds * SAMPLE_RATE))
    begin = int(start * SAMPLE_RATE)
    t = time_axis(seconds - start)
    sweep = 1.0 + 0.6 * np.exp(-t / 0.04)
    tone = np.sin(2 * math.pi * frequency * sweep * t) * np.exp(-t / decay)
    out[begin:begin + len(tone)] += tone * amplitude
    return out


def click(seconds, start, seed, amplitude=0.5, low=1800, high=9000):
    out = np.zeros(int(seconds * SAMPLE_RATE))
    begin = int(start * SAMPLE_RATE)
    burst = noise_burst(0.035, low, high, 0.001, 0.008, seed)
    out[begin:begin + len(burst)] += burst * amplitude
    return out


def air(seconds, seed, low=700, high=7000, attack=0.015, decay=0.35, amplitude=1.0):
    return noise_burst(seconds, low, high, attack, decay, seed, amplitude)


def door_open(tone):
    seconds = 0.75
    out = click(seconds, 0.005, 11, 0.35)
    out += air(seconds, 12, 800, 5200, 0.012, tone["open_decay"], 0.7)
    out += thud(seconds, tone["open_thud_at"], tone["thud"], 0.09, 0.9)
    out += click(seconds, tone["open_thud_at"] + 0.01, 13, 0.25, 900, 5000)
    return out


def door_close(tone):
    seconds = 0.8
    out = click(seconds, 0.005, 21, 0.4)
    out += air(seconds, 22, 700, 4800, 0.01, tone["close_decay"], 0.65)
    out += thud(seconds, 0.02, tone["thud"], 0.07, 0.7)
    out += thud(seconds, tone["close_thud_at"], tone["thud"] * 0.85, 0.11, 1.0)
    out += click(seconds, tone["close_thud_at"] + 0.005, 23, 0.3, 800, 4500)
    return out


def brake_release(tone):
    seconds = 1.35
    out = click(seconds, 0.005, 31, 0.35, 1200, 7000)
    out += air(seconds, 32, tone["air_low"], tone["air_high"], 0.02, tone["air_decay"], 0.9)
    out += thud(seconds, 0.04, tone["thud"], 0.1, 0.7)
    out += click(seconds, 0.16, 33, 0.25, 900, 5000)
    if tone["double_clack"]:
        out += click(seconds, 0.28, 34, 0.2, 700, 4000)
    return out


def compressor_loop(tone, seed, loop_seconds):
    out = motor_loop(loop_seconds, tone["compressor_hz"], [
        (1, 0.55), (2, 0.35), (3, 0.2), (4, 0.12), (5, 0.07)
    ], [(tone["compressor_hz"] * 6.1, 0.05, 3.0)], (250, 1400), 0.035, seed)
    render_seconds = loop_seconds + CROSSFADE_SECONDS
    rng = np.random.default_rng(seed + 1)
    pulse_length = int(0.04 * SAMPLE_RATE)
    pulse_time = np.arange(pulse_length) / SAMPLE_RATE
    band = bandpass(rng.standard_normal(pulse_length), 200, 1800)
    band /= max(1e-9, np.max(np.abs(band)))
    pulse_frequency = max(1, round(5.0 * loop_seconds)) / loop_seconds
    pulse_count = max(1, int(round(loop_seconds * pulse_frequency)))
    strengths = rng.uniform(0.75, 1.0, pulse_count)
    attack = np.minimum(1.0, pulse_time / 0.002)
    for index in range(int(math.ceil(render_seconds * pulse_frequency))):
        strength = strengths[index % pulse_count]
        pulse = (band + 0.5 * np.sin(2 * math.pi * tone["compressor_hz"] * pulse_time)) * np.exp(-pulse_time / 0.012) * 0.16 * strength * attack
        start = int(index / pulse_frequency * SAMPLE_RATE)
        if start >= len(out):
            break
        end = min(len(out), start + len(pulse))
        out[start:end] += pulse[:end - start]
    return out


def compressor_attack(tone):
    seconds = 0.65
    out = click(seconds, 0.002, 41, 0.4)
    out += air(seconds, 42, 400, 3000, 0.01, 0.15, 0.4)
    t = time_axis(seconds)
    hum = np.sin(2 * math.pi * tone["compressor_hz"] * t) * np.minimum(1.0, t / 0.12) * np.exp(-np.maximum(0, t - 0.35) / 0.12)
    return out + 0.35 * hum


def compressor_release(tone):
    seconds = 0.55
    out = air(seconds, 51, 500, 4500, 0.008, 0.13, 0.55)
    out += click(seconds, 0.008, 52, 0.3)
    return out


def flange_loop(tone, seed, loop_seconds):
    render_seconds = loop_seconds + CROSSFADE_SECONDS
    t = time_axis(render_seconds)
    phase_time = t + slow_warp(loop_seconds, render_seconds, seed + 3, 0.0006)
    frequency = snap(tone["flange_hz"], loop_seconds)
    wobble = np.sin(2 * math.pi * snap(4.3, loop_seconds) * phase_time + 0.7) * tone["flange_wobble"]
    tone_signal = np.sin(2 * math.pi * frequency * phase_time + wobble) * (0.75 + 0.25 * np.sin(2 * math.pi * snap(3.1, loop_seconds) * phase_time))
    second = 0.35 * np.sin(2 * math.pi * snap(frequency * 1.5, loop_seconds) * phase_time + 1.1)
    noise = periodic_noise(loop_seconds, frequency * 0.8, frequency * 1.25, 120, seed, 0.25, render_seconds)
    return (tone_signal + second + noise) * slow_wobble(loop_seconds, render_seconds, seed + 5, 0.03)


TYPES = {
    "uk_emu": {
        "motor": dict(base=150, partials=[(1, 0.5), (2, 0.32), (3, 0.26), (4, 0.16), (5, 0.1)], extras=[(820, 0.2, 2.0), (3200, 0.07, 5.0)], noise_band=(200, 2600), noise_level=0.02, seed=101),
        "chime": ([659.25, 523.25], [1, 2.0, 2.75, 4.1], [0.55, 0.3, 0.18, 0.1], [1.0, 0.55, 0.3, 0.16], 0.3),
        "door": dict(open_decay=0.16, close_decay=0.2, open_thud_at=0.5, close_thud_at=0.6, thud=105, air_low=800, air_high=7000, air_decay=0.42, double_clack=False),
        "compressor_hz": 58,
        "flange_hz": 3200,
        "flange_wobble": 0.02,
    },
    "tube": {
        "motor": dict(base=95, partials=[(1, 0.6), (3, 0.3), (5, 0.2), (7, 0.11), (9, 0.05)], extras=[(560, 0.22, 3.0), (2500, 0.04, 4.0)], noise_band=(120, 2200), noise_level=0.06, seed=201),
        "chime": ([1046.5, 784.0, 659.25], [1, 2.4, 3.7, 5.1], [0.45, 0.25, 0.15, 0.09], [1.0, 0.5, 0.26, 0.14], 0.2),
        "door": dict(open_decay=0.2, close_decay=0.24, open_thud_at=0.52, close_thud_at=0.62, thud=88, air_low=600, air_high=6200, air_decay=0.5, double_clack=True),
        "compressor_hz": 48,
        "flange_hz": 3600,
        "flange_wobble": 0.025,
    },
    "dmu": {
        "motor": dict(base=45, partials=[(1, 0.45), (2, 0.3), (3, 0.2), (4, 0.12), (5, 0.08)], extras=[(90, 0.14, 7.0), (2800, 0.035, 2.0)], noise_band=(150, 3400), noise_level=0.09, seed=301, wobble=0.25, diesel=True),
        "chime": ([784.0, 659.25], [1, 2.0, 3.0], [0.5, 0.28, 0.15], [1.0, 0.45, 0.2], 0.3),
        "door": dict(open_decay=0.18, close_decay=0.22, open_thud_at=0.48, close_thud_at=0.58, thud=78, air_low=500, air_high=5000, air_decay=0.38, double_clack=False),
        "compressor_hz": 40,
        "flange_hz": 3000,
        "flange_wobble": 0.03,
    },
    "dutch_emu": {
        "motor": dict(base=170, partials=[(1, 0.38), (2, 0.36), (3, 0.22), (4, 0.1)], extras=[(1080, 0.18, 2.4), (4100, 0.045, 6.0)], noise_band=(250, 3000), noise_level=0.018, seed=401),
        "chime": ([740.0, 554.37], [1, 2.1, 3.1, 4.7], [0.6, 0.32, 0.2, 0.11], [1.0, 0.52, 0.28, 0.15], 0.3),
        "door": dict(open_decay=0.17, close_decay=0.21, open_thud_at=0.5, close_thud_at=0.6, thud=112, air_low=850, air_high=6800, air_decay=0.4, double_clack=False),
        "compressor_hz": 62,
        "flange_hz": 3400,
        "flange_wobble": 0.02,
    },
    "metro": {
        "motor": dict(base=130, partials=[(1, 0.42), (2, 0.32), (3, 0.22), (4, 0.12)], extras=[(2600, 0.08, 4.0), (3900, 0.05, 6.0), (1560, 0.06, 3.0)], noise_band=(200, 3000), noise_level=0.03, seed=501),
        "chime": ([523.25, 659.25, 783.99], [1, 2.0, 2.6, 3.4], [0.5, 0.26, 0.16, 0.09], [1.0, 0.5, 0.27, 0.13], 0.2),
        "door": dict(open_decay=0.15, close_decay=0.19, open_thud_at=0.46, close_thud_at=0.56, thud=120, air_low=900, air_high=7200, air_decay=0.36, double_clack=False),
        "compressor_hz": 54,
        "flange_hz": 3800,
        "flange_wobble": 0.02,
    },
    "tram": {
        "motor": dict(base=110, partials=[(1, 0.4), (2, 0.22), (3, 0.16), (4, 0.08)], extras=[(440, 0.26, 5.0), (1980, 0.05, 7.0)], noise_band=(90, 1800), noise_level=0.055, seed=601),
        "chime": ([1318.5, 1046.5], [1, 2.7, 4.1], [0.32, 0.18, 0.1], [1.0, 0.45, 0.2], 0.3),
        "door": dict(open_decay=0.14, close_decay=0.17, open_thud_at=0.44, close_thud_at=0.54, thud=140, air_low=1000, air_high=7500, air_decay=0.32, double_clack=False),
        "compressor_hz": 46,
        "flange_hz": 2600,
        "flange_wobble": 0.035,
    },
}


def write_loop(relative_path, rendered, loop_seconds, peak):
    """Crossfades a loop, then rotates it a few samples at a time until the encoded wrap has no click."""
    loop = rotate_to_zero(crossfade_loop(rendered, loop_seconds))
    target = os.path.join(OUT_DIR, relative_path)
    os.makedirs(os.path.dirname(target), exist_ok=True)
    with tempfile.TemporaryDirectory() as temporary:
        candidate = os.path.join(temporary, "candidate.ogg")
        best_path = os.path.join(temporary, "best.ogg")
        best_ratio = float("inf")
        chosen = 0
        for offset in range(0, 121):
            encode_ogg(candidate, np.roll(loop, -offset), peak, loop=True)
            ratio = seam_ratio(decode_ogg(candidate))
            if ratio < best_ratio:
                best_ratio = ratio
                chosen = offset
                shutil.copyfile(candidate, best_path)
            if ratio <= 0.8:
                best_ratio = ratio
                chosen = offset
                shutil.copyfile(candidate, best_path)
                break
        shutil.copyfile(best_path, target)
        print("  %-42s offset %3d, seam %.2fx" % (relative_path, chosen, best_ratio))


def seam_ratio(samples):
    return abs(float(samples[-1] - samples[0])) / max(1e-9, float(np.percentile(np.abs(np.diff(samples)), 99)))


def generate_profile(name, tone):
    motor = tone["motor"]
    write_loop(os.path.join(name, "motor.ogg"), motor_loop(MOTOR_LOOP_SECONDS, **motor), MOTOR_LOOP_SECONDS, 0.8)
    write_ogg(os.path.join(name, "chime.ogg"), chime(*tone["chime"]), 0.85)
    write_ogg(os.path.join(name, "door_open.ogg"), door_open(tone["door"]), 0.8)
    write_ogg(os.path.join(name, "door_close.ogg"), door_close(tone["door"]), 0.8)
    write_ogg(os.path.join(name, "brake_release.ogg"), brake_release(tone["door"]), 0.75)
    write_loop(os.path.join(name, "compressor.ogg"), compressor_loop(tone, 700 + len(name), COMPRESSOR_LOOP_SECONDS), COMPRESSOR_LOOP_SECONDS, 0.38)
    write_ogg(os.path.join(name, "compressor_attack.ogg"), compressor_attack(tone), 0.45)
    write_ogg(os.path.join(name, "compressor_release.ogg"), compressor_release(tone), 0.42)
    write_loop(os.path.join(name, "flange.ogg"), flange_loop(tone, 800 + len(name), FLANGE_LOOP_SECONDS), FLANGE_LOOP_SECONDS, 0.6)


def decode_ogg(path):
    result = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-f", "f32le", "-ac", "1", "-ar", str(SAMPLE_RATE), "-"], capture_output=True, check=True)
    return np.frombuffer(result.stdout, dtype="<f4")


def check_loops():
    """Decodes every loop and fails when the wrap step is out of line with the clip's own sample-to-sample steps."""
    rows = []
    failed = False
    for path in sorted(glob.glob(os.path.join(OUT_DIR, "**", "*.ogg"), recursive=True)):
        name = os.path.basename(path)
        if not any(key in name for key in ("motor", "compressor", "flange")):
            continue
        samples = decode_ogg(path)
        if len(samples) < 3:
            continue
        seam = abs(float(samples[-1] - samples[0]))
        percentile = float(np.percentile(np.abs(np.diff(samples)), 99))
        ratio = seam / max(1e-9, percentile)
        passed = ratio <= SEAM_LIMIT
        failed |= not passed
        rows.append((os.path.relpath(path, OUT_DIR), seam, percentile, ratio, passed))
    print("Loop seam check (wrap step / p99 sample step, limit %.1f)" % SEAM_LIMIT)
    print("%-38s %9s %9s %7s  %s" % ("file", "seam", "p99", "ratio", "result"))
    for relative_path, seam, percentile, ratio, passed in rows:
        print("%-38s %9.4f %9.4f %7.2f  %s" % (relative_path, seam, percentile, ratio, "OK" if passed else "FAIL"))
    if failed:
        raise SystemExit("one or more loops click at the wrap")
    print("%d files checked, all at or below %.1fx" % (len(rows), SEAM_LIMIT))


def main():
    if not shutil.which("ffmpeg") or not (shutil.which("oggenc") or shutil.which("ffmpeg")):
        raise SystemExit("ffmpeg and oggenc (or ffmpeg with the vorbis encoder) are required")
    if "--check" not in sys.argv:
        for name, tone in TYPES.items():
            generate_profile(name, tone)
            print("generated", name)
    check_loops()


if __name__ == "__main__":
    main()
