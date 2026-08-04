package net.pouyan.screenshotassistant.config;

/**
 * Sound played the moment a screenshot is automatically captured.
 * Each value maps to an existing Minecraft SoundEvent so no custom
 * sound resources are needed.
 */
public enum CaptureSound {
    /** UI toast ping — clean camera-click feel (default). */
    CAMERA,
    /** Experience orb pickup — satisfying classic. */
    XP_ORB,
    /** Level-up fanfare — dramatic. */
    LEVEL_UP,
    /** Note-block pling — short and bright. */
    NOTE_PLING,
    /** No sound at all. */
    NONE
}
