package me.hexian000.cuehelper;

import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;

public class CueTrack {
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    private String title;
    private String artist;
    private Map<Integer, Duration> indexes = new TreeMap<>();

    public Duration getIndex(int id) {
        return indexes.get(id);
    }

    public void addIndex(int id, Duration duration) {
        indexes.put(id, duration);
    }

    public Duration getLastIndex() {
        if (indexes.size() < 1) return null;
        int last = Integer.MIN_VALUE;
        for (int i : indexes.keySet()) {
            if (i > last) last = i;
        }
        return indexes.get(last);
    }
}
