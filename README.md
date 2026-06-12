# qupath-yolov5

A FastAPI YOLOv5 inference service with QuPath integration, plus dataset configuration and example Docker training commands.

## Project overview

This repository contains two main workflows:

1. **Inference service**
   - `app.py` is a FastAPI app that loads custom YOLOv5 weights from `yolov5-best.pt` and exposes `/predict`.
   - QuPath scripts (`yolo-script.groovy`, `yolo-script-tiled.groovy`) send image regions to the service and convert detections into annotations.
   - `Dockerfile` builds the inference container.

2. **Training examples**
   - `training/pollen_class_28052024.yaml` is the YOLO dataset config.
   - `training/info.txt` contains working `ultralytics/yolov5:latest` Docker training commands.
   - `training/dockerfile` is an optional custom training image definition.

## Repository structure

- `app.py` - FastAPI inference server using YOLOv5.
- `Dockerfile` - builds the inference container.
- `yolov5-best.pt` - custom YOLOv5 weights used by the inference service.
- `yolo-script.groovy` - QuPath script for single-region inference.
- `yolo-script-tiled.groovy` - QuPath script for tiled inference.
- `training/pollen_class_28052024.yaml` - dataset configuration.
- `training/info.txt` - example Docker training commands.
- `training/dockerfile` - optional custom training image definition.

## Inference setup

### 1. Build the inference Docker image

From the repository root:

```bash
docker build -t qupath-yolov5:latest .
```

### 2. Run the inference service

```bash
docker run --rm -it -p 8001:8000 qupath-yolov5:latest
```

The server listens on `http://0.0.0.0:8000`.

### 3. Test the prediction endpoint

Use `curl` or any HTTP client:

```bash
curl -X POST "http://localhost:8000/predict" \
  -F "file=@/path/to/image.jpg" \
  -G --data-urlencode "threshold=0.5"
```

### 4. Use with QuPath

Open QuPath and run one of the Groovy scripts:

- `yolo-script.groovy` - sends a single selected region or current viewer region to the inference API.
- `yolo-script-tiled.groovy` - splits a large ROI into tiles and sends each tile for detection.

Update the `path` variable in the scripts if your service is not running on `http://anton:8001/predict`.

## Training setup

This repository uses the official `ultralytics/yolov5:latest` image as the recommended training environment.

### 1. Run training with the official Ultralytics image

From the repository root, run:

```bash
sudo docker run --rm -it --ipc=host --runtime=nvidia --gpus all \
  -v "$(pwd)":/w -w /w ultralytics/yolov5:latest \
  yolo train model=yolov5s.pt data=training/pollen_class_28052024.yaml project=/w/runs
```

### 2. Use `single_cls=True` only when the dataset should be treated as one class

The current YAML file defines `nc: 52` and a list of 52 species names. If you want to train in single-class mode, update the YAML to:

```yaml
train: ./train_data/images/train/
val: ./train_data/images/val/
nc: 1
names: ['pollen']
```

Then run:

```bash
sudo docker run --rm -it --ipc=host --runtime=nvidia --gpus all \
  -v "$(pwd)":/w -w /w ultralytics/yolov5:latest \
  yolo train model=yolov5s.pt data=training/pollen_class_28052024.yaml project=/w/runs single_cls=True
```

### 3. Optional custom training image

If you want a custom image instead of the official one, `training/dockerfile` is available, but the recommended path is to use `ultralytics/yolov5:latest`.

## Notes on `model=`

- `model=yolov5s.pt` requires either the file to exist locally in the mounted folder or internet access so the container can download it.
- If your repo contains `yolov5-best.pt`, use that filename:

```bash
yolo train model=yolov5-best.pt data=training/pollen_class_28052024.yaml project=/w/runs
```

- If you are using a YOLO config file instead of checkpoint weights, use `model=yolov5s.yaml`.

## How output paths work

With `-v "$(pwd)":/w -w /w` and `project=/w/runs`, training outputs are written to `./runs` in your current host folder.
For example, a container output path of `/w/runs/train-2` maps to `./runs/train-2` on the host.

## QuPath integration

The Groovy scripts use the `/predict` endpoint to send image bytes as multipart form-data.

- `yolo-script.groovy` sends the selected region or viewer region.
- `yolo-script-tiled.groovy` divides large regions into smaller tiles and aggregates detections.

Make sure your FastAPI service is reachable from QuPath. If QuPath is running on a different machine or inside a VM, update the endpoint URL in the scripts.

## Troubleshooting

### `yolo5s.pt` not found

If you get `FileNotFoundError: [Errno 2] No such file or directory: 'yolo5s.pt'`, then either:

- the file is not present in the mounted working directory, or
- the container has no internet access to download it.

Use a local checkpoint file with `model=yolov5-best.pt` or place `yolo5s.pt` into the mounted directory.

### `single_cls=True` usage

Use `single_cls=True` only if the dataset should be treated as a single class.
For a multi-class dataset, remove that flag.

## Recommended workflow

1. Build the inference image.
2. Run the inference API.
3. Test with a sample image.
4. Run QuPath script against the API.
5. Build the training image and run training with the correct dataset YAML.
6. Inspect `runs/` for training output.

## File locations

- Inference app: `app.py`
- Inference Dockerfile: `Dockerfile`
- Custom weights: `yolov5-best.pt`
- QuPath scripts: `yolo-script.groovy`, `yolo-script-tiled.groovy`
- Training image: `training/dockerfile`
- Dataset config: `training/pollen_class_28052024.yaml`
- Training notes: `training/info.txt`

