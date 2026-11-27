#!/bin/sh

echo "Running pi firmware..."


PROJECT_DIR="$HOME/SmartSafe_Code_ENGR_COEN_390"

VENV_DIR="$PROJECT_DIR/myvenv"

SERVER_PATH="$PROJECT_DIR/Database_Files/Websocket.py"
SERVER_TEST_PATH="$PROJECT_DIR/Database_Files/websockets test/testClient.py"
HW_PATH="$PROJECT_DIR/HW_Interface/main.py"


lxterminal -e "sh -c 'cd \"$PROJECT_DIR\" && . \"$VENV_DIR/bin/activate\" && python3 -m HW_Interface.main; exec bash'"

lxterminal -e "sh -c 'cd \"$PROJECT_DIR\" && . \"$VENV_DIR/bin/activate\" && python3 \"$SERVER_PATH\"; exec bash'"


if [ "$1" = "--debug" ]; 

then lxterminal -e "sh -c 'cd \"$PROJECT_DIR\" && . \"$VENV_DIR/bin/activate\" && python3 \"$SERVER_TEST_PATH\"; exec bash'"

fi

