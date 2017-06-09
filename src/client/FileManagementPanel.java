/*
 * Copyright (C) 2013 Alistair Neil <info@dazzleships.net>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package client;

import lib.FileTableModel;
import lib.GlobalFunctions;
import lib.InfoDialog;
import lib.SimpleINI;
import java.awt.Frame;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import javax.swing.JSpinner;
import javax.swing.SpinnerDateModel;
import javax.swing.SwingWorker;

/**
 *
 * @author Alistair Neil <info@dazzleships.net>
 */
public class FileManagementPanel extends javax.swing.JPanel {

    private static final GlobalFunctions gf = GlobalFunctions.getInstance();
    private final List<Integer> listBackup = new ArrayList<>();
    private final Date schedTime = new Date();
    private boolean boolDoBackupNow = false;
    private ConnectionHandler connHandler;
    private FileTableModel fileTableModel;
    private DialogHandler dh;
    private SimpleINI simpleIni;

    /**
     * Creates new form FileManagement
     */
    public FileManagementPanel() {
        initComponents();
        jProgDatabase.setVisible(false);
        jProgDatabase.setString("");
        jProgDatabase.setMinimum(0);
        jProgDatabase.setMaximum(100);
    }

    public void setConnection(final ConnectionHandler connHandler) {
        this.connHandler = connHandler;
        connHandler.getActiveSQL().setBulkInsertEnabled(true);
        connHandler.getActiveSQL().setBackupPath(getBackupFolder());
        connHandler.getBackupSQL().setBulkInsertEnabled(true);
        connHandler.getBackupSQL().setBackupPath(getBackupFolder());
        connHandler.getBackupSQL().getDatabases(jComboDatabases);
        // Check to see if script database is used
        boolean scriptDBPresent = (connHandler.getBackupSQL().getScriptDBName() != null);
        jCheckImmScriptdev.setEnabled(scriptDBPresent);
        jCheckSchedScriptdev.setEnabled(scriptDBPresent);
        // Setup our property listener which will update our database progress bars
        PropertyChangeListener propListen = new java.beans.PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                String strTable = connHandler.getBackupSQL().getActionTableName();
                String strStatus = connHandler.getBackupSQL().getActionFileName();
                if (!strTable.isEmpty()) {
                    strStatus += ", " + strTable;
                }
                strStatus += ", " + ((Integer) evt.getNewValue()).toString() + "%";
                if (jProgDatabase.isVisible()) {
                    jProgDatabase.setString(strStatus);
                    jProgDatabase.setValue((Integer) evt.getNewValue());
                }
            }
        };
        connHandler.getBackupSQL().addPropertyChangeListener(propListen);
    }

    /**
     * Set dialog handler
     *
     * @param dh
     */
    public void setDialogHandler(DialogHandler dh) {
        this.dh = dh;
    }

    /**
     * Convenience method to set the file table rowheight
     *
     * @param height
     */
    public void setTableRowHeight(int height) {
        jTableFiles.setRowHeight(height);
    }

    /**
     * Load preferences into preferences storage object
     *
     * @param simpleIni
     */
    public void loadPreferences(SimpleINI simpleIni) {
        this.simpleIni = simpleIni;
        simpleIni.setGroup("GUI");
        jCheckSchedMangos.setSelected(simpleIni.getBoolValue("schedmangos", false));
        jCheckSchedRealm.setSelected(simpleIni.getBoolValue("schedrealm", true));
        jCheckSchedChar.setSelected(simpleIni.getBoolValue("schedchar", true));
        jCheckSchedScriptdev.setSelected(simpleIni.getBoolValue("schedscript", true));
        jCheckSchedEnableBackup.setSelected(simpleIni.getBoolValue("schedenable", false));
        jSpinSchedBackupTime.setValue(simpleIni.getTime("schedtime", "00:00:00"));
        jTextBackupFolder.setText(simpleIni.getStringValue("backupfolder", gf.getUsersDocFolder()));
    }

    public void updatePreferences() {
        simpleIni.setGroup("GUI");
        simpleIni.setValue("backupfolder", jTextBackupFolder.getText());
        simpleIni.setValue("schedmangos", jCheckSchedMangos.isSelected());
        simpleIni.setValue("schedrealm", jCheckSchedRealm.isSelected());
        simpleIni.setValue("schedchar", jCheckSchedChar.isSelected());
        simpleIni.setValue("schedscript", jCheckSchedScriptdev.isSelected());
        simpleIni.setValue("schedenable", jCheckSchedEnableBackup.isSelected());
        simpleIni.setTime("schedtime", (Date) jSpinSchedBackupTime.getValue());
    }

    public String getBackupFolder() {
        return jTextBackupFolder.getText();
    }

    /**
     * Does what it says on the tin
     */
    public void updateFileTable() {

        if (fileTableModel == null) {
            fileTableModel = new MangosFileTableModel();
            fileTableModel.setFileFilter(".sql");
            jTableFiles.setModel(fileTableModel);
            jComboDatabases.setEnabled(false);
        }
        fileTableModel.setFilePath(connHandler.getBackupSQL().getBackupPath());
        fileTableModel.refreshFileList();
        fileTableModel.refreshTable();
        jTableFiles.getColumn("DB Version").setPreferredWidth(300);
        jLabelNoOfFiles.setText(fileTableModel.getRowCount() + " " + dh.getString("info_records"));
    }

    public void setFileTableEnabled(boolean enable) {
        jTableFiles.setEnabled(enable);
        jButtonFileBackup.setEnabled(enable);
        jMenuItemRestoreFile.setEnabled(false);
        jMenuItemDeleteFile.setEnabled(false);
    }

    public void updateTick() {

        if (!jCheckSchedEnableBackup.isSelected() || !jCheckSchedEnableBackup.isEnabled()) {
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        Date now;
        String strNow = sdf.format(new Date());
        try {
            now = sdf.parse(strNow);
        } catch (ParseException ex) {
            return;
        }
        if (now.getTime() == ((Date) jSpinSchedBackupTime.getValue()).getTime()) {
            boolDoBackupNow = true;
        }
        if (boolDoBackupNow && listBackup.isEmpty()) {
            boolDoBackupNow = false;
            if (jCheckSchedRealm.isSelected()) {
                listBackup.add(new Integer(MangosSql.WRITE_REALM));
            }
            if (jCheckSchedScriptdev.isSelected()) {
                listBackup.add(new Integer(MangosSql.WRITE_SCRIPTDEV));
            }
            if (jCheckSchedMangos.isSelected()) {
                listBackup.add(new Integer(MangosSql.WRITE_MANGOS));
            }
            if (jCheckSchedChar.isSelected()) {
                listBackup.add(new Integer(MangosSql.WRITE_CHAR));
            }
            if (listBackup.isEmpty()) {
                return;
            }
            backupDatabase();
        }

    }

    /**
     * Does what it says on the tin
     */
    private void backupDatabase() {

        // Disable associated buttons
        jComboDatabases.setEnabled(false);
        jButtonFileBackup.setEnabled(false);
        jTableFiles.setEnabled(false);

        // Setup and execute our background task
        SwingWorker task = new SwingWorker<String, String>() {
            @Override
            public String doInBackground() {
                for (Integer x : listBackup) {
                    connHandler.getBackupSQL().writeSQLDatabase(x.intValue());
                }
                return null;
            }

            @Override
            protected void done() {
                updateFileTable();
                jProgDatabase.setVisible(false);
                listBackup.clear();
                setFileTableEnabled(true);
                connHandler.setQuickConnectEnabled(true);
            }
        };
        connHandler.setQuickConnectEnabled(false);
        setFileTableEnabled(false);
        jProgDatabase.setVisible(true);
        // Start our task
        task.execute();
    }

    /**
     * Does what it says on the tin
     */
    private void restoreDatabase() {

        // Disable associated buttons
        jComboDatabases.setEnabled(false);
        jButtonFileBackup.setEnabled(false);
        jMenuItemRestoreFile.setEnabled(false);
        jMenuItemDeleteFile.setEnabled(false);

        // Setup and execute our background task
        SwingWorker task = new SwingWorker<String, Integer>() {
            String strDB = null;
            String strFilename = null;

            @Override
            public String doInBackground() {
                strFilename = fileTableModel.getSelectedFile(jTableFiles).getName();
                strDB = (String) jComboDatabases.getSelectedItem();
                connHandler.getBackupSQL().readSQLFile(strFilename, strDB);
                return null;
            }

            @Override
            protected void done() {
                // Hide progress bar
                jProgDatabase.setVisible(false);
                // Enable associated buttons
                jComboDatabases.setEnabled(true);
                jMenuItemRestoreFile.setEnabled(true);
                jMenuItemDeleteFile.setEnabled(true);
                jButtonFileBackup.setEnabled(true);
                // Callback for restore completion
                restoreCompleted(strFilename, strDB);
            }
        };

        task.execute();
        // Bring up our progress bar
        jProgDatabase.setVisible(true);
    }

    /**
     * Executed when a file has been restored, should normally be overriden in
     * parent class
     * @param filename
     * @param dbname
     */
    public void restoreCompleted(String filename, String dbname) {
    }

    private synchronized void fileDelete() {
        dh.createWarn("title_file_delete", "info_file_delete");
        dh.setVisible(true);
        if (dh.getReturnStatus() == InfoDialog.CANCEL) {
            return;
        }
        fileTableModel.deleteRecords(jTableFiles);
        updateFileTable();
        setFileTableEnabled(true);
    }

    private void selectDatabaseFile() {
        if (!jTableFiles.isEnabled()) {
            return;
        }
        String fname = fileTableModel.getSelectedFile(jTableFiles).getName();
        String dbname = connHandler.getBackupSQL().getDBNameFromFilename(fname);
        for (int i = 0; i < jComboDatabases.getItemCount(); i++) {
            if (((String) jComboDatabases.getItemAt(i)).contains(dbname)) {
                jComboDatabases.setSelectedIndex(i);
                break;
            }
        }
        if (jTableFiles.getSelectedRowCount() == 1) {
            if (fname.contains("portals")) {
                jMenuItemRestoreFile.setEnabled(false);
                if (connHandler.getBackupSQL().getPortalVersion(fname) >= 1.1) {
                    jMenuItemRestoreFile.setEnabled(true);
                }
            } else {
                jMenuItemRestoreFile.setEnabled(true);

            }
            jMenuItemDeleteFile.setEnabled(true);
            jComboDatabases.setEnabled(true);
        } else {
            jMenuItemRestoreFile.setEnabled(false);
            jComboDatabases.setEnabled(false);
        }
    }

    private void immediateBackup() {

        if (jCheckImmRealm.isSelected()) {
            listBackup.add(new Integer(MangosSql.WRITE_REALM));
        }
        if (jCheckImmScriptdev.isSelected()) {
            listBackup.add(new Integer(MangosSql.WRITE_SCRIPTDEV));
        }
        if (jCheckImmMangos.isSelected()) {
            listBackup.add(new Integer(MangosSql.WRITE_MANGOS));
        }
        if (jCheckImmChar.isSelected()) {
            listBackup.add(new Integer(MangosSql.WRITE_CHAR));
        }

        if (listBackup.isEmpty()) {
            return;
        }
        backupDatabase();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPopupFile = new javax.swing.JPopupMenu();
        jMenuItemRefreshFile = new javax.swing.JMenuItem();
        jMenuItemRestoreFile = new javax.swing.JMenuItem();
        jMenuItemDeleteFile = new javax.swing.JMenuItem();
        jPanelImmediate = new javax.swing.JPanel();
        jCheckImmRealm = new javax.swing.JCheckBox();
        jCheckImmMangos = new javax.swing.JCheckBox();
        jCheckImmChar = new javax.swing.JCheckBox();
        jCheckImmScriptdev = new javax.swing.JCheckBox();
        jButtonFileBackup = new javax.swing.JButton();
        jPanelScheduled = new javax.swing.JPanel();
        jCheckSchedRealm = new javax.swing.JCheckBox();
        jCheckSchedMangos = new javax.swing.JCheckBox();
        jCheckSchedChar = new javax.swing.JCheckBox();
        jCheckSchedScriptdev = new javax.swing.JCheckBox();
        jCheckSchedEnableBackup = new javax.swing.JCheckBox();
        jSpinSchedBackupTime = new javax.swing.JSpinner();
        jPanelFile = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTableFiles = new javax.swing.JTable();
        jLabel13 = new javax.swing.JLabel();
        jComboDatabases = new javax.swing.JComboBox();
        jTextBackupFolder = new javax.swing.JTextField();
        jButtonSetFolder = new javax.swing.JButton();
        jProgDatabase = new javax.swing.JProgressBar();
        jLabelNoOfFiles = new javax.swing.JLabel();

        jPopupFile.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("lang/MessagesBundle"); // NOI18N
        jMenuItemRefreshFile.setText(bundle.getString("mitem_refreshtable")); // NOI18N
        jMenuItemRefreshFile.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemRefreshFileActionPerformed(evt);
            }
        });
        jPopupFile.add(jMenuItemRefreshFile);

        jMenuItemRestoreFile.setText(bundle.getString("mitem_restorefile")); // NOI18N
        jMenuItemRestoreFile.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemRestoreFileActionPerformed(evt);
            }
        });
        jPopupFile.add(jMenuItemRestoreFile);

        jMenuItemDeleteFile.setText(bundle.getString("mitem_deletefile")); // NOI18N
        jMenuItemDeleteFile.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemDeleteFileActionPerformed(evt);
            }
        });
        jPopupFile.add(jMenuItemDeleteFile);

        setFont(getFont().deriveFont(getFont().getStyle() | java.awt.Font.BOLD, getFont().getSize()+3));
        setPreferredSize(new java.awt.Dimension(1010, 740));

        jPanelImmediate.setBorder(javax.swing.BorderFactory.createTitledBorder(null, bundle.getString("pan_immbackup"), javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, getFont(), getForeground())); // NOI18N
        jPanelImmediate.setOpaque(false);

        jCheckImmRealm.setText(bundle.getString("check_realm")); // NOI18N
        jCheckImmRealm.setRolloverEnabled(false);

        jCheckImmMangos.setText(bundle.getString("check_mangos")); // NOI18N
        jCheckImmMangos.setRolloverEnabled(false);

        jCheckImmChar.setText(bundle.getString("check_char")); // NOI18N
        jCheckImmChar.setRolloverEnabled(false);

        jCheckImmScriptdev.setText(bundle.getString("check_script")); // NOI18N
        jCheckImmScriptdev.setRolloverEnabled(false);

        jButtonFileBackup.setText(bundle.getString("butt_backup")); // NOI18N
        jButtonFileBackup.setToolTipText("Start Selected Backups"); // NOI18N
        jButtonFileBackup.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonFileBackupActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanelImmediateLayout = new javax.swing.GroupLayout(jPanelImmediate);
        jPanelImmediate.setLayout(jPanelImmediateLayout);
        jPanelImmediateLayout.setHorizontalGroup(
            jPanelImmediateLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelImmediateLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelImmediateLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanelImmediateLayout.createSequentialGroup()
                        .addComponent(jCheckImmRealm)
                        .addGap(18, 18, 18)
                        .addComponent(jCheckImmMangos)
                        .addGap(18, 18, 18)
                        .addComponent(jCheckImmChar)
                        .addGap(18, 18, 18)
                        .addComponent(jCheckImmScriptdev))
                    .addComponent(jButtonFileBackup, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 135, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanelImmediateLayout.setVerticalGroup(
            jPanelImmediateLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelImmediateLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelImmediateLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jCheckImmRealm)
                    .addComponent(jCheckImmMangos)
                    .addComponent(jCheckImmChar)
                    .addComponent(jCheckImmScriptdev))
                .addGap(18, 18, 18)
                .addComponent(jButtonFileBackup)
                .addContainerGap())
        );

        jPanelScheduled.setBorder(javax.swing.BorderFactory.createTitledBorder(null, bundle.getString("pan_schedbackup"), javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, getFont(), getForeground())); // NOI18N
        jPanelScheduled.setOpaque(false);

        jCheckSchedRealm.setText(bundle.getString("check_realm")); // NOI18N
        jCheckSchedRealm.setRolloverEnabled(false);

        jCheckSchedMangos.setText(bundle.getString("check_mangos")); // NOI18N
        jCheckSchedMangos.setRolloverEnabled(false);

        jCheckSchedChar.setText(bundle.getString("check_char")); // NOI18N
        jCheckSchedChar.setRolloverEnabled(false);

        jCheckSchedScriptdev.setText(bundle.getString("check_script")); // NOI18N
        jCheckSchedScriptdev.setRolloverEnabled(false);

        jCheckSchedEnableBackup.setText(bundle.getString("check_backupat")); // NOI18N
        jCheckSchedEnableBackup.setRolloverEnabled(false);

        jSpinSchedBackupTime.setModel(new SpinnerDateModel(schedTime, null, null, Calendar.HOUR_OF_DAY));
        jSpinSchedBackupTime.setEditor(new JSpinner.DateEditor(jSpinSchedBackupTime, "HH:mm"));
        jSpinSchedBackupTime.setOpaque(false);

        javax.swing.GroupLayout jPanelScheduledLayout = new javax.swing.GroupLayout(jPanelScheduled);
        jPanelScheduled.setLayout(jPanelScheduledLayout);
        jPanelScheduledLayout.setHorizontalGroup(
            jPanelScheduledLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelScheduledLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelScheduledLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanelScheduledLayout.createSequentialGroup()
                        .addComponent(jCheckSchedRealm)
                        .addGap(18, 18, 18)
                        .addComponent(jCheckSchedMangos)
                        .addGap(18, 18, 18)
                        .addComponent(jCheckSchedChar)
                        .addGap(18, 18, 18)
                        .addComponent(jCheckSchedScriptdev))
                    .addGroup(jPanelScheduledLayout.createSequentialGroup()
                        .addComponent(jCheckSchedEnableBackup)
                        .addGap(18, 18, 18)
                        .addComponent(jSpinSchedBackupTime, javax.swing.GroupLayout.PREFERRED_SIZE, 83, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanelScheduledLayout.setVerticalGroup(
            jPanelScheduledLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelScheduledLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelScheduledLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jCheckSchedRealm, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jCheckSchedMangos, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanelScheduledLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jCheckSchedChar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jCheckSchedScriptdev, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addGap(18, 18, 18)
                .addGroup(jPanelScheduledLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jSpinSchedBackupTime, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jCheckSchedEnableBackup))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanelFile.setBorder(javax.swing.BorderFactory.createTitledBorder(null, bundle.getString("pan_filebrowse"), javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, getFont(), getForeground())); // NOI18N

        jScrollPane1.setToolTipText(bundle.getString("tooltip_tables")); // NOI18N

        jTableFiles.setAutoCreateRowSorter(true);
        jTableFiles.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {},
                {},
                {},
                {}
            },
            new String [] {

            }
        ));
        jTableFiles.setComponentPopupMenu(jPopupFile);
        jTableFiles.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                jTableFilesMouseReleased(evt);
            }
        });
        jTableFiles.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                jTableFilesKeyReleased(evt);
            }
        });
        jScrollPane1.setViewportView(jTableFiles);

        jLabel13.setText(bundle.getString("lab_restoredb")); // NOI18N

        jButtonSetFolder.setText(bundle.getString("butt_browse")); // NOI18N
        jButtonSetFolder.setToolTipText("Set the folder where backups will be saved.");
        jButtonSetFolder.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonSetFolderActionPerformed(evt);
            }
        });

        jProgDatabase.setToolTipText("Save / Restore Status");
        jProgDatabase.setStringPainted(true);

        jLabelNoOfFiles.setText("No of records returned");

        javax.swing.GroupLayout jPanelFileLayout = new javax.swing.GroupLayout(jPanelFile);
        jPanelFile.setLayout(jPanelFileLayout);
        jPanelFileLayout.setHorizontalGroup(
            jPanelFileLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelFileLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelFileLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanelFileLayout.createSequentialGroup()
                        .addComponent(jTextBackupFolder, javax.swing.GroupLayout.PREFERRED_SIZE, 445, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jButtonSetFolder)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jLabel13)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jComboDatabases, javax.swing.GroupLayout.PREFERRED_SIZE, 149, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanelFileLayout.createSequentialGroup()
                        .addComponent(jProgDatabase, javax.swing.GroupLayout.PREFERRED_SIZE, 660, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jLabelNoOfFiles)))
                .addContainerGap())
        );
        jPanelFileLayout.setVerticalGroup(
            jPanelFileLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelFileLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelFileLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(jTextBackupFolder, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButtonSetFolder)
                    .addComponent(jComboDatabases, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel13))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 320, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelFileLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jProgDatabase, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabelNoOfFiles))
                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanelFile, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jPanelImmediate, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanelScheduled, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jPanelImmediate, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanelScheduled, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanelFile, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        getAccessibleContext().setAccessibleName("File Management");
    }// </editor-fold>//GEN-END:initComponents

    private void jButtonFileBackupActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonFileBackupActionPerformed
        immediateBackup();
    }//GEN-LAST:event_jButtonFileBackupActionPerformed

    private void jTableFilesKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_jTableFilesKeyReleased
        switch (evt.getKeyCode()) {
            case KeyEvent.VK_UP:
            case KeyEvent.VK_DOWN:
            case KeyEvent.VK_KP_DOWN:
            case KeyEvent.VK_KP_UP:
                selectDatabaseFile();
                break;
        }
    }//GEN-LAST:event_jTableFilesKeyReleased

    private void jButtonSetFolderActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonSetFolderActionPerformed

        String strResult = gf.openFileChooser((Frame) this.getTopLevelAncestor(), jTextBackupFolder.getText(), null, 1);
        if (strResult != null) {
            jTextBackupFolder.setText(strResult);
            connHandler.getBackupSQL().setBackupPath(strResult);
            updateFileTable();
            setFileTableEnabled(true);
        }
    }//GEN-LAST:event_jButtonSetFolderActionPerformed

    private void jMenuItemRefreshFileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemRefreshFileActionPerformed
        updateFileTable();
        setFileTableEnabled(true);
    }//GEN-LAST:event_jMenuItemRefreshFileActionPerformed

    private void jMenuItemRestoreFileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemRestoreFileActionPerformed
        restoreDatabase();
    }//GEN-LAST:event_jMenuItemRestoreFileActionPerformed

    private void jMenuItemDeleteFileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemDeleteFileActionPerformed
        fileDelete();
    }//GEN-LAST:event_jMenuItemDeleteFileActionPerformed

    private void jTableFilesMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jTableFilesMouseReleased
        if (evt.getButton() == MouseEvent.BUTTON1) {
            selectDatabaseFile();
        }
    }//GEN-LAST:event_jTableFilesMouseReleased
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButtonFileBackup;
    private javax.swing.JButton jButtonSetFolder;
    private javax.swing.JCheckBox jCheckImmChar;
    private javax.swing.JCheckBox jCheckImmMangos;
    private javax.swing.JCheckBox jCheckImmRealm;
    private javax.swing.JCheckBox jCheckImmScriptdev;
    private javax.swing.JCheckBox jCheckSchedChar;
    private javax.swing.JCheckBox jCheckSchedEnableBackup;
    private javax.swing.JCheckBox jCheckSchedMangos;
    private javax.swing.JCheckBox jCheckSchedRealm;
    private javax.swing.JCheckBox jCheckSchedScriptdev;
    private javax.swing.JComboBox jComboDatabases;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabelNoOfFiles;
    private javax.swing.JMenuItem jMenuItemDeleteFile;
    private javax.swing.JMenuItem jMenuItemRefreshFile;
    private javax.swing.JMenuItem jMenuItemRestoreFile;
    private javax.swing.JPanel jPanelFile;
    private javax.swing.JPanel jPanelImmediate;
    private javax.swing.JPanel jPanelScheduled;
    private javax.swing.JPopupMenu jPopupFile;
    private javax.swing.JProgressBar jProgDatabase;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JSpinner jSpinSchedBackupTime;
    private javax.swing.JTable jTableFiles;
    private javax.swing.JTextField jTextBackupFolder;
    // End of variables declaration//GEN-END:variables
}
