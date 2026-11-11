""" Stores safe states and state machine logic """

import logging 
import time 

import config

log = logging.getLogger("hw")

# -- Define Possible States for Safe -- #
LOCKED, UNLOCKED, OPEN, ALARM = "locked", "unlocked", "open", "alarm"

class Safe:

    def __init__(self, loop, pins, notify):

        self.loop = loop
        self.pins = pins
        self.notify = notify

        self.state = LOCKED

        self._relock_timer = None
        self._blink_timer = None
        self._blink_on = False

        self._set_state(LOCKED if pins.door_is_closed() else OPEN)

# -- Public Functions -- #

    def door_opened(self):
        """ Defines logic for when door is opened """

        if self.state == UNLOCKED:

            self._set_state(OPEN)
            self.notify("door_opened")

        elif self.state == LOCKED:

            self._set_state(ALARM)
            self.notify("alarm", reason="Door opened while locked")

    def door_closed(self):
        """ Defines logic for when door is closed """

        self.notify("door_closed")

        if self.state == OPEN:
            self.loop.cancel(self._relock_timer)
            self.loop.after(config.RELOCK_DELAY_SECONDS, self._relock_after_close)


    def unlock(self, source):
        """ Unlocks door if it is closed and sets a timer """

        if self.state == OPEN:
            return

        # Set state to unlock or open and clear alarm #
        self._set_state(UNLOCKED if self.pins.door_is_closed() else OPEN)

        # Cancels any existing timers and sets a timer that runs self.lock after UNLOCK_SECONDS time passes #
        self.loop.cancel(self._relock_timer)
        self._relock_timer = self.loop.after(config.UNLOCK_SECONDS, self.lock)

        self.notify("unlocked", source=source)


    def lock(self):
        """ Checks if door is in UNLOCKED state and locks it. Returns true if it succeeded """

        if self.state == UNLOCKED and self.pins.door_is_closed():

            self._set_state(LOCKED)
            self.notify("locked")

        return self.state == LOCKED

    def status(self):
        return {"state": self.state, "door": "closed" if self.pins.door_is_closed() else "open"}



# -- Internal Functions -- #

    def _set_state(self, state):
        """ Set state. Reset alarm state if any other state is set """

        self.state = state
        self.loop.cancel(self._blink_timer)
        self._blink_on = False

        # Check if unlocked (either unlocked and closed or open)
        is_unlocked = state in (UNLOCKED, OPEN)

        self.pins.set(config.LOCK, is_unlocked)
        self.pins.set(config.BUZZER, False)
        self.pins.set(config.RED_LED, False)

        if state == ALARM:
            self._blink()
        log.info("state: %s", state)

    def _relock_after_close(self):
        """ Checks if door was closed and locks automatically """

        if self.state == OPEN and self.pins.door_is_closed():
            self._set_state(LOCKED)
            self.notify("locked")

    def _blink(self):
        """ Flash red LED and sound buzzer """

        self._blink_on = not self._blink_on
        self.pins.set(config.RED_LED, self._blink_on)
        self.pins.set(config.BUZZER, self._blink_on)
        self._blink_timer = self.loop.after(config.BLINK_SECONDS, self._blink)

    def _beep(self, seconds):
        """ Sound buzzer for fixed amount of time """

        if self.state != ALARM:
            self.pins.set(config.BUZZER, True)
            self.loop.after(seconds, self._beep_off)

    def _beep_off(self):
        """ Called by loop after time specified in _beep passes """

        if self.state != ALARM:
            self.pins.set(config.BUZZER, False)


    

        
