import logging 
import threading

log = logging.getLogger("hw")

class Camera:
    def __init__(self, loop):

        from Camera_Environment import BarcodeScannerFunctions

        self.loop = loop
        self._lib = BarcodeScannerFunctions
        self._camera = BarcodeScannerFunctions.camera_init()
        self._busy = False

# -- Public Functions -- #

    def scan(self, on_result):
        """ Tries to scan and calls on_result when scan completes. Returns false if scan failes (i.e. scan is already happens) """

        if self._busy:
            return False
        
        self._busy = True
        threading.Thread(target=self._work, args=(on_result,), daemon=True).start()
        return True


# -- Internal Functions -- #

    def _work(self, on_result):
        """ Functions is called in thread. Gets frame and pulls barcodes from the image. Calls _finish function when done scanning """
        try:

            frame = self._lib.get_frame(self._camera)
            inventory = self._lib.get_barcodes(self._lib.process_frame(frame))

            inventory = [item for item in inventory if item.item_barcode]
        
        except Exception:
            log.exception("scan failed")    
            inventory = None

        self.loop.call_from_thread(lambda: self._finish(on_result, inventory))

    def _finish(self, on_result, inventory):
        """ Sets busy flag to false and passes the resulting inventory in the on_result function defined externally """
        self._busy = False
        on_result(inventory)

def make_camera(loop):
       
    try:
        return Camera(loop)
    except Exception as error:
        log.warning("camera not available (%s); scanning is disabled", error)
        return None
