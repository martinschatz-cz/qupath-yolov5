# app.py
import os
from fastapi import FastAPI, UploadFile, File, Query
import torch
import numpy as np
import io
from PIL import Image

os.environ.setdefault("YOLO_CONFIG_DIR", "/tmp/Ultralytics")

app = FastAPI()

# Load your custom YOLOv5 model (make sure this weight file is in your Docker build)
model = torch.hub.load('ultralytics/yolov5', 'custom', path='yolov5-best.pt', force_reload=False, trust_repo=True)

@app.get("/health")
def health_check():
    return {"status": "online"}

@app.post("/predict")
async def predict(
    file: UploadFile = File(...),
    threshold: float = Query(0.5, ge=0.0, le=1.0, description="Minimum confidence threshold for predictions")
):
    # Read image bytes from QuPath
    request_object_content = await file.read()
    img = Image.open(io.BytesIO(request_object_content)).convert("RGB")
    img_np = np.array(img)
    
    # Run YOLO inference with the requested confidence threshold
    model.conf = threshold
    results = model(img_np)
    
    predictions = []
    detections = results.pred[0].tolist()
    for x1, y1, x2, y2, confidence, class_id in detections:
        confidence = float(confidence)
        class_id = int(class_id)
        class_name = model.names[class_id]

        if confidence >= threshold:
            predictions.append({
                "x": x1,
                "y": y1,
                "width": x2 - x1,
                "height": y2 - y1,
                "class_name": class_name,
                "confidence": confidence
            })
    return {"predictions": predictions}