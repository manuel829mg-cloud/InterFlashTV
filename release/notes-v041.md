Inter Flash TV 0.41 — fondo de canal y catálogos

Base: v0.40, preserving the existing layout, guide and player. The client rail adds Movies and Series alongside TV and Favorites. Back from fullscreen VOD returns to the corresponding catalogue.

Live background: a static dark-blue-to-black gradient also applies while Glass is enabled. A single selected-channel logo is drawn behind content at 6% opacity, decoded at 128x128 maximum, with 250ms debounce for rapid scrolling. The existing image loader/cache is reused. No new dependencies, bundled artwork, blur or background animation. Missing/failed logos leave the gradient. Movie/series grids retain their existing lazy paging.

Reproduce: workflow .github/workflows/build-v041.yml, branch codex/v041-light-client-vod, build commit 5304fb61bc0c0bb66f3a0a94b228e1861168bf46. Source comes from the pinned v0.31 release plus patches 033–040 and client041.patch.

Validation: successful run 42432148432, including core recommended-settings tests, app guide/mode-hold/time tests and standard release assembly. Unsigned artifact digest verified. Signed locally using the existing private keystore (not committed). ApkVerifier verified the signature and exact certificate equality against delivered v0.40. APK archive/manifest checked: com.interflash.tv.player; versionCode 41; versionName 0.41-light-client-vod. Native .so library contents match v0.40.

Delivered file: InterFlashTV-v0.41-fondo-y-catalogos.apk
Size: 58297431 bytes
SHA-256: 2cd1a16a8e694a8e1ca027b915a4ebd04a7a77af47faedbcb92809da08007f98

Limits: appearance, remote navigation and performance have not been tested on physical TV. VOD catalogue availability depends on the configured source. No automatic update publication or main-branch deployment.