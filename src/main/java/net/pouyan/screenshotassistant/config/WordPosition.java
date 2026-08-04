package net.pouyan.screenshotassistant.config;

/**
 * Which word(s) of a (punctuation-stripped, lower-cased) chat line a Rule
 * should compare against its keyword list.
 *
 * FIRST   – only the first word of the message
 * LAST    – only the last word of the message
 * INDEX   – only the word at the given 0-based index
 * ANYWHERE– keyword can appear at ANY position in the message
 */
public enum WordPosition {
    FIRST,
    LAST,
    INDEX,
    ANYWHERE
}
