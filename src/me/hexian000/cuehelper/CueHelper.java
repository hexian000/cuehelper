package me.hexian000.cuehelper;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class CueHelper extends JFrame {
    private JPanel contentPane;
    private JTable trackTable;
    private JButton copyButton;
    private JButton clearButton;
    private JTextArea outputText;
    private JScrollPane tablePane;
    private JTextField charsetField;
    private JButton openButton;
    JFileChooser chooser;

    public void chooseFile() {
        var result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            readCue(chooser.getSelectedFile());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

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
            private void onChanged() {
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
                    if (cueFile != null) {
                        readCue(cueFile);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                onChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onChanged();
            }
        });

        tablePane.setDropTarget(new FileDropTarget() {
            @Override
            public void onFileDrop(List<File> files) {
                for (File file : files) {
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
        openButton.addActionListener(e -> chooseFile());
        chooser = new JFileChooser();
        chooser.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                if (!f.isFile()) {
                    return false;
                }
                var path = f.getName();
                var index = path.lastIndexOf(".");
                if (index == -1) {
                    return false;
                }
                var ext = path.substring(index).toLowerCase();
                return ".cue".equals(ext);
            }

            @Override
            public String getDescription() {
                return "CUE files";
            }
        });
    }

    Charset charset;
    CueFile cue;
    File cueFile;

    private static String sanitizeFilename(String name) {
        name = name.replace('<', '(');
        name = name.replace('>', ')');
        name = name.replace('/', '_');
        name = name.replace('\\', '_');

        name = name.replace('"', '\'');
        name = name.replace('|', '-');
        name = name.replace(':', '-');

        name = name.replace("\0", "");
        name = name.replace("?", "");
        name = name.replace("*", "");
        return name;
    }

    private static String formatDuration(Duration duration) {
        return String.format("%02d:%02d:%02d.%02d",
                duration.toHours(), duration.toMinutesPart(), duration.toSecondsPart(), duration.toMillisPart() / 10);
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

    private void readCue(File file) throws IOException, CueParseException {
        var parser = new CueParser();
        cue = parser.Parse(file.getAbsolutePath(), charset);
        if (cue.getTitle() != null) {
            if (cue.getArtist() != null) {
                setTitle(String.format("[%s - %s] - CueHelper", cue.getArtist(), cue.getTitle()));
            } else {
                setTitle(String.format("[%s] - CueHelper", cue.getTitle()));
            }
        } else {
            setTitle(String.format("[%s] - CueHelper", cue.getFile()));
        }

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

        cueFile = file;
    }

    private void appendOutput() {
        for (var row : trackTable.getSelectedRows()) {
            int trackId = Integer.parseInt((String) trackTable.getModel().getValueAt(row, 0));
            var track = cue.getTrack(trackId);
            var duration = GetDuration(trackId);
            final List<String> parts = new ArrayList<>();
            parts.add("ffmpeg");
            parts.add(String.format("-i \"%s\"", cue.getFile()));
            parts.add("-map_metadata -1");
            if (track.getTitle() != null) {
                parts.add(String.format("-metadata \"title=%s\"", track.getTitle()));
            }
            if (track.getArtist() != null) {
                parts.add(String.format("-metadata \"artist=%s\"", track.getArtist()));
            }
            parts.add(String.format("-metadata \"track=%s\"", trackId));
            if (cue.getTitle() != null) {
                parts.add(String.format("-metadata \"album=%s\"", cue.getTitle()));
            }
            parts.add("-c:a flac");
            parts.add("-ss");
            parts.add(formatDuration(track.getLastIndex()));
            if (duration != null) {
                parts.add("-t");
                parts.add(formatDuration(duration));
            }
            String outFile;
            if (track.getTitle() != null) {
                if (track.getArtist() != null) {
                    outFile = String.format("%02d %s - %s.flac", trackId, track.getArtist(), track.getTitle());
                } else {
                    outFile = String.format("%02d %s.flac", trackId, track.getTitle());
                }
            } else {
                outFile = String.format("%02d.flac", trackId);
            }
            parts.add(String.format("-y \"%s\"", sanitizeFilename(outFile)));
            outputText.append(String.join(" ", parts) + System.lineSeparator());
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
