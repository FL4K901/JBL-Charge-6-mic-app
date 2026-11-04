# 🎙️ Mic — Karaoke Microphone App for JBL Charge 6

[![Build & Release APK](https://github.com/FL4K901/JBL-Charge-6-mic-app/actions/workflows/build_and_release.yml/badge.svg)](https://github.com/FL4K901/JBL-Charge-6-mic-app/actions/workflows/build_and_release.yml)
[![Latest Release](https://img.shields.io/github/v/release/FL4K901/JBL-Charge-6-mic-app?color=00E5FF&label=APK%20Download)](https://github.com/FL4K901/JBL-Charge-6-mic-app/releases/latest)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Samsung%20S21%20Ultra-blue)](https://github.com/FL4K901/JBL-Charge-6-mic-app)
[![Audio Engine](https://img.shields.io/badge/Audio-Low%20Latency%20DSP-7000FF)](https://github.com/FL4K901/JBL-Charge-6-mic-app)

> אפליקציית מיקרופון קריוקי מקצועית בזמן אמת עבור מכשיר **Samsung Galaxy S21 Ultra** ורמקול **JBL Charge 6** (ובעבור כל רמקול בלוטות' / אוזניות).  
> האפליקציה מזרימה את קולך בזמן אמת מהמיקרופון לרמקול הבלוטות' ומאפשרת **לנגן בו-זמנית שירים ברקע מ-Spotify, YouTube או Apple Music ללא השתקה או הנמכת ווליום (Zero Audio Focus Theft)!**

---

## 📲 הורדת ה-APK להתקנה על הטלפון (Download APK)

באפשרותך להוריד את קובץ ה-APK ישירות למכשיר הסלולרי שלך:

1. 📥 **[לחץ כאן להורדת ה-APK העדכני (GitHub Releases)](https://github.com/FL4K901/JBL-Charge-6-mic-app/releases/latest)**
2. לחץ על **`Mic-Karaoke-v1.0.0.apk`** (או `app-release.apk`) להורדה.
3. פתח את הקובץ בטלפון ולחץ **התקן (Install)** (אם זו פעם ראשונה, אשר "התקנה ממקורות לא מוכרים" בדפדפן).
4. אפשר הרשאות מיקרופון ובלוטות' — והתחל לשיר!

> 🔄 **בנייה אוטומטית בענן:** בכל פעם שמתבצע עדכון בקוד, [GitHub Actions Workflow](https://github.com/FL4K901/JBL-Charge-6-mic-app/actions) בונה אוטומטית APK חדש ומפרסם אותו בעמוד ה-Releases!

---

## ✨ תכונות עיקריות (Key Features)

- ⚡ **שיהוי נמוך במיוחד (Ultra-Low Latency DSP):** שימוש ב-HAL Burst Buffer המקורי של סמסונג (192–240 דגימות ב-48kHz) עם עדיפות מעבד `URGENT_AUDIO` לשיהוי פנימי של פחות מ-5ms!
- 🎵 **מיקס מלא עם מוזיקת רקע (Concurrent Media Mixing):** האפליקציה אינה תופסת Audio Focus בלעדי, כך ש-Spotify, YouTube ו-Apple Music ממשיכים לנגן כרגיל בעוצמה מלאה בזמן שאתה שר לתוך הרמקול.
- 🎛️ **אפקטים קוליים מתקדמים (DSP Effects):**
  - **Mic Volume (Preamp Booster):** הגברת עוצמת המיקרופון (עד 250%) עם מנגנון Soft-Saturation אנלוגי המונע עיוות דיגיטלי (Digital Clipping).
  - **Echo (הד):** קו השהיה מעגלי (Delay Buffer) מתכוונן (50ms–600ms) עם דעיכת פידבק טבעית.
  - **Reverb (הדהוד קונצרט):** אלגוריתם Schroeder/Freeverb (4 מסנני Comb מקבילים + 2 מסנני All-Pass) היוצר נפח קולי עשיר של אולם הופעות.
- 🛡️ **הגנה מפני צפצופי פידבק (Anti-Howling Protection):** זיהוי אוטומטי של תהודה אקוסטית (צפצוף Howling) והנחתה מידית של 18dB- להגנה על האוזניים ועל הדרייברים של הרמקול.
- 📴 **השתקה אוטומטית בהתנתקות (Bluetooth Disconnect Auto-Mute):** אם הרמקול JBL מתנתק או נכבה, המיקרופון מושתק מיידית כדי למנוע צפצוף מחריש אוזניים מרמקול הטלפון.
- 📞 **השהיה אוטומטית בשיחות טלפון (Call Interruption Handling):** בעת קבלת שיחה, האפליקציה מפנה אוטומטית את המיקרופון לטובת השיחה ומחדשת את הקריוקי בסיום השיחה.
- 📱 **עבודה מלאה ברקע (Foreground Service):** פועל גם כשהמסך כבוי או כשאתה פותח את אפליקציית המילים / נגן המוזיקה, עם התראה קבועה הכוללת כפתור Mute מהיר.
- 🍏 **עיצוב נקי ויוקרתי בסגנון Apple:** ממשק Cupertino Dark Glassmorphism, כפתור ענק מואר בטבעת ניאון פועמת, סליידרים בצורת קפסולות בסגנון Control Center של iOS, ומד ווליום (VU Meter) מדויק בזמן אמת.

---

## 🏗️ מבנה הפרויקט (Project Architecture)

```
├── lib/
│   ├── main.dart                      # ממשק המשתמש (Apple Cupertino Style)
│   ├── services/audio_service.dart    # ניהול תקשורת מול שכבת ה-DSP ב-Android
│   └── widgets/
│       ├── apple_card.dart            # כרטיס זכוכית מעורפלת (Frosted Glass)
│       ├── apple_slider.dart          # סליידר קפסולה טקטילי (Control Center Style)
│       ├── device_status_pill.dart    # תג סטטוס בסגנון Dynamic Island
│       ├── mic_hero_button.dart       # כפתור מיקרופון ראשי ענק עם הילת ניאון
│       └── vu_meter_visualizer.dart   # מד רמת שמע בליסטי עם סרגל דציבלים
├── android/
│   └── app/src/main/
│       ├── AndroidManifest.xml        # הרשאות והגדרת Foreground Service
│       ├── kotlin/com/mic/app/mic/
│       │   ├── AudioDspProcessor.kt   # מנוע עיבוד האותות (DSP, Reverb, Echo, Anti-Howl)
│       │   ├── AudioEngine.kt         # לכידת אודיו והשמעה בשיהוי נמוך (AudioRecord/AudioTrack)
│       │   ├── MicForegroundService.kt# שירות רקע, ניטור בלוטות' ושיחות טלפון
│       │   └── MainActivity.kt        # גשר MethodChannel ו-EventChannel ל-Flutter
│       └── cpp/
│           ├── OboeAudioEngine.h      # מנוע C++ Oboe / AAudio
│           └── OboeAudioEngine.cpp    # מימוש C++ ברמת מערכת ההפעלה
└── .github/workflows/
    └── build_and_release.yml          # בנייה אוטומטית של קובץ APK ב-GitHub Actions
```

---

## 🛠️ בנייה מקומית (Local Build)

אם תרצה לבנות את ה-APK בעצמך במחשב:

```bash
# התקנת תלויות
flutter pub get

# הרצת בדיקות תקינות
flutter test

# בניית APK לשחרור
flutter build apk --release
```

הקובץ המותקן יימצא בנתיב:
`build/app/outputs/flutter-apk/app-release.apk`

---

## 📄 רישיון

MIT License © 2026 FL4K901
