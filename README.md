# SmartSafe Launch Instructions

This document explains how to launch different sections of this project

## Android application

Open Android Studio in SafeUI/ project directory.


## Launching Full Backend on Raspberry Pi (Does not support SSH yet)

**Launch Commands:** The run.sh script launchs both hardware interface and websocket backend in order in seperate terminals. Use script in Database_Files/ to test.

```bash

cd ~/SmartSafe_Code_ENGR_COEN_390
./run.sh

```

## DataBase and RPI5 Backend Websocket Service

**Launch Commands:** The following commands are to navigate to the project directory, activate the virual environment and launch the websocket server (necessary for the app backend).

```bash
cd ~/SmartSafe_Code_ENGR_COEN_390
source myvenv/bin/activate

cd Database_Files
python Websocket.py
```

## Hardware Interface

**Start HW Interface:** In a seperate terminal, navigate to the project directory, activate virtual environment and launch HW interface program.

```bash
cd ~/SmartSafe_Code_ENGR_COEN_390
source myvenv/bin/activate

cd HW_Interface
python3 main.py
```


