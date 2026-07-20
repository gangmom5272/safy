from functools import lru_cache
from pathlib import Path

from ultralytics import YOLO


MODEL_PATH = Path(__file__).resolve().parents[2] / "weights" / "yolo11n.pt"


@lru_cache(maxsize=1)
def get_model() -> YOLO:
    if not MODEL_PATH.exists():
        raise FileNotFoundError(f"YOLO model not found: {MODEL_PATH}")

    return YOLO(str(MODEL_PATH))