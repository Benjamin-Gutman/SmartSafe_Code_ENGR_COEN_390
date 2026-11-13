import cv2
from picamera2 import Picamera2
from pyzbar.pyzbar import decode
from libcamera import controls

print("Program Started")

picam2 = Picamera2()
picam2.configure(picam2.create_preview_configuration(main = {"size": (1920, 1080),"format":"RGB888"}))
picam2.start()
picam2.set_controls({"AfMode": controls.AfModeEnum.Continuous})
print("camera initialized")



while True:
    frame = picam2.capture_array()
    
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    
    clahe = cv2.createCLAHE( clipLimit=2.0,tileGridSize=(8,8))
    threshold = cv2.adaptiveThreshold(gray,255,cv2.ADAPTIVE_THRESH_GAUSSIAN_C,cv2.THRESH_BINARY,31,5)

    barcodes = decode(frame)

    for barcode in barcodes:
        x, y, w, h = barcode.rect
        cv2.rectangle(frame, (x, y), (x + w, y + h), (0, 255, 0), 2)

        barcode_data = barcode.data.decode("utf-8")
        barcode_type = barcode.type

        text = f"{barcode_data} ({barcode_type})"
        cv2.putText(
            frame,
            text,
            (x, y - 10),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.5,
            (0, 255, 0),
            2,
        )
        
        text = f"{barcode_data} ({barcode_type})"
        
        print(text)
        
        cv2.putText(
            frame,
            text,
            (x,y-10),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.5,
            (0,255,0),
            2
        )

    cv2.imshow("Barcode Scanner", frame)

    if cv2.waitKey(1) & 0xFF == ord("q"):
        break

cap.release()
cv2.destroyAllWindows()
