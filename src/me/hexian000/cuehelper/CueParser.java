package me.hexian000.cuehelper;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CueParser {
    private CueFile output;
    private int trackId;

    private void reset() {
        output = new CueFile();
        trackId = -1;
    }

    private static String unquote(String s) {
        if (s.length() > 1 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    public CueFile Parse(String path, Charset charset) throws IOException, CueParseException {
        reset();
        output = new CueFile();
        try (FileInputStream f = new FileInputStream(path);
             BufferedReader r = new BufferedReader(new InputStreamReader(f, charset))) {
            Pattern tokenizer = Pattern.compile("\".+?\"|[^ ]+");
            String line;
            while ((line = r.readLine()) != null) {
                Matcher m = tokenizer.matcher(line.trim());
                List<String> parts = new ArrayList<>();
                while (m.find()) {
                    parts.add(unquote(m.group()));
                }
                if (parts.size() > 0) {
                    parseLine(parts);
                }
            }
        }
        return output;
    }

    private CueTrack createTrack(int trackId) throws CueParseException {
        if (output.getTrack(trackId) != null) {
            throw new CueParseException("track {trackId} already exists");
        }
        var track = new CueTrack();
        output.addTrack(trackId, track);
        return track;
    }

    private void createIndex(int trackId, int index, String raw) throws CueParseException {
        var track = output.getTrack(trackId);
        if (track.getIndex(index) != null) {
            throw new CueParseException("index {index} in track {trackId} already exists");
        }
        Pattern time = Pattern.compile("(\\d+):(\\d+):(\\d+)");
        Matcher m = time.matcher(raw);
        if (!m.find() || m.groupCount() != 3) {
            throw new CueParseException(String.format("malformed index %d in track %d: \"%s\"",
                    index, trackId, raw));
        }
        track.addIndex(index, Duration.ofSeconds(
                Long.parseLong(m.group(1)) * 60 + Long.parseLong(m.group(2)),
                Long.parseLong(m.group(3)) * 10000000));
    }

    private void parseLine(List<String> tokens) throws CueParseException {
        switch (tokens.get(0).toUpperCase()) {
            case "FILE" -> output.setFile(tokens.get(1));
            case "TITLE" -> {
                if (trackId > 0) {
                    output.getTrack(trackId).setTitle(tokens.get(1));
                    break;
                }
                output.setTitle(tokens.get(1));
            }
            case "PERFORMER" -> {
                if (trackId > 0) {
                    output.getTrack(trackId).setArtist(tokens.get(1));
                    break;
                }
                output.setArtist(tokens.get(1));
            }
            case "TRACK" -> {
                trackId = Integer.parseInt(tokens.get(1));
                if (trackId <= 0) {
                    throw new CueParseException("TRACK <= 00");
                }
                var track = createTrack(trackId);
                track.setArtist(output.getArtist());
            }
            case "INDEX" -> {
                if (trackId <= 0) {
                    throw new CueParseException("INDEX without TRACK");
                }
                createIndex(trackId, Integer.parseInt(tokens.get(1)), tokens.get(2));
            }
        }
    }
}
