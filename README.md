# MyPassMan

**MyPassMan** is a modern, secure, and user-friendly Android password manager. Built with a
**Local-First** approach, it ensures your sensitive data always remains strictly under your control.

---

## Features

* **Access Protection**:
    * **PIN Code** authentication supporting variable lengths (4, 5, or 6 digits).
    * Hidden PIN length on the unlock screen to prevent shoulder surfing.
    * Brute-force protection with delay timeouts after failed attempts.
    * Fast and secure **Biometric** authentication (fingerprint / face unlock) using Android
      KeyStore.

* **Password & Credential Management**:
    * Store titles, logins, passwords, notes, and tags.
    * Search and sorting (alphabetically, by creation or modification date).
    * Secure clipboard copy functionality.

* **Tag Categorization**:
    * Create and manage tags to organize entries into custom categories.
    * Quickly filter your vault by selected tags.

* **Backup & Export**:
    * Export and import encrypted local backup files (`.pman`).
    * Strong encryption using **AES-256-GCM** with PBKDF2 key derivation (10,000 iterations).

* **Cloud Sync**:
    * Cloud integration with **Google Drive** and **Yandex Disk**.
    * Automatic background synchronization via **WorkManager**.
    * Secure OAuth 2.0 authentication flows.

* **Modern Interface**:
    * Built entirely with **Jetpack Compose** and **Material 3**.
    * Full support for Light, Dark, and System Default themes.
    * Smooth transition animations using `SharedTransitionLayout`.

---

## Tech Stack

| Component                | Technologies                                                 |
|--------------------------|--------------------------------------------------------------|
| **Language**             | Kotlin 2.x                                                   |
| **UI Framework**         | Jetpack Compose, Material 3, Navigation3                     |
| **Architecture**         | MVVM + Unidirectional Data Flow (StateFlow, SharedFlow)      |
| **Dependency Injection** | Dagger Hilt                                                  |
| **Database & Storage**   | Room ORM, DataStore Preferences                              |
| **Security**             | BiometricPrompt, Android KeyStore, AES-256-GCM, PBKDF2       |
| **Networking & Cloud**   | Retrofit 2, OkHttp 4, Google Drive API, Yandex Disk REST API |
| **Background Work**      | WorkManager (Hilt integrated)                                |
| **Build & Linting**      | Gradle (Kotlin DSL), KSP, Ktlint                             |

---

## Project Structure

```
my.passman/
├── data/           # Room entities, DAOs, Database, SettingsRepository
├── di/             # Hilt modules (DatabaseModule, DataModule, SyncModule, YandexModule)
├── sync/           # Cloud sync implementation (Google Drive, Yandex Disk, WorkManager)
│   ├── google/
│   └── yandex/
├── ui/             # Compose screens, ViewModels, navigation, theme
│   ├── screens/
│   │   ├── edit/     # Create / Edit record screen
│   │   ├── list/     # Main vault list screen
│   │   ├── pin/      # PIN entry & setup screen
│   │   ├── settings/ # App settings & cloud sync screen
│   │   └── tags/     # Tag management screen
│   └── theme/
└── util/           # Encryption, backups, clipboard, and biometric utilities
```

---

## Security

1. **Data Privacy**: All vault credentials are stored locally in the application's encrypted
   database.
2. **Encrypted Backups**: Backup files are encrypted using AES-256-GCM and salted PBKDF2 key
   derivation from a user master password.
3. **Biometrics**: Cryptographic keys for biometric unlock are safely held inside hardware-backed
   Android KeyStore.
