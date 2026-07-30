FROM debian:bookworm-slim AS p2pnet-source

ARG P2PNET_REPOSITORY=https://github.com/TencentYoutuResearch/CrowdCounting-P2PNet.git
ARG P2PNET_COMMIT=5c91a81ca062b1c7fd3db3ad1c55b1c21f0a7455

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates git \
    && rm -rf /var/lib/apt/lists/*

RUN git clone --filter=blob:none "${P2PNET_REPOSITORY}" /opt/p2pnet \
    && cd /opt/p2pnet \
    && git checkout --detach "${P2PNET_COMMIT}"

COPY deploy/p2pnet-compatibility.patch /tmp/p2pnet-compatibility.patch

RUN cd /opt/p2pnet \
    && git apply --check /tmp/p2pnet-compatibility.patch \
    && git apply /tmp/p2pnet-compatibility.patch \
    && git rev-parse HEAD > .safy-source-revision \
    && rm -rf .git


FROM python:3.12-slim AS runtime

ARG YOLO_WEIGHT_URL=https://github.com/ultralytics/assets/releases/download/v8.4.0/yolo11n.pt
ARG YOLO_WEIGHT_SHA256=0ebbc80d4a7680d14987a577cd21342b65ecfd94632bd9a8da63ae6417644ee1

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    YOLO_WEIGHT_URL=${YOLO_WEIGHT_URL} \
    YOLO_WEIGHT_SHA256=${YOLO_WEIGHT_SHA256} \
    P2PNET_SHADOW_ROOT=/opt/p2pnet \
    P2PNET_SHADOW_WEIGHT_PATH=/var/cache/safy-ai/models/p2pnet/epoch-003.pth \
    P2PNET_ASSET_MANIFEST_PATH=/app/deploy/p2pnet-assets.json

RUN apt-get update \
    && apt-get install -y --no-install-recommends libgl1 libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt ./requirements.txt

RUN python -m pip install --no-cache-dir -r requirements.txt

COPY --from=p2pnet-source /opt/p2pnet /opt/p2pnet
COPY app ./app
COPY deploy ./deploy

RUN mkdir -p /app/weights /var/cache/safy-ai/models/p2pnet \
    && python -c "import hashlib, os, urllib.request; path='/app/weights/yolo11n.pt'; urllib.request.urlretrieve(os.environ['YOLO_WEIGHT_URL'], path); actual=hashlib.sha256(open(path, 'rb').read()).hexdigest(); assert actual == os.environ['YOLO_WEIGHT_SHA256'], f'YOLO checksum mismatch: {actual}'" \
    && useradd --create-home --uid 10001 safy \
    && chown -R safy:safy /app /var/cache/safy-ai

USER safy

EXPOSE 8000

CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
