# BeAware — Final Implementation Plan

## Context

Screen time awareness app. User selects apps to track → foreground service polls UsageStatsManager every 3s → floating Compose overlay timer appears over tracked apps. Usage sessions are recorded to Room DB. Stats screen shows daily/3-day/weekly/monthly breakdowns.

**Tech Stack:**
- Kotlin, Jetpack Compose + Material 3, Hilt, DataStore, Room, Coroutines + Flow
- Foreground Service + UsageStatsManager (3s polling)
- WindowManager + ComposeView overlay
- Min SDK 26, Target SDK 36, package `com.rudy.beaware`

---

## Project Structure

```
com.rudy.beaware/
├── BeAwareApp.kt                         @HiltAndroidApp
│
├── data/
│   ├── datastore/
│   │   └── PrefsDataStore.kt             DataStore: selected packages, tracking flag
│   ├── local/
│   │   ├── BeAwareDatabase.kt            Room database (entities: UsageSession)
│   │   ├── dao/
│   │   │   └── UsageSessionDao.kt        Insert, query by date range
│   │   └── entity/
│   │       └── UsageSessionEntity.kt     package, startTime, endTime, durationMs, date
│   └── repository/
│       ├── AppRepository.kt              Interface
│       └── AppRepositoryImpl.kt          PackageManager + DataStore + Room
│
├── di/
│   └── AppModule.kt                      Provides DataStore, Room DB, DAOs, binds Repository
│
├── model/
│   ├── AppInfo.kt                        packageName, label, icon, isSelected
│   └── UsageStats.kt                     Aggregated stats model for UI (package, totalMs, sessions)
│
├── service/
│   ├── MonitorService.kt                 Foreground Service: polling + session recording + overlay
│   └── overlay/
│       ├── FloatingTimerManager.kt       WindowManager: add/remove/update ComposeView
│       └── TimerPillContent.kt           @Composable pill UI
│
├── ui/
│   ├── MainActivity.kt                   @AndroidEntryPoint, setContent { NavGraph }
│   ├── navigation/
│   │   └── NavGraph.kt                   Onboarding → Home ↔ Picker, Home → Stats
│   ├── screens/
│   │   ├── onboarding/
│   │   │   ├── OnboardingScreen.kt       Permission grant steps
│   │   │   └── OnboardingViewModel.kt    Permission state checks
│   │   ├── home/
│   │   │   ├── HomeScreen.kt             Toggle + tracked apps list + navigate to Stats
│   │   │   └── HomeViewModel.kt          Service control, tracked apps
│   │   ├── picker/
│   │   │   ├── AppPickerScreen.kt        Search + multi-select installed apps
│   │   │   └── AppPickerViewModel.kt     Load/filter/save
│   │   └── stats/
│   │       ├── StatsScreen.kt            Date range tabs + per-app usage bars
│   │       └── StatsViewModel.kt         Query Room by date range, aggregate
│   └── theme/
│       ├── Theme.kt
│       ├── Color.kt
│       └── Type.kt
│
└── util/
    ├── PermissionHelper.kt               Permission checks
    └── TimeFormatter.kt                  millis → "1h 23m" display strings
```

**Total: ~30 files**

---

## Implementation Phases

### PHASE 1 — Core MVP (Steps 1–30)
Get the app running: permissions → app selection → tracking service → floating overlay.
Session recording to Room is wired in at the end of this phase.

### PHASE 2 — Stats (Steps 31–34)
Build the Stats screen on top of the data Phase 1 is already recording.

---

## PHASE 1: Core MVP

### Step 1: Version Catalog (`gradle/libs.versions.toml`)

Add to `[versions]`:
```toml
lifecycle = "2.9.0"
composeBom = "2025.05.00"
composeCompiler = "1.5.15"
hilt = "2.56.1"
hiltNavigationCompose = "1.2.0"
ksp = "2.0.21-1.0.28"
datastore = "1.1.4"
room = "2.7.1"
navigationCompose = "2.9.0"
coroutines = "1.10.1"
```

Add to `[libraries]`:
```toml
# Compose
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version = "1.10.1" }

# Lifecycle
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }

# Navigation
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }

# Hilt
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
androidx-hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }

# DataStore
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }

# Room
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# Coroutines
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
```

Add to `[plugins]`:
```toml
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

### Step 2: App Build File (`app/build.gradle.kts`)

Apply plugins:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}
```

In `android {}` block, add:
```kotlin
buildFeatures {
    compose = true
}
```

Dependencies — add:
```kotlin
// Compose
implementation(platform(libs.androidx.compose.bom))
implementation(libs.androidx.compose.ui)
implementation(libs.androidx.compose.ui.graphics)
implementation(libs.androidx.compose.ui.tooling.preview)
implementation(libs.androidx.compose.material3)
implementation(libs.androidx.activity.compose)
debugImplementation(libs.androidx.compose.ui.tooling)

// Lifecycle
implementation(libs.androidx.lifecycle.viewmodel.compose)
implementation(libs.androidx.lifecycle.runtime.compose)

// Navigation
implementation(libs.androidx.navigation.compose)

// Hilt
implementation(libs.hilt.android)
ksp(libs.hilt.compiler)
implementation(libs.androidx.hilt.navigation.compose)

// DataStore
implementation(libs.androidx.datastore.preferences)

// Room
implementation(libs.androidx.room.runtime)
implementation(libs.androidx.room.ktx)
ksp(libs.androidx.room.compiler)

// Coroutines
implementation(libs.kotlinx.coroutines.android)
```

### Step 3: AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"
        tools:ignore="ProtectedPermissions" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <queries>
        <intent>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent>
    </queries>

    <application
        android:name=".BeAwareApp"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.BeAware">

        <activity
            android:name=".ui.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".service.MonitorService"
            android:foregroundServiceType="specialUse"
            android:exported="false" />
    </application>
</manifest>
```

### Step 4: `BeAwareApp.kt`
- `@HiltAndroidApp` annotated Application class. Empty body.

### Step 5: `model/AppInfo.kt`
- `data class AppInfo(packageName: String, label: String, icon: Drawable, isSelected: Boolean = false)`

### Step 6: `data/local/entity/UsageSessionEntity.kt`
- `@Entity(tableName = "usage_sessions")`
- Fields: `id` (auto PK), `packageName`, `appLabel`, `startTime` (Long), `endTime` (Long), `durationMs` (Long), `date` (String "yyyy-MM-dd")

### Step 7: `data/local/dao/UsageSessionDao.kt`
- `@Insert` fun insertSession
- `@Query` fun getSessionsByDateRange(startDate: String, endDate: String): Flow<List<UsageSessionEntity>>
- `@Query` fun getTotalDurationByApp(startDate: String, endDate: String): Flow<List<AppUsageSummary>> (packageName + totalMs)

### Step 8: `data/local/BeAwareDatabase.kt`
- `@Database(entities = [UsageSessionEntity::class], version = 1)`
- Abstract fun usageSessionDao()

### Step 9: `data/datastore/PrefsDataStore.kt`
- DataStore<Preferences> extension on Context
- `getSelectedApps(): Flow<Set<String>>`
- `setSelectedApps(packages: Set<String>)`
- `isTrackingActive(): Flow<Boolean>`
- `setTrackingActive(active: Boolean)`

### Step 10: `data/repository/AppRepository.kt` (interface)
- `getInstalledApps(): List<AppInfo>`
- `getSelectedApps(): Flow<Set<String>>`
- `saveSelectedApps(packages: Set<String>)`
- `insertSession(session: UsageSessionEntity)`
- `getSessionsByDateRange(start: String, end: String): Flow<List<UsageSessionEntity>>`

### Step 11: `data/repository/AppRepositoryImpl.kt`
- `@Inject constructor(context, prefsDataStore, usageSessionDao)`
- Implements all interface methods
- `getInstalledApps()` queries PackageManager for MAIN/LAUNCHER intents, filters out own package

### Step 12: `di/AppModule.kt`
- `@Module @InstallIn(SingletonComponent::class)`
- `@Provides @Singleton` fun provideDatabase
- `@Provides` fun provideUsageSessionDao
- `@Provides @Singleton` fun provideDataStore
- `@Binds` fun bindRepository (AppRepositoryImpl → AppRepository)

### Step 13: `ui/theme/Color.kt`
- M3 color tokens: primary, secondary, surface, overlay colors

### Step 14: `ui/theme/Type.kt`
- M3 typography using default Material 3 type scale

### Step 15: `ui/theme/Theme.kt`
- `@Composable fun BeAwareTheme` with light/dark dynamic color support

### Step 16: `util/PermissionHelper.kt`
- `hasUsageStatsPermission(context): Boolean` — AppOpsManager check
- `hasOverlayPermission(context): Boolean` — Settings.canDrawOverlays
- `hasNotificationPermission(context): Boolean` — API 33+ check

### Step 17: `util/TimeFormatter.kt`
- `formatDuration(ms: Long): String` → "1h 23m", "45m", "30s"
- `formatTimer(seconds: Long): String` → "05:23" MM:SS format

### Step 18: `ui/screens/onboarding/OnboardingViewModel.kt`
- `@HiltViewModel`, exposes permission states as StateFlow
- `refreshPermissions()` called when returning from Settings

### Step 19: `ui/screens/onboarding/OnboardingScreen.kt`
- 3 permission cards (Usage Stats, Overlay, Notifications)
- Each shows granted/not-granted status
- "Grant" button opens corresponding Settings intent
- "Continue" enabled only when all required permissions granted
- Navigates to Home

### Step 20: `ui/screens/picker/AppPickerViewModel.kt`
- `@HiltViewModel`, injects AppRepository
- Loads installed apps, exposes filtered list as StateFlow
- Search filter, toggle selection, save to DataStore

### Step 21: `ui/screens/picker/AppPickerScreen.kt`
- Top search bar (OutlinedTextField)
- LazyColumn of app items: icon + label + package name + checkbox
- "Save" FAB or top bar action
- Selected apps sorted to top

### Step 22: `ui/screens/home/HomeViewModel.kt`
- `@HiltViewModel`, injects AppRepository
- Observes selected apps from DataStore
- Controls service start/stop via Intent
- Exposes tracking state

### Step 23: `ui/screens/home/HomeScreen.kt`
- App title
- Toggle switch (Start/Stop Tracking)
- List of currently tracked apps with icons
- "Add Apps" button → navigates to Picker
- "View Stats" button → navigates to Stats (Phase 2)

### Step 24: `ui/navigation/NavGraph.kt`
- Routes: Onboarding, Home, Picker, Stats
- Start destination: Onboarding (checks if permissions already granted → skip to Home)

### Step 25: `ui/MainActivity.kt`
- `@AndroidEntryPoint`
- `setContent { BeAwareTheme { NavGraph() } }`

### Step 26: `res/drawable/ic_notification.xml`
- Clock vector icon (Material `access_time` path data)

### Step 27: `service/overlay/TimerPillContent.kt`
- `@Composable fun TimerPill(appLabel: String, elapsedSeconds: Long)`
- Small Surface with rounded shape, semi-transparent dark background
- Row: app label (maxWidth 120dp, ellipsis) + dot + MM:SS monospace

### Step 28: `service/overlay/FloatingTimerManager.kt`
- `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`
- `FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL`
- Creates ComposeView, sets content to TimerPill
- Handles ViewTreeLifecycleOwner + ViewTreeSavedStateRegistryOwner for Compose in Service context
- `show(appLabel)`, `updateTimer(seconds)`, `updateLabel(label)`, `hide()`
- Drag touch listener on parent FrameLayout wrapping ComposeView

### Step 29: `service/MonitorService.kt`
- `@AndroidEntryPoint` Foreground Service
- Injects `UsageSessionDao` via Hilt
- Creates notification channel (IMPORTANCE_LOW, silent)
- `onStartCommand`: reads tracked packages from DataStore, starts polling coroutine
- Polling: `while(isActive) { checkForeground(); delay(3000) }`
- `checkForeground()`:
  - Query `UsageStatsManager.queryUsageStats(INTERVAL_DAILY, now - 10_000, now)`
  - `maxByOrNull { lastTimeUsed }` → current foreground package
  - If tracked: show/update overlay + track session start time
  - If not tracked: hide overlay + save completed session to Room (insert with duration)
- `onDestroy`: cancel coroutines, hide overlay, save any active session, mark tracking inactive
- `ACTION_STOP` intent action to stop from notification
- `START_STICKY` for OS restart resilience
- API 34+: explicit `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`

### Step 30: `res/values/strings.xml`
- All user-facing strings for all screens, notifications, permissions

---

## PHASE 2: Stats Screen

### Step 31: `model/UsageStats.kt`
- `data class AppUsageSummary(packageName: String, appLabel: String, totalDurationMs: Long, sessionCount: Int)`
- Used by the Stats screen to display per-app aggregated data

### Step 32: `ui/screens/stats/StatsViewModel.kt`
- `@HiltViewModel`, injects AppRepository
- `selectedRange: StateFlow<DateRange>` — enum: TODAY, THREE_DAYS, WEEK, MONTH
- `usageStats: StateFlow<List<AppUsageSummary>>` — aggregated from Room
- `onRangeSelected(range)` → recalculates date boundaries → re-queries Room
- Date calculation: today's date, today - 3, today - 7, today - 30

### Step 33: `ui/screens/stats/StatsScreen.kt`
- Top: Tab row or chip group for date range selection (Today | 3 Days | Week | Month)
- Total screen time summary text
- LazyColumn of per-app usage:
  - App icon + label
  - Duration text ("1h 23m")
  - Horizontal progress bar (proportional to max app usage in this range)
- Empty state when no data

### Step 34: Wire Stats into NavGraph
- Add Stats route
- Home screen "View Stats" button navigates to Stats
- Back navigation returns to Home

---

## Edge Cases Handled

- **BeAware itself** filtered out of trackable apps list
- **Screen off**: polling continues (negligible CPU), overlay invisible
- **Service killed**: `START_STICKY` restarts, re-reads DataStore, timer resets, active session saved with estimated end time
- **Permissions revoked mid-tracking**: overlay guarded by `canDrawOverlays()`, empty stats → overlay hides
- **API 34+**: explicit `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` in `startForeground()`
- **No apps selected**: "Start Tracking" shows toast, refuses to start service
- **App switch during tracking**: current session saved to Room, new session starts
- **Compose in Service context**: FloatingTimerManager sets up ViewTreeLifecycleOwner manually for ComposeView

---

## Verification

### Phase 1 Verification
1. Build project — zero compile errors
2. Install on device/emulator API 26+
3. App launches → Onboarding screen with permission cards
4. Grant Usage Stats → card updates
5. Grant Overlay → card updates
6. Grant Notifications (API 33+) → all granted → navigate to Home
7. Home shows empty tracked list + "Add Apps" button
8. Picker screen: searchable list, select 2-3 apps, save → return to Home
9. Toggle ON → notification appears, service starts
10. Open tracked app → floating pill timer appears (MM:SS counting up)
11. Switch to non-tracked app → pill disappears
12. Drag pill → repositions
13. Notification "Stop" → service stops, pill gone, toggle OFF
14. Verify Room: sessions recorded (check via Android Studio DB inspector)

### Phase 2 Verification
15. Navigate Home → Stats
16. "Today" tab shows usage from current tracking sessions
17. Switch tabs (3 Days / Week / Month) → data updates
18. Per-app bars show proportional usage
19. Empty state when no data for selected range
20. Back → returns to Home
