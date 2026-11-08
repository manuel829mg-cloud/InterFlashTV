Inter Flash TV 0.42 — interfaz de cliente y navegación

Base: delivered 0.41. Client rail has TV with two antennas, Movies with a clapperboard/play mark and Series with stacked film frames/play mark. The client heart entry is removed. The existing channel watermark retains its position and behaviour; the existing Inter Flash artwork is additionally requested at 160x160 and drawn at 3.5% opacity on the left. No added artwork files or dependencies.

Client Movies uses a full-width poster grid with search, sort and a category sheet, replacing the fixed category/details split in that mode. Entering Movies/Series stops previous playback, and catalogues do not mount a docked video surface even during the transition frame. Back from VOD stops playback, saves progress and restores the relevant catalogue; live playback keeps its existing docking behaviour.

Navigation overhead reductions: client transitions/ambient pulse disabled; rail position snaps; poster requests target 256x384 with no crossfade in client mode; focus metadata is collected only on demand while the movie menu is open. The shell retains saveable screen state. Client Movies retains its current-category grid offset across section changes and resets on explicit category change. Existing paged data flows and image-loader cache are reused. No new buffer or player-engine changes.

Reproduce: .github/workflows/build-v042.yml, branch codex/v042-fluid-cinema, build commit 1a459c6dbd626dea3360ae47e58cafef6a129e0b. Source is reconstructed from the v0.31 source release and existing patches through client041.patch, followed by client042.patch.

Validation: final run 42443640522 succeeded, including app guide/mode-hold/time regression tests and release assembly. The unchanged core migration suite was already validated in 0.41 and was not repeated. Vector silhouettes were rendered and inspected separately; this is not a TV UI screenshot. Downloaded artifact digest checked. APK signed locally with the existing private keystore, and ApkVerifier confirmed certificate equality with the delivered 0.41. ZIP contents and manifest verified: com.interflash.tv.player, versionCode 42, versionName 0.42-fluid-cinema. All 26 native library contents match 0.41. Installer size unchanged.

Delivered: InterFlashTV-v0.42-navegacion-fluida.apk
Size: 58297431 bytes
SHA-256: 3bd9fe43b1097410418b5ff3b654f9db13f02b7cfc4f29408454da1f0d360e81

Limits: no physical-TV test of layout, remote navigation, resume or measured frame timing. Actual navigation improvement must be checked on the user's TV. Movie/series content depends on the configured source. No automatic update publication or main-branch deployment.