<div align="center">
   <img src="/app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.webp" width="256" height="256"/>

   # PicoNeverSleep
   [English](README.md) | [中文](README_zh.md) | [Русский](README_ru.md)
   
   ### Add a "Never Sleep" quick setting button to your Pico VR headset.
   PicoNeverSleep is an LSPosed module designed for Pico VR headsets that adds a dedicated "Never Sleep" toggle to the Quick Settings panel. It allows you to keep the screen on indefinitely with a single tap, perfect for testing, watching long videos, or unattended downloads.
</div>

## 👓 Screenshot
<image src="Resource/Screenshot1.jpeg" width="400"/>

## 🌟 Key Features
* 🌙 **Never Sleep Toggle:** Easily prevent your headset from going to sleep directly from the Quick Settings.
* 🔄 **Persistent After Reboot:** Automatically restores your "Never Sleep" state after the device restarts by hooking into the system boot phase.
* 🌐 **Multi-language Support:** Fully localized in 27+ languages including English, Chinese, Japanese, Korean, and many European languages.
* 🧹 **Clean & Focused:** Single-purpose module with no unnecessary features or background battery drain.

## ⛏️ Prerequisites
* **Device:** Pico 4 Headset (Phoenix/China firmware supported).
* **Permissions:** **[Root Access](https://pico4.wiki/guides/root/01-root/)** is required to apply changes to system files.
   * Recommend using [picounlock](https://github.com/chaixshot/more-picohaxx)
* **Environment:** **[LSPosed Framework](https://github.com/JingMatrix/Vector/releases/tag/v2.0)** must be installed and active.
* **Scope:** Both **System Framework (android)** and **PicoVR Settings (com.picovr.settings)** must be selected in LSPosed Manager.

## 📐 How to use?
1. **Install** the `PicoNeverSleep.apk` on your headset.
2. **Open** the LSPosed Manager app.
3. **Enable** the PicoNeverSleep module.
4. **Check the Scopes:** Ensure both `System Framework` and `PicoVR Settings` are checked.
5. **Reboot** your device to activate the hooks.
6. **Usage:**
    * Open your Quick Settings panel (click the clock/battery area in the dock).
    * You will see a new **Never Sleep** button at the beginning of the list.
    * Tap to toggle: The icon will highlight and text will update to show it is active.

## ⁉️ Why is the button not appearing?
* Ensure you have enabled the module in **LSPosed Manager**.
* Double-check that the **com.picovr.settings** scope is selected.
* You **must reboot** the headset (or at least restart the Settings app) after enabling the module for the first time.

## ⁉️ How does it work?
The module hooks into `com.picovr.settings` to inject a custom tile into the Quick Settings adapter. 
* It toggles the system property `pvr.factorytest.never.sleep` to control the sleep behavior.
* Since Pico OS resets this property to `0` on every boot, the module also hooks the **System Server** (`android` package) boot phases to restore your saved state from a persistent `Settings.Global` variable as soon as the system starts.

## 🔃 Language Support
The app supports 27 languages including:
Čeština, Dansk, Nederlands, English (UK/US), Suomi, Français, Deutsch, Ελληνικά, Italiano, 日本語, 한국어, Melayu, Norsk bokmål, Polski, Português (PT/BR), Română, Русский, Español (ES/LA), Svenska, ไทย, Türkçe, 中文 (简体/繁體/香港).

## 🙏 Special thanks to:
* [Xposed Framework](https://github.com/rovo89/XposedBridge) - The foundation for this module.
* [LSPosed](https://github.com/LSPosed/LSPosed) - Modern Xposed implementation for Android.
* [pico4-sleep-mode](https://github.com/hhhbwc/pico4-sleep-mode) - Original inspiration for the Quick Settings injection logic.
