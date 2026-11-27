import cv2
from picamera2 import Picamera2
from pyzbar.pyzbar import decode
from libcamera import controls
import numpy as np
from Database_Files.Schemas.Inventory import Inventory

def camera_init() -> Picamera2:
    picam2 = Picamera2()
    picam2.configure(picam2.create_preview_configuration(main = {"size": (1920, 1080),"format":"RGB888"}))
    picam2.start()
    picam2.set_controls({"AfMode": controls.AfModeEnum.Continuous})
    return picam2


def get_frame(picam2: Picamera2) -> np.ndarray:
    return picam2.capture_array()


def process_frame(frame: np.ndarray) -> np.ndarray:
    gray = cv2.cvtColor(frame, cv2.COLOR_RGB2GRAY)  
    clahe = cv2.createCLAHE( clipLimit=2.0,tileGridSize=(8,8))
    enhanced = clahe.apply(gray)
    threshold = cv2.adaptiveThreshold(enhanced,255,cv2.ADAPTIVE_THRESH_GAUSSIAN_C,cv2.THRESH_BINARY,31,5)
    return threshold

def get_barcodes(processed_frame: np.ndarray) -> list[Inventory]:
    barcodes = decode(processed_frame)
    barcode_values = [barcode.data.decode("utf-8")for barcode in barcodes]
    values, counts = np.unique(barcode_values, return_counts = True)
    inventory: list[Inventory] = []
    for i in range(len(values)):
        inventory.append(Inventory(item_barcode=values[i], quantity=int(counts[i])))
    return inventory