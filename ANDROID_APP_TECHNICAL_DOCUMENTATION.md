# Complete Android App Technical Documentation & Architectural Reference

**Application Name:** Messages (`#Messages`)  
**Package Name:** `com.example.message`  
**Target Platform:** Android (minSdk: 26 / Android 8.0 Oreo, targetSdk: 37, compileSdk: 37)  
**Primary Language:** Pure Java (Java 11)  
**Role:** Senior Android Developer, Software Architect, and Technical Mentor  
**Date of Technical Audit:** November 2026  

---

## Table of Contents
1. [Project Technology Overview](#1-project-technology-overview)
2. [Complete Dependency Explanation](#2-complete-dependency-explanation)
3. [How Messages Are Displayed in the App (End-to-End Workflow)](#3-how-messages-are-displayed-in-the-app)
4. [Themes, Colors, and Dark Mode Implementation](#4-themes-colors-and-dark-mode-implementation)
5. [Language Selection and Dynamic Localization](#5-language-selection-and-dynamic-localization)
6. [Complete Application Architecture](#6-complete-application-architecture)
7. [Screen-by-Screen Technical Breakdown](#7-screen-by-screen-technical-breakdown)
8. [User Interactions and Execution Tracing](#8-user-interactions-and-execution-tracing)
9. [Android Manifest, Permissions & Default SMS Compliance](#9-android-manifest-permissions--default-sms-compliance)
10. [Important Source Code Explanation (Deep Dive)](#10-important-source-code-explanation)
11. [Manager Interview Preparation (40+ Practical Q&A)](#11-manager-interview-preparation)
12. [Practical End-to-End Walkthrough Scenarios](#12-practical-end-to-end-walkthrough-scenarios)
13. [Technical Glossary for Android Development](#13-technical-glossary-for-android-development)
14. [How I Can Explain My Android Application to My Manager (Spoken Script)](#14-how-i-can-explain-my-android-application-to-my-manager)

---

## 1. Project Technology Overview

This application is a **production-grade, fully functional Native Android Default SMS & MMS Messaging Client** with built-in call detection, automated OTP extraction, scheduled SMS delivery via WorkManager, custom multi-select thread management, local JSON backup/restore, and multi-language/theme support.

Below is the verified audit of all tools and technologies present in the codebase:

### 1. Programming Language: Java (Java 11)
- **What it is:** Pure Object-Oriented Programming language running on the Android Runtime (ART).
- **Why it is used:** The entire codebase is implemented in standard Java (source and target compatibility set to `JavaVersion.VERSION_11`). No Kotlin source files exist in `app/src/main/java`.
- **Where it is used:** Every class across `activities`, `adapters`, `database`, `models`, `preferences`, `receivers`, `services`, `utils`, and `workers`.
- **What functionality it provides:** Core application logic, concurrency control via `ExecutorService` and `Handler`, Android framework interactions.
- **If removed:** The project would fail to compile entirely.

### 2. Android SDK & Versions
- **Minimum SDK (`minSdk = 26`):** Supports Android 8.0 (Oreo) and above. Guarantees modern telephony APIs, Notification Channels (`NotificationChannel`), and background execution limits.
- **Target SDK (`targetSdk = 37`):** Configured against the latest Android platform requirements, ensuring edge-to-edge support, strict permission policies, and role-based SMS handling.
- **Compile SDK (`compileSdk = release(37)`):** The SDK version used by Gradle to compile Java code into Dalvik/ART bytecode.

### 3. Build System: Gradle (Kotlin DSL `.gradle.kts`)
- **What it is:** Advanced build automation tool using Kotlin DSL scripts (`build.gradle.kts`, `settings.gradle.kts`, and `libs.versions.toml`).
- **Why it is used:** Resolves dependencies from Google Maven and Maven Central, manages Android Gradle Plugin (AGP `9.5.1`), builds APKs/AABs, and runs R8/ProGuard code shrinking.
- **Where it is used:** Root project configuration and `app/build.gradle.kts`.

### 4. Architectural Pattern: Layered Architecture (MVC / Repository-Helper Pattern)
- **What it is:** A structured separation of concerns:
  - **View Layer:** XML layouts, Activities (`BaseActivity`, `MainActivity`, `ActivityChat`), Adapters (`MessageAdapter`, `ConversationAdapter`).
  - **Data Access Layer:** `SmsHelper` (ContentProvider queries), `ContactHelper` (Contacts provider), Room Database (`AppDatabase`, DAOs, Entities), `PreferenceManager` (`SharedPreferences`).
  - **Background / Work Layer:** `ScheduledSmsWorker` (WorkManager), `SmsReceiver`, `CallReceiver`, `BootReceiver`.
- **Note on MVVM:** While Jetpack ViewModel and LiveData dependencies are imported in Gradle, the UI screens primarily interact with helper classes (`SmsHelper`) running asynchronous tasks on background executors (`Executors.newSingleThreadExecutor()`) posting back to the UI thread via `Handler(Looper.getMainLooper())`. LiveData is explicitly used for Room reactive observations in `ActivitySchedule`, `ActivityBlockNumber`, and `ActivityRecyclebin`.

### 5. UI Framework: Classical Android XML Layouts & View Binding
- **What it is:** Declarative XML layouts combined with standard `findViewById` view bindings and Material 3 DayNight theming.
- **Jetpack Compose:** **NOT implemented**. All UI is built using XML, `RecyclerView`, `DrawerLayout`, `FrameLayout`, `LinearLayout`, and `RelativeLayout`.

### 6. Android Jetpack Libraries
- **AppCompat (`androidx.appcompat:appcompat:1.6.1`):** Backward-compatible Activity base (`AppCompatActivity`), theme switching (`AppCompatDelegate`), vector drawables.
- **RecyclerView (`androidx.recyclerview:recyclerview:1.3.2`):** Efficient view recycling for conversations, message streams, contacts, and language lists.
- **WorkManager (`androidx.work:work-runtime:2.9.0`):** Guaranteed background execution for deferred SMS scheduling surviving app termination.
- **Room Database (`androidx.room:room-runtime:2.6.1`):** SQLite abstraction storing blocked contacts, archived threads, recycle bin, and scheduled message queues.
- **Lifecycle & LiveData (`androidx.lifecycle:lifecycle-livedata:2.8.7`):** Lifecycle-aware data observation from Room DAOs to UI components.

### 7. Database & Local Storage
- **System Telephony Content Provider (`Telephony.Sms`, `content://mms-sms/`):** The primary data store for all native SMS/MMS messages and conversation threads on the device.
- **Room SQLite Database (`messages_app.db`):** App-private relational database containing:
  - `archived_conversations` (Archive status)
  - `blocked_numbers` (Sender blocking)
  - `recycle_bin` (Soft-deleted threads)
  - `scheduled_messages` (Queued future SMS)
- **SharedPreferences (`message_preferences`):** Key-value store for language code, theme mode (0/1/2), font scale index, swipe action preferences, and call toggle flags.

### 8. Networking Libraries
- **HTTP / REST Client (Retrofit, OkHttp, Volley):** **NOT implemented**. The app operates entirely offline and communicates with the cellular SMS network via `android.telephony.SmsManager` and system Content Providers.

### 9. Image Loading: Glide (`com.github.bumptech.glide:glide:4.16.0`)
- **What it is:** High-performance media and image loading framework.
- **Where it is used:** `ConversationAdapter` to load contact avatar thumbnail photos asynchronously from `content://` URIs with circular cropping (`circleCrop()`).
- **If removed:** Contact profile photos would not be rendered; fallback colored letter avatar placeholders would display instead.

### 10. Dependency Injection (Dagger / Hilt)
- **Status:** **NOT implemented**. Singleton instances are created explicitly via thread-safe static factory methods: `AppDatabase.getInstance(context)` and `PreferenceManager.getInstance(context)`.

### 11. Third-Party Utility Libraries
- **Google Gson (`com.google.code.gson:gson:2.10.1`):** Serializes and deserializes the full message database to/from structured JSON during backup and restore operations in `ActivityBackup`.
- **Intuit SDP & SSP (`sdp-android:1.1.1`, `ssp-android:1.1.1`):** Scalable dimension units (`@dimen/_12sdp`, `@dimen/_14ssp`) that automatically scale across varying screen densities and screen sizes.

---

## 2. Complete Dependency Explanation

### Dependency Table

| Dependency | Version | Purpose | Where Used in Project |
|---|---|---|---|
| `androidx.appcompat:appcompat` | 1.6.1 | Provides backward-compatible UI, `AppCompatActivity`, `AppCompatDelegate` theme switching. | `BaseActivity`, `MainActivity`, all Activity screens. |
| `com.google.android.material:material` | 1.10.0 | Material Design 3 UI components (FAB, NavigationView, Dialogs, RadioGroups). | `activity_main.xml`, `MainActivity`, `ActivityThemes`, `ActivitySetting`. |
| `androidx.constraintlayout:constraintlayout` | 2.1.4 | Responsive, flat layout hierarchies reducing nested view overhead. | XML layout files (e.g., `activity_main.xml`). |
| `androidx.activity:activity-ktx` | 1.13.0 | Modern Activity Result API (`registerForActivityResult`) for permissions and document pickers. | `MainActivity`, `ActivityLanguage`, `ActivityBackup`, `ActivityAfterCallSettings`. |
| `androidx.recyclerview:recyclerview` | 1.3.2 | High-performance list rendering and view holder recycling. | All list screens: conversations, messages, contacts, settings, schedule. |
| `androidx.drawerlayout:drawerlayout` | 1.2.0 | Navigation drawer container sliding in from the left edge. | `MainActivity` (`R.id.drawerLayout`). |
| `androidx.coordinatorlayout:coordinatorlayout` | 1.2.0 | Layout orchestrator coordinating behaviors between FAB, toolbars, and scrollable lists. | Root layout of `MainActivity`. |
| `androidx.room:room-runtime` | 2.6.1 | Object-relational mapping (ORM) library over SQLite database (`messages_app.db`). | `AppDatabase`, `ArchiveDao`, `BlockedNumberDao`, `RecycleBinDao`, `ScheduledMessageDao`. |
| `androidx.room:room-compiler` | 2.6.1 | Annotation processor generating DAO and entity implementation code at compile time. | Annotation processor in `app/build.gradle.kts`. |
| `androidx.work:work-runtime` | 2.9.0 | Guaranteed persistent background execution for delayed SMS delivery. | `ScheduledSmsWorker`, `ActivityChat`, `BootReceiver`. |
| `androidx.lifecycle:lifecycle-viewmodel` | 2.8.7 | Manages UI-related data in a lifecycle-conscious way. | Core Jetpack runtime architecture. |
| `androidx.lifecycle:lifecycle-livedata` | 2.8.7 | Observable data holder notifying UI upon Room database changes. | Observed in `ActivitySchedule`, `ActivityBlockNumber`, `ActivityRecyclebin`. |
| `com.google.code.gson:gson` | 2.10.1 | JSON serialization and deserialization for SMS backup and restore. | `ActivityBackup` (`BackupData` object graph). |
| `com.github.bumptech.glide:glide` | 4.16.0 | Asynchronous image loading, caching, and transformation for contact profile photos. | `ConversationAdapter` (loads contact avatar thumbnails). |
| `com.github.bumptech.glide:compiler` | 4.16.0 | Generates Glide API extensions at compile time. | Annotation processor in `app/build.gradle.kts`. |
| `com.intuit.sdp:sdp-android` | 1.1.1 | Scalable size unit (sdp) proportional to screen width for consistent layout sizing. | Layout dimensions across all XML files (`@dimen/_12sdp`). |
| `com.intuit.ssp:ssp-android` | 1.1.1 | Scalable font size unit (ssp) based on SP scaled across screen densities. | Text sizes across all XML files (`@dimen/_14ssp`). |
| `junit:junit` | 4.13.2 | Unit testing framework for local JVM unit tests. | `ExampleUnitTest.java`. |
| `androidx.test.ext:junit` | 1.1.5 | AndroidX JUnit test runner extensions for on-device instrumentation tests. | `ExampleInstrumentedTest.java`. |
| `androidx.test.espresso:espresso-core` | 3.5.1 | UI test automation framework for functional instrumentation tests. | `ExampleInstrumentedTest.java`. |

### Core Build Concepts Explained

#### What is a Gradle Dependency?
A Gradle dependency is an external library or software module developed outside your project that your application relies upon to execute specific functionality (such as Room for SQLite storage or Glide for image decoding). Instead of downloading `.jar` or `.aar` binary files manually and placing them into a `libs/` folder, Gradle declares coordinate identifiers: `group:name:version` (e.g., `com.google.code.gson:gson:2.10.1`).

#### What is the Difference Between `implementation` and `api`?
- `implementation`: The dependency is available to the current module at compile time and runtime, but is **hidden from upstream modules** that depend on this module. This improves build performance through compile-classpath leakage avoidance (ABI isolation).
- `api`: The dependency is exported transitively to any module that depends on this module. If Module A exposes Module B via `api`, any Module C depending on Module A can directly access Module B's classes.
*In this single-module project, `implementation` is used everywhere to maintain clean compilation boundaries.*

#### What is a Gradle Plugin?
A Gradle plugin (e.g., `com.android.application`) extends Gradle’s core functionality by contributing specific tasks, configurations, and build models. The Android Application plugin provides Android-specific compilation tasks: resource merging (`aapt2`), Dex compilation (`d8`), manifest merging, code shrinking (`R8`), and APK packaging.

#### How Does Gradle Download and Manage Dependencies?
1. **Declaration:** In `settings.gradle.kts`, repositories are defined: `google()` and `mavenCentral()`.
2. **Resolution:** Gradle parses `build.gradle.kts` dependency blocks, determines versions from `libs.versions.toml`, and creates a directed dependency graph resolving transitive dependencies and version conflicts.
3. **Caching:** Gradle checks the local user cache (`~/.gradle/caches/modules-2/files-2.1/`). If missing, it downloads the corresponding `.aar` or `.jar` archive and its `.pom` metadata over HTTPS.
4. **Compilation & Packaging:** The library classes are compiled into the build classpath and packaged into classes.dex during DEX compilation.

---

## 3. How Messages Are Displayed in the App

This is the core functional pipeline of the application. The following detailed explanation traces the exact end-to-end data lifecycle from incoming transmission or storage query to on-screen pixel rendering.

```
       [ Incoming Cellular SMS ]                   [ App Cold Launch / User Navigation ]
                   │                                                  │
                   ▼                                                  ▼
     SmsReceiver (BroadcastReceiver)                       MainActivity / ActivityChat
                   │                                                  │
       [ Check Blocked Numbers ]                                      │
                   │                                                  │
       [ Write to Telephony.Sms.Inbox ]                                │
                   │                                                  │
                   └───────────► ContentObserver ◄────────────────────┘
                                (content://sms)
                                       │
                                       ▼
                             Background Worker Thread
                         (ExecutorService / Background)
                                       │
                                       ▼
                             SmsHelper.getMessages()
                         [ ContentResolver.query() ]
                                       │
                                       ▼
                             List<MessageItem>
                          (Extracted OTP, Timestamps)
                                       │
                                       ▼
                                 mainHandler
                           (Looper.getMainLooper())
                                       │
                                       ▼
                             MessageAdapter.setMessages()
                                       │
                  ┌────────────────────┴────────────────────┐
                  ▼                                         ▼
        TYPE_INCOMING (0)                         TYPE_OUTGOING (1)
     [item_message_in.xml]                     [item_message_out.xml]
                  │                                         │
                  ▼                                         ▼
          IncomingViewHolder                        OutgoingViewHolder
  - Gray Bubble (Left-aligned)              - Blue Bubble (Right-aligned)
  - Time text (HH:mm)                       - Time text + Sent status icon
  - Interactive "Copy OTP" Pill             - Interactive "Copy OTP" Pill
                  │                                         │
                  └────────────────────┬────────────────────┘
                                       ▼
                       RecyclerView.scrollToPosition(last)
                                       ▼
                         Rendered Visible on Screen
```

### Detailed Step-by-Step Data Flow

#### 1. Where the Message Data Comes From
Message data originates from two distinct channels:
- **Historical / Persisted Data:** Retrieved from the Android System SMS Content Provider (`content://sms` and `content://mms-sms/conversations?simple=true`).
- **Live Inbound SMS:** Intercepted by `SmsReceiver.java` via `android.provider.Telephony.SMS_DELIVER` (when default SMS app) or `android.provider.Telephony.SMS_RECEIVED`.

#### 2. Components Managing Message Data
- `SmsHelper.java`: Central utility class acting as the data repository. Encapsulates all `ContentResolver` queries, insertions, deletions, and thread resolution methods.
- `ContentResolver`: Android system client querying the Telephony provider across IPC boundaries.
- `AppDatabase.java`: Room database managing metadata filters: archived threads (`ArchiveDao`), blocked senders (`BlockedNumberDao`), and recycle bin records (`RecycleBinDao`).

#### 3. Data Models Representing a Message
- `MessageItem.java`: Represents an individual SMS message:
  - `id` (`long`): Message ID in system telephony database (`_id`).
  - `threadId` (`long`): Conversation thread identifier.
  - `address` (`String`): Phone number of recipient/sender.
  - `body` (`String`): The text payload.
  - `date` (`long`): Epoch millisecond timestamp.
  - `type` (`int`): `1` for Incoming (`MESSAGE_TYPE_INBOX`), `2` for Outgoing (`MESSAGE_TYPE_SENT`).
  - `status` (`int`): Delivery status indicator.
  - `read` (`boolean`): Whether the message was opened.
  - `otpCode` (`String`): Extracted 4–8 digit verification code if present.
- `Conversation.java`: Represents a conversation thread displayed on `MainActivity`:
  - `threadId`, `address`, `name`, `snippet` (last message body), `date`, `read`, `messageCount`, `photoUri`.

#### 4. How Messages Are Stored
- Stored permanently in the device's native SQLite telephony database maintained by Android OS (`/data/user_de/0/com.android.providers.telephony/databases/mmssms.db`).
- Auxiliary state (Archive, Trash, Blocked) is stored in the private Room SQLite database `messages_app.db`.

#### 5. Passing Messages to the UI
1. `ActivityChat.java` triggers `loadMessages()` inside a background executor (`Executors.newSingleThreadExecutor()`).
2. Calls `SmsHelper.getMessagesForThread(context, threadId)`.
3. Calls `SmsHelper.markThreadAsRead(context, threadId)` to clear unread badges.
4. Results are posted to the Main UI thread via `mainHandler.post(() -> messageAdapter.setMessages(list))`.

#### 6. UI Component Displaying Messages
- `androidx.recyclerview.widget.RecyclerView` (`rvMessages` inside `activity_all_message.xml`).
- Uses a `LinearLayoutManager` configured with `layoutManager.setStackFromEnd(true)` so the message list anchors to the bottom.

#### 7. View Creation and ViewHolders
`MessageAdapter.java` overrides `getItemViewType(int position)`:
- Returns `TYPE_INCOMING = 0` if `item.isIncoming()` is true.
- Returns `TYPE_OUTGOING = 1` if `item.isIncoming()` is false.
- Returns `TYPE_DATE_HEADER = 2` if `item.isDateHeader()` is true.
- In `onCreateViewHolder`:
  - `TYPE_INCOMING` inflates `R.layout.item_message_in` into `IncomingViewHolder`.
  - `TYPE_OUTGOING` inflates `R.layout.item_message_out` into `OutgoingViewHolder`.

#### 8. Visual Differentiation of Incoming vs. Outgoing
- **Incoming Messages (`item_message_in.xml`):**
  - Left-aligned (`layout_gravity="start"`).
  - Background: `@drawable/bg_bubble_incoming` (Subtle gray/card color).
  - Text color: `@color/title_color` (Dark slate in light mode, white in dark mode).
  - Displays message text and timestamp.
- **Outgoing Messages (`item_message_out.xml`):**
  - Right-aligned (`layout_gravity="end"`).
  - Background: `@drawable/bg_bubble_outgoing` (Vibrant primary blue `#3187fb`).
  - Text color: Solid white (`#FFFFFF`).
  - Displays message text, timestamp, and status check icon (`ivStatus`).

#### 9. Message Timestamps Display
- Inside `MessageAdapter.java`, timestamps are formatted via:
  ```java
  new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(item.getDate()));
  ```
- In `ConversationAdapter.java` (for threads), dynamic calendar relative dates are computed:
  - Today: Formatted as `HH:mm` (e.g., `14:30`).
  - Current week: Formatted as Day of Week (e.g., `Mon`, `Thu`).
  - Older dates: Formatted as date (`dd/MM/yy`).

#### 10. How Conversations Are Loaded on Launch
In `MainActivity.java`:
1. `loadConversations()` runs on `executor`.
2. Queries Room `archiveDao().getArchivedThreadIdsSync()` and creates an `archivedSet`.
3. Calls `SmsHelper.getConversations(this)` which queries `content://mms-sms/conversations?simple=true`.
4. Filters out any thread ID present in `archivedSet`.
5. Resolves contact display names and thumbnail photos via `ContactHelper`.
6. Dispatches the list to `ConversationAdapter.setConversations()`.

#### 11. Real-Time UI Updates on New Message Arrival
- `MainActivity` and `ActivityChat` both register a `ContentObserver`:
  ```java
  getContentResolver().registerContentObserver(Uri.parse("content://sms"), true, smsObserver);
  ```
- When `SmsReceiver` writes an incoming SMS or `SmsHelper` inserts a sent SMS into `Telephony.Sms.Inbox` or `Sent`, Android triggers `smsObserver.onChange()`.
- The observer immediately executes `loadMessages()` or `loadConversations()`, seamlessly reloading the UI without polling.

#### 12. Scrolling to Latest Message
Inside `ActivityChat.java`, after `messageAdapter.setMessages(list)` is called on the main thread:
```java
if (!list.isEmpty()) {
    rvMessages.scrollToPosition(list.size() - 1);
}
```

#### 13. Message Status Indicators
- Outgoing message items contain `ivStatus` (`ImageView` in `item_message_out.xml`), initialized with `ic_msg_sent.xml`.
- When an outgoing message is written to `Telephony.Sms.Sent.CONTENT_URI`, its type is marked as sent.

#### 14. Automated OTP Detection and Interactive Action Pill
Both incoming and outgoing rows check for one-time passwords via regex:
```java
Pattern OTP_PATTERN = Pattern.compile("\\b(\\d{4,8})\\b");
```
When found, the layout reveals `tvOtpPill` ("Copy OTP: 123456"). Clicking the pill copies the OTP directly to `ClipboardManager` and triggers a confirmation toast.

---

## 4. Themes, Colors, and Dark Mode Implementation

### Theme Architecture
The application implements modern **Material Design 3 DayNight theming** (`Theme.Material3.DayNight.NoActionBar`).

### Light vs. Dark vs. System Default Theme Explained
1. **Light Theme (`MODE_NIGHT_NO`):** Forces all activities to render with light backgrounds (`#FFFFFF`) and dark typography (`#303B4F`), regardless of the device's global display settings.
2. **Dark Theme (`MODE_NIGHT_YES`):** Forces high-contrast, battery-efficient dark backgrounds (`#000000` / `#1A1A1A`) and white typography (`#FFFFFF`).
3. **System Default (`MODE_NIGHT_FOLLOW_SYSTEM`):** Delegates theme switching to Android OS system settings (automatically switching at sunset or battery saver mode).

### Theme Storage & Definitions
- Defined in `app/src/main/res/values/themes.xml` (Light) and `app/src/main/res/values-night/themes.xml` (Dark).
- Named themes:
  - `Theme.Message`: Primary application theme.
  - `Theme.Transparent`: Used for floating dialog overlays like `PopupMessageActivity`.
  - `Theme.Transparent.After.Call`: Used for translucent call completion summary in `AfterCallDetailActivity`.

### Color Architecture Matrix

| Color Resource Name | Light Mode Value (`values/colors.xml`) | Dark Mode Value (`values-night/colors.xml`) | Role in UI |
|---|---|---|---|
| `background` | `#FFFFFFFF` (Pure White) | `#FF000000` (Pitch Black) | Window and screen canvas |
| `title_color` | `#FF303B4F` (Dark Slate Blue) | `#FFFFFFFF` (Pure White) | Headings, conversation names, message text |
| `sub_color` | `#FF989DA7` (Medium Gray) | `#FF989DA7` (Unchanged) | Timestamps, snippets, phone numbers |
| `card_bg` | `#FFF4F7FC` (Soft Ice Blue) | `#FF2B2B2B` (Dark Charcoal) | Search bars, cards, settings rows |
| `dialog_bg` | `#FFF4F7FC` | `#FF1A1A1A` | Popup background |
| `blue` / `app_color` | `#FF3187FB` | `#FF3187FB` | Outgoing chat bubbles, primary action buttons |
| `copy_otp_bg` | `#FFEEEEEE` | `#FF282828` | OTP pill background |

### How Theme Preference Is Saved and Restored
1. In `ActivityThemes.java`, the user selects Light, Dark, or System Default via `RadioGroup`.
2. Selection is committed to `PreferenceManager.java`:
   ```java
   prefManager.setTheme(selectedTheme); // 0: Light, 1: Dark, 2: System
   ```
3. Runtime theme change is triggered instantly:
   ```java
   AppCompatDelegate.setDefaultNightMode(mode);
   ```
4. Every activity in the project extends `BaseActivity.java`. In `BaseActivity.onCreate()`:
   ```java
   protected void applyCurrentTheme() {
       int themeMode = PreferenceManager.getInstance(this).getThemeMode();
       switch (themeMode) {
           case 0: AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO); break;
           case 1: AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES); break;
           case 2: default: AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM); break;
       }
   }
   ```
5. When the user relaunches the app from a cold boot, `BaseActivity` reads SharedPreferences before `super.onCreate()`, restoring the chosen theme without any visual flicker.

---

## 5. Language Selection and Dynamic Localization

### How Dynamic Language Selection Operates
The application does not rely solely on the Android OS locale; it provides an **in-app language switching engine** that overrides the resource configuration at runtime.

### Supported Languages
The application supports **11 languages** initialized in `ActivityLanguage.java`:
1. English (`en`) - Default
2. Spanish (`es`) - Español
3. French (`fr`) - Français
4. Portuguese (`pt`) - Português
5. Russian (`ru`) - Русский
6. Hindi (`hi`) - हिन्दी
7. German (`de`) - Deutsch
8. Italian (`it`) - Italiano
9. Bengali (`bn`) - বাংলা
10. Turkish (`tr`) - Türkçe
11. Romanian (`ro`) - Română

### Storage of Translated Strings
Translated string resource files exist under:
- `res/values/strings.xml` (Base / English)
- `res/values-es/strings.xml` (Spanish)
- `res/values-fr/strings.xml` (French)
- `res/values-pt/strings.xml` (Portuguese)
- `res/values-ru/strings.xml` (Russian)

### Language Application Workflow (`LocaleHelper.java`)
1. User selects a language on `ActivityLanguage` and clicks "DONE".
2. `preferenceManager.setLanguage(selectedLanguage.code)` writes the ISO code to SharedPreferences.
3. `LocaleHelper.setLocale(context, languageCode)` constructs a `Locale` object:
   ```java
   Locale locale = new Locale(languageCode);
   Locale.setDefault(locale);
   Configuration configuration = context.getResources().getConfiguration();
   configuration.setLocale(locale);
   LocaleList localeList = new LocaleList(locale);
   LocaleList.setDefault(localeList);
   configuration.setLocales(localeList);
   return context.createConfigurationContext(configuration);
   ```
4. **Context Injection in `MyApp.java` & `BaseActivity.java`:**
   Both `Application.attachBaseContext()` and `BaseActivity.attachBaseContext(Context newBase)` wrap their base context using `LocaleHelper.onAttach(newBase)` and `FontHelper.applyFontScale(langContext)`.
5. This guarantees that all inflated layouts, string resource lookups (`getString()`), and system views reflect the chosen language across all screens without requiring a full device reboot.

---

## 6. Complete Application Architecture

The project follows a clean, componentized Layered Architecture:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              VIEW LAYER                                 │
│  Activities (BaseActivity, MainActivity, ActivityChat, ActivitySetting) │
│  Adapters (MessageAdapter, ConversationAdapter, ContactAdapter, etc.)   │
│  Layouts & Views (XML layouts, Material 3, RecyclerViews, Drawers)      │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        CONTROLLER / HELPER LAYER                        │
│  SmsHelper (CRUD operations, ContentProvider query dispatching)         │
│  ContactHelper (Contact name & photo caching via PhoneLookup)           │
│  NotificationHelper (Notification Channels, RemoteInput, Quick Reply)   │
│  LocaleHelper & FontHelper (Context configuration overrides)            │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         DATA PERSISTENCE LAYER                          │
│  Android Telephony Provider (Telephony.Sms, content://sms)              │
│  Room Database (messages_app.db: Archive, Block, Trash, Schedule)       │
│  SharedPreferences (message_preferences: Theme, Locale, Font, Swipes)   │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      BACKGROUND EXECUTION LAYER                         │
│  SmsReceiver (SMS_DELIVER / SMS_RECEIVED broadcast processor)           │
│  CallReceiver (PHONE_STATE tracking for After Call trigger)             │
│  BootReceiver (BOOT_COMPLETED re-enqueuing of scheduled SMS)            │
│  ScheduledSmsWorker (WorkManager delayed SMS delivery execution)        │
│  HeadlessSmsSendService (RESPOND_VIA_MESSAGE default service)           │
└─────────────────────────────────────────────────────────────────────────┘
```

### Component Roles & Responsibilities

1. **`MyApp.java` (Application):**
   - Application entry point.
   - Attaches base context with saved locale to ensure application-level resources are localized.
2. **`BaseActivity.java` (Abstract Activity Base):**
   - Enforces uniform theme application (`applyCurrentTheme()`) and font scaling in `onCreate()`.
   - Injects locale and font configurations in `attachBaseContext()`.
3. **Activities (`com.example.message.activities`):**
   - Manage screen UI state, lifecycle, user inputs, and navigation.
4. **Adapters (`com.example.message.adapters`):**
   - Bind domain model data to ViewHolders and inflate item layout XML files.
5. **Database (`com.example.message.database`):**
   - `AppDatabase`: Singleton Room SQLite database.
   - DAOs (`ArchiveDao`, `BlockedNumberDao`, `RecycleBinDao`, `ScheduledMessageDao`): Provide SQL queries returning synchronous objects or reactive `LiveData`.
6. **Workers (`ScheduledSmsWorker.java`):**
   - Implements `doWork()` to transmit delayed messages via `SmsManager` and update database records.
7. **BroadcastReceivers (`com.example.message.receivers`):**
   - Listen to asynchronous OS intents (inbound SMS, incoming calls, system reboots).

---

## 7. Screen-by-Screen Technical Breakdown

### 1. Language Screen (`ActivityLanguage.java`)
- **Layout:** `activity_language.xml` (`rvLanguages`, `btnDone`, `ivBack`).
- **Purpose:** Onboarding screen on first launch; settings screen for changing language.
- **Data Source:** Hardcoded `List<LanguageItem>` representing 11 languages with flag drawables.
- **Key Methods:** `onDoneClicked()`, `requestSmsRoleOrPermissions()`, `goToMain()`.
- **Navigation:** If launched from Settings, back button finishes activity; if initial onboarding, forwards to Default SMS role prompt then `MainActivity`.

### 2. Main Conversation List (`MainActivity.java`)
- **Layout:** `activity_main.xml` (`DrawerLayout`, `rvConversations`, `fabNewChat`, `bannerDefaultSms`, `emptyState`).
- **Purpose:** Primary hub displaying all active SMS conversation threads.
- **Data Source:** System `content://mms-sms/conversations?simple=true` filtered against Room `archiveDao`.
- **Key Methods:** `loadConversations()`, `setupSwipeActions()`, `checkDefaultSms()`, `registerSmsObserver()`.
- **Navigation:** Clicking thread opens `ActivityChat`; FAB opens `ActivitySearchContact`; Drawer navigates to Archive, Blocked, Schedule, Backup, Settings, Recycle Bin.

### 3. Chat / Message Thread Screen (`ActivityChat.java`)
- **Layout:** `activity_all_message.xml` (`rvMessages`, `etMessage`, `ivSend`, `ivSchedule`, `blockedBanner`, `btnUnblock`).
- **Purpose:** Full conversation history with a contact; supports sending, scheduling, and OTP extraction.
- **Data Source:** `Telephony.Sms.CONTENT_URI` queried via `SmsHelper.getMessagesForThread()`.
- **Key Methods:** `loadMessages()`, `sendMessage()`, `scheduleMessage()`, `checkBlockStatus()`.
- **Navigation:** Header info click opens `ActivityInfo`; Call icon initiates `Intent.ACTION_DIAL`.

### 4. Search Screen (`ActivitySearch.java`)
- **Layout:** `activity_search.xml` (`etSearch`, `ivClear`, `rvResults`, `emptyState`).
- **Purpose:** Real-time debounced search across all conversation snippets, contact names, and numbers.
- **Data Source:** `SmsHelper.searchConversations()`.
- **Key Logic:** `TextWatcher` with 300ms debounce handler dispatching queries to background executor.

### 5. New Chat / Contact Selection (`ActivitySearchContact.java`)
- **Layout:** `activity_search_contact.xml` (`etRecipient`, `btnStartChat`, `rvContacts`, `emptyState`).
- **Purpose:** Pick existing contact or type a raw number to begin a new thread.
- **Data Source:** `ContactHelper.getContacts()` querying `ContactsContract.CommonDataKinds.Phone`.

### 6. Contact Information Screen (`ActivityInfo.java`)
- **Layout:** `activity_info.xml` (`tvName`, `tvPhoneNumber`, `llCall`, `llBlock`, `btnArchive`, `btnDelete`).
- **Purpose:** Detailed metadata of the contact, quick call, block/unblock, archive thread, or delete to recycle bin.
- **Data Source:** Intent extras + Room `blockedNumberDao` status.

### 7. Settings Screen (`ActivitySetting.java`)
- **Layout:** `activity_setting.xml` (Rows for Theme, Font Size, Language, Swipe Actions, Schedule, Blocked, Backup, After Call).
- **Purpose:** Main hub for user preferences, font scaling dialog, and system notification settings.
- **Key Logic:** `showFontSizeDialog()` allows slider adjustment (Small, Normal, Large, XL) recalculating font scale.

### 8. Themes Screen (`ActivityThemes.java`)
- **Layout:** `activity_themes.xml` (`rgThemes`, `rbLight`, `rbDark`, `rbSystem`).
- **Purpose:** Selecting Light, Dark, or System Default theme.
- **Key Logic:** Commits to `PreferenceManager` and invokes `AppCompatDelegate.setDefaultNightMode()`.

### 9. Swipe Action Settings (`ActivitySwipeAction.java`)
- **Layout:** `activity_swipe_action.xml` (Two RadioGroups for Right and Left swipe).
- **Purpose:** Customize swipe behavior in the conversation list (Read, Archive, Delete, None).

### 10. Scheduled Messages Screen (`ActivitySchedule.java`)
- **Layout:** `activity_schedule.xml` (`rvScheduled`, `llEmpty`).
- **Purpose:** View pending scheduled SMS messages; cancel and delete scheduled jobs.
- **Data Source:** `scheduledMessageDao().getAllLive()` (Room LiveData).
- **Key Logic:** Cancels pending WorkManager tasks using tag `"schedMsg_" + id`.

### 11. Backup & Restore Screen (`ActivityBackup.java`)
- **Layout:** `activity_backup.xml` (`tvLastBackup`, `btnBackup`, `btnRestore`).
- **Purpose:** Export full SMS history to a standalone JSON file or restore from a previously exported JSON backup.
- **Data Source:** Storage Access Framework (`CreateDocument`, `OpenDocument`) + Gson serialization.

### 12. Archive Screen (`ActivityArchive.java`)
- **Layout:** `activity_archive.xml` (`rvArchive`, `llEmpty`).
- **Purpose:** Holds conversations removed from the main inbox without deleting SMS history.
- **Key Logic:** Swipe or long-click unarchives thread back to main list.

### 13. Recycle Bin Screen (`ActivityRecyclebin.java`)
- **Layout:** `activity_recyclebin.xml` (`rvBin`, `btnEmptyBin`, `llEmpty`).
- **Purpose:** Soft-deleted conversations store; allows restoring or permanently deleting conversations.
- **Data Source:** `recycleBinDao().getAllLive()`.

### 14. Blocked Numbers Screen (`ActivityBlockNumber.java`)
- **Layout:** `activity_block_number.xml` (`rvBlocked`, `fabAdd`, `llEmpty`).
- **Purpose:** Manage blocked phone numbers; add new numbers or unblock existing ones.
- **Data Source:** `blockedNumberDao().getAllLive()`.

### 15. After Call Settings Screen (`ActivityAfterCallSettings.java`)
- **Layout:** `activity_after_call_settings.xml` (`ivToggle`, `btnGrantPermission`).
- **Purpose:** Enable or disable the post-call summary popup and request `READ_PHONE_STATE` and `READ_CALL_LOG` permissions.

### 16. After Call Details Screen (`AfterCallDetailActivity.java`)
- **Layout:** `activity_after_call_details.xml` (`Theme.Transparent.After.Call`).
- **Purpose:** Pops up when a phone call terminates, displaying contact name, phone number, and shortcuts to call back, send SMS, save contact, or block.

### 17. Popup Message Screen (`PopupMessageActivity.java`)
- **Layout:** `activity_popup_message.xml` (`Theme.Transparent`).
- **Purpose:** Heads-up dialog overlay appearing on top of the lock screen (`setShowWhenLocked(true)`) when a new message arrives.

---

## 8. User Interactions and Execution Tracing

### 1. Opening a Conversation
- User clicks an item in `MainActivity`.
- `ConversationAdapter.OnConversationClickListener.onClick(conv)` fires.
- Creates intent targeting `ActivityChat.class` with extras: `thread_id`, `address`, `contact_name`.
- `ActivityChat.onCreate()` parses extras, configures toolbar, checks block status via Room DAO, and invokes `loadMessages()`.
- Background thread queries `SmsHelper.getMessagesForThread(context, threadId)`, updates `MessageAdapter`, and scrolls to position `size - 1`.

### 2. Sending a Message
- User types in `etMessage` and taps `ivSend`.
- `ActivityChat.sendMessage()` extracts string, clears input field.
- Submits task to `ExecutorService`:
  ```java
  boolean success = SmsHelper.sendSms(this, address, body);
  ```
- `SmsHelper.sendSms()` divides text via `SmsManager.divideMessage()`, transmits via `sendMultipartTextMessage()`, and inserts a record into `Telephony.Sms.Sent.CONTENT_URI`.
- The insert triggers `ContentObserver`, which fires `loadMessages()`, appending the outgoing bubble to the chat stream.

### 3. Receiving a Message
- Incoming cellular PDU broadcast intercepted by `SmsReceiver.onReceive()`.
- Extracts `SmsMessage` array and reconstructs text payload.
- Checks `AppDatabase.getInstance(context).blockedNumberDao().isBlockedSync(sender)`. If blocked, execution terminates immediately.
- If app is Default SMS handler, inserts message into `Telephony.Sms.Inbox.CONTENT_URI`.
- Calls `NotificationHelper.showIncomingSmsNotification()`, showing a high-importance notification with Quick Reply (`RemoteInput`) and "Copy OTP" action.
- If Instant Reply popup is enabled in preferences, launches `PopupMessageActivity` with `FLAG_ACTIVITY_NEW_TASK`.

### 4. Swiping a Conversation
- User swipes a conversation left or right in `MainActivity`.
- `ItemTouchHelper.SimpleCallback.onSwiped(viewHolder, direction)` triggers.
- Direction checked against `prefManager.getSwipeRightAction()` or `prefManager.getSwipeLeftAction()`:
  - If `archive`: Inserts `ArchiveEntity` into Room `archiveDao` and removes from visible inbox.
  - If `delete`: Inserts `RecycleBinEntity` into Room `recycleBinDao`, deletes system thread via `SmsHelper.deleteThread()`, and refreshes list.
  - If `read`: Calls `SmsHelper.markThreadRead()`.

### 5. Scheduling an SMS
- In `ActivityChat`, user types text and clicks clock icon (`ivSchedule`).
- DatePickerDialog and TimePickerDialog prompt user for future timestamp.
- Inserts `ScheduledMessageEntity` into Room `scheduledMessageDao` with status `"PENDING"`.
- Calculates delay: `scheduledTime - System.currentTimeMillis()`.
- Enqueues `OneTimeWorkRequest` to WorkManager targeting `ScheduledSmsWorker` with delay and input data.
- When timer elapses, `ScheduledSmsWorker.doWork()` runs, transmits SMS via `SmsHelper.sendSms()`, and updates Room status to `"SENT"`.

---

## 9. Android Manifest, Permissions & Default SMS Compliance

### Default SMS App Compliance (Android Specification)
For an Android application to be eligible to hold the `RoleManager.ROLE_SMS` role and become the default messaging application, Android OS strictly mandates the implementation of four specific components:
1. **Activity handling `SENDTO` / `SEND`:** `ActivitySearchContact` declared with intent filters for `sms:`, `smsto:`, `mms:`, `mmsto:`.
2. **BroadcastReceiver handling `SMS_DELIVER`:** `SmsReceiver` declared with `android.permission.BROADCAST_SMS` permission and priority 999.
3. **BroadcastReceiver handling `WAP_PUSH_DELIVER`:** `MmsReceiver` declared with `android.permission.BROADCAST_WAP_PUSH` permission and MIME type `application/vnd.wap.mms-message`.
4. **Service handling `RESPOND_VIA_MESSAGE`:** `HeadlessSmsSendService` declared with `android.permission.SEND_RESPOND_VIA_MESSAGE` permission.

All four requirements are implemented and verified in `app/src/main/AndroidManifest.xml`.

### Declared Permissions Analysis

| Permission | Category | Why It Is Required in This Application |
|---|---|---|
| `READ_SMS` | Dangerous | Read messages from the system telephony provider (`content://sms`). |
| `WRITE_SMS` | Dangerous | Insert, delete, and mark messages as read in the telephony provider. |
| `SEND_SMS` | Dangerous | Transmit cellular SMS messages via `SmsManager`. |
| `RECEIVE_SMS` | Dangerous | Intercept incoming SMS broadcasts in `SmsReceiver`. |
| `RECEIVE_MMS` | Dangerous | Required for default SMS app compliance to receive multimedia broadcasts. |
| `RECEIVE_BOOT_COMPLETED` | Normal | Allows `BootReceiver` to re-enqueue pending scheduled SMS after device reboots. |
| `READ_CONTACTS` | Dangerous | Resolves sender phone numbers to contact names and avatar photos. |
| `WRITE_CONTACTS` | Dangerous | Required to allow saving unknown numbers directly to contacts from the after-call screen. |
| `READ_PHONE_STATE` | Dangerous | Detects call states (`RINGING`, `OFFHOOK`, `IDLE`) in `CallReceiver` to trigger After Call features. |
| `READ_CALL_LOG` | Dangerous | Reads call details (duration, caller) displayed in `AfterCallDetailActivity`. |
| `READ_PHONE_NUMBERS` | Dangerous | Identifies active SIM telephone numbers on dual-SIM devices. |
| `PROCESS_OUTGOING_CALLS` | Dangerous | Legacy permission to capture dialed numbers for call summary tracking. |
| `SCHEDULE_EXACT_ALARM` | Normal | Allows precise timing for scheduled messages. |
| `POST_NOTIFICATIONS` | Dangerous (Android 13+) | Displays heads-up incoming SMS notifications and Quick Reply prompts. |
| `INTERNET` | Normal | Standard permission for Glide remote avatar loading or external web links. |
| `VIBRATE` | Normal | Triggers haptic feedback upon receiving notifications. |
| `WAKE_LOCK` | Normal | Keeps CPU awake briefly when processing incoming SMS or showing lock-screen popup. |

---

## 10. Important Source Code Explanation

### 1. `MainActivity.java`
- **Location:** `app/src/main/java/com/example/message/MainActivity.java`
- **Responsibility:** Main screen orchestrating conversation list rendering, navigation drawer, swipe gestures, selection mode, and live ContentObserver updates.
- **Key Code Block:**
  ```java
  private void registerSmsObserver() {
      smsObserver = new ContentObserver(mainHandler) {
          @Override
          public void onChange(boolean selfChange) {
              super.onChange(selfChange);
              loadConversations();
          }
      };
      getContentResolver().registerContentObserver(Uri.parse("content://sms"), true, smsObserver);
  }
  ```
  *Explanation:* Dynamically listens to the Android telephony provider. Whenever an SMS is sent, received, or deleted anywhere on the device, `onChange` triggers `loadConversations()` on a background thread, refreshing the UI without manual user intervention.

### 2. `ActivityChat.java`
- **Location:** `app/src/main/java/com/example/message/activities/ActivityChat.java`
- **Responsibility:** Manages 1-on-1 chat history, message sending, message scheduling via WorkManager, block banner display, and action callbacks (copy, forward, delete).
- **Key Code Block:**
  ```java
  private void scheduleMessage(String body, long scheduledTime) {
      executor.execute(() -> {
          ScheduledMessageEntity entity = new ScheduledMessageEntity(address, contactName, body, scheduledTime);
          long id = AppDatabase.getInstance(this).scheduledMessageDao().insert(entity);
          long delay = scheduledTime - System.currentTimeMillis();
          Data inputData = new Data.Builder()
                  .putLong("scheduled_id", id)
                  .putString("address", address)
                  .putString("message", body)
                  .build();
          OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(ScheduledSmsWorker.class)
                  .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                  .setInputData(inputData)
                  .addTag("schedMsg_" + id)
                  .build();
          WorkManager.getInstance(this).enqueue(workRequest);
      });
  }
  ```
  *Explanation:* Creates a persistent database record in Room, then packages the parameters into a delayed `OneTimeWorkRequest`. WorkManager guarantees execution even if the user swipes away the application.

### 3. `SmsReceiver.java`
- **Location:** `app/src/main/java/com/example/message/receivers/SmsReceiver.java`
- **Responsibility:** High-priority broadcast receiver processing live incoming SMS transmissions, filtering blocked senders, parsing OTPs, handling inline notification replies, and launching lockscreen popups.
- **Key Code Block:**
  ```java
  if ("com.example.message.ACTION_REPLY".equals(action)) {
      Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
      if (remoteInput != null) {
          CharSequence replyText = remoteInput.getCharSequence(NotificationHelper.KEY_TEXT_REPLY);
          String address = intent.getStringExtra("address");
          if (replyText != null && address != null) {
              Executors.newSingleThreadExecutor().execute(() -> {
                  SmsHelper.sendSms(context, address, replyText.toString());
              });
          }
      }
      return;
  }
  ```
  *Explanation:* Implements Android Direct Reply. Extracts user input typed into the system notification shade via `RemoteInput`, immediately dispatching the reply via `SmsHelper.sendSms()` on a background thread without launching an Activity.

### 4. `MessageAdapter.java`
- **Location:** `app/src/main/java/com/example/message/adapters/MessageAdapter.java`
- **Responsibility:** RecyclerView adapter rendering distinct incoming and outgoing chat bubbles, formatting timestamps, extracting OTP numbers, and providing long-press context menus.
- **Key Code Block:**
  ```java
  private void detectOtp(String body, TextView otpPill) {
      if (body == null) { otpPill.setVisibility(View.GONE); return; }
      Matcher matcher = OTP_PATTERN.matcher(body);
      if (matcher.find()) {
          String otp = matcher.group(1);
          otpPill.setText("Copy OTP: " + otp);
          otpPill.setVisibility(View.VISIBLE);
          otpPill.setOnClickListener(v -> {
              ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
              if (cm != null) {
                  cm.setPrimaryClip(ClipData.newPlainText("OTP", otp));
                  Toast.makeText(context, "OTP copied: " + otp, Toast.LENGTH_SHORT).show();
              }
          });
      } else {
          otpPill.setVisibility(View.GONE);
      }
  }
  ```
  *Explanation:* Inspects the message body with regular expressions. If an authentication code pattern is found, displays an interactive chip directly above the chat bubble allowing one-tap clipboard copying.

### 5. `SmsHelper.java`
- **Location:** `app/src/main/java/com/example/message/utils/SmsHelper.java`
- **Responsibility:** Data repository managing low-level interactions with Android Telephony Content Providers (`Telephony.Sms`, `content://mms-sms/`).
- **Key Code Block:**
  ```java
  public static boolean sendSms(Context context, String destination, String text) {
      try {
          SmsManager smsManager = SmsManager.getDefault();
          ArrayList<String> parts = smsManager.divideMessage(text);
          smsManager.sendMultipartTextMessage(destination, null, parts, null, null);
          ContentValues values = new ContentValues();
          values.put("address", destination);
          values.put("body", text);
          values.put("date", System.currentTimeMillis());
          values.put("read", 1);
          values.put("type", Telephony.Sms.MESSAGE_TYPE_SENT);
          values.put("thread_id", getOrCreateThreadId(context, destination));
          context.getContentResolver().insert(Telephony.Sms.Sent.CONTENT_URI, values);
          return true;
      } catch (Exception e) {
          e.printStackTrace();
          return false;
      }
  }
  ```
  *Explanation:* Safely handles multi-part SMS messages exceeding 160 characters via `divideMessage()`, transmits them via hardware telephony, and mirrors the record into the native Android Sent SMS database.

---

## 11. Manager Interview Preparation

Here are **40 practical technical questions** with both concise conversational answers and in-depth architectural explanations based directly on this codebase.

---

### General Architecture & Core Stack

#### Q1: Which programming language did you use to build this application?
- **Simple Answer:** "We built the application in pure Java (Java 11) using modern Android Jetpack libraries."
- **Technical Explanation:** "All classes under `app/src/main/java` are authored in Java, configured in `app/build.gradle.kts` with `JavaVersion.VERSION_11`. The build system uses Gradle with Kotlin DSL, and Android SDK targets API level 37."

#### Q2: What architecture does the application follow?
- **Simple Answer:** "It follows a clean Layered Architecture with View, Helper/Repository, and Database layers."
- **Technical Explanation:** "UI logic resides in Activities and Adapters; data retrieval is abstracted through `SmsHelper` and `ContactHelper`; local caching is managed by Room and SharedPreferences; and background jobs are handled by WorkManager and BroadcastReceivers."

#### Q3: Why didn’t you use Jetpack Compose for the UI?
- **Simple Answer:** "We utilized classical XML layouts and RecyclerView to ensure stability, predictable memory management, and compatibility with custom Material 3 DayNight themes."
- **Technical Explanation:** "The project uses XML layouts with scalable dimension units (`sdp` and `ssp`) and classical ViewHolders (`MessageAdapter`, `ConversationAdapter`). This offers high rendering performance and zero Compose-runtime overhead."

#### Q4: How is Dependency Injection implemented?
- **Simple Answer:** "We did not use heavy DI frameworks like Dagger or Hilt; instead, we used thread-safe Singleton patterns."
- **Technical Explanation:** "Classes like `AppDatabase` and `PreferenceManager` use double-checked locking and synchronized factory methods (`getInstance(context)`). This keeps the APK lightweight and eliminates annotation processing overhead."

---

### Messaging & Telephony Integration

#### Q5: Where does the message data come from?
- **Simple Answer:** "It comes from the native Android Telephony Content Provider."
- **Technical Explanation:** "We query `content://mms-sms/conversations?simple=true` for conversation threads and `Telephony.Sms.CONTENT_URI` (`content://sms`) for individual message streams."

#### Q6: How does the application qualify as the Android Default SMS App?
- **Simple Answer:** "We implemented all four components required by Android OS: a compose activity, SMS receiver, MMS receiver, and headless service."
- **Technical Explanation:** "The manifest defines: 1) `ActivitySearchContact` handling `SENDTO` (`sms:`, `smsto:`), 2) `SmsReceiver` with `BROADCAST_SMS` handling `SMS_DELIVER`, 3) `MmsReceiver` with `BROADCAST_WAP_PUSH` handling `WAP_PUSH_DELIVER`, and 4) `HeadlessSmsSendService` handling `RESPOND_VIA_MESSAGE`."

#### Q7: How are incoming and outgoing messages differentiated in the chat screen?
- **Simple Answer:** "The adapter uses view types: incoming messages inflate a gray bubble on the left; outgoing inflate a blue bubble on the right."
- **Technical Explanation:** "`MessageAdapter.getItemViewType()` returns `TYPE_INCOMING` (0) when `item.isIncoming()` is true (`type == 1`), and `TYPE_OUTGOING` (1) otherwise. These inflate `item_message_in.xml` and `item_message_out.xml` respectively."

#### Q8: How does the app update the screen when a new message arrives?
- **Simple Answer:** "Through a `ContentObserver` that listens for changes to the SMS database."
- **Technical Explanation:** "`MainActivity` and `ActivityChat` register a `ContentObserver` on `content://sms`. When `SmsReceiver` inserts an incoming message, Android notifies the observer, which reloads messages on a background executor."

#### Q9: How are long messages over 160 characters handled during sending?
- **Simple Answer:** "We use `SmsManager.divideMessage()` to split long texts into multi-part SMS."
- **Technical Explanation:** "In `SmsHelper.sendSms()`, `smsManager.divideMessage(text)` segments the text, and `smsManager.sendMultipartTextMessage()` sends the parts seamlessly across the cellular network."

#### Q10: How does the app extract and copy OTP verification codes?
- **Simple Answer:** "We run a regular expression check on every message to detect 4 to 8-digit codes and display a one-tap 'Copy OTP' button."
- **Technical Explanation:** "In `MessageAdapter.detectOtp()`, regex `\\b(\\d{4,8})\\b` matches numbers in messages containing words like 'otp', 'code', or 'verification'. An interactive `TextView` pill is displayed, which copies the code to `ClipboardManager` when clicked."

---

### RecyclerView & Performance

#### Q11: How does `RecyclerView` work in this app?
- **Simple Answer:** "`RecyclerView` recycles views that scroll off-screen, rebinding them to new data to conserve memory."
- **Technical Explanation:** "Instead of creating a new `View` for every message or conversation, `RecyclerView` uses `ViewHolder` objects. When a row scrolls out of view, its container is recycled and passed to `onBindViewHolder` with new data."

#### Q12: What is the purpose of an `Adapter`?
- **Simple Answer:** "An adapter acts as a bridge between the raw data list and the UI layout."
- **Technical Explanation:** "It converts objects (e.g., `MessageItem`) into visible views (`ViewHolder`). It determines row count (`getItemCount`), inflates layouts (`onCreateViewHolder`), and binds fields (`onBindViewHolder`)."

#### Q13: Why did you set `setStackFromEnd(true)` on `ActivityChat`'s RecyclerView?
- **Simple Answer:** "To make the message list start from the bottom, exactly like standard chat applications."
- **Technical Explanation:** "`layoutManager.setStackFromEnd(true)` instructs the layout manager to fill viewports from the bottom up. Combined with `scrollToPosition(size - 1)`, the latest messages are always in view."

#### Q14: How does multi-selection work in `ConversationAdapter`?
- **Simple Answer:** "Long-pressing a conversation turns on selection mode, and tapping items toggles their selected state in a `Set`."
- **Technical Explanation:** "The adapter maintains `Set<Long> selectedIds`. When selection mode is active, clicks toggle IDs inside the set and notify the adapter. The toolbar updates to show the selected count."

#### Q15: How are swipe gestures implemented on conversations?
- **Simple Answer:** "We attached an `ItemTouchHelper` to the `RecyclerView`."
- **Technical Explanation:** "`ItemTouchHelper.SimpleCallback` listens for `LEFT` and `RIGHT` swipes. When swiped, it reads user preferences (`prefManager.getSwipeRightAction()`) to trigger Archive, Delete, or Mark Read."

---

### Database & Persistence

#### Q16: What is Room Database and why is it used?
- **Simple Answer:** "Room is a Jetpack library that provides an abstraction layer over SQLite, making database operations safe and structured."
- **Technical Explanation:** "Room maps Java classes to database tables (`@Entity`), verifies SQL queries at compile time (`@Dao`), and provides database migration support. We use it for blocked numbers, archives, recycle bin, and scheduled SMS."

#### Q17: What is the difference between synchronous Room calls and `LiveData` Room calls?
- **Simple Answer:** "Synchronous calls run once and return data immediately; LiveData calls automatically notify the UI whenever table data changes."
- **Technical Explanation:** "Synchronous queries (e.g., `getAllSync()`) return raw `List<T>` on worker threads. LiveData queries (e.g., `getAllLive()`) return observable wrappers that automatically emit fresh lists when rows are inserted or deleted."

#### Q18: What is stored in `SharedPreferences`?
- **Simple Answer:** "Small key-value preferences like selected language, theme mode, font size, and swipe actions."
- **Technical Explanation:** "`PreferenceManager.java` uses Android `SharedPreferences` in private mode (`Context.MODE_PRIVATE`) to persist onboarding state, theme mode (0/1/2), language ISO codes, and feature toggles."

#### Q19: How does the Recycle Bin feature work?
- **Simple Answer:** "Deleting a conversation moves its record to Room's recycle bin table before removing it from the system inbox."
- **Technical Explanation:** "`RecycleBinEntity` stores the thread ID, address, snippet, and deletion timestamp. `ActivityRecyclebin` displays these soft-deleted items, allowing users to either restore them or delete them permanently."

---

### Background Execution & Scheduling

#### Q20: What is WorkManager and why is it used for scheduled SMS?
- **Simple Answer:** "WorkManager guarantees task execution even if the app closes or the phone restarts."
- **Technical Explanation:** "`ScheduledSmsWorker` extends `Worker`. When scheduling an SMS, `ActivityChat` computes the delay and enqueues a `OneTimeWorkRequest`. WorkManager stores the request in internal SQLite and triggers execution at the specified time."

#### Q21: What happens to scheduled messages when the phone reboots?
- **Simple Answer:** "`BootReceiver` catches the reboot broadcast and re-schedules all pending messages."
- **Technical Explanation:** "When `Intent.ACTION_BOOT_COMPLETED` is received, `BootReceiver` queries `scheduledMessageDao().getPendingSync()`, calculates remaining delay, and enqueues fresh `OneTimeWorkRequest` tasks into WorkManager."

#### Q22: How does the After Call feature work?
- **Simple Answer:** "`CallReceiver` detects when a phone call ends and shows a summary screen."
- **Technical Explanation:** "`CallReceiver` monitors `TelephonyManager.ACTION_PHONE_STATE_CHANGED`. When state transitions from `OFFHOOK` or `RINGING` to `IDLE`, it launches `AfterCallDetailActivity` displaying contact info, call duration, and quick-action buttons."

---

### Theming & Localization

#### Q23: How does dark mode work in this application?
- **Simple Answer:** "We use Material 3 DayNight themes and call `AppCompatDelegate.setDefaultNightMode()`."
- **Technical Explanation:** "Styles inherit from `Theme.Material3.DayNight.NoActionBar`. Color resources are split between `values/colors.xml` and `values-night/colors.xml`. When the mode changes, `AppCompatDelegate` recreates activities to load dark resources."

#### Q24: How does dynamic language selection work without restarting the whole phone?
- **Simple Answer:** "We update the configuration context inside `attachBaseContext()` with the selected locale."
- **Technical Explanation:** "`LocaleHelper.setLocale()` creates a new `Configuration` with the chosen `LocaleList`. `BaseActivity.attachBaseContext()` injects this configuration into every activity, forcing strings to reload from the appropriate `values-<lang>` folder."

#### Q25: How does font scaling work?
- **Simple Answer:** "A slider dialog lets users choose small, normal, large, or extra-large text, updating `configuration.fontScale`."
- **Technical Explanation:** "`FontHelper.applyFontScale()` reads the font scale index from `PreferenceManager` (0.85f, 1.0f, 1.15f, 1.30f), updates `Configuration.fontScale`, and re-attaches the context."

---

### Backup, Restore & Serialization

#### Q26: How does the Backup and Restore feature work?
- **Simple Answer:** "We serialize all messages into a JSON file using Gson and save it via Android's file picker."
- **Technical Explanation:** "`ActivityBackup` queries `SmsHelper.getMessages()` across all threads, wraps them into `BackupData`, and serializes the tree using Gson. Android's Storage Access Framework (`CreateDocument`) writes the JSON stream to the user's chosen location."

#### Q27: How does Restore avoid duplicating thread IDs across devices?
- **Simple Answer:** "It uses `Telephony.Threads.getOrCreateThreadId()` to ensure messages attach to valid local threads."
- **Technical Explanation:** "During restore, `SmsHelper.insertMessage()` inserts each record into `Telephony.Sms.Inbox` or `Sent`. If the thread ID is invalid, it resolves the canonical thread ID via `getOrCreateThreadId(context, address)`."

---

### Security, Lifecycle & Permissions

#### Q28: How does the app handle runtime permissions on Android 13+?
- **Simple Answer:** "We use modern `ActivityResultLauncher` contracts to request `POST_NOTIFICATIONS`, `READ_SMS`, and `READ_CONTACTS`."
- **Technical Explanation:** "Instead of legacy `requestPermissions()`, we register `ActivityResultContracts.RequestMultiplePermissions()`. For notifications on API 33+, we explicitly check `Manifest.permission.POST_NOTIFICATIONS`."

#### Q29: What happens when a blocked number sends an SMS?
- **Simple Answer:** "`SmsReceiver` checks the block list and drops the message before it reaches the inbox or creates a notification."
- **Technical Explanation:** "In `SmsReceiver.onReceive()`, `db.blockedNumberDao().isBlockedSync(sender)` runs on a worker thread. If true, the receiver returns immediately without inserting to `Telephony.Sms.Inbox` and without calling `NotificationHelper`."

#### Q30: What is the purpose of `BaseActivity`?
- **Simple Answer:** "It centralizes theme application, font scaling, and locale management for every screen."
- **Technical Explanation:** "By inheriting from `BaseActivity`, child activities don't need duplicate theme, font, or locale boilerplate. `attachBaseContext()` and `onCreate()` guarantee consistent styling across the app."

#### Q31: How do you prevent memory leaks when using `ContentObserver`?
- **Simple Answer:** "We unregister the observer in `onDestroy()`."
- **Technical Explanation:** "Both `MainActivity` and `ActivityChat` call `getContentResolver().unregisterContentObserver(smsObserver)` in their `onDestroy()` callbacks, preventing leaked context references."

#### Q32: Why did you use `Executors.newSingleThreadExecutor()` instead of AsyncTasks?
- **Simple Answer:** "`AsyncTask` is deprecated; `ExecutorService` provides clean, reliable background execution."
- **Technical Explanation:** "`AsyncTask` was deprecated in API 30 due to lifecycle coupling and thread pool starvation issues. A dedicated `ExecutorService` ensures sequential, off-UI-thread execution, posting results back via `Handler(Looper.getMainLooper())`."

---

### Gradle, Compilation & Tools

#### Q33: What is Gradle and how does it compile an Android APK?
- **Simple Answer:** "Gradle is the build automation tool that compiles Java code, merges resources, and packages everything into an APK."
- **Technical Explanation:** "Gradle runs tasks: 1) `aapt2` compiles XML layouts and resources into `R.java`, 2) `javac` compiles Java source code into bytecode (`.class`), 3) `d8/r8` desugars and converts bytecode into Dalvik Executable (`classes.dex`), and 4) `apkbuilder` signs and zips resources and dex files into the APK."

#### Q34: What is the purpose of `libs.versions.toml`?
- **Simple Answer:** "It is a central version catalog that manages library versions in one place."
- **Technical Explanation:** "Gradle Version Catalogs (`libs.versions.toml`) centralize dependency coordinates and versions under `[versions]`, `[libraries]`, and `[plugins]`, preventing version mismatches across build scripts."

#### Q35: What are `sdp` and `ssp` libraries?
- **Simple Answer:** "They are dimension libraries that scale layout sizes and fonts proportionally to the screen size."
- **Technical Explanation:** "`sdp` (scalable dp) and `ssp` (scalable sp) compute dimensions dynamically based on screen width buckets, ensuring consistent UI layouts across phone and tablet screens."

#### Q36: What is the purpose of `dataExtractionRules` and `fullBackupContent` in the Manifest?
- **Simple Answer:** "They control which private app files are backed up to Google Drive cloud backup."
- **Technical Explanation:** "Introduced in Android 12, `data_extraction_rules.xml` and `backup_rules.xml` define fine-grained inclusion and exclusion rules for Google Drive cloud backup and device-to-device transfers."

#### Q37: How does `ContactHelper` avoid lagging when querying contacts?
- **Simple Answer:** "It caches contact names and photo URIs in memory."
- **Technical Explanation:** "`ContactHelper` maintains static `Map<String, String> nameCache` and `photoCache`. When a number is looked up once via `ContactsContract.PhoneLookup`, the result is cached, avoiding expensive repeated ContentProvider queries."

#### Q38: How does inline notification reply work?
- **Simple Answer:** "It uses Jetpack `RemoteInput` and passes the user's text back to `SmsReceiver`."
- **Technical Explanation:** "`NotificationHelper` creates a `RemoteInput` tied to `KEY_TEXT_REPLY`. The action launches a `PendingIntent` targeting `SmsReceiver` with action `ACTION_REPLY`. `SmsReceiver` extracts the text via `RemoteInput.getResultsFromIntent()` and calls `SmsHelper.sendSms()`."

#### Q39: What is `HeadlessSmsSendService`?
- **Simple Answer:** "It is an Android service that handles quick response messages sent from notifications or car displays."
- **Technical Explanation:** "Android requires default SMS apps to declare a service with permission `SEND_RESPOND_VIA_MESSAGE` to handle quick text replies when users reject incoming phone calls."

#### Q40: What would happen if `RoomDatabase.fallbackToDestructiveMigration()` was removed and the schema changed?
- **Simple Answer:** "The application would crash with an `IllegalStateException` on launch."
- **Technical Explanation:** "If the Room schema version is bumped without providing a concrete `Migration` object, Room throws an `IllegalStateException`. `fallbackToDestructiveMigration()` safely drops and recreates tables when no migration path exists."

---

## 12. Practical End-to-End Walkthrough Scenarios

### Scenario 1: The User Opens a Conversation and Replies
1. **User Action:** Taps contact "John Doe" in `MainActivity`.
2. **Intent Navigation:** `MainActivity` launches `ActivityChat` passing `thread_id = 45`, `address = "+1234567890"`, and `contact_name = "John Doe"`.
3. **Database Read:** `ActivityChat.loadMessages()` runs `SmsHelper.getMessagesForThread(context, 45)` inside an `ExecutorService`.
4. **Mark As Read:** `SmsHelper.markThreadAsRead(context, 45)` updates `Telephony.Sms.Inbox` setting `read = 1`.
5. **UI Rendering:** Main handler delivers the `List<MessageItem>` to `MessageAdapter.setMessages()`. The `RecyclerView` displays historical incoming and outgoing bubbles and scrolls to the bottom.
6. **User Input:** User types "See you at 5!" and clicks `ivSend`.
7. **Transmission:** `ActivityChat.sendMessage()` delegates to `SmsHelper.sendSms(context, "+1234567890", "See you at 5!")`.
8. **OS Dispatch:** `SmsManager.getDefault().sendMultipartTextMessage()` hands the payload to the cellular radio.
9. **Telephony Record:** `SmsHelper` inserts the message into `Telephony.Sms.Sent.CONTENT_URI`.
10. **Reactive Update:** The insert triggers `ContentObserver.onChange()`, prompting `ActivityChat` to reload messages. The new blue outgoing bubble appears instantly with a sent confirmation icon.

### Scenario 2: Receiving an SMS with an Authentication OTP
1. **Cellular Inbound:** An SMS arrives containing: *"Your verification code is 849201. Do not share."*
2. **Receiver Activation:** The OS broadcasts `SMS_DELIVER_ACTION`, waking up `SmsReceiver.onReceive()`.
3. **Block Check:** `AppDatabase.getInstance(context).blockedNumberDao().isBlockedSync(sender)` confirms the sender is not blocked.
4. **Provider Insert:** `SmsReceiver` inserts the message into `Telephony.Sms.Inbox.CONTENT_URI`.
5. **OTP Parsing:** Regex pattern `\b(\d{4,8})\b` extracts `"849201"`.
6. **Notification Display:** `NotificationHelper.showIncomingSmsNotification()` posts a high-importance notification displaying:
   - Direct Reply input box (`RemoteInput`).
   - Action Button: `"Copy OTP: 849201"`.
7. **Observer Notification:** Active chat screens reload automatically via their `ContentObserver`.
8. **In-App OTP Chip:** If the conversation is open, `MessageAdapter.detectOtp()` reveals a `"Copy OTP: 849201"` pill. Clicking the pill copies `"849201"` to the clipboard and shows a toast.

---

## 13. Technical Glossary for Android Development

- **Activity:** An application component providing a single screen with a user interface where users interact.
- **Fragment:** A modular, reusable section of an activity hosting its own view hierarchy and lifecycle.
- **Intent:** An asynchronous messaging object used to request an action from another app component (e.g., launching an activity or delivering a broadcast).
- **BroadcastReceiver:** An Android component that listens for system-wide broadcast announcements (e.g., incoming SMS, boot completion, call state changes).
- **Service:** An application component that performs long-running operations in the background without a user interface.
- **ContentProvider / ContentResolver:** A standardized mechanism to manage and query structured data across application sandboxes (e.g., Telephony SMS database, Contacts).
- **RecyclerView:** A memory-efficient UI container that recycles off-screen view holders when rendering large lists or grids.
- **ViewHolder:** A design pattern object caching references to child views inside an item layout, eliminating repeated `findViewById` calls.
- **Adapter:** A controller binding an underlying dataset to `ViewHolder` rows within a `RecyclerView`.
- **ItemTouchHelper:** An AndroidX utility class providing swipe-to-dismiss and drag-and-drop gestures on `RecyclerView` items.
- **Room Database:** A Google Jetpack persistence library providing compile-time verified object-relational mapping (ORM) over SQLite.
- **DAO (Data Access Object):** A Room interface declaring SQL queries, inserts, updates, and deletes.
- **LiveData:** A lifecycle-aware observable data holder that updates active UI observers when database records change.
- **WorkManager:** The recommended Android Jetpack library for persistent, deferrable, and guaranteed background work.
- **SharedPreferences:** A lightweight framework framework storing private primitive key-value pairs in XML files.
- **Storage Access Framework (SAF):** Android's secure file picker API (`CreateDocument`, `OpenDocument`) enabling document read/write across storage providers without requiring broad storage permissions.
- **AppCompatDelegate:** A support library delegate controlling night mode (`MODE_NIGHT_YES`, `MODE_NIGHT_NO`, `MODE_NIGHT_FOLLOW_SYSTEM`) and back-ported UI behaviors.
- **RoleManager:** An Android 10+ (API 29) system service managing system roles like `RoleManager.ROLE_SMS` for default apps.
- **RemoteInput:** An Android Notification API capturing user text entered directly within notification shades for inline quick replies.
- **DEX (Dalvik Executable):** The compiled bytecode format (`.dex`) executed by the Android Runtime (ART) on target devices.

---

## 14. How I Can Explain My Android Application to My Manager

*(Practice speaking this natural 3–5 minute presentation during your technical review or 1-on-1 meeting)*

> "Hi [Manager's Name], today I’d like to walk you through the architecture and implementation of our Android messaging application, **Messages**.
>
> At its core, this application is a **full-featured, production-ready Default SMS and MMS Client** built natively in Java for Android 8.0 up to Android 14 and 15 (API level 37).
>
> We designed the application around three key architectural priorities: **clean separation of concerns**, **instant responsive UI**, and **robust background reliability**.
>
> First, for the architecture, we adopted a structured Layered pattern. The UI layer uses classical Android XML with Google Material Design 3 and responsive dimension libraries (`sdp` and `ssp`) to ensure consistent visual hierarchy across all phone screen sizes. We deliberately chose not to introduce Compose to keep APK size minimal, avoid unnecessary runtime dependencies, and maintain fine-grained control over view recycling.
>
> For data management, we interact directly with the Android Telephony Content Provider using a custom `SmsHelper` repository. Because telephony queries can be slow on large databases, all CRUD queries run asynchronously on dedicated worker threads via `ExecutorService` and post back to the UI thread using main Looper handlers. To make the interface feel live, both our conversation list and chat screen use a `ContentObserver` on `content://sms`. This means whenever a message arrives or is sent, the UI refreshes instantly without any periodic polling.
>
> In addition to standard messaging, we built several standout smart features:
> 1. **Automated OTP Detection:** Incoming messages are parsed with regular expressions. When an authentication code is detected, an interactive chip appears above the message bubble and inside the notification, allowing the user to copy their OTP with a single tap.
> 2. **Scheduled SMS via WorkManager:** Users can pick a future date and time to send a message. We store the message in Room and schedule it with WorkManager. WorkManager guarantees execution even if the app is killed, and our `BootReceiver` re-enqueues pending jobs if the phone restarts.
> 3. **After Call Intelligence:** We implemented a `CallReceiver` that detects when phone calls end, presenting a non-intrusive popup with the caller's details and quick shortcuts to reply via SMS, call back, or block the number.
> 4. **Theming and Localization:** We support Light Mode, Dark Mode, and System Default using `AppCompatDelegate` backed by Material DayNight themes. In addition, we built an in-app localization engine supporting 11 languages, overriding configuration contexts dynamically inside our custom `BaseActivity` so language switches happen seamlessly without restarting the phone.
> 5. **Backup and Restore:** Users can export their full conversation history to a structured JSON file and restore it on any device using Android’s secure Storage Access Framework and Google Gson.
>
> To ensure complete compliance with Android platform security standards, we implemented all four mandatory components required for default SMS handlers: the compose activity with scheme filters, the `SMS_DELIVER` receiver with broadcast permissions, the `WAP_PUSH_DELIVER` MMS receiver, and the headless `RESPOND_VIA_MESSAGE` service.
>
> The codebase is clean, modular, and fully documented in our root architectural documentation. I'm ready to walk you through any specific class, data flow, or implementation detail you'd like to explore."
