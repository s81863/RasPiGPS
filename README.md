# Overview
This application displays and saves GNSS data coming from a Raspberry Pi via Bluetooth and therefore only works in combination with a Raspberry Pi, a Ublox C099-F9P application 
board and a corresponding Ublox ANN-MB-00-00 GNSS antenna. 
In addition, a Python script must run on the Raspberry Pi that establishes an interface to the GNSS receiver, starts a Bluetooth server and sends the parsed GNSS data to the app.
To start this Python script automatically when the Raspberry Pi boots, it is recommended to set up a corresponding service.
An example of such a script and service can be found [here](https://github.com/s81863/RasPiGPS-Server-Side).
In order to use the RTK functionality we recommend [RTKLIB](https://github.com/rtklibexplorer/RTKLIB) and follow the [instructions](https://rtklibexplorer.wordpress.com/2022/11/10/raspberry-pi-based-ppk-and-rtk-solutions-with-rtklib/) by rtklibexplorer to set it up on your Raspberry Pi.

## Android Requirements
- Minimum Android Version: 7.0
- A Mapbox API-Key to be entered in the AndroidManifest.xml
