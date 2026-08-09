import asyncio
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.analysis import router as analysis_router
from app.api.privacy import router as privacy_router
from app.logging_config import configure_logging
from app.services import p2pnet_assets


# lifespan에서 자산 준비 로그가 발생하므로 앱 생성 전에 설정해야 한다.
configure_logging()


@asynccontextmanager
async def lifespan(_: FastAPI):
    await asyncio.to_thread(
        p2pnet_assets.initialize_p2pnet_shadow_assets,
    )
    yield


app = FastAPI(
    title="SAFY AI API",
    version="0.1.0",
    lifespan=lifespan,
)

app.include_router(analysis_router)
app.include_router(privacy_router)


@app.get("/health")
def health_check() -> dict:
    """서비스 생존 여부와 P2PNet 그림자 자산 상태를 함께 보고한다.

    `status`는 P2PNet 상태와 무관하게 항상 `ok`다. P2PNet은 보조 모델이므로
    준비에 실패해도 YOLO 공식 분석은 정상 동작하며, 이때 컨테이너를
    unhealthy로 표시하면 재시작 루프에 빠진다.

    `p2pnetShadow`는 진단용 정보다. 자격증명이나 다운로드 URL은 포함하지
    않는다.
    """
    preparation = p2pnet_assets.get_last_preparation()

    return {
        "status": "ok",
        "p2pnetShadow": {
            "ready": (preparation.ready if preparation is not None else False),
            "status": (
                preparation.status if preparation is not None else "NOT_INITIALIZED"
            ),
            "modelVersion": (
                preparation.model_version if preparation is not None else None
            ),
        },
    }
