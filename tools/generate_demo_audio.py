#!/usr/bin/env python3
"""Generate the original Aether demo instrumentals with Python's standard library.

No samples, recordings, or existing compositions are used. Re-running this script
reproduces the PCM assets shipped in the app. Each track is 24 seconds long.
"""
import math
from pathlib import Path
import struct
import wave

SAMPLE_RATE = 16_000
DURATION = 24
OUTPUT = Path(__file__).resolve().parents[1] / "app/src/main/assets/media"
TRACKS = {
    "mountain_drive": ([130.8128, 174.6141, 220.0, 195.9977], [0, 7, 12, 4, 9, 7, 4, 2]),
    "coastal_road": ([146.8324, 195.9977, 164.8138, 220.0], [7, 12, 14, 9, 7, 4, 2, 4]),
    "evening_lights": ([110.0, 130.8128, 174.6141, 164.8138], [12, 7, 3, 10, 7, 5, 3, 0]),
    "ocean_air": ([123.4708, 164.8138, 184.9972, 146.8324], [0, 4, 11, 7, 4, 2, 9, 7]),
    "midnight_city": ([103.8262, 138.5913, 155.5635, 116.5409], [0, 3, 7, 10, 12, 10, 7, 3]),
    "golden_hour": ([164.8138, 220.0, 195.9977, 146.8324], [4, 7, 12, 16, 14, 12, 9, 7]),
}


# The original three retain their exact synthesis parameters and PCM bytes.
STYLES = {
    "ocean_air": dict(beat_seconds=.75, pad_level=.24, bell_level=.12, bass_level=.08),
    "midnight_city": dict(beat_seconds=.375, pad_level=.16, bell_level=.16, bass_level=.20),
    "golden_hour": dict(beat_seconds=.6, pad_level=.15, bell_level=.23, bass_level=.10),
}


def synthesize(roots, notes, beat_seconds=.5, pad_level=.19, bell_level=.19, bass_level=.15):
    samples = bytearray()
    for sample_index in range(SAMPLE_RATE * DURATION):
        seconds = sample_index / SAMPLE_RATE
        chord = int(seconds / 3) % len(roots)
        root = roots[chord]
        chord_time = seconds % 3
        beat_time = seconds % beat_seconds
        melody = root * 2 * (2 ** (notes[int(seconds / beat_seconds) % len(notes)] / 12))
        # Soft major/minor pad, plucked bell line, and rounded bass pulses.
        third = 2 ** ((3 if chord % 2 == 0 else 4) / 12)
        pad = sum(math.sin(2 * math.pi * root * ratio * seconds) for ratio in (1, third, 1.5)) / 3
        pad *= pad_level * min(1, chord_time * 3, (3 - chord_time) * 3)
        bell = (math.sin(2 * math.pi * melody * seconds)
                + .18 * math.sin(2 * math.pi * melody * 2 * seconds))
        bell *= bell_level * math.exp(-beat_time * 9)
        bass = bass_level * math.sin(2 * math.pi * root / 2 * seconds) * math.exp(-beat_time * 4)
        fade = min(1, seconds / .7, (DURATION - seconds) / 1.2)
        amplitude = max(-1, min(1, (pad + bell + bass) * fade))
        samples.extend(struct.pack("<h", int(amplitude * 32767)))
    return samples


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for name, (roots, notes) in TRACKS.items():
        with wave.open(str(OUTPUT / f"{name}.wav"), "wb") as output:
            output.setnchannels(1)
            output.setsampwidth(2)
            output.setframerate(SAMPLE_RATE)
            output.writeframes(synthesize(roots, notes, **STYLES.get(name, {})))


if __name__ == "__main__":
    main()
