/*
 * Copyright (C) 2007-2013 Alistair Neil, <info@dazzleships.net>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License Version 2 as published by
 * the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package client;

import lib.HashString;
import lib.GlobalFunctions;
import lib.ProgressBarDialog;
import lib.InfoDialog;
import lib.CSVToHashString;
import java.awt.Color;
import java.awt.Cursor;
import java.sql.SQLException;
import java.util.List;
import javax.swing.SwingWorker;
import javax.swing.event.TableModelEvent;

/**
 *
 * @author Alistair Neil, <info@dazzleships.net>
 */
public class PortalManager extends javax.swing.JFrame {

    private final GlobalFunctions gf = GlobalFunctions.getInstance();
    private MangosSql dbIO = null;
    private MangosTelnet mangosTelnet = null;
    private String charname = null;
    private final String[] spellId = {"33055", "33056", "49665",
        "29216", "20449", "52750", "52747",
        "47506", "46618", "46617", "46616",
        "46615", "46614", "46613", "46612",
        "46611", "28025", "28026", "29231"};
    private String unusedId;
    private String xcoord;
    private String ycoord;
    private String zcoord;
    private String orientation;
    private String map;
    private HashString hsFaction;
    private HashString hsDisplayId;
    private final String strTitle = "MaNGOLin Portal Manager";
    private DialogHandler dh = null;
    private final int dbVersion;

    /**
     * Creates new form PortalManager
     *
     * @param connHandler
     * @param dh
     */
    public PortalManager(ConnectionHandler connHandler, DialogHandler dh) {
        dbVersion = connHandler.getActiveSQL().getMangosDBVersion();
        this.dh = dh;
        initComponents();
        if (dbVersion == MangosSql.MANGOSZERO) {
            jTabbedPane.remove(jPanelUser);
        }

        this.setIconImage(new javax.swing.ImageIcon(
                getClass().getResource("/resources/logo.png")).getImage());
        setTitle(strTitle);
        setDatabaseIO(connHandler.getActiveSQL(), connHandler.getRAConnection());
        CSVToHashString csvHash = new CSVToHashString();
        hsFaction = csvHash.getHashStringFromResource("/resources/factions.csv");
        if (hsFaction == null) {
            hsFaction = new HashString(3);
            hsFaction.putStringValue("0", "All");
            hsFaction.putStringValue("31", "Friendly");
            hsFaction.putStringValue("35", "Villain");
        }

        dbTableUserDest.getModel().addTableModelListener(new javax.swing.event.TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {
                if (e != null && e.getType() == TableModelEvent.UPDATE) {
                    updateActivePortalsTable();
                }
            }
        });

        dbTableActivePortals.getModel().addTableModelListener(new javax.swing.event.TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {
                if (e != null && e.getType() == TableModelEvent.UPDATE) {
                    updateUserDestTable();
                    updatePreDefDestTable();
                }
            }
        });

        updateUserDestTable();
        updatePreDefDestTable();
        updateActivePortalsTable();
    }

    public final void setDatabaseIO(MangosSql io, MangosTelnet telnet) {
        dbIO = io;
        if (dbIO != null) {
            dbIO.createMangosStatement("userdest");
            dbIO.createMangosStatement("udbdest");
            dbIO.createMangosStatement("activeportals");
        }
        mangosTelnet = telnet;
    }

    public final void setCharacter(String name) {
        charname = name;
        if (name == null) {
            setTitle(strTitle + " - Portal Creation Disabled No In-Game Character Was Selected");
            buCreateUserDest.setEnabled(false);
            buCreatePortal.setEnabled(false);
            buCreateUserPortal.setEnabled(false);
        } else {
            setTitle(strTitle + " - Selected Character is " + name);
            buCreateUserDest.setEnabled(dbTableUserDest.getRowCount() < spellId.length);
            buCreatePortal.setEnabled(dbTableExistingDest.getSelectedRowCount() == 1);
            buCreateUserPortal.setEnabled(dbTableUserDest.getSelectedRowCount() == 1);
        }
    }

    private boolean getCharacterLocation(String name) {

        boolean result = false;
        setCursor(new Cursor(Cursor.WAIT_CURSOR));
        // Comfirm our dummy data has been written to database
        int i = 0;
        try {
            dbIO.createCharStatement("tmp");
            dbIO.setStatement("tmp");
            while (true) {
                orientation = "";
                dbIO.executeUpdate("update characters set orientation = 999 where name = '" + name + "'");
                sleep(500);
                dbIO.executeQuery("select position_x,position_y,position_z,map,orientation "
                        + "from characters where name = '" + name + "'");
                dbIO.next();
                orientation = dbIO.getResultSet().getString("orientation");
                if (orientation.contentEquals("999")) {
                    break;
                }
                if (++i >= 6) {
                    throw new Exception();
                }
            }
        } catch (Exception ex) {
            dbIO.removeStatement("tmp");
            setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
            return result;
        }

        // Save all characters info back to database
        mangosTelnet.saveAll();

        // Now check that our dummy info has been overwritten by valid info
        i = 0;
        try {
            while (true) {
                orientation = "999";
                dbIO.executeQuery("select position_x,position_y,position_z,map,orientation "
                        + "from characters where name = '" + name + "'");
                dbIO.next();
                orientation = dbIO.getResultSet().getString("orientation");
                if (!orientation.contentEquals("999")) {
                    break;
                }
                if (++i >= 6) {
                    throw new Exception();
                }
                sleep(500);
            }
            xcoord = dbIO.getResultSet().getString("position_x");
            ycoord = dbIO.getResultSet().getString("position_y");
            zcoord = dbIO.getResultSet().getString("position_z");
            map = dbIO.getResultSet().getString("map");
            result = true;
        } catch (Exception ex) {
        }
        dbIO.removeStatement("tmp");
        setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        return result;
    }

    private Long getGameobjectGuid() {

        Long result = null;
        dbIO.createMangosStatement("tmp");
        dbIO.setStatement("tmp");
        dbIO.executeQuery("select guid from gameobject where guid >= 300000");
        dbIO.next();
        try {
            if (dbIO.isResultSetEmpty()) {
                result = new Long(300000);
            } else {
                dbIO.getResultSet().last();
                result = Long.parseLong(dbIO.getResultSet().getString("guid")) + 1;
            }
        } catch (SQLException | NumberFormatException ex) {
        }
        dbIO.removeStatement("tmp");
        return result;
    }

    public final void updatePreDefDestTable() {
        dbIO.setStatement("udbdest");
        String query = "select entry, name, faction from gameobject_template where "
                + "type = 22 and entry < 400000 order by entry";
        dbTableExistingDest.getModel().setResultSet(dbIO.executeQuery(query));
        dbTableExistingDest.getModel().setPrimaryKey("entry");
        dbTableExistingDest.enableTextReplacement("faction", hsFaction);
        dbTableExistingDest.getModel().refreshTableContents();
        dbTableExistingDest.autoAdjustRowHeight();
    }

    public final void updateActivePortalsTable() {
        dbIO.setStatement("activeportals");
        String query = "select guid, id, gameobject_template.name, gameobject_template.faction from gameobject "
                + "left join gameobject_template on gameobject_template.entry = id "
                + "where type = 22 and guid >= 300000";
        dbTableActivePortals.getModel().setResultSet(dbIO.executeQuery(query));
        dbTableActivePortals.getModel().setPrimaryKey("id");
        dbTableActivePortals.getModel().setSecondaryKey("entry");
        dbTableActivePortals.enableComboEditor("faction", true, hsFaction);
        dbTableActivePortals.enableTextEditor("name");
        dbTableActivePortals.getModel().refreshTableContents();
        dbTableActivePortals.autoAdjustRowHeight();
        buDeletePortal.setEnabled(false);
    }

    private void createUDBPortal() {

        // Get character positional information
        if (!getCharacterLocation(charname)) {
            dh.createError("title_portalfailed", "info_portalfailed");
            dh.setLocationRelativeTo(this);
            dh.setVisible(true);
            return;
        }

        // Get the next GUID from gameobject table
        Long guid = getGameobjectGuid();

        // Get id from udb destination table
        dbIO.createMangosStatement("tmp");
        dbIO.setStatement("tmp");
        Integer id = (Integer) dbTableExistingDest.getModel().getValueAt(dbTableExistingDest.getSelectedRow(), 0);
        // Create our insert query
        String insert = "insert into `gameobject` (guid,id,map,position_x,position_y,position_z,"
                + "orientation,state) values "
                + "(" + guid.toString() + "," + id.toString() + "," + map + "," + xcoord + "," + ycoord + ","
                + zcoord + "," + orientation + "," + "1);";
        dbIO.executeUpdate(insert);
        // This will unlock portal
        dbIO.executeUpdate("update gameobject_template set data2 = 0 where entry = '" + id.toString() + "'");
        dbIO.removeStatement("tmp");
        updateActivePortalsTable();
    }

    private void deletePortal() {

        // Lets issue a warning here
        dh.createWarn("title_deleteportal", "info_deleteportal");
        dh.setLocationRelativeTo(this);
        dh.setVisible(true);
        if (dh.getReturnStatus() == InfoDialog.CANCEL) {
            return;
        }

        // create our temp sql statement
        dbIO.createMangosStatement("tmp");
        dbIO.setStatement("tmp");

        // Set up our progress bar
        final int[] selRows = dbTableActivePortals.getSelectedRows();
        final ProgressBarDialog ourProgress = new ProgressBarDialog(this, true, "Portal Delete in Progress", 0, selRows.length);
        ourProgress.setLocationRelativeTo(this);
        ourProgress.setBackground(new Color(255, 255, 255));

        SwingWorker task = new SwingWorker<String, Integer>() {
            Long guid = null;

            @Override
            public String doInBackground() {
                // Do our deletes here
                for (int i = 0; i < selRows.length; i++) {
                    guid = (Long) dbTableActivePortals.getModel().getValueAt(selRows[i], 0);
                    publish(i + 1);
                    dbIO.executeUpdate("delete from `gameobject` where guid = " + guid.toString());
                    gf.pause(250);
                }
                return null;
            }

            @Override
            protected void done() {
                ourProgress.setVisible(false);
            }

            @Override
            protected void process(List<Integer> progress) {
                for (Integer x : progress) {
                    ourProgress.setProgress(x);
                }
                ourProgress.setNote1("Deleting Portal with Guid = " + guid.toString());
            }
        };

        task.execute();
        ourProgress.setVisible(true);
        if (ourProgress.getReturnStatus() == ProgressBarDialog.RET_CANCEL) {
            task.cancel(false);
        }

        dbIO.removeStatement("tmp");
        updateActivePortalsTable();
    }

    private void createUserPortal() {

        // Get character positional information
        getCharacterLocation(charname);

        // Get the next GUID from gameobject table
        Long guid = getGameobjectGuid();

        // Get id from destination table
        dbIO.createMangosStatement("tmp");
        dbIO.setStatement("tmp");
        Integer id = (Integer) dbTableUserDest.getModel().getValueAt(dbTableUserDest.getSelectedRow(), 0);
        // Create our insert query
        String insert = "insert into `gameobject` (guid,id,map,position_x,position_y,position_z,"
                + "orientation,rotation2,rotation3,spawntimesecs,state) values "
                + "(" + guid.toString() + "," + id.toString() + "," + map + "," + xcoord + "," + ycoord + ","
                + zcoord + "," + orientation + "," + "1,0.014,25,1);";
        dbIO.executeUpdate(insert);
        dbIO.removeStatement("tmp");
        updateActivePortalsTable();
    }

    public final void updateUserDestTable() {

        String query;
        String strId;

        buDeleteUserDest.setEnabled(false);
        buCreateUserPortal.setEnabled(false);
        dbIO.setStatement("userdest");

        // Get a list of portal descriptions and displayid
        if (hsDisplayId == null) {
            hsDisplayId = new HashString(30);
            query = "select distinct name, displayid from gameobject_template where name like "
                    + "'Portal to%' and type = 22 and entry < 400000 order by entry";
            dbIO.executeQuery(query);
            String desc;
            while (dbIO.next()) {
                desc = dbIO.getString("name");
                strId = dbIO.getString("displayid");
                desc = desc.replace("Portal to ", "");
                hsDisplayId.putStringValue(strId, desc);
            }
        }

        query = "select entry, name, faction, displayid, data0 from gameobject_template "
                + "where entry >= 400000 order by entry";
        dbTableUserDest.getModel().setResultSet(dbIO.executeQuery(query));
        dbTableUserDest.getModel().setPrimaryKey("entry");
        dbTableUserDest.enableTextEditor("name");
        dbTableUserDest.enableComboEditor("faction", true, hsFaction);
        dbTableUserDest.enableComboEditor("displayid", true, hsDisplayId);
        dbTableUserDest.getModel().refreshTableContents();
        dbTableUserDest.autoAdjustRowHeight();
        if (charname != null) {
            buCreateUserDest.setEnabled(dbTableUserDest.getRowCount() < spellId.length);
        }
    }

    private void savePortalsAsSQL() {
        dbIO.writePortalSQL(spellId);
        portalsSaved();
        dh.createInfo("title_portalsaved", "info_portalsaved");
        dh.setLocationRelativeTo(this);
        dh.setVisible(true);
    }

    /**
     * Called when a new portals file has been created, should be overriden to
     * provide callback
     */
    public void portalsSaved() {
    }

    private void createUserDestination() {

        // Get character positional information
        if (!getCharacterLocation(charname)) {
            dh.createError("title_createdest_err", "info_portalfailed");
            dh.setLocationRelativeTo(this);
            dh.setVisible(true);
            return;
        }

        // Setup our db io
        dbIO.createMangosStatement("tmp");
        dbIO.setStatement("tmp");

        // Find unused teleport spell
        int i;
        for (i = 0; i < spellId.length; i++) {
            unusedId = spellId[i];
            dbIO.executeQuery("select * from spell_target_position where id = " + unusedId);
            dbIO.next();
            if (dbIO.isResultSetEmpty()) {
                break;
            }
            unusedId = null;
        }

        if (unusedId != null) {
            String insert = "insert into `spell_target_position` values (" + unusedId + "," + map + "," + xcoord + "," + ycoord + "," + zcoord + "," + orientation + ");";
            dbIO.executeUpdate(insert);
            String entry = String.valueOf(400000 + i);
            insert = "insert into `gameobject_template` (entry,name,faction,type,displayid,data0) values ("
                    + entry + ",' Insert Portal Description Here',0,22,7146," + unusedId + ")";
            dbIO.executeUpdate(insert);
        }

        dbIO.removeStatement("tmp");
        updateUserDestTable();
    }

    private void deleteUserDestination() {

        // Lets issue a warning here
        dh.createWarn("title_deleteportaldest", "info_deleteportaldest");
        dh.setLocationRelativeTo(this);
        dh.setVisible(true);
        if (dh.getReturnStatus() == InfoDialog.CANCEL) {
            return;
        }

        // create our temp sql statement
        dbIO.createMangosStatement("tmp");
        dbIO.setStatement("tmp");

        // Set up our progress bar
        final int[] selRows = dbTableUserDest.getSelectedRows();
        final ProgressBarDialog ourProgress = new ProgressBarDialog(this, true, dh.getString("prog_deleteportaldest"), 0, selRows.length);
        ourProgress.setLocationRelativeTo(this);
        ourProgress.setBackground(new Color(255, 255, 255));

        SwingWorker task = new SwingWorker<String, Integer>() {
            Long spellid = null;
            Integer entry = null;

            @Override
            public String doInBackground() {
                // Do our deletes here
                for (int i = 0; i < selRows.length; i++) {
                    spellid = (Long) dbTableUserDest.getModel().getValueAt(selRows[i], 4);
                    entry = (Integer) dbTableUserDest.getModel().getValueAt(selRows[i], 0);
                    publish(i + 1);
                    dbIO.executeUpdate("delete from `spell_target_position` where id = " + spellid.toString());
                    dbIO.executeUpdate("delete from `gameobject_template` where data0 = " + spellid.toString());
                    dbIO.executeUpdate("delete from `gameobject` where id = " + entry.toString());
                }
                return null;
            }

            @Override
            protected void done() {
                ourProgress.setVisible(false);
            }

            @Override
            protected void process(List<Integer> progress) {
                for (Integer x : progress) {
                    ourProgress.setProgress(x);
                }
                ourProgress.setNote1(dh.getString("prog_deleteportaldest1") + " = " + entry.toString());
            }
        };

        task.execute();
        ourProgress.setVisible(true);
        if (ourProgress.getReturnStatus() == ProgressBarDialog.RET_CANCEL) {
            task.cancel(false);
        }

        dbIO.removeStatement("tmp");
        updateUserDestTable();
        updateActivePortalsTable();
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanelMain = new javax.swing.JPanel();
        jTabbedPane = new javax.swing.JTabbedPane();
        jPanelUser = new javax.swing.JPanel();
        buDeleteUserDest = new javax.swing.JButton();
        buCreateUserDest = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        dbTableUserDest = new lib.DBJTableBean();
        buCreateUserPortal = new javax.swing.JButton();
        jPanelExisting = new javax.swing.JPanel();
        jScrollPane3 = new javax.swing.JScrollPane();
        dbTableExistingDest = new lib.DBJTableBean();
        buCreatePortal = new javax.swing.JButton();
        jPanelActivePortals = new javax.swing.JPanel();
        buDeletePortal = new javax.swing.JButton();
        jScrollPane2 = new javax.swing.JScrollPane();
        dbTableActivePortals = new lib.DBJTableBean();
        jButtonSave = new javax.swing.JButton();
        jButtonClose = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setResizable(false);

        jPanelMain.setFont(jPanelMain.getFont().deriveFont(jPanelMain.getFont().getStyle() | java.awt.Font.BOLD, jPanelMain.getFont().getSize()+3));

        jTabbedPane.setFont(jTabbedPane.getFont().deriveFont(jTabbedPane.getFont().getStyle() | java.awt.Font.BOLD));

        jPanelUser.setFont(jPanelUser.getFont().deriveFont(jPanelUser.getFont().getStyle() | java.awt.Font.BOLD, jPanelUser.getFont().getSize()+3));

        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("lang/MessagesBundle"); // NOI18N
        buDeleteUserDest.setText(bundle.getString("butt_deletedest")); // NOI18N
        buDeleteUserDest.setToolTipText(bundle.getString("tool_deletedest")); // NOI18N
        buDeleteUserDest.setEnabled(false);
        buDeleteUserDest.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buDeleteUserDestActionPerformed(evt);
            }
        });

        buCreateUserDest.setText(bundle.getString("butt_createdest")); // NOI18N
        buCreateUserDest.setToolTipText(bundle.getString("tool_createdest")); // NOI18N
        buCreateUserDest.setEnabled(false);
        buCreateUserDest.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buCreateUserDestActionPerformed(evt);
            }
        });

        dbTableUserDest.setAutoColumnEnabled(true);
        dbTableUserDest.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                dbTableUserDestMouseReleased(evt);
            }
        });
        jScrollPane1.setViewportView(dbTableUserDest);

        buCreateUserPortal.setText(bundle.getString("butt_createportaltodest")); // NOI18N
        buCreateUserPortal.setToolTipText(bundle.getString("tool_createportaltodest")); // NOI18N
        buCreateUserPortal.setEnabled(false);
        buCreateUserPortal.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buCreateUserPortalActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanelUserLayout = new javax.swing.GroupLayout(jPanelUser);
        jPanelUser.setLayout(jPanelUserLayout);
        jPanelUserLayout.setHorizontalGroup(
            jPanelUserLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelUserLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelUserLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 932, Short.MAX_VALUE)
                    .addGroup(jPanelUserLayout.createSequentialGroup()
                        .addComponent(buCreateUserDest)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(buCreateUserPortal)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(buDeleteUserDest)))
                .addContainerGap())
        );
        jPanelUserLayout.setVerticalGroup(
            jPanelUserLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelUserLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 263, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelUserLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(buDeleteUserDest)
                    .addGroup(jPanelUserLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(buCreateUserDest)
                        .addComponent(buCreateUserPortal)))
                .addContainerGap())
        );

        jTabbedPane.addTab(bundle.getString("tab_userdest"), jPanelUser); // NOI18N

        dbTableExistingDest.setAutoColumnEnabled(true);
        dbTableExistingDest.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                dbTableExistingDestMouseReleased(evt);
            }
        });
        jScrollPane3.setViewportView(dbTableExistingDest);

        buCreatePortal.setText(bundle.getString("butt_createportal")); // NOI18N
        buCreatePortal.setToolTipText(bundle.getString("tool_createportal")); // NOI18N
        buCreatePortal.setEnabled(false);
        buCreatePortal.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buCreatePortalActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanelExistingLayout = new javax.swing.GroupLayout(jPanelExisting);
        jPanelExisting.setLayout(jPanelExistingLayout);
        jPanelExistingLayout.setHorizontalGroup(
            jPanelExistingLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelExistingLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelExistingLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanelExistingLayout.createSequentialGroup()
                        .addComponent(buCreatePortal)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 932, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanelExistingLayout.setVerticalGroup(
            jPanelExistingLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelExistingLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 263, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(buCreatePortal)
                .addContainerGap())
        );

        jTabbedPane.addTab(bundle.getString("tab_preexistdest"), jPanelExisting); // NOI18N

        jPanelActivePortals.setBorder(javax.swing.BorderFactory.createTitledBorder(null, bundle.getString("pan_activeportals"), javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, jPanelMain.getFont(), jPanelMain.getForeground())); // NOI18N

        buDeletePortal.setText(bundle.getString("butt_deleteportal")); // NOI18N
        buDeletePortal.setToolTipText(bundle.getString("tool_deleteportal")); // NOI18N
        buDeletePortal.setEnabled(false);
        buDeletePortal.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buDeletePortalActionPerformed(evt);
            }
        });

        dbTableActivePortals.setAutoColumnEnabled(true);
        dbTableActivePortals.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                dbTableActivePortalsMousePressed(evt);
            }
        });
        jScrollPane2.setViewportView(dbTableActivePortals);

        jButtonSave.setText(bundle.getString("butt_saveportals")); // NOI18N
        jButtonSave.setToolTipText(bundle.getString("tool_saveportals")); // NOI18N
        jButtonSave.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonSaveActionPerformed(evt);
            }
        });

        jButtonClose.setText(bundle.getString("butt_close")); // NOI18N
        jButtonClose.setToolTipText(bundle.getString("tool_closemanager")); // NOI18N
        jButtonClose.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonCloseActionPerformed(evt);
            }
        });

        jLabel1.setText(bundle.getString("lab_editingsupport")); // NOI18N

        javax.swing.GroupLayout jPanelActivePortalsLayout = new javax.swing.GroupLayout(jPanelActivePortals);
        jPanelActivePortals.setLayout(jPanelActivePortalsLayout);
        jPanelActivePortalsLayout.setHorizontalGroup(
            jPanelActivePortalsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelActivePortalsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelActivePortalsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane2)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelActivePortalsLayout.createSequentialGroup()
                        .addComponent(buDeletePortal)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jButtonSave)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jButtonClose))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelActivePortalsLayout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(jLabel1)))
                .addContainerGap())
        );
        jPanelActivePortalsLayout.setVerticalGroup(
            jPanelActivePortalsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelActivePortalsLayout.createSequentialGroup()
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 241, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelActivePortalsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(buDeletePortal)
                    .addComponent(jButtonClose)
                    .addComponent(jButtonSave))
                .addContainerGap())
        );

        javax.swing.GroupLayout jPanelMainLayout = new javax.swing.GroupLayout(jPanelMain);
        jPanelMain.setLayout(jPanelMainLayout);
        jPanelMainLayout.setHorizontalGroup(
            jPanelMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelMainLayout.createSequentialGroup()
                .addGroup(jPanelMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jTabbedPane)
                    .addComponent(jPanelActivePortals, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(0, 0, 0))
        );
        jPanelMainLayout.setVerticalGroup(
            jPanelMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelMainLayout.createSequentialGroup()
                .addComponent(jTabbedPane, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(jPanelActivePortals, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(jPanelMain, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(jPanelMain, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void buDeleteUserDestActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buDeleteUserDestActionPerformed
        deleteUserDestination();
}//GEN-LAST:event_buDeleteUserDestActionPerformed

    private void buCreateUserDestActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buCreateUserDestActionPerformed
        createUserDestination();
}//GEN-LAST:event_buCreateUserDestActionPerformed

    private void buCreateUserPortalActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buCreateUserPortalActionPerformed
        createUserPortal();
}//GEN-LAST:event_buCreateUserPortalActionPerformed

    private void buCreatePortalActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buCreatePortalActionPerformed
        createUDBPortal();
}//GEN-LAST:event_buCreatePortalActionPerformed

    private void buDeletePortalActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buDeletePortalActionPerformed
        deletePortal();
}//GEN-LAST:event_buDeletePortalActionPerformed

    private void dbTableActivePortalsMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_dbTableActivePortalsMousePressed
        buDeletePortal.setEnabled(true);
    }//GEN-LAST:event_dbTableActivePortalsMousePressed

    private void dbTableExistingDestMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_dbTableExistingDestMouseReleased
        if (charname != null) {
            buCreatePortal.setEnabled(dbTableExistingDest.getSelectedRowCount() == 1);
        }
    }//GEN-LAST:event_dbTableExistingDestMouseReleased

    private void dbTableUserDestMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_dbTableUserDestMouseReleased
        buDeleteUserDest.setEnabled(true);
        if (charname != null) {
            buCreateUserPortal.setEnabled(dbTableUserDest.getSelectedRowCount() == 1);
        }
    }//GEN-LAST:event_dbTableUserDestMouseReleased

    private void jButtonSaveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonSaveActionPerformed
        savePortalsAsSQL();
    }//GEN-LAST:event_jButtonSaveActionPerformed

    private void jButtonCloseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonCloseActionPerformed
        dispose();
    }//GEN-LAST:event_jButtonCloseActionPerformed
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton buCreatePortal;
    private javax.swing.JButton buCreateUserDest;
    private javax.swing.JButton buCreateUserPortal;
    private javax.swing.JButton buDeletePortal;
    private javax.swing.JButton buDeleteUserDest;
    private lib.DBJTableBean dbTableActivePortals;
    private lib.DBJTableBean dbTableExistingDest;
    private lib.DBJTableBean dbTableUserDest;
    private javax.swing.JButton jButtonClose;
    private javax.swing.JButton jButtonSave;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JPanel jPanelActivePortals;
    private javax.swing.JPanel jPanelExisting;
    private javax.swing.JPanel jPanelMain;
    private javax.swing.JPanel jPanelUser;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JTabbedPane jTabbedPane;
    // End of variables declaration//GEN-END:variables
}
