import os
import cv2
import time
import json
import pickle
import asyncio
import websockets
import numpy as np
import supervision as sv
from datetime import datetime
from paddleocr import PaddleOCR

os.makedirs("./opencv_cache", exist_ok=True)
os.environ["OPENCV_OCL4DNN_CONFIG_PATH"] = "./opencv_cache"
yoloNet = cv2.dnn.readNetFromONNX("../models/yolo26n.onnx")
yoloNet.setPreferableTarget(cv2.dnn.DNN_TARGET_OPENCL)

anprNet = cv2.dnn.readNetFromONNX("../models/anpr_n.onnx")
anprNet.setPreferableTarget(cv2.dnn.DNN_TARGET_OPENCL)

ocr = PaddleOCR(device="npu:0")

face_dt = cv2.FaceDetectorYN.create(
    model="../models/face_detection_yunet_2026may.onnx",
    config="",
    score_threshold=0.7,
    input_size=(320, 320),
    nms_threshold=0.3,
    top_k=5000,
)

arcface_template = np.array(
    [
        [38.2946, 51.6963],  # left eye
        [73.5318, 51.5014],  # right eye
        [56.0252, 71.7366],  # nose
        [41.5493, 92.3655],  # left mouth
        [70.7299, 92.2041],  # right mouth
    ],
    dtype=np.float32,
)

faceNet = cv2.dnn.readNetFromONNX("../models/arc_face.onnx")
faceNet.setPreferableTarget(cv2.dnn.DNN_TARGET_OPENCL)

with open("tmp/emb.json", "r") as f:
    emb_data = json.loads(f.read())
with open("tmp/vech.json", "r") as f:
    vech_data = json.loads(f.read())

def processYOLO(output, image, conf_threshold=0.25, nms_threshold=0.45):
    h, w = image.shape[:2]
    predictions = output[0].T

    boxes = []
    scores = []
    class_ids = []

    for prediction in predictions:

        cx, cy, bw, bh = prediction[:4]
        class_scores = prediction[4:]
        class_id = np.argmax(class_scores)
        confidence = float(class_scores[class_id])

        if confidence < conf_threshold:
            continue

        x = int((cx - bw / 2) * w / 640)
        y = int((cy - bh / 2) * h / 640)

        box_w = int(bw * w / 640)
        box_h = int(bh * h / 640)

        boxes.append([x, y, box_w, box_h])
        scores.append(confidence)
        class_ids.append(int(class_id))

    final_indices = []
    for class_id in set(class_ids):

        indices = [
            i for i, cid in enumerate(class_ids)
            if cid == class_id
        ]

        class_boxes = [boxes[i] for i in indices]
        class_scores = [scores[i] for i in indices]
        selected = cv2.dnn.NMSBoxes(class_boxes, class_scores, conf_threshold, nms_threshold)

        for idx in np.array(selected).flatten():
            final_indices.append(indices[int(idx)])

    detections = []

    for idx in final_indices:
        x, y, bw, bh = boxes[idx]
        detections.append([x, y, x + bw, y + bh])

    xyxy = np.array(detections, dtype=np.float32).reshape(-1, 4)
    confidence = np.array([scores[i] for i in final_indices], dtype=np.float32)
    class_ids = np.array([class_ids[i] for i in final_indices], dtype=int)

    return sv.Detections(xyxy=xyxy, confidence=confidence, class_id=class_ids), [xyxy, confidence, class_ids]

async def handler(websocket):
    print("Client connected")

    try:
        async for message in websocket:
            print("Received:", message)

    except websockets.ConnectionClosed:
        print("Client disconnected")


async def main():
    async with websockets.serve(handler, "0.0.0.0", 8080):
        print("WebSocket server running on port 8080")
        await asyncio.Future()

def face_test():
    print("Begin testing")
    conn_id = "343423"
    stream_name = "cam 1"
    offset = 2.65
    dets = []
    stream_source = "video3.mp4"
    frame = cv2.imread("../assets/images/group.png")
    
    # YuNet requires the input size to match the frame dimensions exactly
    h, w = frame.shape[:2]
    face_dt.setInputSize((w, h))
    
    tracker = sv.ByteTrack()
    if os.path.exists(f"tmp/f{conn_id}"):
        with open(f"tmp/f{conn_id}", "rb") as f:
            dets, face_id, face_count = pickle.load(f)
    else:
        face_id = set()
        face_count = 0

    # Run YuNet Detection
    _, faces = face_dt.detect(frame)
    
    if faces is not None:
        # YuNet outputs [x, y, w, h]. Convert to [x1, y1, x2, y2]
        boxes_xywh = faces[:, 0:4]
        boxes_xyxy = np.empty_like(boxes_xywh)
        boxes_xyxy[:, 0] = boxes_xywh[:, 0]
        boxes_xyxy[:, 1] = boxes_xywh[:, 1]
        boxes_xyxy[:, 2] = boxes_xywh[:, 0] + boxes_xywh[:, 2]
        boxes_xyxy[:, 3] = boxes_xywh[:, 1] + boxes_xywh[:, 3]
        
        confidences = faces[:, 14]
        # Extract 5 landmarks (indices 4 to 13) and reshape to (N, 5, 2)
        kpss = faces[:, 4:14].reshape(-1, 5, 2)
        
        det = sv.Detections(
            xyxy=boxes_xyxy,
            confidence=confidences,
            class_id=np.zeros(len(faces), dtype=int),
            data={"kps": kpss}
        )
    else:
        det = sv.Detections.empty()

    # Note: ByteTrack will return length 0 on a single image without a loop. 
    # Iterate over `det` instead of `tracked` if you are just testing one static image.
    tracked = tracker.update_with_detections(det)
    print("len: ", len(tracked))
    if tracked.tracker_id is not None and len(tracked) > 0:
        tracked_kpss = tracked.data.get("kps")
        for t_id, xyxy, conf, kps in zip(tracked.tracker_id, tracked.xyxy, tracked.confidence, tracked_kpss):
            if t_id not in face_id and conf >= 0.8:
                face_id.add(t_id)
                x1, y1, x2, y2 = map(int, xyxy)
                try:
                    transform, _ = cv2.estimateAffinePartial2D(
                        kps, arcface_template, method=cv2.LMEDS
                    )
                    aligned_face = cv2.warpAffine(
                        frame, transform, (112, 112), flags=cv2.INTER_LINEAR
                    )
                    
                    # Pure NumPy normalization (replacing PyTorch)
                    aligned_face = aligned_face.astype(np.float32)
                    aligned_face = np.transpose(aligned_face, (2, 0, 1))
                    aligned_face = np.expand_dims(aligned_face, axis=0)
                    aligned_face = (aligned_face - 127.5) / 127.5

                    faceNet.setInput(aligned_face)
                    embs = faceNet.forward().reshape(-1)
                    embs = (embs - embs.min()) / (embs.max() - embs.min())
                    
                    os.makedirs("../assets/emb", exist_ok=True)
                    np.save(f"../assets/emb/{stream_name}_{face_count}", embs)
                    
                    print("embedding saved")
                    face_count += 1
                except Exception as e:
                    print("Error during alignment/embedding:", e)
                    # ... [Keep your existing JSON saving logic here] ...
                    
                    print("embedding saved")
                    face_count += 1
                except Exception as e:
                    print("Error during alignment/embedding:", e)
                    
    with open(f"tmp/f{conn_id}", "wb") as f:
        pickle.dump([dets, face_id, face_count], f)
    print("Done")

def vechile_test():
    print("Begin testing")
    conn_id = "343423"
    stream_name = "cam 1"
    offset = 2.65
    dets = []
    stream_source = "video3.mp4"
    frame = cv2.imread("../assets/images/group.png")
    blob = cv2.dnn.blobFromImage(
        frame,
        scalefactor=1/255.0,
        size=(640, 640),
        swapRB=True,
        crop=False
    )
    tracker = sv.ByteTrack()
    if os.path.exists(f"tmp/v{conn_id}"):
        with open(f"tmp/v{conn_id}", "rb") as f:
            dets, vech_id = pickle.load(f)
        for i in dets:
            tracker.update_with_detections(sv.Detections(xyxy=i[0], confidence=i[1], class_id=i[2]))
    else:
        vech_id = set()
    yoloNet.setInput(blob)
    output = yoloNet.forward()
    det, raw = processYOLO(output, frame)
    dets.append(raw)
    tracked = tracker.update_with_detections(det)
    if tracked.tracker_id is not None:
        for t_id, xyxy, c_id, conf in zip(tracked.tracker_id, tracked.xyxy, tracked.class_id, tracked.confidence):
            if t_id not in vech_id:
                vech_id.add(t_id)
                x1, y1, x2, y2 = map(int, xyxy)
                if conf >= 0.5 and int(c_id) in [1, 2, 3, 5, 6, 7]:
                    now = datetime.now()
                    dt = now.strftime("%Y-%m-%d")
                    if dt not in vech_data:
                        vech_data[dt] = {}
                    if stream_name not in vech_data[dt]:
                        vech_data[dt][stream_name] = {"st": time.time(), "dt":[], "src": stream_source}
                    vd = {"cs": int(c_id), "pt":"", "tm": time.time(), "vt": offset, "box": [x1, y1, x2, y2]}
                    vech_cropped = frame[y1:y2, x1:x2]
                    plate_blob = cv2.dnn.blobFromImage(
                        vech_cropped,
                        scalefactor=1/255.0,
                        size=(640, 640),
                        swapRB=True,
                        crop=False
                    )
                    anprNet.setInput(plate_blob)
                    plate_res = anprNet.forward()
                    p_boxes, _ = processYOLO(plate_res, vech_cropped)
                    if len(p_boxes) > 0:
                        best_idx = np.argmax(p_boxes.confidence)
                        plate = p_boxes[best_idx]

                        px1, py1, px2, py2 = map(int, plate.xyxy[0])
                        gray = cv2.cvtColor(vech_cropped[py1:py2, px1:px2], cv2.COLOR_BGR2GRAY)
                        score = cv2.Laplacian(gray, cv2.CV_64F).var()
                        if score > 20:
                            print("reading plate: ", end="")
                            results = ocr.predict(vech_cropped[py1:py2, px1:px2])[0]
                            score = max(results["rec_scores"])
                            if score > 0.5:
                                vd["pt"] = results["rec_texts"][results["rec_scores"].index(score)]
                                print(results["rec_texts"][results["rec_scores"].index(score)])
                            else:
                                print("N/A")
                    vech_data[dt][stream_name]["dt"].append(vd)
                    with open("tmp/vech.json", "wb") as f:
                            f.write(json.dumps(vech_data).encode("utf-8"))
                    print("vechile written")
    with open(f"tmp/v{conn_id}", "wb") as f:
        pickle.dump([dets, vech_id], f)
    print("Done")

if __name__ == "__main__":
    # asyncio.run(main())
    face_test()