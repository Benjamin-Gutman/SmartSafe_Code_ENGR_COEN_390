""" Stores all constants and pins """
import os
 
GPIO_CHIP = "/dev/gpiochip15" 

# -- GPIO Numbers for peripheral devices (not pin numbers) -- #

REED_SWITCH = 26 # Normally Open 
LOCK = 16
BUZZER = 5
RED_LED = 6 # Alarm Light

# -- End GPIO Numbers -- #




# -- Global Variables (e.g. for timing purposes or constants) -- #
# DO NOT STORE SENSITIVE INFORMATION IN CODE #

SERVER_HOST = "127.0.0.1" # Host this progam server socket on localhost
SERVER_PORT = 8766

BACKEND_HOST = "127.0.0.1"
BACKEND_PORT = 8765


BLINK_SECONDS = 0.5
RELOCK_DELAY_SECONDS = 2
UNLOCK_SECONDS = 20
CLIENT_TIMEOUT_SECONDS = 1.0

# -- End Global Variables -- #