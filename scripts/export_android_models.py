#!/usr/bin/env python3
"""Export ConsTrakr Core ML models to TFLite for Android.

Requires: pip install coremltools tensorflow

Usage:
  python3 scripts/export_android_models.py

Outputs:
  app/src/main/assets/models/adaface_ir18.tflite
  app/src/main/assets/models/minifasnetv2.tflite
"""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
IOS_MODELS = ROOT.parent / "ConsTrakr" / "ConsTrakr" / "FaceRecognition" / "Models"
OUT_DIR = ROOT / "app" / "src" / "main" / "assets" / "models"


def export_coreml_to_tflite(name: str, out_name: str) -> None:
    import coremltools as ct

    src = IOS_MODELS / f"{name}.mlpackage"
    if not src.exists():
        print(f"SKIP {name}: not found at {src}")
        return
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    dest = OUT_DIR / out_name
    print(f"Loading {src} …")
    model = ct.models.MLModel(str(src))
    try:
        tflite = ct.convert(model, convert_to="tflite", source="milinternal")
        tflite.save(str(dest))
        print(f"Wrote {dest} ({dest.stat().st_size // 1024} KB)")
    except Exception as exc:
        print(f"FAIL {name}: {exc}")
        print("Try manual export via Xcode or onnxruntime conversion.")


def main() -> int:
    try:
        import coremltools  # noqa: F401
    except ImportError:
        print("Install: pip install coremltools tensorflow")
        return 1
    export_coreml_to_tflite("AdaFace_IR18", "adaface_ir18.tflite")
    export_coreml_to_tflite("MiniFASNetV2", "minifasnetv2.tflite")
    return 0


if __name__ == "__main__":
    sys.exit(main())
