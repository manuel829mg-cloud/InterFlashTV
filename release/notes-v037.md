Inter Flash TV 0.37 (versionCode 37)

Requested behavior: live browsing uses the existing large stage rectangle for the current video surface; an 88 x 56 dp decorative channel-logo badge sits at bottom right. The rail's profile/mode button uses the existing interflash_brand drawable. Holding OK for at least 3000 monotonic milliseconds toggles client/administrator mode once; release before the threshold retains the former short action, focus loss cancels the hold. Channel-list right arrow is consumed and preview schedule items do not take focus; full-screen guide navigation is unchanged.

Exact reproducible source: the v0.31-clean-rail source archive, followed by dominican033.patch, guide034.patch, guide035.patch, client-guide036.patch and layout037.patch in that order. Repository manuel829mg-cloud/InterFlashTV, branch codex/v037-stage-preview. Build workflow .github/workflows/build-v037.yml, run 42265118084, build commit 4318ea6cb87d2a95981c12caf205398fbcb19f32. Local snapshot of customized OwnTV and OwnTV_Core is reconstructed by that workflow; main is an older, unrelated Java client.

Validation: Gradle testStandardDebugUnitTest (PanelGuide* and ModeHoldPressTest) and assembleStandardRelease succeeded. Mode-hold tests cover the three-second boundary, repeated key-down events, late release and cancellation on focus loss. APK archive and manifest checked: com.interflash.tv.player, versionCode 37, versionName 0.37-stage-preview. Locally signed with the permanent key; signature verifier confirmed the same certificate as the delivered 0.36 APK. Private keys/passwords are not in the repository.

Delivered APK: InterFlashTV-v0.37-video-grande-logo.apk
SHA-256: cdf600ff18a4bdd9192f0d309387417c57b196b2cd3c79fa441a9369c2e6ace0

Not verified on physical TV: video bounds during layout transitions, remote focus behavior, and visual logo sizing. No claim of on-device testing. This change does not add EPG feeds or change IPTV subscriptions, panel, ISP billing, MikroTik or WhatsApp.
