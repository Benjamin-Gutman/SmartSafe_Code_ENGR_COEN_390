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

BLINK_SECONDS = 0.5
RELOCK_DELAY_SECONDS = 2
UNLOCK_SECONDS = 20

# -- End Global Variables -- #