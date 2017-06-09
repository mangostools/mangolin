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

import lib.DBJTableBean;
import lib.GlobalFunctions;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;

/**
 *
 * @author Alistair Neil <info@dazzleships.net>
 */
public class BannedPanel extends javax.swing.JPanel {

    private final GlobalFunctions gf = GlobalFunctions.getInstance();
    private ConnectionHandler connHandler;
    private DialogHandler dh;
    private int intBanRefreshTimer = -1;

    /**
     * Creates new form BannedPanel
     */
    public BannedPanel() {
        initComponents();
        jProgActBans.setVisible(false);
        jProgInActBans.setVisible(false);
    }

    public void setConnection(ConnectionHandler connHandler) {
        this.connHandler = connHandler;
        // Create the statements used in this panel
        connHandler.getActiveSQL().createRealmStatement("bansactive");
        connHandler.getActiveSQL().createRealmStatement("bansinactive");
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
     * Provide this panel with timeing
     */
    public void updateTick() {

        if (intBanRefreshTimer > -1) {
            intBanRefreshTimer--;
            if (intBanRefreshTimer == 0) {
                intBanRefreshTimer--;
                refreshBanTables();
            }
        }
    }

    /**
     * Update account table after specified delay
     *
     * @param delay
     */
    public void updateBanTables(int delay) {
        if (delay < 0) {
            dbTableActiveBans.clearSelection();
            dbTableInactiveBans.clearSelection();
            return;
        }
        if (delay == 0) {
            refreshBanTables();
        } else {
            intBanRefreshTimer = delay;
        }
    }

    /**
     * Enable/Disable active ban menus
     *
     * @param enabled
     */
    public void setActiveBanMenusEnabled(boolean enabled) {
        jMenuItemUnban.setEnabled(enabled & connHandler.isRAConnected());
    }

    /**
     * Enable/Disable inactive ban menus
     *
     * @param enabled
     */
    public void setInActiveBanMenusEnabled(boolean enabled) {
        jMenuItemDeleteEntry.setEnabled(enabled);
    }

    /**
     * Does what it says on the tin
     */
    private void refreshBanTables() {

        setActiveBanMenusEnabled(false);
        setInActiveBanMenusEnabled(false);
        // Using a custom query to do some fancy stuff by joining 2 tables together
        String query = "select username as 'acct or ip',from_unixtime(bandate,'%m.%d.%Y %H:%i:%s') as bandate,"
                + "from_unixtime(unbandate,'%m.%d.%Y %H:%i:%s') as unbandate,banreason from *realm*.account_banned "
                + "left join *realm*.account on *realm*.account_banned.id = *realm*.account.id "
                + "where active = 1 union select ip,from_unixtime(bandate,'%m.%d.%Y %H:%i:%s'),from_unixtime(unbandate,'%m.%d.%Y %H:%i:%s'),"
                + "banreason from *realm*.ip_banned";
        connHandler.getActiveSQL().setStatement("bansactive");
        dbTableActiveBans.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        dbTableActiveBans.getModel().setResultSet(connHandler.getActiveSQL().executeQuery(query));
        dbTableActiveBans.getModel().setPrimaryKey("Username");
        dbTableActiveBans.getModel().refreshTableContents();
        dbTableActiveBans.autoAdjustRowHeight();
        jLabelActBans.setText(dbTableActiveBans.getRowCount() + " " + dh.getString("info_records"));

        // Using a custom query to do some fancy stuff by joining 2 tables together
        query = "select username as 'account',from_unixtime(bandate,'%m.%d.%Y %H:%i:%s') as bandate,"
                + "from_unixtime(unbandate,'%m.%d.%Y %H:%i:%s') as unbandate,banreason from *realm*.account_banned "
                + "left join *realm*.account on *realm*.account_banned.id = *realm*.account.id "
                + "where active = 0";
        connHandler.getActiveSQL().setStatement("bansinactive");
        dbTableInactiveBans.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        dbTableInactiveBans.getModel().setResultSet(connHandler.getActiveSQL().executeQuery(query));
        dbTableInactiveBans.getModel().refreshTableContents();
        dbTableInactiveBans.autoAdjustRowHeight();
        jLabelInActBans.setText(dbTableInactiveBans.getRowCount() + " " + dh.getString("info_records"));
    }

    private void unbanAcctIp(final DBJTableBean dbtable) {

        final int[] selRows = dbtable.getSelectedRows();
        jProgActBans.setMinimum(0);
        jProgActBans.setMaximum(selRows.length);
        jProgActBans.setVisible(true);

        SwingWorker task = new SwingWorker<String, Integer>() {
            String acctip = null;

            @Override
            public String doInBackground() {
                for (int i = 0; i < selRows.length; i++) {
                    acctip = (String) dbtable.getValueAt(selRows[i], 0);
                    if (acctip.indexOf(".") > 0) {
                        connHandler.getRAConnection().unbanIP(acctip);
                    } else {
                        connHandler.getRAConnection().unbanAcct(acctip);
                    }
                    publish(Integer.valueOf(i + 1));
                    gf.pause(1000 / selRows.length);
                }
                return null;
            }

            @Override
            protected void done() {
                jProgActBans.setVisible(false);
                updateBanTables(1);
            }

            @Override
            protected void process(List<Integer> progress) {
                for (Integer x : progress) {
                    jProgActBans.setValue(x.intValue());
                    jProgActBans.setString(dh.getString("prog_unbanacct") + " " + acctip);
                }
            }
        };
        task.execute();
    }

    private void deleteBanEntry(final DBJTableBean dbtable) {


        final int[] selRows = dbtable.getSelectedRows();
        jProgInActBans.setMinimum(0);
        jProgInActBans.setMaximum(selRows.length);
        jProgInActBans.setVisible(true);

        connHandler.getActiveSQL().createRealmStatement("delban");
        connHandler.getActiveSQL().setStatement("delban");

        SwingWorker task = new SwingWorker<String, Integer>() {
            String acctid = null;

            @Override
            public String doInBackground() {
                for (int i = 0; i < selRows.length; i++) {
                    acctid = (String) dbtable.getValueAt(selRows[i], 0);
                    acctid = connHandler.getActiveSQL().getAcctIDFromAcctName(acctid);
                    connHandler.getActiveSQL().executeUpdate("delete from account_banned where id='" + acctid + "' and active='0'");
                    publish(Integer.valueOf(i + 1));
                    gf.pause(1000 / selRows.length);
                }
                return null;
            }

            @Override
            protected void done() {
                jProgInActBans.setVisible(false);
                connHandler.getActiveSQL().removeStatement("delban");
                updateBanTables(1);
            }

            @Override
            protected void process(List<Integer> progress) {
                for (Integer x : progress) {
                    jProgInActBans.setValue(x.intValue());
                    jProgInActBans.setString(dh.getString("prog_delban") + " " + acctid);
                }
            }
        };
        task.execute();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPopupActiveBans = new javax.swing.JPopupMenu();
        jMenuItemRefreshActBans = new javax.swing.JMenuItem();
        jMenuItemUnban = new javax.swing.JMenuItem();
        jPopupInactiveBans = new javax.swing.JPopupMenu();
        jMenuItemRefreshInaBans = new javax.swing.JMenuItem();
        jMenuItemDeleteEntry = new javax.swing.JMenuItem();
        jPanelActiveBans = new javax.swing.JPanel();
        jScrollPane5 = new javax.swing.JScrollPane();
        dbTableActiveBans = new lib.DBJTableBean();
        jLabelActBans = new javax.swing.JLabel();
        jProgActBans = new javax.swing.JProgressBar();
        jPanelInactiveBans = new javax.swing.JPanel();
        jScrollPane7 = new javax.swing.JScrollPane();
        dbTableInactiveBans = new lib.DBJTableBean();
        jLabelInActBans = new javax.swing.JLabel();
        jProgInActBans = new javax.swing.JProgressBar();

        jPopupActiveBans.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("lang/MessagesBundle"); // NOI18N
        jMenuItemRefreshActBans.setText(bundle.getString("mitem_refreshtable")); // NOI18N
        jMenuItemRefreshActBans.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemRefreshActBansActionPerformed(evt);
            }
        });
        jPopupActiveBans.add(jMenuItemRefreshActBans);

        jMenuItemUnban.setText(bundle.getString("mitem_unbanacct")); // NOI18N
        jMenuItemUnban.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemUnbanActionPerformed(evt);
            }
        });
        jPopupActiveBans.add(jMenuItemUnban);

        jPopupInactiveBans.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        jMenuItemRefreshInaBans.setText(bundle.getString("mitem_refreshtable")); // NOI18N
        jMenuItemRefreshInaBans.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemRefreshInaBansActionPerformed(evt);
            }
        });
        jPopupInactiveBans.add(jMenuItemRefreshInaBans);

        jMenuItemDeleteEntry.setText(bundle.getString("mitem_deleteban")); // NOI18N
        jMenuItemDeleteEntry.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemDeleteEntryActionPerformed(evt);
            }
        });
        jPopupInactiveBans.add(jMenuItemDeleteEntry);

        setFont(getFont().deriveFont(getFont().getStyle() | java.awt.Font.BOLD, getFont().getSize()+3));

        jPanelActiveBans.setBorder(javax.swing.BorderFactory.createTitledBorder(null, bundle.getString("pan_activebans"), javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, getFont(), getForeground())); // NOI18N

        jScrollPane5.setToolTipText(bundle.getString("tooltip_tables")); // NOI18N

        dbTableActiveBans.setAutoColumnEnabled(true);
        dbTableActiveBans.setComponentPopupMenu(jPopupActiveBans);
        dbTableActiveBans.setSelectionRetention(false);
        dbTableActiveBans.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                dbTableActiveBansMouseReleased(evt);
            }
        });
        dbTableActiveBans.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                dbTableActiveBansKeyReleased(evt);
            }
        });
        jScrollPane5.setViewportView(dbTableActiveBans);

        jLabelActBans.setText("No of records returned");

        jProgActBans.setToolTipText("Accounts Status");
        jProgActBans.setStringPainted(true);

        javax.swing.GroupLayout jPanelActiveBansLayout = new javax.swing.GroupLayout(jPanelActiveBans);
        jPanelActiveBans.setLayout(jPanelActiveBansLayout);
        jPanelActiveBansLayout.setHorizontalGroup(
            jPanelActiveBansLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelActiveBansLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelActiveBansLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane5)
                    .addGroup(jPanelActiveBansLayout.createSequentialGroup()
                        .addComponent(jProgActBans, javax.swing.GroupLayout.PREFERRED_SIZE, 660, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jLabelActBans)))
                .addContainerGap())
        );
        jPanelActiveBansLayout.setVerticalGroup(
            jPanelActiveBansLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelActiveBansLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane5, javax.swing.GroupLayout.DEFAULT_SIZE, 280, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelActiveBansLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jProgActBans, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabelActBans))
                .addContainerGap())
        );

        jPanelInactiveBans.setBorder(javax.swing.BorderFactory.createTitledBorder(null, bundle.getString("pan_inactivebans"), javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, getFont(), getForeground())); // NOI18N

        jScrollPane7.setToolTipText(bundle.getString("tooltip_tables")); // NOI18N

        dbTableInactiveBans.setAutoColumnEnabled(true);
        dbTableInactiveBans.setComponentPopupMenu(jPopupInactiveBans);
        dbTableInactiveBans.setSelectionRetention(false);
        dbTableInactiveBans.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                dbTableInactiveBansMouseReleased(evt);
            }
        });
        dbTableInactiveBans.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                dbTableInactiveBansKeyReleased(evt);
            }
        });
        jScrollPane7.setViewportView(dbTableInactiveBans);

        jLabelInActBans.setText("No of records returned");

        jProgInActBans.setToolTipText("Accounts Status");
        jProgInActBans.setStringPainted(true);

        javax.swing.GroupLayout jPanelInactiveBansLayout = new javax.swing.GroupLayout(jPanelInactiveBans);
        jPanelInactiveBans.setLayout(jPanelInactiveBansLayout);
        jPanelInactiveBansLayout.setHorizontalGroup(
            jPanelInactiveBansLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelInactiveBansLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelInactiveBansLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane7)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelInactiveBansLayout.createSequentialGroup()
                        .addComponent(jProgInActBans, javax.swing.GroupLayout.PREFERRED_SIZE, 660, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 130, Short.MAX_VALUE)
                        .addComponent(jLabelInActBans)))
                .addContainerGap())
        );
        jPanelInactiveBansLayout.setVerticalGroup(
            jPanelInactiveBansLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelInactiveBansLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane7, javax.swing.GroupLayout.DEFAULT_SIZE, 280, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelInactiveBansLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabelInActBans, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jProgInActBans, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jPanelInactiveBans, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanelActiveBans, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanelActiveBans, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanelInactiveBans, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void dbTableActiveBansKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_dbTableActiveBansKeyReleased
        switch (evt.getKeyCode()) {
            case KeyEvent.VK_UP:
            case KeyEvent.VK_DOWN:
            case KeyEvent.VK_KP_DOWN:
            case KeyEvent.VK_KP_UP:
                setActiveBanMenusEnabled(true);
                break;
        }
    }//GEN-LAST:event_dbTableActiveBansKeyReleased

    private void dbTableInactiveBansKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_dbTableInactiveBansKeyReleased
        switch (evt.getKeyCode()) {
            case KeyEvent.VK_UP:
            case KeyEvent.VK_DOWN:
            case KeyEvent.VK_KP_DOWN:
            case KeyEvent.VK_KP_UP:
                setInActiveBanMenusEnabled(true);
                break;
        }
    }//GEN-LAST:event_dbTableInactiveBansKeyReleased

    private void jMenuItemRefreshActBansActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemRefreshActBansActionPerformed
        updateBanTables(0);
    }//GEN-LAST:event_jMenuItemRefreshActBansActionPerformed

    private void jMenuItemUnbanActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemUnbanActionPerformed
        unbanAcctIp(dbTableActiveBans);
    }//GEN-LAST:event_jMenuItemUnbanActionPerformed

    private void jMenuItemRefreshInaBansActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemRefreshInaBansActionPerformed
        updateBanTables(0);
    }//GEN-LAST:event_jMenuItemRefreshInaBansActionPerformed

    private void jMenuItemDeleteEntryActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemDeleteEntryActionPerformed
        deleteBanEntry(dbTableInactiveBans);
    }//GEN-LAST:event_jMenuItemDeleteEntryActionPerformed

    private void dbTableActiveBansMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_dbTableActiveBansMouseReleased
        if (evt.getButton() == MouseEvent.BUTTON1) {
            setActiveBanMenusEnabled(true);
        }
    }//GEN-LAST:event_dbTableActiveBansMouseReleased

    private void dbTableInactiveBansMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_dbTableInactiveBansMouseReleased
        if (evt.getButton() == MouseEvent.BUTTON1) {
            setInActiveBanMenusEnabled(true);
        }
    }//GEN-LAST:event_dbTableInactiveBansMouseReleased
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private lib.DBJTableBean dbTableActiveBans;
    private lib.DBJTableBean dbTableInactiveBans;
    private javax.swing.JLabel jLabelActBans;
    private javax.swing.JLabel jLabelInActBans;
    private javax.swing.JMenuItem jMenuItemDeleteEntry;
    private javax.swing.JMenuItem jMenuItemRefreshActBans;
    private javax.swing.JMenuItem jMenuItemRefreshInaBans;
    private javax.swing.JMenuItem jMenuItemUnban;
    private javax.swing.JPanel jPanelActiveBans;
    private javax.swing.JPanel jPanelInactiveBans;
    private javax.swing.JPopupMenu jPopupActiveBans;
    private javax.swing.JPopupMenu jPopupInactiveBans;
    private javax.swing.JProgressBar jProgActBans;
    private javax.swing.JProgressBar jProgInActBans;
    private javax.swing.JScrollPane jScrollPane5;
    private javax.swing.JScrollPane jScrollPane7;
    // End of variables declaration//GEN-END:variables
}
