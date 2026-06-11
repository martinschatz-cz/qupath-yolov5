# Dockerfile
FROM python:3.10 
#-slim

# Install system dependencies needed for OpenCV and git for torch hub
RUN apt-get update && apt-get install -y git libgl1 libglib2.0-0 && rm -rf /var/lib/apt/lists/*

WORKDIR /app
ENV YOLO_CONFIG_DIR=/tmp/Ultralytics

RUN python -m pip install --upgrade pip setuptools wheel && \
    pip install --no-cache-dir fastapi uvicorn opencv-python pillow python-multipart && \
    pip install --no-cache-dir torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cpu && \
    pip install --no-cache-dir yolov5

COPY app.py .
#COPY 20260604_yolo11s.pt .
COPY yolov5-best.pt .

# Expose port 8000
EXPOSE 8000

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]