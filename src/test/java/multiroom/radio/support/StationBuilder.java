package multiroom.radio.support;

import multiroom.radio.catalogue.Station;

import java.util.UUID;

/** A clean station whose fields a test overrides one by one. */
public final class StationBuilder {

    private UUID uuid = UUID.fromString("9617a958-0601-11e8-ae97-52543be04c81");
    private String name = "Test FM";
    private String streamUrl = "https://s.example.test/live.mp3";
    private String codecLabel = "MP3";
    private boolean hls;
    private String favicon = "https://s.example.test/i.png";
    private String homepage = "https://s.example.test/";
    private String countryCode = "UA";
    private String languages = "ukrainian";
    private String tags = "jazz";
    private int bitrate = 128;
    private boolean lastCheckOk = true;
    private long votes = 1;
    private long clickCount = 2;

    public StationBuilder uuid(UUID v) { uuid = v; return this; }
    public StationBuilder name(String v) { name = v; return this; }
    public StationBuilder streamUrl(String v) { streamUrl = v; return this; }
    public StationBuilder codecLabel(String v) { codecLabel = v; return this; }
    public StationBuilder hls(boolean v) { hls = v; return this; }
    public StationBuilder favicon(String v) { favicon = v; return this; }
    public StationBuilder homepage(String v) { homepage = v; return this; }
    public StationBuilder countryCode(String v) { countryCode = v; return this; }
    public StationBuilder languages(String v) { languages = v; return this; }
    public StationBuilder tags(String v) { tags = v; return this; }
    public StationBuilder bitrate(int v) { bitrate = v; return this; }
    public StationBuilder lastCheckOk(boolean v) { lastCheckOk = v; return this; }
    public StationBuilder votes(long v) { votes = v; return this; }
    public StationBuilder clickCount(long v) { clickCount = v; return this; }

    public Station build() {
        return new Station(uuid, name, streamUrl, codecLabel, hls, favicon, homepage, countryCode, languages, tags,
                bitrate, lastCheckOk, votes, clickCount);
    }
}
