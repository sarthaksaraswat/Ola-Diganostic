<p align="center">
  <img src="../ola_diagnostic_icon.png" width="120" height="120" alt="Ola Diagnostic Icon" />
</p>

# 🛵 Ola Diagnostic (OlaCanApp)

A modern Android application built with **Kotlin & Jetpack Compose** to connect to MoveOS on the Ola S1 series (Gen 1 & Gen 2) over local Wi-Fi / Hotspot, stream raw vehicle CAN bus traffic (`vcan0`), display real-time automotive telemetry, execute vehicle controls, and perform comprehensive diagnostic health assessments.

---

> [!CAUTION]
> **Warranty & Disclaimer:**
> This project is an independent tool and is **NOT affiliated with or endorsed by Ola Electric Mobility Pvt Ltd**. Using diagnostic functions or transmitting custom CAN frames may **void your vehicle warranty**. Use responsibly and at your own risk.

---

## ⚡ Quick Start

1. **Install APK or Build:**
   - Install the signed release **`../OlaDiagnostic-v2.0.2.apk`** directly on your device, or download from the **[GitHub Release v2.0.2](https://github.com/sarthaksaraswat/Ola-Diganostic/releases/tag/v2.0.2)**.
   - Or build debug build from source:
     ```bash
     ./gradlew :app:assembleDebug
     ```
2. **Scooter Hotspot Setup:**
   - Turn on **Personal Hotspot** on your phone.
   - On the scooter touchscreen, go to **Settings > Wi-Fi** and connect to your phone's hotspot.
   - Keep the scooter **side stand UP** while testing.
3. **Connect:**
   - Tap **Auto-Scan for Scooter** (or enter the IP).
   - Tap **Connect** to dial MoveOS diagnostics daemon (Port `50005`) and begin live CAN telemetry streaming!

---

## 📊 Categorized Tabs & Features

### 1. ⚡ Cockpit
- **Speedometer & Rotor RPM:** Real-time vehicle speed and motor stator RPM.
- **Pack Voltage & Current:** High-voltage bus level (V) and real-time current draw / charge (A).
- **Instant Power (kW) & Odometer:** Real-time motor power draw and lifetime odometer.
- **Cell Delta Spread (mV):** Imbalance difference between highest and lowest cell voltages.

### 2. 🔋 Battery Diagnosis & Health
- **Individual Cell Voltages:** Live individual cell voltages with min/max indicator badges and balance tracking.
- **Dynamic Rest vs Under-Load Sag:** Differential analysis comparing resting OCV against peak acceleration (>50A).
- **6 Pack Thermal Probes:** Readings across Front, Left, Right, Rear, Core, and BMS AFE probes.
- **Coulomb SOH Estimator:** Usable kWh and health percentage calculation based on discharged ampere-hours.

### 3. 🏍️ Motor & Thermals
- **Stator & MCU Inverter Temps:** Live gauges with MoveOS derate alerts (Normal <85°C, Derate 105°C/157°C, Cutoff 115°C/165°C).
- **Traction & Slip Ratio:** Real-time wheel slip calculation from front wheel Hall sensor vs motor rotor resolver.
- **Throttle Calibration:** Live monitoring of throttle angle (Reverse Regen, Neutral, WOT 100%).

### 4. ⚠️ Faults & Safety
- **Active Safety Flags:** Kickstand sensor, fall/crash alerts, hill hold state, and anti-theft triggers.
- **ECU Firmware Versions:** Readout of VCU, BMS, MCU, and Head Unit software releases.
- **DTC Fault Catalog:** Built-in fault code descriptions and diagnostic insights.

### 5. 🔧 Calibrate & Vehicle Controls
- **Instant Lock & Unlock:** Power management key events paired with CAN `0x6A1`/`0x6BC`.
- **Boot / Seat Release:** Direct trigger for trunk solenoid unlatch.
- **Screen Wake:** Wake and dismiss display lock screen.
- **BMS AFE Fault Unlatch:** Clear persistent AFE register lockouts via CAN `0x6DA`.
- **Dynamic Regen Level:** Adjust regenerative braking deceleration levels via CAN `0x115`.
- **Automated Self-Test:** Step-by-step interactive test sequence for all scooter subsystems.

### 6. 📡 CAN Logs & Diagnostics
- **Live `vcan0` Stream:** Real-time message rate counter (Hz) and raw hexadecimal frame monitor.
- **Compressed GZIP Logger:** Memory-buffered `.csv.gz` recording (~85% space saving) saved to `Downloads/OlaCAN/`.
- **PDF Report Generator:** Export formatted diagnostic reports and SOH certificates directly on the device.

---

## 🛠️ Architecture

```
Phone (Hotspot / Wi-Fi)
   │
   ├─> Auto-Discovery: IPv6 Multicast (ff02::1) / Neighbor Table / SLAAC EUI-64
   │
   └─> gRPC OkHttp Channel (Port 50005)
         │
         ├─> CanDumpStream: Server-streaming RPC pushing 16-byte raw CAN frames
         │     │
         │     └─> OlaCanDecoder: Intel little-endian bit-unpacking (DBC formulas)
         │           │
         │           └─> StateFlow: Reactive UI updates at 60 FPS (Jetpack Compose)
         │
         └─> ExecuteADBCommand: MoveOS ECU query, lock, and diagnostic control calls
```

---

## 👨‍💻 Author & Collaboration

This app was engineered from scratch in **2 days over a weekend**! Contributions, bug reports, and ideas are warmly welcomed — feel free to connect and collaborate.

<p align="left">
  <a href="https://www.linkedin.com/in/sarthaksaraswat/" target="_blank">
    <img src="https://img.shields.io/badge/LinkedIn-Connect-0A66C2?style=for-the-badge&logo=linkedin&logoColor=white" alt="LinkedIn" />
  </a>
  &nbsp;
  <a href="https://buymeacoffee.com/sarthaksaraswat" target="_blank">
    <img src="https://img.shields.io/badge/Buy_Me_A_Coffee-Support-FFDD00?style=for-the-badge&logo=buy-me-a-coffee&logoColor=black" alt="Buy Me a Coffee" />
  </a>
</p>
