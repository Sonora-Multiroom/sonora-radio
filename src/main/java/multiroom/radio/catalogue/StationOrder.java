package multiroom.radio.catalogue;

/** What a search is sorted by, with the word the catalogue knows it by. */
public enum StationOrder {
    NAME("name"),
    VOTES("votes"),
    CLICK_COUNT("clickcount"),
    BITRATE("bitrate");

    private final String catalogueWord;

    StationOrder(String catalogueWord) {
        this.catalogueWord = catalogueWord;
    }

    /** The value of the catalogue's {@code order} parameter. */
    public String catalogueWord() {
        return catalogueWord;
    }
}
