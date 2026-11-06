Inter Flash TV 0.39 — recommended defaults and readable guide

Base: 0.38, preserving automatic browse tuning, OK fullscreen, stage video, top Inter Flash logo, removed footer hints, and three-second mode hold.

Playback: set LiveLatency.DEFAULT (BALANCED, the UI-recommended choice) and preroll 0 once on fresh installs and upgrades. Retire the forced CUSTOM 30-second buffer and 5-second preroll from 0.29. Subsequent manual choices are preserved. Initial settings flows also report the recommended choices until migration finishes. No player-engine replacement, new buffer algorithm or global stream changes.

Guide: use a dedicated es-DO, America/Santo_Domingo, h:mm a formatter for guide times, date-time labels and banner ranges; guide day boundaries use the same zone. Programme timestamps are not shifted or rewritten. Banner text is explicitly white over an 88% black background; number is larger and has no hash prefix. Show selected/current programme and following programme with their start/end times when data exists; otherwise show Sin guía disponible. No new EPG feed or unsupported programme data was invented.

Reproduce: source archive from v0.31-clean-rail followed by dominican033.patch, guide034.patch, guide035.patch, client-guide036.patch, layout037.patch, browse038.patch and recommended039.patch. Repository manuel829mg-cloud/InterFlashTV, branch codex/v039-recommended-guide; workflow .github/workflows/build-v039.yml; successful run 42297131357 at build commit ec035b206ff403d7d7656b224d077ef06ef7c2da.

Checks: core InterFlashRecommendedTest passed (fresh defaults, old custom upgrade, later manual settings preserved); app PanelGuide*, ModeHoldPressTest, DominicanTimeTest and TimeFormatTest passed; standard release assembly passed. DominicanTimeTest verifies conversion with device default set to Tokyo and 12-hour formatting across local evening. APK archive and manifest checked: com.interflash.tv.player, versionCode 39, versionName 0.39-recommended-guide. APK signed locally; signature verified against delivered 0.38 certificate. Keys/passwords are not in this repository.

Delivered: InterFlashTV-v0.39-recomendado-guia.apk
SHA-256: adb356ff7959a0ab163e0cefb2ef107190c7f67ea44d90fe4e142ab20c617baf

Limits: no physical-TV playback, contrast or remote-navigation test. No claim of fixing provider streams or completing guide coverage for channels without programme data. ISP panel, MikroTik, billing and WhatsApp are outside this change.
