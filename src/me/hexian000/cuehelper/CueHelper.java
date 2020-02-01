package me.hexian000.cuehelper;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.List;

public class CueHelper extends JFrame {
    private JPanel contentPane;
    private JTable trackTable;
    private JButton copyButton;
    private JButton clearButton;
    private JTextArea outputText;
    private JScrollPane tablePane;
    private JTextField charsetField;

    public CueHelper() {
        setContentPane(contentPane);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setTitle("CueHelper");

        copyButton.addActionListener(e -> {
            StringSelection stringSelection = new StringSelection(outputText.getText());
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(stringSelection, null);
        });
        clearButton.addActionListener(e -> outputText.setText(""));

        charset = Charset.defaultCharset();
        charsetField.setText(charset.name());
        charsetField.getDocument().addDocumentListener(new DocumentListener() {
            private void onChanged(DocumentEvent e) {
                var name = charsetField.getText();
                try {
                    if (name.isEmpty()) {
                        charset = Charset.defaultCharset();
                    } else {
                        charset = Charset.forName(name);
                    }
                    charsetField.setForeground(Color.BLACK);
                } catch (Exception ignore) {
                    charsetField.setForeground(Color.RED);
                }
                try {
                    if (cuePath != null) {
                        readCue(cuePath);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                onChanged(e);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onChanged(e);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onChanged(e);
            }
        });

        tablePane.setDropTarget(new FileDropTarget() {
            @Override
            public void onFileDrop(List<String> files) {
                for (String file : files) {
                    try {
                        readCue(file);
                        break;
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        JOptionPane.showMessageDialog(CueHelper.this, ex.getLocalizedMessage(),
                                "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });
        trackTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                JTable table = (JTable) e.getSource();
                if (e.getClickCount() != 2 || table.getSelectedRowCount() < 1) {
                    return;
                }
                appendOutput();
            }
        });
    }

    Charset charset;
    CueFile cue;
    String cuePath;

    private static String formatDuration(Duration duration) {
        return String.format("%d:%02d.%02d",
                duration.toMinutes(), duration.toSecondsPart(), duration.toMillisPart() / 10);
    }

    private Duration GetDuration(int trackId) {
        var next = cue.getTrack(trackId + 1);
        if (next != null) {
            var startTime = cue.getTrack(trackId).getLastIndex();
            var stopTime = next.getLastIndex();
            return stopTime.minus(startTime);
        }
        return null;
    }

    private void readCue(String path) throws IOException, CueParseException {
        var parser = new CueParser();
        cue = parser.Parse(path, charset);
        setTitle(String.format("[%s - %s] - CueHelper", cue.getArtist(), cue.getTitle()));

        final DefaultTableModel model = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        model.setColumnIdentifiers(new String[]{
                "Id", "Title", "Artist", "Duration"
        });
        cue.forEachTrack((trackId, track) -> {
            String durationStr = "?";
            var duration = GetDuration(trackId);
            if (duration != null) {
                durationStr = formatDuration(duration);
            }
            model.addRow(new String[]{
                    trackId + "", track.getTitle(), track.getArtist(), durationStr
            });
            return true;
        });
        trackTable.setModel(model);

        var columnModel = trackTable.getColumnModel();
        columnModel.getColumn(0).setMaxWidth(48);
        columnModel.getColumn(3).setMaxWidth(96);

        cuePath = path;
    }

    private void appendOutput() {
        for (var row : trackTable.getSelectedRows()) {
            int trackId = Integer.parseInt((String) trackTable.getModel().getValueAt(row, 0));
            var track = cue.getTrack(trackId);
            var duration = GetDuration(trackId);
            if (duration != null) {
                String[] parts = new String[]{
                        "ffmpeg",
                        String.format("-i \"%s\"", cue.getFile()),
                        "-map_metadata -1",
                        String.format("-metadata \"title=%s\"", track.getTitle()),
                        String.format("-metadata \"artist=%s\"", track.getArtist()),
                        String.format("-metadata \"track=%d\"", trackId),
                        String.format("-metadata \"album=%s\"", cue.getTitle()),
                        "-c:a flac",
                        "-ss", formatDuration(track.getLastIndex()),
                        "-t", formatDuration(duration),
                        String.format("-y \"%02d %s.flac\"", trackId, track.getTitle()),
                };
                outputText.append(String.join(" ", parts) + System.lineSeparator());
            } else {
                String[] parts = new String[]{
                        "ffmpeg",
                        String.format("-i \"%s\"", cue.getFile()),
                        "-map_metadata -1",
                        String.format("-metadata \"title=%s\"", track.getTitle()),
                        String.format("-metadata \"artist=%s\"", track.getArtist()),
                        String.format("-metadata \"track=%d\"", trackId),
                        String.format("-metadata \"album=%s\"", cue.getTitle()),
                        "-c:a flac",
                        "-ss", formatDuration(track.getLastIndex()),
                        String.format("-y \"%02d %s.flac\"", trackId, track.getTitle()),
                };
                outputText.append(String.join(" ", parts) + System.lineSeparator());
            }
        }
    }

    public static void main(String[] args) {
        try {
            // Set System L&F
            UIManager.setLookAndFeel(
                    UIManager.getSystemLookAndFeelClassName());
        } catch (UnsupportedLookAndFeelException |
                ClassNotFoundException |
                InstantiationException |
                IllegalAccessException e) {
            e.printStackTrace();
        }

        final CueHelper frame = new CueHelper();
        frame.setSize(640, 480);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
