from fastapi import FastAPI

from app.api.routes.collections import router as collections_router


app = FastAPI(
    title="SAFY Data API",
    version="0.1.0",
)

app.include_router(collections_router)


@app.get("/health")
def health_check() -> dict[str, str]:
    return {
        "status": "ok",
        "service": "safy-data",
    }
