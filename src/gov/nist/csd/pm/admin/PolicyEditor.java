/*
 * PolicyEditor.java
 *
 * Created on November 21, 2018
 */

package gov.nist.csd.pm.admin;

import gov.nist.csd.pm.common.application.SSLSocketClient;
import gov.nist.csd.pm.common.constants.GlobalConstants;
import gov.nist.csd.pm.common.net.ItemType;
import gov.nist.csd.pm.common.net.Packet;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;



/**
 * @author gavrila@nist.gov
 * @version $Revision: 1.1 $, $Date: 2008/07/16 17:02:57 $
 * @since 1.5
 */
public class PolicyEditor extends JDialog implements ActionListener, DocumentListener {
    /**
     * @uml.property  name="tool"
     * @uml.associationEnd
     */
    private PmAdmin tool;
    /**
     * @uml.property  name="sslClient"
     * @uml.associationEnd
     */
    private SSLSocketClient sslClient;
    /**
     * @uml.property  name="constraints"
     */
    private GridBagConstraints constraints = new GridBagConstraints();

    /**
     * @uml.property  name="textArea"
     * @uml.associationEnd  multiplicity="(1 1)"
     */
    private JTextArea textArea;
    /**
     * @uml.property  name="textAreaChanged"
     */
    private boolean textAreaChanged;

    /**
     * @uml.property  name="crtOpenFile"
     */
    private File crtOpenFile;

    public PolicyEditor(PmAdmin tool, SSLSocketClient sslClient) {
        super(tool, false);

        this.tool = tool;
        this.sslClient = sslClient;
        setTitle("Policy Rules");

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent we) {
                doClose();
            }
        });

        // Start building the GUI
        JMenuBar menuBar = new JMenuBar();
        setJMenuBar(menuBar);

        // The File menu.
        JMenu menu = new JMenu("File");
        menuBar.add(menu);

        JMenuItem menuItem = new JMenuItem("Open...");
        menuItem.addActionListener(this);
        menu.add(menuItem);

        menu.addSeparator();

        menuItem = new JMenuItem("Save");
        menuItem.addActionListener(this);
        menu.add(menuItem);

        menuItem = new JMenuItem("Save As...");
        menuItem.addActionListener(this);
        menu.add(menuItem);

        menu.addSeparator();

        menuItem = new JMenuItem("Close");
        menuItem.addActionListener(this);
        menu.add(menuItem);

        menu = new JMenu("Operations");
        menuBar.add(menu);

        menuItem = new JMenuItem("Generate .pm File");
        menuItem.addActionListener(this);
        menu.add(menuItem);

        // Now the text area
        textArea = new JTextArea(40, 80);
        textArea.setFont(new Font("Lucida console", Font.PLAIN, 14));
        textArea.setLineWrap(true);
        textArea.getDocument().addDocumentListener(this);

        JScrollPane scrollPane = new JScrollPane(textArea,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);

        JPanel contentPane = new JPanel();
        contentPane.add(scrollPane);
        setContentPane(contentPane);
        this.setResizable(false);

    }

    public void prepare() {
        crtOpenFile = null;
        textArea.setText("");
        textAreaChanged = false;
    }

    public void actionPerformed(ActionEvent e) {
        String sCommand = ((JMenuItem)(e.getSource())).getText();

        if (sCommand.equalsIgnoreCase("Open...")) {
            doInput(true); // true = open (i.e., overwrite the old contents).
        } else if (e.getActionCommand().equalsIgnoreCase("Save")) {
            doSave();
        } else if (e.getActionCommand().equalsIgnoreCase("Save As...")) {
            doSaveAs();
        } else if (e.getActionCommand().equalsIgnoreCase("Close")) {
            doClose();
        } else if (e.getActionCommand().equalsIgnoreCase("Generate .pm File")) {
            doGenerateXML();
        }
    }

    /**
     * @uml.property  name="lastOpenedFile"
     */
    private File lastOpenedFile = null;

    private void doInput(boolean open) {
        int option;

        if (open && textAreaChanged) {
            option = JOptionPane.showConfirmDialog(this,
                    "You have unsaved document changes. Do you want to continue?",
                    "Open File", JOptionPane.YES_NO_OPTION);
            if (option != JOptionPane.YES_OPTION) return;
        }

        String[] evr = new String[] {"evr"};
        JFileChooser chooser = new JFileChooser(lastOpenedFile);
        chooser.addChoosableFileFilter(new SimpleFileFilter(evr, "Event Response (*.evr)"));
        option = chooser.showOpenDialog(this);
        if (option != JFileChooser.APPROVE_OPTION) return;
        if (chooser.getSelectedFile() == null) return;
        File f = chooser.getSelectedFile();
        if (f == null) return;
        if (!f.exists() || !f.isFile() || !f.canRead()) {
            JOptionPane.showMessageDialog(this, "The input file does not exist or cannot be read!");
            return;
        }
        lastOpenedFile = f;
        if (open) {
            crtOpenFile = f;
        }
        try {
            BufferedReader in = new BufferedReader(new FileReader(f));
            if (open) {
                textArea.setText("");
            }
            String sLine;
            int pos = textArea.getCaretPosition();
            String sNewLine = System.getProperty("line.separator");
            System.out.println("new line has " + sNewLine.length());
            while ((sLine = in.readLine()) != null) {
                if (open) {
                    textArea.append(sLine);
                    textArea.append("\n");
                } else {
                    textArea.insert(sLine, pos);
                    pos += sLine.length();
                    textArea.insert("\n", pos);
                    pos += "\n".length();
                }
            }
            if (open) {
                textAreaChanged = false;
                setTitle("Event-Response Rules - " + f.getName());
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Exception while reading data: " +
                    e.getMessage());
        }
    }

    // The Packet script contains the source in items 0,...,length - 1.
    public void doInputFromPacket(Packet script) {
        int option;
        if (textAreaChanged) {
            option = JOptionPane.showConfirmDialog(this,
                    "You have unsaved document changes. Do you want to continue?",
                    "Display Source Script", JOptionPane.YES_NO_OPTION);
            if (option != JOptionPane.YES_OPTION) return;
        }
        textArea.setText("");
        String sLine;
        int pos = textArea.getCaretPosition();
        String sNewline = "\n";

        for (int i = 0; i < script.size(); i++) {
            sLine = script.getStringValue(i);
            textArea.append(sLine);
            textArea.append(sNewline);
        }
        textAreaChanged = false;
        setTitle("Event-Response Rules - ");
    }

    private void doSaveAs() {
        String[] evr = new String[] {"evr"};
        JFileChooser chooser = new JFileChooser();
        chooser.addChoosableFileFilter(new SimpleFileFilter(evr, "Event Response Rules (*.evr)"));
        int option = chooser.showSaveDialog(this);
        if (option != JFileChooser.APPROVE_OPTION) return;
        if (chooser.getSelectedFile() == null) return;
        File f = chooser.getSelectedFile();
        if (f == null) return;
        String sPath = f.getAbsolutePath();
        if (!sPath.endsWith(".evr")) sPath += ".evr";
        f = new File(sPath);
        if (f.exists() && (!f.isFile() || !f.canWrite())) {
            JOptionPane.showMessageDialog(this, "The output file is not a file or cannot be written!");
            return;
        }
        try {
            FileOutputStream fos = new FileOutputStream(f);
            PrintWriter pw = new PrintWriter(fos);
            int lineCount = textArea.getLineCount();
            for (int i = 0; i < lineCount; i++) {
                int lineStart = textArea.getLineStartOffset(i);
                int lineEnd = textArea.getLineEndOffset(i);

                System.out.println("Line " + i + " starts at " + lineStart + " ends at " + lineEnd);
                if (lineEnd - lineStart > 1) {
                    String sLine = textArea.getText(lineStart, lineEnd - lineStart - 1);
                    pw.println(sLine);
                } else if (lineEnd - lineStart == 1) {
                    pw.println();
                }
            }
            pw.close();
            textAreaChanged = false;
            setTitle("Event-Response Rules - " + f.getName());
            crtOpenFile = f;
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Exception while exporting data: " +
                    e.getMessage());
            return;
        }
    }

    private void doSave() {
        if (crtOpenFile == null) {
            doSaveAs();
            return;
        }
        if (crtOpenFile.exists() && (!crtOpenFile.isFile() || !crtOpenFile.canWrite())) {
            JOptionPane.showMessageDialog(this, "The output file is not a file or cannot be written!");
            return;
        }
        try {
            FileOutputStream fos = new FileOutputStream(crtOpenFile);
            PrintWriter pw = new PrintWriter(fos);
            int lineCount = textArea.getLineCount();
            for (int i = 0; i < lineCount; i++) {
                int lineStart = textArea.getLineStartOffset(i);
                int lineEnd = textArea.getLineEndOffset(i);

                System.out.println("Line " + i + " starts at " + lineStart + " ends at " + lineEnd);
                if (lineEnd - lineStart > 1) {
                    String sLine = textArea.getText(lineStart, lineEnd - lineStart - 1);
                    pw.println(sLine);
                } else if (lineEnd - lineStart == 1) {
                    pw.println();
                }
            }
            pw.close();
            textAreaChanged = false;
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Exception while saving data: " +
                    e.getMessage());
            return;
        }
    }

    private void doClose() {
        if (textAreaChanged) {
            int option = JOptionPane.showConfirmDialog(this,
                    "You have unsaved document changes. Do you still want to close the editor?",
                    "Close Rule Editor", JOptionPane.YES_NO_OPTION);
            if (option != JOptionPane.YES_OPTION) return;
        }
        this.setVisible(false);
    }

    private void doGenerateXML() {
        int lineCount = textArea.getLineCount();
        System.out.println("Line count is " + lineCount);
        if (lineCount == 0) {
            JOptionPane.showMessageDialog(this, "Document is empty!");
            return;
        }

        try {
            for (int i = 0; i < lineCount; i++) {
                int lineStart = textArea.getLineStartOffset(i);
                int lineEnd = textArea.getLineEndOffset(i);
                String sLine = textArea.getText(lineStart, lineEnd - lineStart);
                System.out.println("Line " + i + " starts at " + lineStart +
                        " ends at " + lineEnd + " <" + sLine + ">");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Compile script and delete synonym scripts.
        try {
            Packet cmd = tool.makeCmd("generateXML", "yes");
            String lineSep = System.getProperty("line.separator");
            for (int i = 0; i < lineCount; i++) {
                int lineStart = textArea.getLineStartOffset(i);
                int lineEnd = textArea.getLineEndOffset(i);

                if (lineEnd > lineStart) {
                    String sLine = textArea.getText(lineStart, lineEnd - lineStart);
                    if (sLine.charAt(sLine.length()-1) < 0x20)
                        cmd.addItem(ItemType.CMD_ARG, sLine.substring(0, sLine.length() - 1));
                    else
                        cmd.addItem(ItemType.CMD_ARG, sLine);
                } else {
                    cmd.addItem(ItemType.CMD_ARG, "");
                }
            }

            Packet res = sslClient.sendReceive(cmd, null);
            if (res == null) {
                JOptionPane.showMessageDialog(this, "Engine returned a null result!");
                return;
            }
            if (res.hasError()) {
                String s = res.getErrorMessage();
                String sPrefix = "Error around line ";
                int startIndex = s.indexOf(sPrefix) + sPrefix.length();
                int endIndex = s.indexOf(":");
                if (startIndex < 0 || endIndex < 0) {
                    JOptionPane.showMessageDialog(this, res.getErrorMessage());
                    return;
                }
                int lineNo = Integer.parseInt(s.substring(startIndex, endIndex)) - 1;
                if (lineNo <= lineCount) {
                    textArea.select(textArea.getLineStartOffset(lineNo), textArea.getLineEndOffset(lineNo));
                }
                JOptionPane.showMessageDialog(this, res.getErrorMessage());
                return;
            }

            // The result should contain <script name>:<script id> or failure.
            if (res.hasError()) {
                JOptionPane.showMessageDialog(this, res.getErrorMessage());
            }else{
                JOptionPane.showMessageDialog(this, "Script \"" + res.getItemStringValue(0).split(GlobalConstants.PM_FIELD_DELIM)[0] + "\" compiled successfully!");
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Exception while compiling rules: " +
                    e.getMessage());
        }
    }


    public void changedUpdate(javax.swing.event.DocumentEvent documentEvent) {
        textAreaChanged = true;
    }

    public void insertUpdate(javax.swing.event.DocumentEvent documentEvent) {
        textAreaChanged = true;
    }

    public void removeUpdate(javax.swing.event.DocumentEvent documentEvent) {
        textAreaChanged = true;
    }
}
