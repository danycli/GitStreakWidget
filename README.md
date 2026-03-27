<div align="center">

# GitStreakWidget

**Keep your GitHub contribution momentum visible — right from your home screen.**

![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Release](https://img.shields.io/badge/Release-v1.0-161B22?style=for-the-badge&logo=github&logoColor=white)
---

> A sleek, Android-native widget for developers who want to track daily GitHub contribution streaks —  
> built with a **GitHub Dark Mode** inspired interface to keep you motivated and consistent.

<!-- Replace with an actual home screen screenshot -->
<img width="270" height="540" alt="Screenshot_20260327-234225" src="https://github.com/user-attachments/assets/61404609-6b85-4eb8-a2bf-4b1e7c92a37b" />

</div>

---

## Table of Contents

- [Features](#features)
- [Installation](#installation)
- [How to Use](#how-to-use)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Contributing](#contributing)

---

## Features

GitStreakWidget is purpose-built for developers who care about consistency. Every feature is designed to be lightweight, fast, and unobtrusive.

| Feature | Description |
|---|---|
| **Real-time Tracking** | Fetches your current contribution streak directly from the GitHub Contributions API |
| **Native Widget** | A 3×2 home screen widget that refreshes automatically in the background |
| **Modern UI** | A clean dashboard built with Material 3 and Jetpack Compose |
| **Frictionless Setup** | Only your GitHub username is required — no OAuth tokens or API keys |
| **Lightweight** | Engineered for minimal battery and data consumption |

---

## Installation

GitStreakWidget is distributed as a standalone APK during its independent release phase. Follow the steps below to install it on any Android device.

> **Note:** Since the app is self-signed and distributed outside the Play Store, you may encounter security prompts during installation. These are expected and safe to proceed through.

**Step 1 — Download**

Navigate to the [Releases](https://github.com/danycli/GitStreakWidget/releases) page and download the latest `GitStreak-v1.0.apk`.

**Step 2 — Allow Unknown Sources**

On your Android device, open the downloaded file. If prompted, grant permission to **Install from Unknown Sources** via your browser or file manager settings.

**Step 3 — Install**

Tap **Install**. If Google Play Protect displays a warning, select **Install Anyway** — this warning appears because the app is self-signed, not because it is harmful.

**Step 4 — Launch**

Once installed, open GitStreak from your app drawer and follow the [setup steps below](#how-to-use).

---

## How to Use

Getting started takes under a minute. The app guides you through three simple steps.

### Step 1 — Login

Open the app and enter your GitHub username in the input field. No passwords or tokens are needed.

```
GitHub Username  →  [ yourusername ]  →  [ Verify ]
```

<!-- Replace with actual screenshot -->
<div align="center">
<img width="270" height="540" alt="Screenshot_20260327-235144" src="https://github.com/user-attachments/assets/1c208328-298a-45d7-be90-de892dd81d6c" />
</div>


---

### Step 2 — Dashboard

After verification, your current streak and contribution summary are displayed on the main dashboard.

<!-- Replace with actual screenshot -->
<div align="center">
<img width="270" height="540" alt="Screenshot_20260327-234225" src="https://github.com/user-attachments/assets/61404609-6b85-4eb8-a2bf-4b1e7c92a37b" />
</div>

---

### Step 3 — Add the Widget

Place the widget on your home screen using the standard Android process:

```
Long-press Home Screen  →  Select "Widgets"  →  Find "GitStreak"  →  Drag to Screen
```

<!-- Replace with actual screenshot -->
<div align="center">
<img width="270" height="540" alt="Screenshot_20260327-235505" src="https://github.com/user-attachments/assets/b0c4a095-05d4-419f-8c86-e3b912a02c86" />
</div>

---

## Tech Stack

| Layer | Technology |
|---|---|
| **Language** | Kotlin |
| **Main App UI** | Jetpack Compose + Material 3 |
| **Widget Layer** | Jetpack Glance |
| **Networking** | GitHub Contributions API |
| **Concurrency** | Kotlin Coroutines |
| **Architecture** | Clean Architecture (MVVM) |

---

## Architecture

GitStreakWidget follows **Clean Architecture** principles, separating data, domain, and presentation concerns to ensure the codebase stays maintainable and testable.

```
app/
├── data/
│   ├── remote/          # GitHub API client & response models
│   └── repository/      # Repository implementations
├── domain/
│   ├── model/           # Core business models (Streak, Contribution)
│   └── usecase/         # Application logic (FetchStreakUseCase)
├── presentation/
│   ├── ui/              # Jetpack Compose screens & components
│   └── viewmodel/       # State holders & UI logic
└── widget/
    └── glance/          # Jetpack Glance widget definitions
```

Asynchronous networking is handled entirely with **Kotlin Coroutines**, keeping the main thread free and the UI responsive.

---

## Contributing

Contributions are welcome — whether it's a new widget style, improved data visualization, or a bug fix.

**Getting Started**

```bash
# 1. Fork the repository and clone your fork
git clone https://github.com/your-username/GitStreakWidget.git

# 2. Create a feature branch
git checkout -b feature/your-feature-name

# 3. Make your changes and commit with a clear message
git commit -m "feat: add weekly heatmap widget style"

# 4. Push your branch
git push origin feature/your-feature-name

# 5. Open a Pull Request on GitHub
```

**Guidelines**

- Keep commits atomic and well-described.
- Follow existing code style and architecture patterns.
- Add comments where behavior may not be immediately obvious.
- Test your changes on a physical device or emulator before opening a PR.

---

<div align="center">

Built for developers, by a developer.  
If you find GitStreakWidget useful, consider leaving a star on the repository.

![GitHub Stars](https://img.shields.io/github/stars/danycli/GitStreakWidget?style=for-the-badge&logo=github&logoColor=white&labelColor=161B22&color=238636)

</div>
