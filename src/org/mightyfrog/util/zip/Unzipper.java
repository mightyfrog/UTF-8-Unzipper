package org.mightyfrog.util.zip;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.InvalidDnDOperationException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.TransferHandler;
import javax.swing.UIManager;

import javax.swing.ProgressMonitorInputStream;

/**
 *
 *
 * @author Shigehiro Soejima
 */
public class Unzipper extends JFrame {
    // windows illegal chars, don't escape '/'
    private static final char[] WIN_ILLEGAL_CHARS =
        new char[]{'\\', ':',  '*', '?', '"', '<', '>', '|'};

    //
    private static final String MSG = I18N.get("MessageArea.Text.0");

    //
    private static final String ICON_PATH = "org/mightyfrog/util/zip/icon.png";

    //
    private boolean running = false;

    //
    //private final JCheckBoxMenuItem ESC_ISO_CONTROL_MI =
    //    new JCheckBoxMenuItem(I18N.get("MenuItem.Escape.ISO.Controls"));
    private final JCheckBoxMenuItem CREATE_FOLDER_MI =
        new JCheckBoxMenuItem(I18N.get("MenuItem.Create.Folder"));
    private final JCheckBoxMenuItem OVERWRITE_ALL_MI =
        new JCheckBoxMenuItem(I18N.get("MenuItem.Overwrite.All"));

    //
    private final JTextArea TA = new JTextArea() {
            {
                setIconImage(new ImageIcon(Unzipper.class.getClassLoader().
                                           getSystemResource(ICON_PATH)).getImage());
                setEditable(false);
                setTransferHandler(new ZipTransferHandler());
            }

            /** */
            @Override
            public void paintComponent(Graphics g) {
                super.paintComponent(g);

                if (getText().length() == 0) {
                    int w =
                        (getWidth() - g.getFontMetrics().stringWidth(MSG)) / 2;
                    int h = getHeight() / 2;
                    g.setColor(UIManager.getColor("TextArea.inactiveForeground"));
                    g.drawString(MSG, w, h);
                }
            }

            /** */
            @Override
            public void append(String str) {
                super.append(str);
                setCaretPosition(getText().length());
            }
        };

    // these should be cleared for each .zip file
    private List<ZipEntry> badCrcList = null;
    private Map<String, String> renamedMap = null;

    /**
     * Constructs a Unzipper.
     *
     */
    public Unzipper() {
        super(I18N.get("Frame.Title"));

        setJMenuBar(createMenuBar());
        add(new JScrollPane(TA));

        setSize(400, 250);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setVisible(true);
    }

    /**
     *
     */
    public static void main(String[] args) {
        try{
            String className = UIManager.getSystemLookAndFeelClassName();
            UIManager.setLookAndFeel(className);
        }catch(Exception e){
            e.printStackTrace();
            return;
        }

        EventQueue.invokeLater(new Runnable() {
                /** */
                @Override
                public void run() {
                    new Unzipper();
                }
            });
    }

    //
    //
    //

    /**
     *
     */
    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu optionMenu = new JMenu(I18N.get("Menu.Option"));
        optionMenu.add(CREATE_FOLDER_MI);
        optionMenu.add(OVERWRITE_ALL_MI);
        //optionMenu.add(ESC_ISO_CONTROL_MI); 
        optionMenu.addSeparator();
        JMenuItem aboutMI = new JMenuItem(I18N.get("MenuItem.About",
                                                   "UTF-8 Unzipper"));
        aboutMI.addActionListener(new ActionListener() {
                /** */
                @Override
                public void actionPerformed(ActionEvent evt) {
                    showAboutDialog();
                }
            });
        optionMenu.add(aboutMI);

        menuBar.add(optionMenu);

        CREATE_FOLDER_MI.setSelected(true);
        //ESC_ISO_CONTROL_MI.setSelected(true);

        return menuBar;
    }

    /**
     * Unzip the specified file.
     *
     * @param file
     */
    private boolean unzip(File file) throws IOException {
        ZipInputStream zis = null;
        try {
            BufferedInputStream bis =
                new BufferedInputStream(new FileInputStream(file));
            zis = new ZipInputStream(bis);
            ZipEntry entry = null;
            if (CREATE_FOLDER_MI.isSelected()) {
                String name = file.getName();
                name = name.substring(0, name.lastIndexOf("."));
                file = new File(file.getParent(), name);
                if (!file.mkdirs()) {
                    int option = confirmFileOverwrite(file);
                    if (option != JOptionPane.OK_OPTION) {
                        return false;
                    }
                }
            } else {
                file = file.getParentFile();
            }

            setRunning(true);
            boolean escape = System.getProperty("os.name").startsWith("W");
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                //if (ESC_ISO_CONTROL_MI.isSelected()) {
                //    name = escapeISOControl(name);
                //}
                name = escapeISOControl(name);
                if (escape) { // TODO: make me an option
                    name = escapeEntryName(name);
                }
                File f = new File(file, name);
                if (!f.isDirectory() && f.exists()) {
                    int option = confirmFileOverwrite(f);
                    if (option != JOptionPane.OK_OPTION) {
                        if (option == JOptionPane.CANCEL_OPTION) {
                            return false;
                        }
                        continue;
                    }
                }

                if (entry.isDirectory()) {
                    if (f.exists() || f.mkdir()) {
                        continue;
                    } else {
                        throw new IOException("Unable to make a path: " +
                                              f.getPath());
                    }
                } else {
                    TA.append(entry.getName() + "\n");
                }

                if (f.getParentFile() != null) {
                    if (!f.getParentFile().exists() &&
                        !f.getParentFile().mkdirs()) {
                        throw new IOException("Unable to make a path: " +
                                              f.getParentFile());
                    }
                }

                BufferedOutputStream bos = null;
                try {
                    bos = new BufferedOutputStream(new FileOutputStream(f));
                    int n = 0;
                    byte[] b = new byte[8092];
                    while ((n = zis.read(b)) != -1) {
                        bos.write(b, 0, n);
                    }
                } catch (ZipException e) {
                    addBadCrcEntryToList(entry);
                } catch (IOException e) {
                    throw e; // double-fold
                } finally {
                    if (bos != null) {
                        try {
                            bos.close();
                        } catch (IOException e) {
                        }
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            TA.append(I18N.get("MessageArea.Text.3"));
            //if (!file.delete()) {
            //    file.deleteOnExit();
            //}
            return false;
        } catch (ZipException e) {
            TA.append(e.getLocalizedMessage());
        } catch(IOException e) {
            throw e;
        } finally {
            if (zis != null) {
                try {
                    zis.close();
                } catch (IOException e) {
                }
            }
            setRunning(false);
        }

        return true;
    }

    /**
     * Tests whether the unzip method is running or not.
     *
     */
    private boolean isRunning() {
        return this.running;
    }

    /**
     * Sets whether the unzip  method is running or not.
     *
     * @param running
     */
    private synchronized void setRunning(boolean running) {
        this.running = running;
        if (running) {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        } else {
            // make sure setCursor is called
            EventQueue.invokeLater(new Runnable() {
                    /** */
                    @Override
                    public void run() {
                        setCursor(null);
                    }
                });
        }
    }

    /**
     * Tests whether the file is a zip file or not by checking its magic
     * number (0x04034b50).
     *
     * @param file
     */
    private boolean magicCheck(File file) {
        BufferedInputStream bis = null;
        try {
            bis = new BufferedInputStream(new FileInputStream(file));
            byte[] sig = new byte[4];
            if (bis.read(sig) != 4 || // read magic bytes
                sig[3] != 0x04 || sig[2] != 0x03 ||
                sig[1] != 0x4b || sig[0] != 0x50) {
                return false;
            }
        } catch (IOException e) {
            return false;
        } finally {
            if (bis != null) {
                try {
                    bis.close();
                } catch (IOException e) {
                }
            }
        }

        return true;
    }

    /**
     *
     * @param before
     * @param after
     */
    private void addRenamedPairs(String before, String after) {
        if (this.renamedMap == null) {
            this.renamedMap = new HashMap<String, String>();
        }
        this.renamedMap.put(before, after);
    }

    /**
     *
     */
    private Map<String, String> getRenamedPairs() {
        return this.renamedMap;
    }

    /**
     *
     * @param entry
     */
    private void addBadCrcEntryToList(ZipEntry entry) {
        if (this.badCrcList == null) {
            this.badCrcList = new ArrayList<ZipEntry>();
        }
        this.badCrcList.add(entry);
    }

    /**
     *
     */
    private List<ZipEntry> getBadCrcList() {
        return this.badCrcList;
    }

    /**
     * Shows the confirmation dialog.
     *
     * @param f
     */
    private int confirmFileOverwrite(File f) {
        if (OVERWRITE_ALL_MI.isSelected()) {
            return JOptionPane.YES_OPTION;
        }
        String msg = I18N.get("Dialog.Text.0", f.getName());
        return JOptionPane.showConfirmDialog(this, msg);
    }

    /**
     * Escapes ISO control chars.
     *
     * @param name
     */
    private String escapeISOControl(String name) {
        char[] c = name.toCharArray();
        boolean escaped = false;
        for (int i = 0; i < c.length; i++) {
            if (Character.isISOControl(c[i])) {
                c[i] = ' ';
                escaped = true;
            }
        }

        if (escaped) {
            String tmp = new String(c);
            addRenamedPairs(name, tmp);
            name = tmp;
        }

        return name;
    }

    /**
     * Escapes an entry name.
     *
     * @param name
     */
    private String escapeEntryName(String name) {
        char[] c = name.toCharArray();
        boolean escaped = false;
        for (int i = 0; i < c.length; i++) {
            for (char ic : WIN_ILLEGAL_CHARS) {
                if (c[i] == ic) {
                    c[i] = '_';
                    escaped = true;
                }
            }
        }

        if (escaped) {
            String tmp = new String(c);
            addRenamedPairs(name, tmp);
            name = tmp;
        }

        return name;
    }

    /**
     *
     */
    private void reportErrors() {
        if (getBadCrcList() != null) {
            for (ZipEntry ze : getBadCrcList()) {
                TA.append(I18N.get("MessageArea.Text.4", ze));
            }
        }
        if (getRenamedPairs() != null) {
            TA.append(I18N.get("MessageArea.Text.5"));
            for (Map.Entry entry : getRenamedPairs().entrySet()) {
                TA.append(I18N.get("MessageArea.Text.6", entry.getKey(),
                                   entry.getValue()));
            }
        }
    }

    /**
     *
     */
    private void cleanup() {
        this.badCrcList = null;
        this.renamedMap = null;
    }

    /**
     *
     */
    private void showAboutDialog() {
        String version = I18N.get("Dialog.Text.1",
                                  "UTF-8 Unzipper",
                                  "Shigehiro Soejima",
                                  "mightyfrog.gc@gmail.com",
                                  "@TIMESTAMP@");
        JOptionPane.showMessageDialog(this, version);
    }

    //
    //
    //

    /**
     *
     */
    private class ZipTransferHandler extends TransferHandler {
        //
        private File fileToImport = null;

        /** */
        @Override
        public boolean canImport(TransferHandler.TransferSupport support) {
            support.setShowDropLocation(false);
            Transferable t = support.getTransferable();
            try {
                List list = java.util.Collections.emptyList();
                if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    list =
                        (List) t.getTransferData(DataFlavor.javaFileListFlavor);
                } else {
                    DataFlavor df =
                        new DataFlavor("text/uri-list;class=java.lang.String");
                    String data = (String) t.getTransferData(df);
                    list = textURIListToFileList(data);
                }
                if (list.size() == 1) { // doesn't accept multiple files
                    setFileToImport((File) list.get(0));
                    if (!getFileToImport().isDirectory() && !isRunning()) {
                        if (magicCheck(getFileToImport())) {
                            return true;
                        }
                    }
                }
                return false;
            } catch (ClassNotFoundException e) {
                // won't happen
            } catch (InvalidDnDOperationException e) {
                // ignore
            } catch (UnsupportedFlavorException e) {
                return false;
            } catch (IOException e) {
                return false;
            }

            return true;
        }

        /** */
        @Override
        public boolean importData(JComponent comp, Transferable t) {
            new Thread(new Runnable() { // violates threading rule
                    /** */
                    @Override
                        public void run() {
                        try {
                            long s = System.currentTimeMillis();
                            if (unzip(getFileToImport())) {
                                reportErrors();
                                long e = System.currentTimeMillis();
                                TA.append(I18N.get("MessageArea.Text.2",
                                                   e - s));
                            }
                        } catch (IOException e) {
                            TA.append(I18N.get("MessageArea.Text.1",
                                               e.getLocalizedMessage()));
                        } finally {
                            cleanup();
                        }
                    }
                }).start();

            return true;
        }

        //
        //
        //

        /**
         *
         */
        private File getFileToImport() {
            return this.fileToImport;
        }

        /**
         *
         * @param fileToImport
         */
        private void setFileToImport(File fileToImport) {
            this.fileToImport = fileToImport;
        }

        /**
         *
         * @param uriList
         */
        private List<File> textURIListToFileList(String uriList) {
            List<File> list = new ArrayList<File>(1);
            StringTokenizer st = new StringTokenizer(uriList, "\r\n");
            while (st.hasMoreTokens()) {
                String s = st.nextToken();
                if (s.startsWith("#")) { // the line is a comment (RFC 2483)
                    continue;
                }
                try {
                    URI uri = new URI(s);
                    File file = new File(uri);
                    if (file.length() != 0) {
                        list.add(file);
                    }
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                }
            }

            return list;
        }
    }
}
