# Field Inspections — Couchbase Mobile (Android, cloud-edge sync)

## (a) What this is

An always-on Android app (Kotlin + Jetpack Compose) for **field inspections**. Each device keeps a local
**Couchbase Lite 4.1 (Enterprise Edition)** database and syncs both ways with **Capella App Services** whenever it's online.
It keeps working offline and catches up when the connection comes back.

**The people involved:**
- **App users:** field **inspectors** (e.g. `bob`). Each one signs in and works only on the inspections assigned to them.
- **Manager (admin role):** the **inspection manager** (`manager`). The manager creates inspections, assigns them, and sees everything.
- **Records:** **inspections** (title, site, status, notes, assigned inspector, optional checklist).
- **Reference data:** **checklists**. The manager writes them and every inspector can read them.
- **Flow:** the manager creates an inspection and assigns it to `bob` → only `bob`'s device receives it → `bob` updates the status or notes → the manager sees the change live.

**Access patterns** (enforced server-side by the Access Control Functions in `sync-functions/`):

| Collection (`fieldops` scope) | Pattern | Read | Create | Update |
|---|---|---|---|---|
| `inspections` | **Admin-Assigns** | the assigned inspector (own only) + manager (all) | manager only | assigned inspector + manager. Only the manager can reassign, edit a `closed` inspection or delete |
| `checklists` | **Shared Read-Only** (add-on) | everyone (public `!` channel) | manager only | manager only |

Project layout (all in this folder):

```
InspectionsApp/
├── settings.gradle.kts, build.gradle.kts, gradle.properties, gradlew, gradlew.bat, gradle/wrapper/
├── local.properties            ← cbl.endpointUrl (setup-capella.sh writes the real URL here)
├── .run/app.run.xml            ← ready-made "app" run configuration
├── app/                        ← the Android app module
├── sync-functions/             ← inspections-sync-function.js, checklists-sync-function.js
├── setup-capella.sh            ← provisions the Capella backend (you run it)
├── teardown-capella.sh         ← deletes ONLY what setup created (only if you ask for it)
├── provision.env               ← settings for the scripts (add your API key here)
└── README.md
```

Toolchain pins: CBL `com.couchbase.lite:couchbase-lite-android-ee-ktx:4.1.0` (from `https://mobile.maven.couchbase.com/maven2/dev/`),
AGP 8.7.3, Gradle 8.9 (wrapper), Kotlin 2.0.21, compileSdk/targetSdk 35, minSdk 24.

> Enterprise Edition is free for development and testing. Production deployment needs a Couchbase Enterprise license or subscription.

---

## (b) Backend setup — Capella App Services (you run this, ~20–45 min)

> ⚠️ This provisions a **free-tier** Capella backend: 1 node, and it turns off after 72 h of inactivity. Use it for development and testing only.
> Capella allows **one free-tier cluster per organization**. If you already have one, the script asks before reusing it.

**Prerequisites:** a free Capella account (https://cloud.couchbase.com) and a **bash** shell with `curl` and `jq`.
**On Windows 11** the script does **not** run in `cmd` or PowerShell. Use one of these instead:
- **Git Bash** (installed with Git for Windows). Install jq with `winget install jqlang.jq`, then reopen Git Bash.
- **WSL** (Ubuntu): `sudo apt install jq`. You can reach this folder under `/mnt/c/...`.

1. **Create a Capella API key** in the Capella web console:
   - Select your **Organization**, then **Settings → API Keys → Generate Key**
   - Enter a **Key Name**
   - Under **Organization Roles**, check **Organization Owner**. Leave the other roles, the 180-day expiration and Allowed IP Addresses at their defaults.
   - Click **Generate**, then **copy or download the key**. It's shown only once, so download it to be safe.
2. **Open `provision.env`** in this folder:
   - Paste the **`APIKeyToken`** value from the downloaded key file into `CB_API_KEY=''`. The Capella UI calls this value the **API Secret**.
   - Leave `APPSVC_ADMIN_PASS` blank (recommended). The script generates a strong password, saves it back into this file and never prints it.
   - `MANAGER_PASS` / `BOB_PASS` default to `Password1!`. To use different passwords, edit those two lines in this file.
   - The app values are already filled in: `CB_ENDPOINT_NAME='fieldinspections'`, `APPSVC_ADMIN_USER='fieldinspectionsadmin'`,
     `COLLECTIONS='fieldops/inspections fieldops/checklists'`, `SYNC_FUNCTIONS_DIR='sync-functions'`.
   - Never commit `provision.env`, because it holds your API key.
3. **Run the script from this folder** (in Git Bash or WSL):
   ```bash
   cd "/c/path/to/InspectionsApp"     # Git Bash form; WSL: cd "/mnt/c/path/to/InspectionsApp"
   source provision.env && ./setup-capella.sh
   ```
   The script does the following, in order:
   - finds or creates the project, the free-tier cluster, bucket `mobileapp`, scope `fieldops` and collections `inspections` and `checklists`
   - creates the App Service and the App Endpoint `fieldinspections`
   - uploads both Access Control Functions
   - creates the Admin Credential and opens the allowed CIDR
   - brings the endpoint **Online**
   - creates the `admin` App Role and the App Users `manager` (admin role) and `bob`
   - writes the `wss://…:4984/fieldinspections` URL into **`local.properties`** (`cbl.endpointUrl`)

   It's safe to re-run.
4. **Re-sync Gradle** in Android Studio when it finishes (**File → Sync Project with Gradle Files**). This rebuilds `BuildConfig` with the real URL.

If the script didn't find `local.properties`, copy the WSS URL from its output, or from Capella → App Services → the endpoint → **Connect**.
Then set it in `local.properties` as `cbl.endpointUrl=wss://…` and re-sync Gradle.

**Seed data:** none was created. The backend starts empty. Sign in as `manager` and create a checklist and an inspection from the app.

---

## (c) Run the app (Android Studio)

1. **Open the ROOT folder:** **File → Open →** select `InspectionsApp` (the folder that contains `settings.gradle.kts`). Don't open a subfolder or a single file.
2. **Android SDK:** if Android Studio says *"Select SDKs — provide the path"*, the SDK isn't installed yet. Install it through Android Studio:
   - Open **SDK Manager** (Welcome screen → *More Actions → SDK Manager*, or *Settings → Languages & Frameworks → Android SDK*)
   - Under **SDK Platforms**, tick **Android 15 (API 35)**
   - Under **SDK Tools**, tick **Build-Tools**, **Platform-Tools** and **Emulator**
   - Click **Apply**. On Windows the default location is `%LOCALAPPDATA%\Android\Sdk`.
3. **Gradle JDK must be 17–22.** This project pins Gradle 8.9, which can't run on JDK 23 or newer. Your embedded **JBR 21** is fine. If the setting gets changed, fix it one of these ways:
   - If a **"Please Select Gradle JVM to Import Project"** dialog appears, click **"Use JVM `<N>`"**.
   - If a sync error appears, click **"Apply compatible Gradle JDK configuration and sync"**.
   - To set it manually: **File → Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK**. Pick the embedded **JetBrains Runtime (`jbr-21`)** or `GRADLE_LOCAL_JAVA_HOME`, as long as it resolves to 17–22. Click **Apply / OK**, then **File → Sync Project with Gradle Files**.
4. **Let Gradle sync.** It downloads Couchbase Lite (EE, from the Couchbase Maven repo) and Compose, which takes about 1–2 min.
5. **Pick an emulator** in the device dropdown (your *Pixel 8 API 35* or *Pixel 8 Pro API 35*).
6. **Run:** select the **app** run configuration (shipped in `.run/app.run.xml`) and click **Run ▶**.
   - If the dropdown still shows "Add Configuration", Gradle sync hasn't finished yet.
   - Command-line build alternative: run `gradlew.bat assembleDebug` in Windows cmd/PowerShell, or `./gradlew assembleDebug` in Git Bash or WSL.
7. After `setup-capella.sh` writes `local.properties`, **re-sync Gradle** and Run ▶ again.
   - Until you do, the app shows **"APP_SERVICES_ENDPOINT_URL not set"** under Settings & Diagnostics.
   - The local database still works offline in the meantime.

In-app: the colored cloud icon at the top left shows sync status. The ⚙ icon opens **Settings & Diagnostics**, and it turns red on a sync error. That screen shows:
- the user and role
- sync status and the last error, with hints
- the endpoint URL
- the scope and collections
- the local document count
- the **Always-On Demo (Online/Offline) toggle**, plus Reconnect and Sign Out

---

## (d) Default logins

| User | Password | Role | Sees |
|---|---|---|---|
| `manager` | `Password1!`* | `admin` App Role (inspection manager) | all inspections + all checklists; can create/assign/close/delete |
| `bob` | `Password1!`* | regular App User (inspector) | only inspections assigned to `bob` + all checklists (read-only) |

\* Unless you changed them. In Git Bash or WSL, run `grep -E "MANAGER_PASS|BOB_PASS" provision.env` to see the actual values.
(`fieldinspectionsadmin` is the App Services **Admin Credential**. It's for infrastructure only and can't sign in to the app.)

The app **always opens on the Login screen** on a cold start. Your data stays in the local database, and sync resumes after you sign in.

---

## (e) Two-emulator always-on test

Use **two SEPARATE AVDs** (your Pixel 8 API 35 and Pixel 8 Pro API 35). Don't use a **Duplicate** of one AVD, because a duplicate copies the first one's data.

1. Open **Device Manager** (right-hand tool strip, or **View → Tool Windows → Device Manager**). Click **▶** on both AVDs and wait for both to reach the home screen.
2. In the device dropdown next to Run ▶, click **Select Multiple Devices…**, tick both emulators → **OK**.
3. Click **Run ▶ once**. The app installs and launches on both.
4. Sign in as **`manager`** on emulator 1 and as **`bob`** on emulator 2.
5. **Manager:**
   - Tap the list icon → **+** to create a checklist (e.g. "Fire safety": one item per line).
   - Back on the list, tap **+** to create an inspection assigned to **`bob`**, and pick that checklist.
6. **Bob:**
   - The inspection appears within a second or two, along with the checklist.
   - Open it, set the status to **In progress** and add notes, then **Save**. The manager sees the change live.
7. **Check isolation:** as manager, create an inspection assigned to someone else, e.g. `alice`. It must **not** appear on bob's device.
8. **Always-on:**
   - On bob's device, open **Settings → turn the Online switch off** ("Offline (simulated)").
   - Edit the inspection. It saves locally right away.
   - Turn the switch back on. The pending change syncs to the manager right away.
   - Alternatively, use the emulator's extended controls (`•••`) → Cellular/Wi-Fi off, or Airplane mode.
9. **Locking:** the manager sets an inspection to **Closed**. Bob can still see it but can no longer edit it.

**Troubleshooting:**
- Check **Logcat** filtered by `CouchbaseLite`.
- **401:** the username or password is wrong.
- **404 / stuck on "Connecting":** the URL is wrong or the endpoint is Offline.
- **403 `sg missing channel access`:** the Access Control Function wasn't deployed. Re-run `setup-capella.sh`.
- **"APP_SERVICES_ENDPOINT_URL not set":** run the script, then re-sync Gradle.

---

## Tearing down (only when you want to delete the demo backend)

```bash
source provision.env && ./teardown-capella.sh
```

This deletes only the resources that `setup-capella.sh` created, and it asks for confirmation first.
