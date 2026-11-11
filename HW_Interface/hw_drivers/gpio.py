""" Configures and Implements GPIO Outputs and Reed Switch """

from datetime import timedelta

import gpiod
from gpiod.line import Bias, Direction, Edge, Value

import config


# -- Populate with gpio values from config.py (ONLY OUTPUT VALUES) -- #
OUTPUTS = (config.LOCK, config.RED_LED, config.BUZZER)

class Pins:

    def __init__(self):

        # -- Define settings dictionnary variable -- #
        # Dict format:
        #   {
        #       GPIO_NUM : Linesettings
        #   }
        
        settings = {
            config.REED_SWITCH: gpiod.LineSettings
            (

                direction = Direction.INPUT, # Define as read pin
                edge_detection = Edge.BOTH, # Detect any change
                bias = Bias.PULL_UP, # High when door is open
                debounce_period = timedelta(milliseconds=30),
            ),

        }

        # Configure outputs and set to default low #
        for pin in OUTPUTS:
            settings[pin] = gpiod.LineSettings(direction = Direction.OUTPUT, output_value = Value.INACTIVE)

        # Creates LineRequest Objects Tied to Chip #
        self._lines = gpiod.request_lines(config.GPIO_CHIP, consumer="safe-hw", config = settings)

    @property
    def fd(self):
        """ Loop watches this and wakes when event triggers (reed switch) """
        return self._lines.fd

    def door_events(self):
        """ Returns "opened" or "closed" when door event is triggered """

        changes = []

        for event in self._lines.read_edge_events():

            closed = event.event_type == gpiod.EdgeEvent.Type.FALLING_EDGE  
            changes.append("closed" if closed else "opened")

        return changes

    def door_is_closed(self):
        """ Returns door status: True for closed, False for open """
        return self._lines.get_value(config.REED_SWITCH) == Value.INACTIVE

    def set(self, pin, on):
        self._lines.set_value(pin, Value.ACTIVE if on else Value.INACTIVE)

    def release(self):
        """ Resets every output to off """

        for pin in OUTPUTS:
            self.set(pin, False)

        self._lines.release()


            






