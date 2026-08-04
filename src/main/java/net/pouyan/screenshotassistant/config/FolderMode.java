package net.pouyan.screenshotassistant.config;

/**
 * @deprecated Used only for migrating configs written by v0.4.x and earlier.
 *             New code uses {@link ModConfig#baseSavePath} directly.
 */
@Deprecated
public enum FolderMode {
    /** The game's normal ".minecraft/screenshots" folder. */
    DEFAULT,
    /**
     * @deprecated Removed in v0.5.0. SUBFOLDER configs are silently dropped
     *             (base path stays as the default screenshots folder).
     */
    @Deprecated
    SUBFOLDER,
    /** Any absolute path on disk. */
    ABSOLUTE
}
