# SMS Gateway

This is a simple android 2.0 application that allows one to use their
android-based phone as a simplistic SMS gateway to the web.

## Operation

After installation, open the application and set the server URL in the settings.
This URL is where any received SMSs will be POSTed to when the gateway is active.

Tap the "Enable" button to start the gateway.

## Server-side

On thes server, the specified URL will receive two POST parameters:
- from: the phone number that the text message came from
- text: the content of the received text message.
