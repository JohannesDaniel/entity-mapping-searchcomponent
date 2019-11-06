package entity.mapping.component.model;

public class SearchToken {
    public final String text;
    public final boolean fuzzy;
    public final int prefix;
    public final int variants;

    public SearchToken(String text, boolean fuzzy) {
        this(text, fuzzy, 0, 0);
    }

    public SearchToken(String text, boolean fuzzy, int prefix, int variants) {
        this.text = text;
        this.fuzzy = fuzzy;
        this.prefix = prefix;
        this.variants = variants;
    }

    @Override
    public String toString() {
        return fuzzy ? "(" + text + ", " + fuzzy + ", " + prefix + ", " + variants + ")" : "(" + text + ", " + fuzzy + ")";
    }


}
