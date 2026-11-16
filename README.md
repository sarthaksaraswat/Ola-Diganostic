<p align="center">
  <img src="ola_diagnostic_icon.png" width="130" height="130" alt="Ola Diagnostic Icon" />
</p>

<h1 align="center">Ola Diagnostic</h1>

<p align="center">
  <b>Advanced Automotive CAN Telemetry & Diagnostic Suite for Ola Electric S1 / S1 Pro / S1 Air</b><br>
  Built with Kotlin & Jetpack Compose • Direct MoveOS Diagnostics over Local Wi-Fi
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Version-v2.0.2-brightgreen?style=flat" alt="Version" />
  <img src="https://img.shields.io/badge/Platform-Android_8.0+-3DDC84?style=flat&logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/Architecture-Kotlin_%7C_Compose-7F52FF?style=flat&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Status-Production_Ready-success?style=flat" alt="Status" />
</p>

---

### 📲 Latest Release Download

👉 **[Download Ola Diagnostic v2.0.2 APK (Signed Release)](OlaDiagnostic-v2.0.2.apk)**

*Signed for Android 8.0+ (ARM64 / x86_64). Ready for direct phone installation or sideloading via ADB.*

```bash
adb install -r OlaDiagnostic-v2.0.2.apk
```

---

> [!CAUTION]
> **Warranty & Safety Disclaimer:**
> This project is an independent open-source tool and is **NOT affiliated with, endorsed by, or sponsored by Ola Electric Mobility Pvt Ltd**.
> Interacting with MoveOS diagnostic interfaces, unlatching safety registers, or sending custom frames on vehicle CAN buses carries inherent risks. Improper use **may void your scooter's manufacturer warranty**, affect MoveOS software support, or alter vehicle behavior. The software is provided "as is" strictly for diagnostic, research, and educational purposes.

---

## 📸 Application Screenshots

| ⚡ Live Telemetry Cockpit | 🔋 14S Battery & SOH Engine |
| :---: | :---: |
| <img src="screenshots/01-cockpit-telemetry.png" width="300" alt="Telemetry Cockpit" /> | <img src="screenshots/02-battery-health-probes.png" width="300" alt="14S Battery Health & Probes" /> |

| 🏍️ Motor & Inverter Thermals | 🛡️ Diagnostic Self-Test Suite |
| :---: | :---: |
| <img src="screenshots/03-motor-powertrain-thermals.png" width="300" alt="Motor Powertrain & Thermals" /> | <img src="screenshots/04-vehicle-self-test.png" width="300" alt="Vehicle Diagnostic Self-Test" /> |

---

## ⚡ Core Capabilities & Features

### 1. ⚡ Live Telemetry Cockpit
- **Real-Time Speed & Stator RPM:** Precision vehicle speed (km/h) and motor rotor angular velocity.
- **Pack Voltage & Current:** Live high-voltage bus readings (V) and continuous draw (A) across acceleration, cruising, and regen.
- **State of Charge (SOC) & Power (kW):** Instant battery percentage, active kilowatt draw, and lifetime odometer (km).
- **Cell Delta Spread:** Live millivolt difference between the highest and lowest NMC cells in real time.

### 2. 🔋 14S NMC Battery Balancer & Health Engine
- **14 Individual Cell Voltages:** Real-time channel 1 through 14 voltages mapped from CAN IDs `0x1A5`, `0x1A6`, `0x1A9`, and `0x1AA`.
- **Rest vs Under-Load Sag Analysis:** Dynamic comparison between zero-load resting OCV and peak loaded acceleration (up to 118A+ WOT).
- **6 Pack Temperature Sensors:** Multi-probe thermal monitoring (Front, Left, Right, Rear, Core, and BMS AFE).
- **Coulomb Integrator SOH:** Coulomb-counting battery state-of-health estimation comparing actual Ah throughput against nominal 72 Ah factory capacity.

### 3. 🏍️ Motor & Powertrain Thermals
- **Motor Stator & MCU Inverter Temps:** Real-time heat tracking with MoveOS thermal protection thresholds:
  - *Normal (< 85°C)* • *Warning (95°C / 147°C)* • *Derate (105°C / 157°C)* • *Emergency Cutoff (115°C / 165°C)*
- **Throttle Sensor Calibration:** Live throttle potentiometer voltages (Reverse Regen 0.55V, Neutral 0.95V, WOT 3.85V).
- **Wheel Slip Ratio:** Real-time traction calculation comparing front wheel Hall sensor vs motor rotor resolver across 5.66 gear ratio.

### 4. 🔧 Live Vehicle Controls (Local Wi-Fi / Hotspot)
- **Instant Lock & Unlock:** Dispatches MoveOS MasterCommander frames (`0x6A1` & `0x6BC`) combined with power-management key events for instant physical locking and display sleep/wake.
- **Trunk / Boot Latch Release:** One-tap boot solenoid unlatch.
- **Display Wake & Panel Refresh:** Wake MoveOS touchscreen and refresh stuck HMI instances.
- **AFE Fault Register & Coulomb Unlatch:** Transmits `0x6DA` unlatch pulse to clear locked BMS AFE fault registers.
- **Dynamic Regen Level:** Configures deceleration regenerative braking drag levels (0% to 15%) via CAN `0x115`.

### 5. 🛡️ Safety & Automated Self-Test Suite
- **Guided Component Self-Tests:** Step-by-step diagnostic verification for Left/Right brake sensors, horn chime, indicators, exterior lighting, HV contactors, and e-SIM/GPS modules.
- **Safety Flags & DTC Catalog:** Live monitoring of kickstand interlock, fall/crash detection, hill hold, and diagnostic trouble codes.

### 6. 📡 High-Speed CAN Stream & Compressed Logger
- **Streaming SocketCAN:** Real-time frame parsing from `vcan0` via MoveOS gRPC channel on port `50005`.
- **Ultra-Compact GZIP Logging:** Memory-buffered `.csv.gz` streaming with **~85% space savings** (exports directly to phone `Downloads/OlaCAN/`).
- **PDF Report Generation:** One-tap export of diagnostic inspection certificates and battery health audits.

---

## 🚀 Quick Setup Guide

1. **Host Connection:** Turn on **Personal Hotspot** on your Android phone.
2. **Connect Scooter:** On the scooter touchscreen, navigate to **Settings > Wi-Fi** and connect to your phone's hotspot.
3. **Keep Scooter Awake:** Ensure the scooter **side stand is UP** (lowering the stand triggers MoveOS sleep mode).
4. **Launch Ola Diagnostic:** Open the app and tap **Auto-Scan for Scooter** (or enter the IP). Tap **Connect** to start streaming live telemetry!

---

## 🛠️ Repository Structure

```
├── OlaDiagnostic-v2.0.2.apk   # Compiled, signed release binary ready for installation
├── screenshots/               # UI captures showcasing app features
├── ola_diagnostic_icon.png    # Official app branding icon
├── OlaCanApp/                 # Complete Kotlin & Jetpack Compose Android Studio project
│   ├── app/
│   │   ├── src/main/java/com/sarthak/olacan/
│   │   │   ├── analyzer/      # SOH estimation & vehicle self-test suite
│   │   │   ├── decoder/       # CAN bit-unpacking DBC engine
│   │   │   ├── network/       # Multicast discovery & gRPC streaming client
│   │   │   ├── service/       # Background CAN streaming & vehicle controls
│   │   │   └── ui/            # Modern Material Design 3 screens & dialogs
│   │   └── build.gradle.kts
│   └── build.gradle.kts
└── README.md
```

---

## 🔨 Building from Source

To build from source using the Gradle wrapper (requires Android SDK 35 & JDK 17):

```bash
cd OlaCanApp
./gradlew :app:assembleRelease
```

---

## ⚖️ License

This project is open-source for educational, research, and non-commercial automotive diagnostic purposes. All trademarks, service marks, and company names are the property of their respective owners.
