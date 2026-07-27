from app.services.model import (
    MODEL_NAME,
    MODEL_PATH,
    MODEL_VERSION,
    MODEL_WEIGHT_ID,
)


def test_model_metadata_matches_weight_path() -> None:
    assert MODEL_NAME == "YOLO"
    assert MODEL_VERSION == "yolo11n"
    assert MODEL_WEIGHT_ID == "yolo11n.pt"
    assert MODEL_PATH.name == MODEL_WEIGHT_ID
