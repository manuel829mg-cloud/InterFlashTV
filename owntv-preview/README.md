# Inter Flash TV v0.18 preview

Reproducible source patches for the user's existing OwnTV design. The workflow checks out immutable upstream commits, applies these patches, tests catalog fingerprints and TV-only update asset selection, then compiles an ARM Android TV APK.

The changes keep the saved catalog visible after panel authorization and synchronize catalog changes in the background. They preserve device authorization, the manual Dominican catalog, the application ID, and the visual layout.

The updater reads releases from manuel829mg-cloud/InterFlashTV and selects only InterFlashTV-OwnTV- APK assets for the correct architecture. The system installer still requires the user's confirmation.

The build artifact is not signed with Inter Flash's permanent key. That key is kept outside GitHub. The final distribution APK must be signed locally with the existing permanent key.

OwnTV app base: eb5a947165f58e737bfe58dd0351a45406ed5363.
OwnTV core base: 38f30e6768bab28bddf3c2a6847788ccfb84358e.
Original upstream licensing and attribution remain GPLv3. Source patches are provided under the same terms.
