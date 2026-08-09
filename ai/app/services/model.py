from functools import lru_cache
from pathlib import Path

from ultralytics import YOLO


MODEL_NAME = "YOLO"
MODEL_VERSION = "yolo11n"
MODEL_WEIGHT_ID = "yolo11n.pt"

MODEL_PATH = Path(__file__).resolve().parents[2] / "weights" / MODEL_WEIGHT_ID


@lru_cache(maxsize=1)
def get_model() -> YOLO:
    if not MODEL_PATH.exists():
        raise FileNotFoundError(f"YOLO model not found: {MODEL_PATH}")

    return YOLO(str(MODEL_PATH))
