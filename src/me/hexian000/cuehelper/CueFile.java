package me.hexian000.cuehelper;

import java.util.Map;
import java.util.TreeMap;

public class CueFile {
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

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    private String title;
    private String artist;
    private String file;
    private final Map<Integer, CueTrack> tracks = new TreeMap<>();

    public void addTrack(int id, CueTrack track) {
        tracks.put(id, track);
    }

    public CueTrack getTrack(int id) {
        return tracks.get(id);
    }

    public interface TrackWalker {
        boolean walk(int trackId, CueTrack track);
    }

    public void forEachTrack(TrackWalker walker) {
        for (var entry : tracks.entrySet()) {
            if (!walker.walk(entry.getKey(), entry.getValue())) {
                break;
            }
        }
    }
}
