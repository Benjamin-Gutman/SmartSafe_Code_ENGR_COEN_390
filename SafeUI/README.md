# SafeUI: Emulator Setup

## 1. Start the server (PC)

```
pip install websockets sqlalchemy pydantic
cd Database_Files
python Websocket.py
```

Needs Python 3.10+. The last line of `Websocket.py` must bind to `0.0.0.0`:

```python
asyncio.run(main("0.0.0.0", 8765))
```

Data is stored in `test.db` in the folder you started the server from. Delete it to reset.

## 2. Connect the emulator

With the emulator running, forward the port (repeat after every emulator restart):

```
adb reverse tcp:8765 tcp:8765
```

In `ClientSocket.java`:

```java
private static final String URL = "ws://127.0.0.1:8765";
```

## 3. Run

Run the app from Android Studio and open the inventory screen. To confirm changes reach the server, run `python check_db.py` in `Database_Files`.

## Troubleshooting

| Problem | Fix |
|---|---|
| Timeout / "failed to connect" | Re-run `adb reverse tcp:8765 tcp:8765` and check the server is running |
| App crashes opening inventory | Uninstall the app so the database is recreated |
