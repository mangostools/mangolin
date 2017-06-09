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

import lib.ComboSelectDialog;
import lib.InfoDialog;
import lib.LineInputDialog;
import lib.SpinnerInputDialog;
import java.awt.Cursor;
import java.awt.Frame;
import java.awt.event.MouseEvent;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;

/**
 *
 * @author Alistair Neil <info@dazzleships.net>
 */
public class ServerRealmPanel extends javax.swing.JPanel {

    private static final Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private final Pattern patternNewline = Pattern.compile("\n\r");
    private ConnectionHandler connHandler;
    private DialogHandler dh;
    private int intRealmRefreshTimer = -1;

    /**
     * Creates new form ServerRealmPanel
     */
    public ServerRealmPanel() {
        initComponents();
    }

    public JTextArea getLogOutputArea() {
        return jTextActionLog;
    }

    /**
     * Set up our connection object
     *
     * @param connHandler
     */
    public void setConnection(ConnectionHandler connHandler) {
        this.connHandler = connHandler;
        // Create our statements
        connHandler.getActiveSQL().createRealmStatement("realms");
    }

    /**
     * Set our dialog handler
     *
     * @param dh
     */
    public void setDialogHandler(DialogHandler dh) {
        this.dh = dh;
    }

    /**
     * Provide this panel with timing functionality
     */
    public void updateTick() {

        if (intRealmRefreshTimer > -1) {
            intRealmRefreshTimer--;
            if (intRealmRefreshTimer == 0) {
                intRealmRefreshTimer--;
                refreshRealmTable();
            }
        }
    }

    /**
     * Update the realm table after a suitable delay
     *
     * @param delay
     */
    public void updateRealmTable(int delay) {
        if (delay == 0) {
            refreshRealmTable();
        } else {
            intRealmRefreshTimer = delay;
        }
    }

    /**
     * Set realm menus enabled condition
     *
     * @param enabled
     */
    public void setRealmMenusEnabled(boolean enabled) {
        if (enabled) {
            jMenuItemDeleteRealm.setEnabled(true);
        } else {
            dbTableRealm.clearSelection();
            jMenuItemDeleteRealm.setEnabled(false);
        }
    }

    /**
     *
     */
    public void resetPanel() {
        dbTableRealm.getModel().reset();
        dbTableRealm.setSelectionRetention(false);
    }

    /**
     * Convenience method, for gettting the Realm table adjusred row height
     *
     * @return height as an int
     */
    public int getTableRowHeight() {
        return dbTableRealm.getRowHeight();
    }

    /**
     * Realm table refresh
     */
    private void refreshRealmTable() {

        // Realm table refresh
        connHandler.getActiveSQL().setStatement("realms");
        String query = "select id,name,address,port,icon,realmflags,population from realmlist";
        dbTableRealm.setSelectionRetention(true);
        dbTableRealm.setResultSet(connHandler.getActiveSQL().executeQuery(query));
        dbTableRealm.getModel().setPrimaryKey("id");
        dbTableRealm.enableTextEditor("name");
        dbTableRealm.enableTextEditor("address");
        dbTableRealm.enableTextEditor("port");
        dbTableRealm.enableComboEditor("icon", true, "Normal", "PVP", "Unused", "Unused", "Normal",
                "Unused", "RP", "Unused", "RP PVP");
        dbTableRealm.enableComboEditor("realmflags", true, "White", "Red", "Grey (Offline)");
        dbTableRealm.getModel().refreshTableContents();
        dbTableRealm.autoAdjustRowHeight();
        if (dbTableRealm.getSelectedRowCount() == 0) {
            setRealmMenusEnabled(false);
        }
    }

    private void realmCreate() {
        try {
            dbTableRealm.getModel().addNewRecord("New Realm", "127.0.0.1", "8085", "1", "0");
            updateRealmTable(0);
        } catch (Exception ex) {
            if (ex.getMessage().contains("Duplicate")) {
                dh.createError("title_realmcreate_err", "info_realmcreate_err");
                dh.setVisible(true);
                return;
            }
            logger.throwing(this.getClass().getName(), "realmCreate()", ex);
        }
    }

    private void realmDelete() {
        dh.createWarn("title_realm_delete", "info_realm_delete");
        dh.setVisible(true);
        if (dh.getReturnStatus() == InfoDialog.CANCEL) {
            return;
        }
        dbTableRealm.getModel().deleteRecords(dbTableRealm);
        updateRealmTable(0);
    }

    public void saveAllPlayers() {
        setCursor(new Cursor(Cursor.WAIT_CURSOR));
        connHandler.getRAConnection().saveAll();
        setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
    }

    public void clearCorpses() {
        connHandler.getRAConnection().clearCorpses();
    }

    public void optimiseDB() {

        dh.createWarn("title_optimisedb", "info_optimisedb");
        dh.setVisible(true);
        if (dh.getReturnStatus() == InfoDialog.CANCEL) {
            return;
        }

        dh.createInfo("title_optimisedb");
        dh.setModal(false);
        dh.setAckEnabled(false);
        dh.setAutoScrollEnabled(true);
        dh.setVisible(true);

        SwingWorker task = new SwingWorker<String, Integer>() {
            @Override
            public String doInBackground() {
                setCursor(new Cursor(Cursor.WAIT_CURSOR));
                connHandler.getActiveSQL().optimizeRealmCharsDB(dh);
                return null;
            }

            @Override
            protected void done() {
                dh.getTextArea().append("\n");
                dh.getTextArea().append(dh.getString("info_optimisedb_finished"));
                dh.setAutoScrollEnabled(false);
                dh.setAckEnabled(true);
                setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
            }
        };
        task.execute();
    }

    public void shutdownNow() {
        String result = connHandler.getRAConnection().shutdown("0");
    }

    public void shutdownDelay() {
        SpinnerInputDialog numDialog = new SpinnerInputDialog((Frame) getTopLevelAncestor(), true);
        numDialog.setTitle(dh.getString("title_shut_delay"));
        numDialog.setEntryLabel(dh.getString("lab_shut_delay") + " :");
        numDialog.setLocationRelativeTo(this);
        numDialog.setVisible(true);
        if (numDialog.getReturnStatus() == SpinnerInputDialog.RET_CANCEL) {
            return;
        }
        String result = connHandler.getRAConnection().shutdown(numDialog.getValue().toString());
    }

    public void idleShutdown() {
        SpinnerInputDialog numDialog = new SpinnerInputDialog((Frame) getTopLevelAncestor(), true);
        numDialog.setTitle(dh.getString("title_shut_idle"));
        numDialog.setEntryLabel(dh.getString("lab_shut_delay") + " :");
        numDialog.setLocationRelativeTo(this);
        numDialog.setVisible(true);
        if (numDialog.getReturnStatus() == SpinnerInputDialog.RET_CANCEL) {
            return;
        }
        String result = connHandler.getRAConnection().idleShutdown(numDialog.getValue().toString());
    }

    public void playerLimits() {
        PLimitDialog pd = new PLimitDialog((Frame) getTopLevelAncestor(), true);
        pd.setLocationRelativeTo(this);
        pd.setVisible(true);
        if (pd.getReturnStatus() == PLimitDialog.RET_CANCEL) {
            return;
        }
        String result;
        if (pd.getSelectedOption().contains("player")) {
            result = connHandler.getRAConnection().setPlayerLimit(pd.getNumericalInput());
        } else {
            result = connHandler.getRAConnection().setPlayerLimit(pd.getSelectedOption());
        }
    }

    public void setMotd() {
        LineInputDialog lid = new LineInputDialog((Frame) getTopLevelAncestor(), true);
        lid.setTitle(dh.getString("title_motd"));
        lid.setLocationRelativeTo(this);
        lid.setVisible(true);
        if (lid.getReturnStatus() == LineInputDialog.RET_CANCEL || lid.getMessage().isEmpty()) {
            return;
        }
        String result = connHandler.getRAConnection().setMotd(lid.getMessage());
    }

    public void systemAnnounce() {
        LineInputDialog lid = new LineInputDialog((Frame) getTopLevelAncestor(), true);
        lid.setTitle(dh.getString("title_announce"));
        lid.setMessageLabel(dh.getString("lab_message_entry") + " :");
        lid.setLocationRelativeTo(this);
        lid.setVisible(true);
        if (lid.getReturnStatus() == LineInputDialog.RET_CANCEL) {
            return;
        }
        if (lid.getMessage().isEmpty()) {
            return;
        }
        String result = connHandler.getRAConnection().announce(lid.getMessage());
    }

    public void systemNotify() {
        LineInputDialog lid = new LineInputDialog((Frame) getTopLevelAncestor(), true);
        lid.setTitle(dh.getString("title_notify"));
        lid.setMessageLabel(dh.getString("lab_message_entry") + " :");
        lid.setLocationRelativeTo(this);
        lid.setVisible(true);
        if (lid.getReturnStatus() == LineInputDialog.RET_CANCEL || lid.getMessage().isEmpty()) {
            return;
        }
        String result = connHandler.getRAConnection().notify(lid.getMessage());
    }

    public void reloadTable() {
        ComboSelectDialog csd = new ComboSelectDialog((Frame) getTopLevelAncestor(), true);
        csd.setTitle(dh.getString("title_reloadtable"));
        csd.setComboLabel(dh.getString("combo_reloadtable"));
        csd.setLocationRelativeTo(this);
        String result = connHandler.getRAConnection().help("reload");
        String[] strArr = patternNewline.split(result);
        int idx;
        String[] options = new String[strArr.length - 1];
        for (idx = 1; idx < strArr.length; idx++) {
            options[idx - 1] = strArr[idx].trim();
        }
        csd.setOptions(options);
        csd.setVisible(true);
        if (csd.getReturnStatus() == ComboSelectDialog.RET_CANCEL) {
            return;
        }
        setCursor(new Cursor(Cursor.WAIT_CURSOR));
        connHandler.getRAConnection().reloadTable(csd.getSelectedOption());
        setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPopupRealms = new javax.swing.JPopupMenu();
        jMenuItemRefreshRealms = new javax.swing.JMenuItem();
        jMenuItemCreateRealm = new javax.swing.JMenuItem();
        jMenuItemDeleteRealm = new javax.swing.JMenuItem();
        jScrollPane2 = new javax.swing.JScrollPane();
        jTextServerInfo2 = new javax.swing.JTextArea();
        jPanelLog = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTextActionLog = new javax.swing.JTextArea();
        jPanelRealmList = new javax.swing.JPanel();
        jLabelInline = new javax.swing.JLabel();
        jScrollPane4 = new javax.swing.JScrollPane();
        dbTableRealm = new lib.DBJTableBean();

        jPopupRealms.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("lang/MessagesBundle"); // NOI18N
        jMenuItemRefreshRealms.setText(bundle.getString("mitem_refreshtable")); // NOI18N
        jMenuItemRefreshRealms.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemRefreshRealmsActionPerformed(evt);
            }
        });
        jPopupRealms.add(jMenuItemRefreshRealms);

        jMenuItemCreateRealm.setText(bundle.getString("mitem_createrealm")); // NOI18N
        jMenuItemCreateRealm.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemCreateRealmActionPerformed(evt);
            }
        });
        jPopupRealms.add(jMenuItemCreateRealm);

        jMenuItemDeleteRealm.setText(bundle.getString("mitem_deleterealm")); // NOI18N
        jMenuItemDeleteRealm.setActionCommand("Delete Selected Realms");
        jMenuItemDeleteRealm.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemDeleteRealmActionPerformed(evt);
            }
        });
        jPopupRealms.add(jMenuItemDeleteRealm);

        jTextServerInfo2.setEditable(false);
        jTextServerInfo2.setColumns(20);
        jTextServerInfo2.setForeground(getForeground());
        jTextServerInfo2.setLineWrap(true);
        jTextServerInfo2.setRows(5);
        jTextServerInfo2.setBorder(null);
        jTextServerInfo2.setFocusable(false);
        jTextServerInfo2.setOpaque(false);
        jScrollPane2.setViewportView(jTextServerInfo2);

        setFont(getFont().deriveFont(getFont().getStyle() | java.awt.Font.BOLD, getFont().getSize()+3));

        jPanelLog.setBorder(javax.swing.BorderFactory.createTitledBorder(null, bundle.getString("pan_logging"), javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, getFont(), getForeground())); // NOI18N
        jPanelLog.setFont(jPanelLog.getFont().deriveFont(jPanelLog.getFont().getStyle() | java.awt.Font.BOLD));

        jScrollPane1.setBorder(null);

        jTextActionLog.setEditable(false);
        jTextActionLog.setColumns(20);
        jTextActionLog.setLineWrap(true);
        jTextActionLog.setRows(5);
        jTextActionLog.setBorder(null);
        jTextActionLog.setFocusable(false);
        jScrollPane1.setViewportView(jTextActionLog);

        javax.swing.GroupLayout jPanelLogLayout = new javax.swing.GroupLayout(jPanelLog);
        jPanelLog.setLayout(jPanelLogLayout);
        jPanelLogLayout.setHorizontalGroup(
            jPanelLogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelLogLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 583, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanelLogLayout.setVerticalGroup(
            jPanelLogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelLogLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 232, Short.MAX_VALUE)
                .addContainerGap())
        );

        jPanelRealmList.setBorder(javax.swing.BorderFactory.createTitledBorder(null, bundle.getString("pan_realms"), javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, getFont(), getForeground())); // NOI18N
        jPanelRealmList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jPanelRealmListMouseClicked(evt);
            }
        });

        jLabelInline.setText(bundle.getString("lab_editingsupport")); // NOI18N

        jScrollPane4.setToolTipText(bundle.getString("tooltip_tables")); // NOI18N

        dbTableRealm.setAutoColumnEnabled(true);
        dbTableRealm.setComponentPopupMenu(jPopupRealms);
        dbTableRealm.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                dbTableRealmMousePressed(evt);
            }
        });
        jScrollPane4.setViewportView(dbTableRealm);

        javax.swing.GroupLayout jPanelRealmListLayout = new javax.swing.GroupLayout(jPanelRealmList);
        jPanelRealmList.setLayout(jPanelRealmListLayout);
        jPanelRealmListLayout.setHorizontalGroup(
            jPanelRealmListLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelRealmListLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelRealmListLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane4, javax.swing.GroupLayout.DEFAULT_SIZE, 583, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelRealmListLayout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(jLabelInline)))
                .addContainerGap())
        );
        jPanelRealmListLayout.setVerticalGroup(
            jPanelRealmListLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelRealmListLayout.createSequentialGroup()
                .addComponent(jLabelInline)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane4, javax.swing.GroupLayout.DEFAULT_SIZE, 231, Short.MAX_VALUE)
                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanelLog, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanelRealmList, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanelRealmList, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanelLog, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void dbTableRealmMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_dbTableRealmMousePressed
        if (evt.getButton() == MouseEvent.BUTTON1) {
            setRealmMenusEnabled(true);
        }
    }//GEN-LAST:event_dbTableRealmMousePressed

    private void jPanelRealmListMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jPanelRealmListMouseClicked
        setRealmMenusEnabled(false);
    }//GEN-LAST:event_jPanelRealmListMouseClicked

    private void jMenuItemRefreshRealmsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemRefreshRealmsActionPerformed
        updateRealmTable(0);
    }//GEN-LAST:event_jMenuItemRefreshRealmsActionPerformed

    private void jMenuItemCreateRealmActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemCreateRealmActionPerformed
        realmCreate();
    }//GEN-LAST:event_jMenuItemCreateRealmActionPerformed

    private void jMenuItemDeleteRealmActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemDeleteRealmActionPerformed
        realmDelete();
    }//GEN-LAST:event_jMenuItemDeleteRealmActionPerformed
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private lib.DBJTableBean dbTableRealm;
    private javax.swing.JLabel jLabelInline;
    private javax.swing.JMenuItem jMenuItemCreateRealm;
    private javax.swing.JMenuItem jMenuItemDeleteRealm;
    private javax.swing.JMenuItem jMenuItemRefreshRealms;
    private javax.swing.JPanel jPanelLog;
    private javax.swing.JPanel jPanelRealmList;
    private javax.swing.JPopupMenu jPopupRealms;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JTextArea jTextActionLog;
    private javax.swing.JTextArea jTextServerInfo2;
    // End of variables declaration//GEN-END:variables
}
