from pathlib import Path
p = Path('OwnTV_Core-main/core/src/main/java/tv/own/owntv/core/settings/SettingsRepository.kt')
s = p.read_text()
s = s.replace('val INTERFLASH_STABILITY_027 =', 'val INTERFLASH_STABILITY_029 = booleanPreferencesKey("interflash_stability_029")\n        val INTERFLASH_STABILITY_027 =', 1)
a = s.index('    suspend fun applyInterFlashStability027()')
b = s.index('    /** Custom live buffer seconds', a)
s = s[:a] + '''    suspend fun applyInterFlashStability027() {
        context.dataStore.edit { prefs ->
            if (prefs[Keys.INTERFLASH_STABILITY_029] == true) return@edit
            prefs[Keys.LIVE_LATENCY_MODE] = LiveLatency.CUSTOM.name
            prefs[Keys.LIVE_LATENCY_CUSTOM_SECS] = 30
            prefs[Keys.LIVE_PREROLL_SECS] = 5
            prefs[Keys.LIVE_LATENCY_RESET_416] = true
            prefs[Keys.INTERFLASH_STABILITY_027] = true
            prefs[Keys.INTERFLASH_STABILITY_029] = true
        }
    }

''' + s[b:]
s = s.replace('if (prefs[Keys.INTERFLASH_STABILITY_027] == true) {', 'if (prefs[Keys.INTERFLASH_STABILITY_029] == true) {')
s = s.replace('            LiveLatency.STABLE.name\n', '            LiveLatency.CUSTOM.name\n')
s = s.replace('LiveBuffer.clampCustom(prefs[Keys.LIVE_LATENCY_CUSTOM_SECS] ?: LiveBuffer.CUSTOM_DEFAULT)', 'LiveBuffer.clampCustom(prefs[Keys.LIVE_LATENCY_CUSTOM_SECS] ?: 30)')
s = s.replace('(prefs[Keys.LIVE_PREROLL_SECS] ?: 2).coerceIn(0, 30)\n        } else 2', '(prefs[Keys.LIVE_PREROLL_SECS] ?: 5).coerceIn(0, 30)\n        } else 5')
assert 'INTERFLASH_STABILITY_029' in s
assert 'prefs[Keys.LIVE_LATENCY_CUSTOM_SECS] = 30' in s
assert 'prefs[Keys.LIVE_PREROLL_SECS] = 5' in s
p.write_text(s)
print('Applied v0.29: 30s live buffer, 5s initial/refill reserve; one-time upgrade migration.')

# Preference keys are technical identifiers, never displayed or translated.
import subprocess
subprocess.run(['python3', 'tools/i18n/check_hardcoded_strings.py', 'classify-safe', '--path', 'core/src/main/java/tv/own/owntv/core/settings/SettingsRepository.kt', '--text', 'interflash_stability_029', '--category', 'preference-key'], cwd='OwnTV_Core-main', check=True)
