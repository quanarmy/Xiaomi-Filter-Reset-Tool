# Contributing to Xiaomi Filter Reset Tool

Thank you for considering contributing to the Xiaomi Filter Reset Tool! Contributions from the open-source community help keep this project maintained, compatible with newly released Xiaomi/Mijia air purifier models, and accessible to everyone.

---

## 🛠️ How to Contribute

### 1. Reporting Issues
- Check the [existing issues](https://github.com/quanarmy/Xiaomi-Filter-Reset-Tool/issues) first to avoid duplicates.
- When opening an issue, please specify:
  - Phone model & Android OS version
  - Air purifier model (e.g., Xiaomi Smart Air Purifier 4 Pro, Mi 3H)
  - Tag type (Original filter tag, OEM replacement, or blank NTAG213/215/216)
  - Log output or error message shown on screen

### 2. Proposing Features or Bug Fixes
- Fork the repository.
- Create a topic branch from `main`: `git checkout -b feature/my-feature` or `git checkout -b fix/issue-description`.
- Commit your changes with clear, descriptive commit messages.
- Ensure the project builds cleanly:
  ```bash
  ./gradlew assembleDebug
  ```
- Push to your fork and submit a Pull Request targeting `main`.

---

## 🏗️ Project Architecture & Stack

- **Platform:** Android (Min SDK 26, Target SDK 34)
- **Language:** Kotlin 1.9.24+
- **Build System:** Gradle 8.14, AGP 8.7.3
- **Architecture:** Clean MVVM with Coroutines
- **NFC Technology:** Android `NfcA` (ISO 14443-3A) communicating directly with NXP NTAG213/215/216 chips using raw transceive commands.
- **Key Derivation:** SHA-1 hash permutation algorithm implemented in [`PasswordCalculator.kt`](app/src/main/java/com/xiaomi/purifier/reset/nfc/PasswordCalculator.kt).

---

## 📜 Code Style Guidelines

- Follow official Kotlin coding conventions.
- Keep UI text localizable in `res/values/strings.xml` (English default) and `res/values-vi/strings.xml` (Vietnamese).
- Ensure all NFC transceive operations handle tag disconnects gracefully and release hardware connections in `finally` blocks.

---

## ⚖️ License
By contributing to this repository, you agree that your contributions will be licensed under the [MIT License](LICENSE).
