# Noise Cancellation

_Quickly turn off noise cancellation on Bose 700 headphones_

![Turn Off Noise Cancellation App](docs/media/feature-graphic.png)


## What?

This Android app allows you to quickly turn off noise cancellation on Bose 700 headphones.

## Why?

Bose 700 headphones have a "noise control" button to cycle through noise cancellation presets, but these are a scale (0-10) between filtering out external sound and actively passing it thorugh.  In a very quiet room, neither of these is needed, and any setting can result in an audible hiss.  The official _Bose Music_ app does allow you to temporarily turn off noise cancelling, but this setting is buried in the app and takes some time to get to.

## When?

You can turn off noise cancellation through the app, or as a _tile_ you can add to your quick settings, or as a quick-access shortcut you can pin to your launcher.

You can turn noise cancelling back on by pressing the Noise Control button on the left ear cup.

## How?

The application sends a short message (repeated 3 times) over the device's Serial Port Profile (SPP) connection, in hex:

> `01 05 02 02 00 00`

<!--

Noise cancellation `enabled` (0=off, 1=on), if enabled, on `level` (0-10):

> Send: 0x01 0x05 0x02 0x02 (10-level) (enabled)

When toggling enabled on or off, device always starts at level=10 regardless of level sent, so a second repeat packet is required to resume to a level other than 10.

> Response: 0x01 0x05 0x03 0x03 0x0b (10-level) (enabled)

-->

## Disclaimer

> This product is provided 'AS IS', the developer makes no warranties, accepts no liability, and hereby disclaims any implied warranties, including any warranty of merchantability and warranty of fitness for a particular purpose. This software sends a signal to the device you choose.  This has only been tested on one specific pair of headphones of a single firmware version.  You accept liability for whatever happens, including any damage, injury, or loss.

## See also

* [App Website](https://noisecancel.danjackson.dev)
<!-- * [Google Play Store Listing](https://play.google.com/store/apps/details?id=dev.danjackson.noisecancel) -->
<!-- * [Open Source Code Repository](https://github.com/danielgjackson/noisecancel/) ([license](https://github.com/danielgjackson/noisecancel/blob/master/LICENSE)) -->
<!-- * [Privacy Policy](https://noisecancel.danjackson.dev/privacy.html) -->
