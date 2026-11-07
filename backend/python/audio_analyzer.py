#!/usr/bin/env python3
"""
Audio Analysis Worker (Python + Librosa)
Analyzes user-provided audio files for:
- BPM / Tempo detection
- Tonality / Key detection (Chroma feature correlation)
- Potential structure boundary change points (milliseconds)
- Dynamic and spectral features (RMS energy, dynamic range)
Note: Does not guess Chorus/Verse without semantic lyrics; provides acoustic features & potential segment boundaries.
"""

import sys
import os
import json
import argparse
import numpy as np

# Major and Minor key profile templates (Krumhansl-Schmuckler)
MAJOR_PROFILE = np.array([6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88])
MINOR_PROFILE = np.array([6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17])
PITCH_NAMES = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B']

def detect_key_from_chroma(chroma_mean):
    """Correlate 12-dimensional mean chroma vector with Krumhansl key profiles."""
    best_corr = -float('inf')
    best_key = "Unknown"
    
    # Normalize chroma
    chroma_norm = chroma_mean - np.mean(chroma_mean)
    c_std = np.std(chroma_norm)
    if c_std == 0:
        return "Unknown"
    chroma_norm = chroma_norm / c_std

    for i in range(12):
        # Major check
        major_rot = np.roll(MAJOR_PROFILE, i)
        major_rot_norm = (major_rot - np.mean(major_rot)) / np.std(major_rot)
        corr_maj = np.dot(chroma_norm, major_rot_norm)
        if corr_maj > best_corr:
            best_corr = corr_maj
            best_key = f"{PITCH_NAMES[i]} Major"

        # Minor check
        minor_rot = np.roll(MINOR_PROFILE, i)
        minor_rot_norm = (minor_rot - np.mean(minor_rot)) / np.std(minor_rot)
        corr_min = np.dot(chroma_norm, minor_rot_norm)
        if corr_min > best_corr:
            best_corr = corr_min
            best_key = f"{PITCH_NAMES[i]} Minor"

    return best_key

def analyze_audio(file_path):
    if not os.path.exists(file_path):
        raise FileNotFoundError(f"Audio file not found: {file_path}")

    import librosa

    # Load audio (mono, 22050 Hz standard rate)
    y, sr = librosa.load(file_path, sr=22050, mono=True)
    duration_sec = librosa.get_duration(y=y, sr=sr)
    duration_ms = int(round(duration_sec * 1000))

    # 1. BPM / Tempo Estimation
    tempo, beats = librosa.beat.beat_track(y=y, sr=sr)
    if hasattr(tempo, '__len__'):
        tempo = float(tempo[0])
    else:
        tempo = float(tempo)
    bpm = round(tempo, 1)

    # 2. Key / Tonality Detection
    chroma = librosa.feature.chroma_cqt(y=y, sr=sr)
    chroma_mean = np.mean(chroma, axis=1)
    detected_key = detect_key_from_chroma(chroma_mean)

    # 3. Boundary Points Detection (Onsets & Spectral Novelty)
    # Subdivide track into potential structural change boundaries
    onset_env = librosa.onset.onset_strength(y=y, sr=sr)
    # Segment boundaries using structural agglomeration
    k_segments = max(4, min(14, int(duration_sec // 20)))
    bounds = librosa.segment.agglomerative(chroma, k=k_segments)
    bound_times = librosa.frames_to_time(bounds, sr=sr)
    
    # Clean up and ensure boundary list starts at 0ms and ends near end
    boundary_ms = [0]
    for bt in bound_times:
        ms = int(round(bt * 1000))
        if ms > 5000 and (duration_ms - ms) > 5000:
            if not boundary_ms or (ms - boundary_ms[-1]) >= 8000:
                boundary_ms.append(ms)

    # 4. Energy & Spectral Features
    rms = librosa.feature.rms(y=y)[0]
    mean_energy = float(np.mean(rms))
    max_energy = float(np.max(rms)) if len(rms) > 0 else 0.0
    dynamic_range_db = float(20 * np.log10((max_energy + 1e-6) / (mean_energy + 1e-6)))

    # Coarse energy curve (10-20 points for visualization)
    num_bins = 16
    chunk_size = len(rms) // num_bins
    energy_curve = []
    if chunk_size > 0:
        for i in range(num_bins):
            chunk = rms[i*chunk_size : (i+1)*chunk_size]
            val = float(np.mean(chunk)) if len(chunk) > 0 else 0.0
            energy_curve.append(round(val * 100, 1))

    return {
        "status": "SUCCESS",
        "bpm": bpm,
        "detectedKey": detected_key,
        "timeSignature": "4/4",
        "durationMs": duration_ms,
        "boundaryPointsMs": boundary_ms,
        "spectralFeatures": {
            "meanEnergy": round(mean_energy, 4),
            "dynamicRangeDb": round(dynamic_range_db, 1),
            "energyCurve": energy_curve
        },
        "analysisEngine": "Librosa-1.0.0"
    }

def main():
    parser = argparse.ArgumentParser(description="Librosa Audio Analyzer Worker")
    parser.add_argument("audio_path", help="Path to audio file (mp3, wav, etc.)")
    parser.add_argument("--output", "-o", help="Optional output JSON path")
    args = parser.parse_args()

    try:
        result = analyze_audio(args.audio_path)
    except Exception as e:
        result = {
            "status": "ERROR",
            "message": str(e),
            "bpm": 120.0,
            "detectedKey": "Unknown",
            "timeSignature": "4/4",
            "durationMs": 0,
            "boundaryPointsMs": [0],
            "spectralFeatures": {},
            "analysisEngine": "Fallback"
        }

    json_str = json.dumps(result, indent=2, ensure_ascii=False)
    if args.output:
        with open(args.output, "w", encoding="utf-8") as f:
            f.write(json_str)
    print(json_str)

if __name__ == "__main__":
    main()
