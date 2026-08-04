package net.pouyan.screenshotassistant.config;

/**
 * A single keyword together with its own position constraint.
 *
 * Example:
 *   { word="BBC",    position=ANYWHERE }
 *   { word="Cuboid", position=LAST     }
 */
public class KeywordEntry {

    public String word = "";
    public WordPosition position = WordPosition.ANYWHERE;
    /** 1-based word number (1 = first word); only meaningful when position == INDEX. */
    public int index = 1;

    public KeywordEntry() {}

    public KeywordEntry(String word, WordPosition position, int index) {
        this.word     = word;
        this.position = position;
        this.index    = index;
    }

    public KeywordEntry copy() {
        return new KeywordEntry(word, position, index);
    }
}
