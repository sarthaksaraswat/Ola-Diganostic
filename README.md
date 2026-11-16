<h1 align="center">Ola Diagnostic</h1>

<h2 align="center"><a href="https://github.com/sarthaksaraswat/Ola-Diganostic/releases/download/v2.0.0/OlaDiagnostic-v2.0.0.apk">Download Ola Diagnostic for Android (.apk)</a></h2>

<p align="center">Version 2.0.0 · Signed prerelease · Android 8.0+ · <a href="https://github.com/sarthaksaraswat/Ola-Diganostic/releases/tag/v2.0.0">Release notes</a></p>

<p align="center">
  <img src="ola_diagnostic_icon.png" width="140" height="140" alt="Ola Diagnostic App Icon" />
</p>

<p align="center">
  <b>Advanced Automotive CAN Telemetry & Diagnostic Suite for Ola Electric S1 / S1 Pro / S1 Air</b><br>
  Built with Kotlin & Jetpack Compose • Experimental MoveOS diagnostics over Wi-Fi
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android_8.0+-3DDC84?style=flat&logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/Architecture-Kotlin_%7C_Compose-7F52FF?style=flat&logo=kotlin&logoColor=white" alt="Kotlin" />
</p>

---

## ⚡ Highlights

- **🔋 Live 14S NMC Cell Balancer:** High-resolution monitoring of all 14 individual series cell voltages with automated cell delta drift detection and min/max cell identification.
- **🌡️ Comprehensive Thermals:** Real-time monitoring of Motor Stator, MCU Inverter, BMS AFE, PDU, and battery pack probe thermals with MoveOS derating threshold alerts.
- **📄 Diagnostic PDF Reports:** Generates diagnostic summaries and battery-health estimates on your device.
- **📡 Compressed GZIP CAN Logger:** Live recording of raw `vcan0` SocketCAN frames into streaming compressed `.csv.gz` files (~85% space savings) compatible with SavvyCAN and Pandas.

---

## Build from source

From the Android project directory, run the Gradle wrapper with Android SDK 35 and JDK 17 installed.

```bash
cd OlaCanApp
./gradlew :app:assembleRelease
```

Sign the generated release APK with your own release key before distribution.

Public release builds support telemetry, CAN logs, and reports. Live vehicle controls are disabled. Compatibility depends on the scooter model and firmware. The app's current firmware check starts at MoveOS 4.0.5. Hardware support needs verification per model and firmware.

---

## 🚀 Getting Started

1. **Host Connection:** Turn on your phone's **Personal Hotspot**.
2. **Scooter Wi-Fi:** On the scooter display, open **Settings > Wi-Fi** and connect to your phone's hotspot.
3. **Wake the scooter while parked:** Use the normal dashboard controls.
4. **Launch Ola Diagnostic:** Open the app and tap **Auto-Scan for Scooter** (or enter the IP). Tap **Connect** to start streaming live telemetry!

---

## 📊 App Screenshots & Dashboard Tabs

| Overview Dashboard | 14S Battery Balancer | Safety & Self-Tests |
| :---: | :---: | :---: |
| Real-time SOC, SOH, Volts, Amps, Power, Speed, and Odo | 14 Series Cell Voltages with High/Low drift tracking | Guided checks and observed CAN alerts |

---

## 🛠️ Repository Architecture

- **[`OlaCanApp/`](OlaCanApp/)**: Primary Kotlin & Jetpack Compose Android application.
  - `service/CanStreamManager.kt`: Connection management, CAN frame dispatch, and S1 Mods lock/wake logic.
  - `decoder/OlaCanDecoder.kt`: Automotive DBC little-endian bit-unpacking engine.
  - `analyzer/`: Real-time battery SOH estimation and vehicle automated self-test suite.
  - `ui/screens/`: Modern Material Design 3 dark-themed dashboards.
  - `util/PdfReportGenerator.kt`: PDF report generation for diagnostic evaluations.

---

## ⚖️ License & Disclaimer

This project is intended for diagnostic, research, and educational purposes. Ola, MoveOS, and Ola Electric are trademarks of Ola Electric Mobility Pvt Ltd. This independent project is not officially affiliated with or endorsed by Ola Electric.
