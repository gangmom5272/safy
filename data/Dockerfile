FROM python:3.11-slim
WORKDIR /app

# requirements를 먼저 복사해 앱 코드만 바뀔 때 pip 레이어를 재사용한다.
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

RUN useradd --create-home safy
USER safy

# pydantic-settings는 .env 파일보다 환경변수를 우선 읽고 .env 부재는 오류가 아니다.
# 그래서 이미지에 .env를 넣지 않고 compose의 environment로만 주입한다.
EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
