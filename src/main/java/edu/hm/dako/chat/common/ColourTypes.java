package edu.hm.dako.chat.common;

/**
 * Enumeration zur Definition der Farben
 *
 * @author Nisi
 */
public enum ColourTypes {
    DEFAULT_COLOUR(0,"#99CBBC","Türkis"),
    WHITE_COLOUR(1,"#FFFFFF","Weiß"),
    GREY_COLOUR(2,"#999999","Grau"),
    GREEN_COLOUR(3,"#97CDA1","Grün"),
    YELLOW_COLOUR(4,"#FEFF99","Gelb"),
    PINK_COLOUR(5,"#FFB1C7","Pink");

    private final int id;
    protected final String hexCode;
    private final String description;

    ColourTypes(int id, String hexCode, String description) {
        this.id = id;
        this.hexCode = hexCode;
        this.description = description;
    }

    public int getId() {
        return id;
    }


    public static String getHexCode( String description ) {
        if (description.equals("Türkis")) {
            return "#99CBBC";
        } else if (description.equals("Weiß")) {
            return "#FFFFFF";
        } else if (description.equals("Grau")) {
            return "#999999";
        } else if (description.equals("Grün")) {
            return "#97CDA1";
        } else if (description.equals("Gelb")) {
            return "#FEFF99";
        } else if (description.equals("Pink")) {
            return "#FFB1C7";
        } else {
            return "#99CBBC";
        }
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return description;
    }
}
