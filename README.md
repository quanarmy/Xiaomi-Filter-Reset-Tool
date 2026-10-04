# Xiaomi Air Purifier NFC Filter Reset Tool 🍃

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-brightgreen.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-purple.svg)](https://kotlinlang.org)
[![Release](https://img.shields.io/badge/Release-v1.0.0-orange.svg)](release/XiaomiFilterReset-v1.0.0.apk)

> A full-featured, open-source Android application for reading, writing, unlocking, and resetting RFID/NFC filter cartridges across all Xiaomi and Mijia Air Purifier models.

**English** | [Tiếng Việt](README.vi.md)

---

## 📥 Download Initial Release (APK)

Ready-to-install Android APK package is available in the repository:

👉 **[Download XiaomiFilterReset-v1.0.0.apk](release/XiaomiFilterReset-v1.0.0.apk)** *(~6.6 MB)*

*Or download from the [GitHub Releases](https://github.com/quanarmy/Xiaomi-Filter-Reset-Tool/releases) page.*

---

## 📖 Background & Problem Statement

Xiaomi air purifiers track filter usage by counting operating hours and seconds into an embedded NFC RFID chip (NXP NTAG213 / NTAG215 / NTAG216) located on the filter cartridge base. Once the timer reaches roughly 3,000 to 4,000 operational hours (6 to 12 months), the machine permanently locks the filter status at `0% Remaining Life` and triggers persistent replacement warnings—even if the physical HEPA filter element has been cleaned, vacuumed, or replaced with a 3rd-party/OEM cartridge.

The RFID chip is protected using NTAG password authentication (`PWD_AUTH`, command `0x1B`). This application implements the reverse-engineered SHA-1 key derivation algorithm (based on research from [Unethical Info: Hacking My Air Purifier](https://unethical.info/2024/01/24/hacking-my-air-purifier/)), derives the 4-byte protection password directly from the tag's unique 7-byte serial number (UID), unlocks the chip, and resets the runtime block back to 100%.

---

## ⚡ Key Features

### 1. ⚡ One-Tap Scan & Instant Reset
- **Automatic NFC Detection:** Hold the upper back of your Android phone near the filter RFID chip to immediately capture ISO 14443-3A (`NfcA`) frames.
- **On-the-Fly Password Derivation:** Instantly computes the SHA-1 derived 4-byte password from the tag's 7-byte UID.
- **`PWD_AUTH` Authentication:** Transceives command `0x1B` to gain authenticated read/write access.
- **Complete Tag Telemetry:**
  - Tag UID (7-byte serial)
  - Calculated Password (PWD) & PACK
  - Remaining Life Percentage with colored progress bar
  - Detected Filter Type (Standard HEPA, Anti-Formaldehyde, Anti-Bacterial)
  - Block 08 Work Time Counter (elapsed runtime)
  - Block 04 (Factory ID) & Block 05 (Product ID)
- **Instant 100% Reset:** Writes `00 00 00 00` to Block 08 and verifies the write.
- **Auto-Reset Switch:** When enabled, simply tapping any filter tag immediately resets it to 100% hands-free.
- **Haptic Feedback:** Distinct vibration patterns confirm successful operations or alert on failures.

### 2. 🎛️ Custom Filter Life / Mileage Slider
- Need to set filter life to a specific percentage (e.g., 80%, 50%, 35%) after partial vacuuming?
- Interactive percentage slider automatically converts desired remaining life into the corresponding 4-byte little-endian hexadecimal counter.
- Supports entering custom raw 4-byte hexadecimal payloads.

### 3. 🏷️ Blank NTAG OEM Cloner (For 3rd-Party Filters)
- Purchased affordable aftermarket/OEM replacement filters without RFID chips?
- Attach any standard, low-cost blank NTAG213, NTAG215, or NTAG216 sticker to the filter base.
- Select desired filter model:
  - **Standard HEPA (Gray / Blue)** — Product ID `00003033`
  - **Anti-Formaldehyde (Green)** — Product ID `00003031`
  - **Anti-Bacterial (Purple)** — Product ID `00004130`
- The app programs Factory ID (Block 04), Product ID (Block 05), Manufacturing Time (Block 06), Serial (Block 07), zeroes Block 08, calculates authentication keys, and locks the tag with `AUTH0 = 04`. The purifier recognizes it as a genuine, brand-new filter!

### 4. 🧮 UID Keygen & Multi-Tool APDU Generator
Generate commands for other NFC hardware tools:
- Supports 14-hex character UIDs (with or without colons: `04:A0:3C:AA:1E:70:80` or `04A03CAA1E7080`).
- Generates 1-click copy commands ready for:
  - **NFC Tools (Android / iOS):** `1B<PWD>,3008,A20800000000`
  - **Flipper Zero CLI:** `nfc apdu 1B<PWD> 3008 A20800000000 3008`
  - **Proxmark3:** `hf 14a raw -c 1B<PWD> ; hf 14a raw -c 3008 ; hf 14a raw -c A20800000000`
  - **Chameleon Ultra:** `hw raw 1B<PWD> ; hw raw 3008 ; hw raw A20800000000`

### 5. 📱 Device Manager & Historical Audit Trail
- **Saved Devices Catalog:**
  - Automatically identifies and saves scanned filters.
  - Custom device naming (e.g., *"Living Room Purifier"*, *"Bedroom 4 Pro"*).
  - Statistics: Total reset count, current life %, and last reset timestamp.
  - **Targeted Re-Reset Button:** 1-Click primes the scanner for that specific device UID only, preventing accidental rewrites of other filters in multi-unit households.
- **Change Log Audit Trail:**
  - Full chronological timeline recording before/after states (`0% ➔ 100%`, `362AD001 ➔ 00000000`).
  - Filter logs by individual device.
  - **1-Click Restore State:** Roll back any tag to a prior recorded state.

---

## 📡 NFC Antenna Placement Guide

| Model Family | NFC Tag Placement on Filter | Scanning Tip |
| :--- | :--- | :--- |
| **Xiaomi 4 Pro, 4, 4 Lite, 4 Compact** | **Circular bottom base** | Touch top-back of phone flat against bottom center |
| **Xiaomi 3, 3H, 3C, 2, 2S, 2H, Pro, MAX** | **Circular bottom base** | Touch top-back of phone flat against bottom center |
| **Xiaomi Smart Air Purifier Elite** | **Top plastic rim** *(under reminder text)* | Hold phone against the top outer collar |
| **Smartmi / Mijia Series** | **Circular bottom base** | Touch top-back of phone flat against bottom center |

---

## 🔬 Reverse Engineering & Cryptographic Breakdown

### 1. Tag Memory Layout (NTAG213/215/216)

| Page / Block | Bytes | Field Description | Example Value |
| :---: | :---: | :--- | :--- |
| `00 - 02` | 7 bytes | Tag Serial Number (UID) + Check Bytes | `04 A0 3C AA 1E 70 80` |
| `04` | 4 bytes | Factory Identifier | `00 00 41 50` |
| `05` | 4 bytes | Filter Product ID | `00 00 30 33` *(HEPA)* |
| `06` | 4 bytes | Manufacturing Date Stamp | `00 20 04 22` |
| `07` | 4 bytes | Internal Serial Number | `C7 83 D7 B7` |
| **`08`** | **4 bytes** | **Work Time Counter (Elapsed Time)** | **`00 00 00 00` (100% New)** |
| `E3` (NTAG213) | 4 bytes | `AUTH0` protection start page | `04 00 00 00` |
| `E5` (NTAG213) | 4 bytes | 32-bit Protection Password (`PWD`) | Derived from UID |
| `E6` (NTAG213) | 2 bytes | 16-bit Password Acknowledge (`PACK`) | `80 80` |

### 2. SHA-1 Key Derivation Algorithm

Given a 7-byte UID $U = [u_0, u_1, u_2, u_3, u_4, u_5, u_6]$:

1. Compute SHA-1 hash over the raw 7 UID bytes:
   $$H = \text{SHA-1}(U) \quad (20 \text{ bytes: } h_0, h_1, \dots, h_{19})$$
2. Extract seed value from the first byte of the hash:
   $$S = h_0 \pmod{256}$$
3. Calculate 4 index positions:
   $$i_1 = S \pmod{20}$$
   $$i_2 = (S + 5) \pmod{20}$$
   $$i_3 = (S + 13) \pmod{20}$$
   $$i_4 = (S + 17) \pmod{20}$$
4. Construct the 4-byte password:
   $$\text{PWD} = [h_{i_1},\; h_{i_2},\; h_{i_3},\; h_{i_4}]$$

#### Test Vector:
- **UID:** `04 A0 3C AA 1E 70 80`
- **SHA-1:** `BC AF 80 63 33 CC F7 20 CD 44 1A 16 7F 91 4F BE 6E A4 A5 13`
- **Seed:** $0\text{xBC} = 188$
  - $i_1 = 188 \pmod{20} = 8 \implies h_8 = 0\text{xCD}$
  - $i_2 = 193 \pmod{20} = 13 \implies h_{13} = 0\text{x91}$
  - $i_3 = 201 \pmod{20} = 1 \implies h_1 = 0\text{xAF}$
  - $i_4 = 205 \pmod{20} = 5 \implies h_5 = 0\text{xCC}$
- **Derived Password:** `CD 91 AF CC`

---

## 🛠️ Building from Source

### Prerequisites
- Android Studio Iguana / Jellyfish / Ladybug or newer
- JDK 17
- Android SDK 34
- Device with NFC hardware running Android 8.0+ (API 26+)

### Build Steps
```bash
# Clone the repository
git clone git@github.com:quanarmy/Xiaomi-Filter-Reset-Tool.git
cd Xiaomi-Filter-Reset-Tool

# Build Debug APK
./gradlew assembleDebug

# Output APK path:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 🤝 Contributing

Contributions are warmly welcomed! Please read our [CONTRIBUTING.md](CONTRIBUTING.md) guide before submitting pull requests.

---

## 📄 License

This project is licensed under the terms of the [MIT License](LICENSE).

---

## ⚠️ Disclaimer

This tool is created for educational, research, and right-to-repair purposes to extend the usable lifespan of clean air filter cartridges and reduce unnecessary e-waste. Always replace heavily soiled or damaged filters when air quality begins to degrade.
