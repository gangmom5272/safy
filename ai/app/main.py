import asyncio
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.analysis import router as analysis_router
from app.api.privacy import router as privacy_router
from app.services import p2pnet_assets


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
def health_check() -> dict[str, str]:
    return {"status": "ok"}
