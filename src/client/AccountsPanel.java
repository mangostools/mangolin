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

import lib.CSVToHashString;
import lib.ComboSelectDialog;
import lib.GlobalFunctions;
import lib.HashString;
import lib.InfoDialog;
import lib.LineInputDialog;
import lib.SpinnerInputDialog;
import java.awt.Cursor;
import java.awt.Frame;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

/**
 *
 * @author Alistair Neil <info@dazzleships.net>
 */
public class AccountsPanel extends javax.swing.JPanel {

    private final GlobalFunctions gf = GlobalFunctions.getInstance();
    private static final Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private Clipboard clippy;
    private ConnectionHandler connHandler;
    private DialogHandler dh;
    private String strSelectedAccountName;
    private String strSelectedCharName;
    private String strSelectedCharId;
    private HashString hsRace;
    private HashString hsClass;
    private HashString hsZone;
    private HashString hsTicket;
    private int intCharRefreshTimer = -1;
    private int intAcctRefreshTimer = -1;
    private PortalManager portalManager;
    private int dbVersion;

    /**
     * Creates new form AccountsPanel
     *
     * @param clippy
     */
    public AccountsPanel(Clipboard clippy) {
        this.clippy = clippy;
        initComponents();
        jProgChars.setVisible(false);
        jProgAccounts.setVisible(false);
        // Get Race, class, zones data etc
        CSVToHashString csvHash = new CSVToHashString();
        hsZone = csvHash.getHashStringFromResource("/resources/zones.csv");
        hsClass = csvHash.getHashStringFromResource("/resources/classes.csv");
        hsRace = csvHash.getHashStringFromResource("/resources/races.csv");
    }

    public void setConnection(ConnectionHandler connHandler) {
        this.connHandler = connHandler;
        // Create the statements used in this panel
        connHandler.getActiveSQL().createRealmStatement("accts");
        connHandler.getActiveSQL().createCharStatement("chars");
        dbVersion = connHandler.getActiveSQL().getMangosDBVersion();

        // Process server specific UI entities
        switch (dbVersion) {
            case MangosSql.MANGOSZERO:
                jPopupChars.remove(jMenuItemCustomise);
                break;
        }
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
     * Set the portal manager, null to disable
     *
     * @param portalManager
     */
    public void setPortalManager(PortalManager portalManager) {
        this.portalManager = portalManager;
    }

    /**
     * Provide this panel with timeing
     */
    public void updateTick() {

        if (intAcctRefreshTimer > -1) {
            intAcctRefreshTimer--;
            if (intAcctRefreshTimer == 0) {
                intAcctRefreshTimer--;
                refreshAccountTable();
            }
        }

        if (intCharRefreshTimer > -1) {
            intCharRefreshTimer--;
            if (intCharRefreshTimer == 0) {
                intCharRefreshTimer--;
                refreshCharTable(0);
            }
        }
    }

    /**
     * Update account table after specified delay, if delay less than zero then
     * reset and disable table
     *
     * @param delay
     */
    public void updateAccountTable(int delay) {
        if (delay < 0) {
            setAccountMenusEnabled(false);
            jComboAcctField.setSelectedIndex(0);
            dbTableAccount.clearSelection();
            return;
        }
        if (delay == 0) {
            refreshAccountTable();
        } else {
            intAcctRefreshTimer = delay;
        }
    }

    /**
     * Enable disable account menus
     *
     * @param enabled
     */
    public void setAccountMenusEnabled(boolean enabled) {

        boolean raConnected = connHandler.isRAConnected();
        jMenuItemCreateAccount.setEnabled(false);
        jMenuItemAcctDelete.setEnabled(false);
        jMenuItemBanAccount.setEnabled(false);
        jMenuItemBanIP.setEnabled(false);
        jMenuItemAcctPriv.setEnabled(false);
        jMenuItemSetPass.setEnabled(false);
        jMenuItemAcctExpansion.setEnabled(false);

        if (enabled) {
            boolean boolSingleRow = dbTableAccount.getSelectedRowCount() == 1;
            jMenuItemCreateAccount.setEnabled(raConnected);
            jMenuItemAcctDelete.setEnabled(raConnected);
            jMenuItemAcctPriv.setEnabled(raConnected);
            jMenuItemAcctExpansion.setEnabled(raConnected);
            jMenuItemBanAccount.setEnabled(raConnected);
            jMenuItemBanIP.setEnabled(boolSingleRow & raConnected);
            jMenuItemSetPass.setEnabled(boolSingleRow & raConnected);
        }
    }

    /**
     * Updates the account table
     */
    private void refreshAccountTable() {

        StringBuilder filtertxt = new StringBuilder();
        String search;
        switch (jComboAcctField.getSelectedIndex()) {
            case 2:
            case 3:
                filtertxt.append(" = '");
                filtertxt.append(jComboAcctFilter.getSelectedIndex());
                filtertxt.append("'");
                break;

            case 4:
                search = (String) jComboAcctFilter.getSelectedItem();
                if (search != null) {
                    filtertxt.append(" <= DATE_SUB(CURDATE(),INTERVAL ");
                    filtertxt.append(search.substring(2));
                    filtertxt.append(")");
                }
                break;
            default:
                search = (String) jComboAcctFilter.getSelectedItem();
                if (search == null) {
                    search = "";
                }
                filtertxt.append(" like '%");
                filtertxt.append(search.trim());
                filtertxt.append("%'");
        }

        StringBuilder searchquery = new StringBuilder("select id,username,last_ip,last_login, "
                + "gmlevel,expansion from account");

        if (filtertxt.length() > 0) {
            searchquery.append(" where ");
            searchquery.append(jComboAcctField.getSelectedItem());
            searchquery.append(filtertxt);
        }

        connHandler.getActiveSQL().setStatement("accts");
        dbTableAccount.setSelectionRetention(true);
        dbTableAccount.setResultSet(connHandler.getActiveSQL().executeQuery(searchquery.toString()));
        dbTableAccount.getModel().setPrimaryKey("id");
        dbTableAccount.enableComboEditor("gmlevel", true,
                dh.getStrings("gm_player", "gm_mod", "gm_games", "gm_admin", "gm_god"));
        dbTableAccount.enableComboEditor("expansion", true, "Classic", "TBC", "WoTLK");
        dbTableAccount.getModel().refreshTableContents();
        dbTableAccount.autoAdjustRowHeight();
        if (dbTableAccount.getSelectedRowCount() == 0) {
            setAccountMenusEnabled(false);
            dbTableAccount.clearSelection();
        }
        jLabelNoOfAccts.setText(dbTableAccount.getRowCount() + " " + dh.getString("info_records"));
    }

    /**
     * Update character filter
     *
     * @param acctname
     */
    private void updateCharFilter(String acctname) {

        jComboCharFilter.removeAllItems();
        jComboCharFilter.setComponentPopupMenu(null);
        switch (jComboCharField.getSelectedIndex()) {
            case 0:
                jComboCharFilter.setComponentPopupMenu(jPopupClipboard);
                jComboCharFilter.setEditable(true);
                jComboCharFilter.addItem("");
                updateCharTable(0, 0);
                break;

            case 1:
                jComboCharFilter.setEditable(true);
                if (acctname.isEmpty()) {
                    break;
                }
                jComboCharFilter.setComponentPopupMenu(jPopupClipboard);
                jComboCharFilter.addItem(acctname);
                updateCharTable(0, 3);
                break;

            case 2:
                jComboCharFilter.setEditable(false);
                jComboCharFilter.addItem(dh.getString("ack_yes"));
                jComboCharFilter.addItem(dh.getString("ack_no"));
                updateCharTable(0, 3);
                break;

            case 3:
                jComboCharFilter.setEditable(false);
                jComboCharFilter.addItem(dh.getString("items_all"));
                for (Long l : hsRace.getAllNumericalKeys(true)) {
                    jComboCharFilter.addItem(hsRace.get(l.toString()));
                }
                updateCharTable(0, 0);
                break;

            case 4:
                jComboCharFilter.setEditable(false);
                jComboCharFilter.addItem(dh.getString("items_all"));
                for (Long l : hsClass.getAllNumericalKeys(true)) {
                    jComboCharFilter.addItem(hsClass.get(l.toString()));
                }
                updateCharTable(0, 0);
                break;
        }
    }

    /**
     * Update character table after specified delay
     *
     * @param delay
     * @param precision
     */
    public void updateCharTable(int delay, int precision) {

        if (delay < 0) {
            setCharMenusEnabled(false);
            dbTableChars.clearSelection();
            jComboCharField.setSelectedIndex(0);
            return;
        }
        if (delay == 0) {
            refreshCharTable(precision);
        } else {
            intCharRefreshTimer = delay;
        }
    }

    /**
     * Updates or disables characters table, if delay is less than zero then it
     * disables
     */
    private void refreshCharTable(int precision) {

        StringBuilder filtertxt = new StringBuilder();
        int intSelected = jComboCharFilter.getSelectedIndex();
        String filter = (String) jComboCharFilter.getSelectedItem();
        if (filter == null) {
            filter = "";
        }
        switch (jComboCharField.getSelectedIndex()) {
            case 4:
                if (intSelected != 0) {
                    filtertxt.append("class = ");
                    filtertxt.append(intSelected);
                }
                break;
            case 3:
                if (intSelected != 0) {
                    filtertxt.append("race = ");
                    filtertxt.append(intSelected);
                }
                break;
            case 2:
                if (intSelected == 0) {
                    filtertxt.append("*char*.character_ticket.guid = *char*.characters.guid");
                } else {
                    filtertxt.append("*char*.character_ticket.guid != *char*.characters.guid");
                }
                break;
            case 1:
                filtertxt.append("username");
            default:
                if (filtertxt.length() == 0) {
                    filtertxt.append(jComboCharField.getSelectedItem());
                }
                switch (precision) {
                    case 0:
                        filtertxt.append(" like '%");
                        filtertxt.append(filter.trim());
                        filtertxt.append("%'");
                        break;

                    case 1:
                        filtertxt.append(" like '%");
                        filtertxt.append(filter.trim());
                        filtertxt.append("'");
                        break;

                    case 2:
                        filtertxt.append(" like '");
                        filtertxt.append(filter.trim());
                        filtertxt.append("%'");
                        break;

                    case 3:
                        filtertxt.append(" = '");
                        filtertxt.append(filter.trim());
                        filtertxt.append("'");
                        break;
                }
        }

        // Using a custom query to do some fancy shit (well fancy by my limited sql ability) by joining 2 tables together
        StringBuilder query = new StringBuilder("select characters.guid, name, *realm*.account.username, online, ");
        query.append("(*char*.character_ticket.guid = characters.guid) as ticket, race, ");
        query.append("class, gender, level, ");
        query.append("zone from *char*.characters ");
        query.append("left join *realm*.account on *realm*.account.id = *char*.characters.account ");
        query.append("left join *char*.character_ticket on *char*.character_ticket.guid = *char*.characters.guid");
        if (filtertxt.length() > 0) {
            query.append(" where ");
            query.append(filtertxt);
        }

        connHandler.getActiveSQL().setStatement("chars");
        dbTableChars.setSelectionRetention(true);
        dbTableChars.setResultSet(connHandler.getActiveSQL().executeQuery(query.toString()));
        dbTableChars.getModel().setPrimaryKey("guid");
        if (hsClass != null) {
            dbTableChars.enableTextReplacement("class", hsClass);
        }
        if (hsRace != null) {
            dbTableChars.enableTextReplacement("race", hsRace);
        }
        if (hsZone != null) {
            dbTableChars.enableTextReplacement("zone", hsZone);
        }
        if (hsTicket == null) {
            hsTicket = new HashString();
            hsTicket.putStringValue("NULL", "No");
            hsTicket.putStringValue("0", "No");
            hsTicket.putStringValue("1", "Yes");
        }
        dbTableChars.getModel().setColumnHeader(4, "ticket");
        dbTableChars.enableTextReplacement("ticket", hsTicket);
        dbTableChars.enableNumericReplacement("gender", "Male", "Female");
        dbTableChars.enableNumericReplacement("online", "No", "Yes");
        dbTableChars.getModel().refreshTableContents();
        dbTableChars.autoAdjustRowHeight();
        setCharMenusEnabled(dbTableChars.getSelectedRowCount() != 0);
        jLabelNoOfChars.setText(dbTableChars.getRowCount() + " " + dh.getString("info_records"));
    }

    /**
     * Enable disable character menus
     *
     * @param enabled
     */
    public void setCharMenusEnabled(boolean enabled) {

        // Disable all items by default
        jMenuItemCopyChar.setEnabled(false);
        jMenuItemBanChar.setEnabled(false);
        jMenuItemDeleteChar.setEnabled(false);
        jMenuItemSendItems.setEnabled(false);
        jMenuItemSendMail.setEnabled(false);
        jMenuItemTeleport.setEnabled(false);
        jMenuItemKick.setEnabled(false);
        jMenuItemSendMsg.setEnabled(false);
        jMenuItemReset.setEnabled(false);
        jMenuItemRepair.setEnabled(false);
        jMenuItemSafeRevive.setEnabled(false);
        jMenuItemRevive.setEnabled(false);
        jMenuItemPortal.setEnabled(false);
        jMenuItemDelTicket.setEnabled(false);
        jMenuItemViewTicket.setEnabled(false);
        jMenuItemRename.setEnabled(false);
        jMenuItemCustomise.setEnabled(false);
        jMenuItemLevel.setEnabled(false);

        // Enable items selectively
        if (enabled) {
            boolean boolSingleRow = (dbTableChars.getSelectedRowCount() == 1);
            boolean raConnected = connHandler.isRAConnected();
            // Take into consideration where Remote accesss is enabled
            boolSingleRow &= raConnected;
            if (boolSingleRow) {
                strSelectedCharId = dbTableChars.getSelectedItemAsString("guid");
                strSelectedCharName = connHandler.getActiveSQL().getCharNameFromGUID(strSelectedCharId);
                if (connHandler.getActiveSQL().isCharOnline(strSelectedCharName)) {
                    jMenuItemRepair.setEnabled(boolSingleRow);
                    jMenuItemTeleport.setEnabled(boolSingleRow);
                    jMenuItemKick.setEnabled(boolSingleRow);
                    jMenuItemSendMsg.setEnabled(boolSingleRow);
                    jMenuItemReset.setEnabled(boolSingleRow);
                }
                String ticketStatus = dbTableChars.getSelectedItemAsString("ticket");
                if (ticketStatus.contains("Yes")) {
                    jMenuItemDelTicket.setEnabled(boolSingleRow);
                    jMenuItemViewTicket.setEnabled(boolSingleRow);
                }
            }

            jMenuItemDeleteChar.setEnabled(raConnected);
            jMenuItemCopyChar.setEnabled(boolSingleRow);
            jMenuItemBanChar.setEnabled(boolSingleRow);
            jMenuItemSendItems.setEnabled(boolSingleRow);
            jMenuItemSendMail.setEnabled(boolSingleRow);
            jMenuItemPortal.setEnabled(boolSingleRow);
            jMenuItemRename.setEnabled(boolSingleRow);
            jMenuItemCustomise.setEnabled(boolSingleRow);
            jMenuItemLevel.setEnabled(boolSingleRow);
            jMenuItemSafeRevive.setEnabled(boolSingleRow);
            jMenuItemRevive.setEnabled(boolSingleRow);
        }
    }

    private void deleteAccount() {

        dh.createWarn("title_acct_delete", "info_acct_delete");
        dh.setVisible(true);
        if (dh.getReturnStatus() == InfoDialog.CANCEL) {
            return;
        }

        final int[] selRows = dbTableAccount.getSelectedRows();
        jProgAccounts.setMinimum(0);
        jProgAccounts.setMaximum(selRows.length);
        jProgAccounts.setVisible(true);

        SwingWorker task = new SwingWorker<String, Integer>() {
            String name;
            String result;

            @Override
            public String doInBackground() {
                for (int i = 0; i < selRows.length; i++) {
                    name = (String) dbTableAccount.getValueAt(selRows[i], 1);
                    result = connHandler.getRAConnection().deleteAccount(name);
                    if (result.contains("low security level")) {
                        dh.createError("title_acctdelete_err", "info_lowsec");
                        dh.setVisible(true);
                        result = "err";
                        break;
                    }
                    publish(i + 1);
                    gf.pause(1000 / selRows.length);
                }
                return null;
            }

            @Override
            protected void done() {
                jProgAccounts.setVisible(false);
                if (!result.contentEquals("err")) {
                    updateAccountTable(0);
                    updateCharTable(0, 0);
                }
            }

            @Override
            protected void process(List<Integer> progress) {
                for (Integer x : progress) {
                    jProgAccounts.setValue(x);
                    jProgAccounts.setString(dh.getString("prog_acctdelete") + " " + name);
                }
            }
        };

        task.execute();
    }

    private void deleteCharacter() {
        dh.createWarn("title_char_delete", "info_char_delete");
        dh.setVisible(true);
        if (dh.getReturnStatus() == InfoDialog.CANCEL) {
            return;
        }

        final int[] selRows = dbTableChars.getSelectedRows();
        jProgChars.setMinimum(0);
        jProgChars.setMaximum(selRows.length);
        jProgChars.setVisible(true);

        SwingWorker task = new SwingWorker<String, Integer>() {
            String name;
            String guid;
            String result;

            @Override
            public String doInBackground() {
                try {
                    for (int i = 0; i < selRows.length; i++) {
                        guid = dbTableChars.getValueAt(selRows[i], 0).toString();
                        name = connHandler.getActiveSQL().getCharNameFromGUID(guid);
                        kickAndVerify(name);
                        result = connHandler.getRAConnection().deleteChar(name);
                        if (result.contains("no such subcommand")) {
                            dh.createError("title_chardelete_err", "info_lowsec");
                            dh.setVisible(true);
                        }
                        publish(i + 1);
                        gf.pause(1000 / selRows.length);
                    }
                } catch (Exception ex) {
                    logger.throwing(this.getClass().getName(), "charDelete", ex);
                }
                return null;
            }

            @Override
            protected void done() {
                jProgChars.setVisible(false);
                updateCharTable(0, 0);
            }

            @Override
            protected void process(List<Integer> progress) {
                for (Integer x : progress) {
                    jProgChars.setValue(x);
                    jProgChars.setString(dh.getString("prog_chardelete") + " " + name);
                }
            }
        };

        task.execute();
    }

    private void kickAndVerify(String name) {
        for (int x = 0; x < 10; x++) {
            if (connHandler.getActiveSQL().isCharOnline(name)) {
                connHandler.getRAConnection().kickChar(name);
            } else {
                break;
            }
        }
    }

    private void charLevel() {

        SpinnerInputDialog spinDialog = new SpinnerInputDialog((Frame) getTopLevelAncestor(), true);
        spinDialog.setLocationRelativeTo(this);
        spinDialog.setEntryLabel(dh.getString("lab_charlevel"));
        spinDialog.setValue(dbTableChars.getSelectedItem("level").toString());
        spinDialog.setTitle(strSelectedCharName + " " + dh.getString("title_charlevel"));
        spinDialog.setVisible(true);
        if (spinDialog.getReturnStatus() == SpinnerInputDialog.RET_CANCEL) {
            return;
        }
        setCursor(new Cursor(Cursor.WAIT_CURSOR));
        connHandler.getRAConnection().setCharLevel(strSelectedCharName, spinDialog.getValue().toString());
        updateCharFilter("");
        setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
    }

    private void charRename() {

        dh.createWarn("title_rename_char", "info_rename_char");
        dh.setVisible(true);
        if (dh.getReturnStatus() == InfoDialog.CANCEL) {
            return;
        }
        dh.createInfo("title_rename_char");
        setCursor(new Cursor(Cursor.WAIT_CURSOR));
        dh.appendInfoText(connHandler.getRAConnection().setCharRename(strSelectedCharName));
        setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        dh.setVisible(true);
    }

    private void customiseChar() {
        dh.createWarn("title_custom_char", "info_custom_char");
        dh.setVisible(true);
        if (dh.getReturnStatus() == InfoDialog.CANCEL) {
            return;
        }
        dh.createInfo("title_custom_char");
        setCursor(new Cursor(Cursor.WAIT_CURSOR));
        dh.appendInfoText(connHandler.getRAConnection().setCharCustomise(strSelectedCharName));
        setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        dh.setVisible(true);
    }

    private void viewTicket(String character) {
        String strText = connHandler.getRAConnection().getTicket(character);
        strText = strText.substring(strText.indexOf(("(")));
        dh.createInfo("title_view_ticket", null);
        dh.appendTitleText(" " + character);
        dh.appendInfoText(strText);
        dh.setVisible(true);
    }

    private void deleteTicket() {

        dh.createWarn("title_delete_ticket", "info_delete_ticket");
        dh.setVisible(true);
        if (dh.getReturnStatus() == InfoDialog.CANCEL) {
            return;
        }
        setCursor(new Cursor(Cursor.WAIT_CURSOR));
        int[] selRows = dbTableChars.getSelectedRows();
        for (int i = 0; i < selRows.length; i++) {
            connHandler.getRAConnection().deleteTicket((String) dbTableChars.getValueAt(selRows[i], 1));
        }
        updateCharTable(2, 0);
        setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
    }

    private void resetOptions() {

        ComboSelectDialog csd = new ComboSelectDialog((Frame) getTopLevelAncestor(), true);
        csd.setTitle(dh.getString("title_reset"));
        csd.setComboLabel(dh.getString("lab_reset"));

        // Process server specific UI entities
        switch (dbVersion) {
            case MangosSql.MANGOSZERO:
                csd.setOptions(dh.getStrings("opt_honor",
                        "opt_level", "opt_stats", "opt_spells", "opt_talents"));
                break;

            default:
                csd.setOptions(dh.getStrings("opt_achievements",
                        "opt_honor", "opt_level", "opt_stats", "opt_spells", "opt_talents"));
                break;
        }

        csd.setLocationRelativeTo(this);
        csd.setVisible(true);
        if (csd.getReturnStatus() == ComboSelectDialog.RET_CANCEL) {
            return;
        }

        String result = connHandler.getRAConnection().resetCharOption((String) strSelectedCharName, csd.getSelectedOption());
        dh.createInfo("title_reset");
        if (result.isEmpty()) {
            dh.appendInfoText(dh.getString("info_reset") + " "
                    + ((String) csd.getSelectedOption()).toLowerCase()
                    + " reset.");
        } else {
            dh.appendInfoText(result);
        }
        dh.setVisible(true);
    }

    private void safeRevive() {
        setCursor(new Cursor(Cursor.WAIT_CURSOR));
        // A dirty hack to allow teleport to homebound (Hearthstone location)
        connHandler.getActiveSQL().createStatement("revive");
        connHandler.getActiveSQL().setStatement("revive");
        // Ensure no previous saferevive entries in game_tele
        connHandler.getActiveSQL().executeUpdate("delete from *mangos*.game_tele where id=4000");
        // Get characters hombind location coords
        connHandler.getActiveSQL().executeQuery("SELECT position_x,position_y,position_z,map "
                + "FROM *char*.character_homebind WHERE guid =" + strSelectedCharId);
        if (connHandler.getActiveSQL().next()) {
            String strInsert = "insert into *mangos*.game_tele values (4000,"
                    + connHandler.getActiveSQL().getString(1) + ","
                    + connHandler.getActiveSQL().getString(2) + ","
                    + connHandler.getActiveSQL().getString(3) + ",0,"
                    + connHandler.getActiveSQL().getString(4) + ",'saferevive'" + ")";
            connHandler.getActiveSQL().executeUpdate(strInsert);
            // Reload the teleport table so mangos server knows about the change
            connHandler.getRAConnection().reloadTable("game_tele");
            // Delete entry from database as we no longer need it after the reload
            connHandler.getActiveSQL().executeUpdate("delete from *mangos*.game_tele where id=4000");
            connHandler.getActiveSQL().removeStatement("revive");
            connHandler.getRAConnection().teleport(strSelectedCharName, "saferevive");
        }
        setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        connHandler.getRAConnection().revive(strSelectedCharName);
        updateCharTable(5, 0);
    }

    private void revive() {
        connHandler.getRAConnection().revive(strSelectedCharName);
        updateCharTable(2, 0);
    }

    private void repairItems() {
        connHandler.getRAConnection().repairItems(strSelectedCharName);
    }

    private void sendMessage() {
        LineInputDialog lid = new LineInputDialog((Frame) getTopLevelAncestor(), true);
        lid.setTitle(dh.getString("title_message") + " " + strSelectedCharName);
        lid.setMessageLabel(dh.getString("lab_message_entry"));
        lid.setLocationRelativeTo((Frame) getTopLevelAncestor());
        lid.setVisible(true);
        if (lid.getReturnStatus() == LineInputDialog.RET_CANCEL) {
            return;
        }
        connHandler.getRAConnection().sendMessage(strSelectedCharName, lid.getMessage());
    }

    public void sendMail(String name) {
        SendMailDialog smd = new SendMailDialog((Frame) getTopLevelAncestor(), true);
        smd.setTitle(dh.getString("title_sendmail"));
        smd.setLocationRelativeTo((Frame) getTopLevelAncestor());
        smd.setToField(name, hsRace);
        smd.setGoldEnabled(true);
        smd.setVisible(true);
        if (smd.getReturnStatus() == SendMailDialog.RET_CANCEL) {
            return;
        }
        if (name != null) {
            if (smd.getGoldAmount() != 0) {
                connHandler.getRAConnection().sendGold(name, smd.getSubject(), smd.getBody(), smd.getGoldAmount().toString());
            } else {
                connHandler.getRAConnection().sendMail(name, smd.getSubject(), smd.getBody());
            }
        } else {
            if (smd.getGoldAmount() != 0) {
                connHandler.getRAConnection().sendMassGold(smd.getToField(), smd.getSubject(), smd.getBody(), smd.getGoldAmount().toString());
            } else {
                connHandler.getRAConnection().sendMassMail(smd.getToField(), smd.getSubject(), smd.getBody());
            }
        }
    }

    public void sendItems(String name) {

        String strClass;
        String strRace;
        String strLevel;

        SendItemsDialog smd = new SendItemsDialog((Frame) getTopLevelAncestor(), true);
        smd.setLocationRelativeTo((Frame) getTopLevelAncestor());
        smd.setDatabaseIO(connHandler.getActiveSQL());
        smd.setDialogHandler(dh);
        smd.populateRace(hsRace);
        if (name == null) {
            smd.setCharProperties(name, null, null, null);
            smd.setVisible(true);
            if (smd.getReturnStatus() == SendItemsDialog.RET_CANCEL) {
                return;
            }
            if (smd.getItems().isEmpty()) {
                connHandler.getRAConnection().sendMassMail(smd.getToField(), smd.getSubject(),
                        smd.getBody());
            } else {
                connHandler.getRAConnection().sendMassItems(smd.getToField(), smd.getSubject(),
                        smd.getBody(), smd.getItems());
            }
        } else {
            strClass = dbTableChars.getSelectedItemAsString("class");
            if (hsClass != null) {
                strClass = hsClass.getKey(strClass);
            }
            strRace = dbTableChars.getSelectedItemAsString("race");
            if (hsRace != null) {
                strRace = hsRace.getKey(strRace);
            }
            strLevel = dbTableChars.getSelectedItemAsString("level");
            smd.setCharProperties(name, strRace, strClass, strLevel);
            smd.setVisible(true);
            if (smd.getReturnStatus() == SendItemsDialog.RET_CANCEL) {
                return;
            }
            if (smd.getItems().isEmpty()) {
                connHandler.getRAConnection().sendMail(name, smd.getSubject(),
                        smd.getBody());
            } else {
                connHandler.getRAConnection().sendItems(name, smd.getSubject(),
                        smd.getBody(), smd.getItems());
            }
        }
    }

    private void setPassword() {
        CreateUserDialog cud = new CreateUserDialog((Frame) getTopLevelAncestor(), true);
        cud.setTitleLocal("title_setpassw");
        cud.setUsername(strSelectedAccountName);
        cud.setUserEntryAllow(false);
        cud.setVisible(true);
        if (cud.getReturnStatus() == CreateUserDialog.RET_CANCEL || cud.getPassword().isEmpty()) {
            return;
        }
        connHandler.getRAConnection().setPassword(cud.getUsername(), cud.getPassword());
    }

    private void createNewAcct() {
        CreateUserDialog cud = new CreateUserDialog((Frame) getTopLevelAncestor(), true);
        cud.setLocationRelativeTo((Frame) getTopLevelAncestor());
        cud.setVisible(true);
        if (cud.getReturnStatus() == CreateUserDialog.RET_CANCEL) {
            return;
        }
        String result = connHandler.getRAConnection().createAccount(cud.getUsername(), cud.getPassword());
        if (result.contains("access level")) {
            return;
        }
        updateAccountTable(2);
    }

    private void kickCharacter() {
        connHandler.getRAConnection().kickChar(strSelectedCharName);
        updateCharTable(2, 0);
    }

    private void banAcctIP(final int column) {

        final BanAcctDialog bad = new BanAcctDialog((Frame) getTopLevelAncestor(), true);
        bad.setVisible(true);
        if (bad.getReturnStatus() == BanAcctDialog.RET_CANCEL) {
            return;
        }

        final int[] selRows = dbTableAccount.getSelectedRows();
        jProgAccounts.setMinimum(0);
        jProgAccounts.setMaximum(selRows.length);
        jProgAccounts.setVisible(true);

        SwingWorker task = new SwingWorker<String, Integer>() {
            String strID = null;

            @Override
            public String doInBackground() {
                for (int i = 0; i < selRows.length; i++) {
                    strID = (String) dbTableAccount.getValueAt(selRows[i], column);
                    if (column == 1) {
                        connHandler.getRAConnection().banAcct(strID, bad.getReason(), bad.getDuration());
                    } else {
                        connHandler.getRAConnection().banIP(strID, bad.getReason(), bad.getDuration());
                    }
                    publish(i + 1);
                    gf.pause(1000 / selRows.length);
                }
                return null;
            }

            @Override
            protected void done() {
                jProgAccounts.setVisible(false);
            }

            @Override
            protected void process(List<Integer> progress) {
                for (Integer x : progress) {
                    jProgAccounts.setValue(x);
                    jProgAccounts.setString(dh.getString("prog_banacct") + " " + strID);
                }
            }
        };

        task.execute();
    }

    private void banChar() {
        BanAcctDialog bud = new BanAcctDialog((Frame) getTopLevelAncestor(), true);
        bud.setVisible(true);
        if (bud.getReturnStatus() == BanAcctDialog.RET_CANCEL) {
            return;
        }
        connHandler.getRAConnection().banChar(strSelectedCharName, bud.getReason(), bud.getDuration());
    }

    private void setAcctPrivileges() {

        ComboSelectDialog csd = new ComboSelectDialog((Frame) getTopLevelAncestor(), true);
        csd.setTitle(dh.getString("title_acctpriv"));
        csd.setComboLabel(dh.getString("combo_acctpriv"));
        csd.setOptions(dh.getStrings("gm_player", "gm_mod", "gm_games", "gm_admin"));
        csd.setVisible(true);
        if (csd.getReturnStatus() == ComboSelectDialog.RET_CANCEL) {
            return;
        }

        final String strIndex = String.valueOf(csd.getSelectedIndex());
        final int[] selRows = dbTableAccount.getSelectedRows();
        jProgAccounts.setMinimum(0);
        jProgAccounts.setMaximum(selRows.length);
        jProgAccounts.setVisible(true);

        SwingWorker task = new SwingWorker<String, Integer>() {
            String name = "";
            String result;

            @Override
            public String doInBackground() {
                for (int i = 0; i < selRows.length; i++) {
                    name = (String) dbTableAccount.getValueAt(selRows[i], 1);
                    result = connHandler.getRAConnection().setGM(name, strIndex);
                    if (result.contains("no such subcommand")) {
                        dh.createError("title_acctpriv_err", "info_lowsec");
                        dh.setVisible(true);
                        result = "err";
                        break;
                    }
                    publish(i + 1);
                    gf.pause(1000 / selRows.length);
                }
                return null;
            }

            @Override
            protected void done() {
                jProgAccounts.setVisible(false);
                if (!result.contentEquals("err")) {
                    updateAccountTable(0);
                }
            }

            @Override
            protected void process(List<Integer> progress) {
                for (Integer x : progress) {
                    jProgAccounts.setValue(x);
                    jProgAccounts.setString(dh.getString("prog_acctpriv") + " " + name);
                }
            }
        };

        task.execute();
    }

    private void teleportChar() {
        TeleportDialog teleDialog = new TeleportDialog((Frame) getTopLevelAncestor(), true);
        teleDialog.setDatabaseIO(connHandler.getActiveSQL());
        teleDialog.updateList();
        teleDialog.setVisible(true);
        if (teleDialog.getReturnStatus() == TeleportDialog.RET_CANCEL) {
            return;
        }
        connHandler.getRAConnection().teleport(strSelectedCharName, teleDialog.getSelectedLocation());
    }

    private void setExpansion() {

        ComboSelectDialog cd = new ComboSelectDialog((Frame) getTopLevelAncestor(), true);
        cd.setTitle(dh.getString("title_expansion"));
        cd.setComboLabel(dh.getString("combo_expansion"));

        // Adjust for db version
        switch (dbVersion) {
            case MangosSql.MANGOSONE:
                cd.setOptions(new String[]{"Classic", "TBC"});
                break;
            case MangosSql.MANGOSTWO:
                cd.setOptions(new String[]{"Classic", "TBC", "WoTLK"});
                break;

        }
        cd.setVisible(true);
        if (cd.getReturnStatus() == ComboSelectDialog.RET_CANCEL) {
            return;
        }

        final String strIndex = String.valueOf(cd.getSelectedIndex());
        final int[] selRows = dbTableAccount.getSelectedRows();
        jProgAccounts.setMinimum(0);
        jProgAccounts.setMaximum(selRows.length);
        jProgAccounts.setVisible(true);

        SwingWorker task;
        task = new SwingWorker<String, Integer>() {
            String name = null;

            @Override
            public String doInBackground() {
                for (int i = 0; i < selRows.length; i++) {
                    name = (String) dbTableAccount.getValueAt(selRows[i], 1);
                    connHandler.getRAConnection().setExpansion(name, strIndex);
                    publish(i + 1);
                    gf.pause(1000 / selRows.length);
                }
                return null;
            }

            @Override
            protected void done() {
                jProgAccounts.setVisible(false);
                updateAccountTable(0);
            }

            @Override
            protected void process(List<Integer> progress) {
                for (Integer x : progress) {
                    jProgAccounts.setValue(x);
                    jProgAccounts.setString(dh.getString("prog_expansion") + " " + name);
                }
            }
        };

        task.execute();
    }

    private void copyCharacter() {
        dh.createWarn("title_copychar", "info_copychar");
        dh.setVisible(true);
        if (dh.getReturnStatus() == InfoDialog.CANCEL) {
            return;
        }
        kickAndVerify(strSelectedCharName);
        String strCopySrc = dbTableChars.getSelectedItemAsString("guid");
        String result = connHandler.getRAConnection().pDumpWrite("pdump_temp", strCopySrc);
        if (result.contains("successfully!")) {
            jMenuItemPasteCharAs.setEnabled(true);
        }
    }

    private void pasteCharAs() {
        LineInputDialog lid = new LineInputDialog((Frame) getTopLevelAncestor(), true);
        lid.setBlockedCharacters(" ");
        lid.setTitle(dh.getString("title_pastechar"));
        lid.setMessageLabel(dh.getString("lab_pastechar"));
        lid.setVisible(true);
        if (lid.getReturnStatus() == LineInputDialog.RET_OK) {
            // Test for existing character
            if (connHandler.getActiveSQL().characterExists(lid.getMessage())) {
                dh.createError("title_pastechar_err", "info_pastechar_err2");
                dh.setVisible(true);
            } else {
                String strPasteTarg = dbTableAccount.getSelectedItemAsString("username");
                String result = connHandler.getRAConnection().pDumpLoad("pdump_temp", strPasteTarg, lid.getMessage());
                jMenuItemPasteCharAs.setEnabled(false);
                if (result.contains("successfully!")) {
                    updateCharTable(2, 0);
                }
            }
        }
    }

    private void openPortalManager() {

        if (portalManager != null) {
            if (connHandler.getActiveSQL().isCharOnline(strSelectedCharName)) {
                portalManager.setCharacter(strSelectedCharName);
            } else {
                portalManager.setCharacter(null);
            }
            portalManager.setLocationRelativeTo(getRootPane());
            portalManager.setVisible(true);
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

        jPopupAccounts = new javax.swing.JPopupMenu();
        jMenuItemRefreshAccounts = new javax.swing.JMenuItem();
        jMenuItemPasteCharAs = new javax.swing.JMenuItem();
        jMenuItemCreateAccount = new javax.swing.JMenuItem();
        jMenuItemAcctDelete = new javax.swing.JMenuItem();
        jMenuItemBanAccount = new javax.swing.JMenuItem();
        jMenuItemBanIP = new javax.swing.JMenuItem();
        jMenuItemAcctPriv = new javax.swing.JMenuItem();
        jMenuItemSetPass = new javax.swing.JMenuItem();
        jMenuItemAcctExpansion = new javax.swing.JMenuItem();
        jPopupChars = new javax.swing.JPopupMenu();
        jMenuItemRefreshChars = new javax.swing.JMenuItem();
        jMenuItemCopyChar = new javax.swing.JMenuItem();
        jMenuItemBanChar = new javax.swing.JMenuItem();
        jMenuItemDeleteChar = new javax.swing.JMenuItem();
        jMenuItemReset = new javax.swing.JMenuItem();
        jMenuItemSendMsg = new javax.swing.JMenuItem();
        jMenuItemSendItems = new javax.swing.JMenuItem();
        jMenuItemSendMail = new javax.swing.JMenuItem();
        jMenuItemPortal = new javax.swing.JMenuItem();
        jMenuItemCustomise = new javax.swing.JMenuItem();
        jMenuItemRename = new javax.swing.JMenuItem();
        jMenuItemLevel = new javax.swing.JMenuItem();
        jMenuItemViewTicket = new javax.swing.JMenuItem();
        jMenuItemDelTicket = new javax.swing.JMenuItem();
        jMenuItemRepair = new javax.swing.JMenuItem();
        jMenuItemSafeRevive = new javax.swing.JMenuItem();
        jMenuItemRevive = new javax.swing.JMenuItem();
        jMenuItemTeleport = new javax.swing.JMenuItem();
        jMenuItemKick = new javax.swing.JMenuItem();
        jPopupClipboard = new javax.swing.JPopupMenu();
        jMenuItemCopy = new javax.swing.JMenuItem();
        jMenuItemPaste = new javax.swing.JMenuItem();
        jPanelUsers = new javax.swing.JPanel();
        jLabel34 = new javax.swing.JLabel();
        jComboAcctField = new javax.swing.JComboBox();
        jComboAcctFilter = new javax.swing.JComboBox();
        jScrollPane2 = new javax.swing.JScrollPane();
        dbTableAccount = new lib.DBJTableBean();
        jProgAccounts = new javax.swing.JProgressBar();
        jLabel36 = new javax.swing.JLabel();
        jLabelNoOfAccts = new javax.swing.JLabel();
        jPanelCharacters = new javax.swing.JPanel();
        jScrollPane3 = new javax.swing.JScrollPane();
        dbTableChars = new lib.DBJTableBean();
        jComboCharFilter = new javax.swing.JComboBox();
        jLabel31 = new javax.swing.JLabel();
        jComboCharField = new javax.swing.JComboBox();
        jLabelNoOfChars = new javax.swing.JLabel();
        jProgChars = new javax.swing.JProgressBar();

        jPopupAccounts.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("lang/MessagesBundle"); // NOI18N
        jMenuItemRefreshAccounts.setText(bundle.getString("mitem_refreshtable")); // NOI18N
        jMenuItemRefreshAccounts.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemRefreshAccountsActionPerformed(evt);
            }
        });
        jPopupAccounts.add(jMenuItemRefreshAccounts);

        jMenuItemPasteCharAs.setText(bundle.getString("mitem_pastechar")); // NOI18N
        jMenuItemPasteCharAs.setEnabled(false);
        jMenuItemPasteCharAs.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemPasteCharAsActionPerformed(evt);
            }
        });
        jPopupAccounts.add(jMenuItemPasteCharAs);

        jMenuItemCreateAccount.setText(bundle.getString("mitem_createacct")); // NOI18N
        jMenuItemCreateAccount.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemCreateAccountActionPerformed(evt);
            }
        });
        jPopupAccounts.add(jMenuItemCreateAccount);

        jMenuItemAcctDelete.setText(bundle.getString("mitem_deleteacct")); // NOI18N
        jMenuItemAcctDelete.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemAcctDeleteActionPerformed(evt);
            }
        });
        jPopupAccounts.add(jMenuItemAcctDelete);

        jMenuItemBanAccount.setText(bundle.getString("mitem_banaccts")); // NOI18N
        jMenuItemBanAccount.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemBanAccountActionPerformed(evt);
            }
        });
        jPopupAccounts.add(jMenuItemBanAccount);

        jMenuItemBanIP.setText(bundle.getString("mitem_banip")); // NOI18N
        jMenuItemBanIP.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemBanIPActionPerformed(evt);
            }
        });
        jPopupAccounts.add(jMenuItemBanIP);

        jMenuItemAcctPriv.setText(bundle.getString("mitem_setprivs")); // NOI18N
        jMenuItemAcctPriv.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemAcctPrivActionPerformed(evt);
            }
        });
        jPopupAccounts.add(jMenuItemAcctPriv);

        jMenuItemSetPass.setText(bundle.getString("mitem_setacctpass")); // NOI18N
        jMenuItemSetPass.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemSetPassActionPerformed(evt);
            }
        });
        jPopupAccounts.add(jMenuItemSetPass);

        jMenuItemAcctExpansion.setText(bundle.getString("mitem_setexpansion")); // NOI18N
        jMenuItemAcctExpansion.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemAcctExpansionActionPerformed(evt);
            }
        });
        jPopupAccounts.add(jMenuItemAcctExpansion);

        jPopupChars.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        jMenuItemRefreshChars.setText(bundle.getString("mitem_refreshtable")); // NOI18N
        jMenuItemRefreshChars.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemRefreshCharsActionPerformed(evt);
            }
        });
        jPopupChars.add(jMenuItemRefreshChars);

        jMenuItemCopyChar.setText(bundle.getString("mitem_copychar")); // NOI18N
        jMenuItemCopyChar.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemCopyCharActionPerformed(evt);
            }
        });
        jPopupChars.add(jMenuItemCopyChar);

        jMenuItemBanChar.setText(bundle.getString("mitem_banchar")); // NOI18N
        jMenuItemBanChar.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemBanCharActionPerformed(evt);
            }
        });
        jPopupChars.add(jMenuItemBanChar);

        jMenuItemDeleteChar.setText(bundle.getString("mitem_deletechar")); // NOI18N
        jMenuItemDeleteChar.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemDeleteCharActionPerformed(evt);
            }
        });
        jPopupChars.add(jMenuItemDeleteChar);

        jMenuItemReset.setText(bundle.getString("mitem_resetchar")); // NOI18N
        jMenuItemReset.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemResetActionPerformed(evt);
            }
        });
        jPopupChars.add(jMenuItemReset);

        jMenuItemSendMsg.setText(bundle.getString("mitem_sendmsg")); // NOI18N
        jMenuItemSendMsg.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemSendMsgActionPerformed(evt);
            }
        });
        jPopupChars.add(jMenuItemSendMsg);

        jMenuItemSendItems.setText(bundle.getString("mitem_senditems")); // NOI18N
        jMenuItemSendItems.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemSendItemsActionPerformed(evt);
            }
        });
        jPopupChars.add(jMenuItemSendItems);

        jMenuItemSendMail.setText(bundle.getString("mitem_sendmail")); // NOI18N
        jMenuItemSendMail.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemSendMailActionPerformed(evt);
            }
        });
        jPopupChars.add(jMenuItemSendMail);

        jMenuItemPortal.setText(bundle.getString("mitem_portal")); // NOI18N
        jMenuItemPortal.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemPortalActionPerformed(evt);
            }
        });
        jPopupChars.add(jMenuItemPortal);

        jMenuItemCustomise.setText(bundle.getString("mitem_customise")); // NOI18N
        jMenuItemCustomise.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemCustomiseActionPerformed(evt);
            }
        });
        jPopupChars.add(jMenuItemCustomise);

        jMenuItemRename.setText(bundle.getString("mitem_rename")); // NOI18N
        jMenuItemRename.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemRenameActionPerformed(evt);
            }
        });
        jPopupChars.add(jMenuItemRename);

        jMenuItemLevel.setText(bundle.getString("mitem_charlevel")); // NOI18N
        jMenuItemLevel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemLevelActionPerformed(evt);
            }
        });
        jPopupChars.add(jMenuItemLevel);

        jMenuItemViewTicket.setText(bundle.getString("mitem_viewticket")); // NOI18N
        jMenuItemViewTicket.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemViewTicketActionPerformed(evt);
            }
        });
        jPopupChars.add(jMenuItemViewTicket);

        jMenuItemDelTicket.setText(bundle.getString("mitem_deleteticket")); // NOI18N
        jMenuItemDelTicket.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemDelTicketActionPerformed(evt);
            }
        });
        jPopupChars.add(jMenuItemDelTicket);

        jMenuItemRepair.setText(bundle.getString("mitem_repair")); // NOI18N
        jMenuItemRepair.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemRepairActionPerformed(evt);
            }
        });
        jPopupChars.add(jMenuItemRepair);

        jMenuItemSafeRevive.setText(bundle.getString("mitem_saferevive")); // NOI18N
        jMenuItemSafeRevive.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemSafeReviveActionPerformed(evt);
            }
        });
        jPopupChars.add(jMenuItemSafeRevive);

        jMenuItemRevive.setText(bundle.getString("mitem_revive")); // NOI18N
        jMenuItemRevive.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemReviveActionPerformed(evt);
            }
        });
        jPopupChars.add(jMenuItemRevive);

        jMenuItemTeleport.setText(bundle.getString("mitem_teleport")); // NOI18N
        jMenuItemTeleport.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemTeleportActionPerformed(evt);
            }
        });
        jPopupChars.add(jMenuItemTeleport);

        jMenuItemKick.setText(bundle.getString("mitem_kick")); // NOI18N
        jMenuItemKick.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemKickActionPerformed(evt);
            }
        });
        jPopupChars.add(jMenuItemKick);

        jPopupClipboard.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        jMenuItemCopy.setText(bundle.getString("mitem_copy")); // NOI18N
        jMenuItemCopy.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemCopyActionPerformed(evt);
            }
        });
        jPopupClipboard.add(jMenuItemCopy);

        jMenuItemPaste.setText(bundle.getString("mitem_paste")); // NOI18N
        jMenuItemPaste.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemPasteActionPerformed(evt);
            }
        });
        jPopupClipboard.add(jMenuItemPaste);

        setFont(getFont().deriveFont(getFont().getStyle() | java.awt.Font.BOLD, getFont().getSize()+3));

        jPanelUsers.setBorder(javax.swing.BorderFactory.createTitledBorder(null, bundle.getString("pan_accounts"), javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, getFont(), getForeground())); // NOI18N
        jPanelUsers.setFont(new java.awt.Font("Dialog", 0, 11)); // NOI18N
        jPanelUsers.setPreferredSize(new java.awt.Dimension(100, 100));
        jPanelUsers.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jPanelUsersMouseClicked(evt);
            }
        });

        jLabel34.setText(bundle.getString("lab_filter")); // NOI18N

        jComboAcctField.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Username", "Last_IP", "GMLevel", "Expansion", "Last_Login" }));
        jComboAcctField.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                jComboAcctFieldItemStateChanged(evt);
            }
        });

        jComboAcctFilter.setEditable(true);
        jComboAcctFilter.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "" }));
        jComboAcctFilter.setLightWeightPopupEnabled(false);
        jComboAcctFilter.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jComboAcctFilterActionPerformed(evt);
            }
        });

        jScrollPane2.setToolTipText(bundle.getString("tooltip_tables")); // NOI18N

        dbTableAccount.setAutoColumnEnabled(true);
        dbTableAccount.setComponentPopupMenu(jPopupAccounts);
        dbTableAccount.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                dbTableAccountMouseReleased(evt);
            }
        });
        dbTableAccount.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                dbTableAccountKeyReleased(evt);
            }
        });
        jScrollPane2.setViewportView(dbTableAccount);

        jProgAccounts.setToolTipText("Accounts Status");
        jProgAccounts.setStringPainted(true);

        jLabel36.setText(bundle.getString("lab_search")); // NOI18N

        jLabelNoOfAccts.setText("No of records returned");

        javax.swing.GroupLayout jPanelUsersLayout = new javax.swing.GroupLayout(jPanelUsers);
        jPanelUsers.setLayout(jPanelUsersLayout);
        jPanelUsersLayout.setHorizontalGroup(
            jPanelUsersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelUsersLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelUsersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane2)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelUsersLayout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(jLabel36)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jComboAcctFilter, javax.swing.GroupLayout.PREFERRED_SIZE, 153, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel34)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jComboAcctField, javax.swing.GroupLayout.PREFERRED_SIZE, 150, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelUsersLayout.createSequentialGroup()
                        .addComponent(jProgAccounts, javax.swing.GroupLayout.PREFERRED_SIZE, 660, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 130, Short.MAX_VALUE)
                        .addComponent(jLabelNoOfAccts)))
                .addContainerGap())
        );
        jPanelUsersLayout.setVerticalGroup(
            jPanelUsersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelUsersLayout.createSequentialGroup()
                .addGroup(jPanelUsersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(jLabel34)
                    .addComponent(jComboAcctFilter, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jComboAcctField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel36))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 283, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelUsersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelNoOfAccts)
                    .addComponent(jProgAccounts, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        jPanelCharacters.setBorder(javax.swing.BorderFactory.createTitledBorder(null, bundle.getString("pan_chars"), javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, getFont(), getForeground())); // NOI18N
        jPanelCharacters.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jPanelCharactersMouseClicked(evt);
            }
        });

        jScrollPane3.setToolTipText(bundle.getString("tooltip_tables")); // NOI18N

        dbTableChars.setAutoColumnEnabled(true);
        dbTableChars.setComponentPopupMenu(jPopupChars);
        dbTableChars.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                dbTableCharsMouseReleased(evt);
            }
        });
        dbTableChars.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                dbTableCharsKeyReleased(evt);
            }
        });
        jScrollPane3.setViewportView(dbTableChars);

        jComboCharFilter.setEditable(true);
        jComboCharFilter.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "" }));
        jComboCharFilter.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jComboCharFilterActionPerformed(evt);
            }
        });

        jLabel31.setText(bundle.getString("lab_filter")); // NOI18N

        jComboCharField.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Name", "Account", "Ticket", "Race", "Class" }));
        jComboCharField.setMinimumSize(new java.awt.Dimension(95, 24));
        jComboCharField.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                jComboCharFieldItemStateChanged(evt);
            }
        });

        jLabelNoOfChars.setText("No of records returned");

        jProgChars.setToolTipText("Character Status");
        jProgChars.setStringPainted(true);

        javax.swing.GroupLayout jPanelCharactersLayout = new javax.swing.GroupLayout(jPanelCharacters);
        jPanelCharacters.setLayout(jPanelCharactersLayout);
        jPanelCharactersLayout.setHorizontalGroup(
            jPanelCharactersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelCharactersLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelCharactersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane3)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelCharactersLayout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(jComboCharFilter, javax.swing.GroupLayout.PREFERRED_SIZE, 169, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel31)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jComboCharField, javax.swing.GroupLayout.PREFERRED_SIZE, 128, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelCharactersLayout.createSequentialGroup()
                        .addComponent(jProgChars, javax.swing.GroupLayout.PREFERRED_SIZE, 660, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 130, Short.MAX_VALUE)
                        .addComponent(jLabelNoOfChars)))
                .addContainerGap())
        );
        jPanelCharactersLayout.setVerticalGroup(
            jPanelCharactersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelCharactersLayout.createSequentialGroup()
                .addGroup(jPanelCharactersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jComboCharField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jComboCharFilter, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel31))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 240, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelCharactersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelNoOfChars)
                    .addComponent(jProgChars, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanelCharacters, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanelUsers, javax.swing.GroupLayout.DEFAULT_SIZE, 986, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanelUsers, javax.swing.GroupLayout.DEFAULT_SIZE, 376, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanelCharacters, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void jComboAcctFieldItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_jComboAcctFieldItemStateChanged
        if (evt.getStateChange() == ItemEvent.DESELECTED) {
            return;
        }
        jComboAcctFilter.setComponentPopupMenu(null);
        switch (jComboAcctField.getSelectedIndex()) {
            case 2:
                jComboAcctFilter.setEditable(false);
                jComboAcctFilter.removeAllItems();
                jComboAcctFilter.addItem(dh.getString("gm_player"));
                jComboAcctFilter.addItem(dh.getString("gm_mod"));
                jComboAcctFilter.addItem(dh.getString("gm_games"));
                jComboAcctFilter.addItem(dh.getString("gm_admin"));
                jComboAcctFilter.addItem(dh.getString("gm_god"));
                break;
            case 3:
                jComboAcctFilter.setEditable(false);
                jComboAcctFilter.removeAllItems();
                jComboAcctFilter.addItem("Classic");
                jComboAcctFilter.addItem("TBC");
                jComboAcctFilter.addItem("WoTLK");
                break;
            case 4:
                jComboAcctFilter.setEditable(false);
                jComboAcctFilter.removeAllItems();
                jComboAcctFilter.addItem(">= 1 Month");
                jComboAcctFilter.addItem(">= 3 Month");
                jComboAcctFilter.addItem(">= 6 Month");
                jComboAcctFilter.addItem(">= 1 Year");
                break;
            default:
                jComboAcctFilter.setComponentPopupMenu(jPopupClipboard);
                jComboAcctFilter.setEditable(true);
                jComboAcctFilter.removeAllItems();
                jComboAcctFilter.addItem("");
                break;
        }
    }//GEN-LAST:event_jComboAcctFieldItemStateChanged

    private void jComboAcctFilterActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboAcctFilterActionPerformed
        updateAccountTable(0);
    }//GEN-LAST:event_jComboAcctFilterActionPerformed

    private void dbTableAccountKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_dbTableAccountKeyReleased
        switch (evt.getKeyCode()) {
            case KeyEvent.VK_UP:
            case KeyEvent.VK_DOWN:
            case KeyEvent.VK_KP_DOWN:
            case KeyEvent.VK_KP_UP:
                dbTableAccountMouseReleased(null);
                break;
        }
    }//GEN-LAST:event_dbTableAccountKeyReleased

    private void jPanelUsersMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jPanelUsersMouseClicked
        setAccountMenusEnabled(false);
    }//GEN-LAST:event_jPanelUsersMouseClicked

    private void dbTableCharsKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_dbTableCharsKeyReleased
        switch (evt.getKeyCode()) {
            case KeyEvent.VK_UP:
            case KeyEvent.VK_DOWN:
            case KeyEvent.VK_KP_DOWN:
            case KeyEvent.VK_KP_UP:
                setCharMenusEnabled(true);
                break;
        }
    }//GEN-LAST:event_dbTableCharsKeyReleased

    private void jComboCharFilterActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboCharFilterActionPerformed
        updateCharTable(0, 0);
    }//GEN-LAST:event_jComboCharFilterActionPerformed

    private void jComboCharFieldItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_jComboCharFieldItemStateChanged
        if (evt.getStateChange() == ItemEvent.DESELECTED) {
            return;
        }
        updateCharFilter("");
    }//GEN-LAST:event_jComboCharFieldItemStateChanged

    private void jPanelCharactersMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jPanelCharactersMouseClicked
        dbTableChars.clearSelection();
        setCharMenusEnabled(false);
    }//GEN-LAST:event_jPanelCharactersMouseClicked

    private void jMenuItemRefreshAccountsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemRefreshAccountsActionPerformed
        updateAccountTable(0);
    }//GEN-LAST:event_jMenuItemRefreshAccountsActionPerformed

    private void jMenuItemPasteCharAsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemPasteCharAsActionPerformed
        pasteCharAs();
    }//GEN-LAST:event_jMenuItemPasteCharAsActionPerformed

    private void jMenuItemCreateAccountActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemCreateAccountActionPerformed
        createNewAcct();
    }//GEN-LAST:event_jMenuItemCreateAccountActionPerformed

    private void jMenuItemAcctDeleteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemAcctDeleteActionPerformed
        deleteAccount();
    }//GEN-LAST:event_jMenuItemAcctDeleteActionPerformed

    private void jMenuItemBanAccountActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemBanAccountActionPerformed
        banAcctIP(1);
    }//GEN-LAST:event_jMenuItemBanAccountActionPerformed

    private void jMenuItemBanIPActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemBanIPActionPerformed
        banAcctIP(2);
    }//GEN-LAST:event_jMenuItemBanIPActionPerformed

    private void jMenuItemAcctPrivActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemAcctPrivActionPerformed
        setAcctPrivileges();
    }//GEN-LAST:event_jMenuItemAcctPrivActionPerformed

    private void jMenuItemSetPassActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemSetPassActionPerformed
        setPassword();
    }//GEN-LAST:event_jMenuItemSetPassActionPerformed

    private void jMenuItemAcctExpansionActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemAcctExpansionActionPerformed
        setExpansion();
    }//GEN-LAST:event_jMenuItemAcctExpansionActionPerformed

    private void jMenuItemRefreshCharsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemRefreshCharsActionPerformed
        updateCharTable(0, 0);
    }//GEN-LAST:event_jMenuItemRefreshCharsActionPerformed

    private void jMenuItemCopyCharActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemCopyCharActionPerformed
        copyCharacter();
    }//GEN-LAST:event_jMenuItemCopyCharActionPerformed

    private void jMenuItemBanCharActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemBanCharActionPerformed
        banChar();
    }//GEN-LAST:event_jMenuItemBanCharActionPerformed

    private void jMenuItemDeleteCharActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemDeleteCharActionPerformed
        deleteCharacter();
    }//GEN-LAST:event_jMenuItemDeleteCharActionPerformed

    private void jMenuItemResetActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemResetActionPerformed
        resetOptions();
    }//GEN-LAST:event_jMenuItemResetActionPerformed

    private void jMenuItemSendItemsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemSendItemsActionPerformed
        sendItems(strSelectedCharName);
    }//GEN-LAST:event_jMenuItemSendItemsActionPerformed

    private void jMenuItemPortalActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemPortalActionPerformed
        openPortalManager();
    }//GEN-LAST:event_jMenuItemPortalActionPerformed

    private void jMenuItemCustomiseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemCustomiseActionPerformed
        customiseChar();
    }//GEN-LAST:event_jMenuItemCustomiseActionPerformed

    private void jMenuItemRenameActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemRenameActionPerformed
        charRename();
    }//GEN-LAST:event_jMenuItemRenameActionPerformed

    private void jMenuItemLevelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemLevelActionPerformed
        charLevel();
    }//GEN-LAST:event_jMenuItemLevelActionPerformed

    private void jMenuItemViewTicketActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemViewTicketActionPerformed
        viewTicket(strSelectedCharName);
    }//GEN-LAST:event_jMenuItemViewTicketActionPerformed

    private void jMenuItemDelTicketActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemDelTicketActionPerformed
        deleteTicket();
    }//GEN-LAST:event_jMenuItemDelTicketActionPerformed

    private void jMenuItemRepairActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemRepairActionPerformed
        repairItems();
    }//GEN-LAST:event_jMenuItemRepairActionPerformed

    private void jMenuItemSafeReviveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemSafeReviveActionPerformed
        safeRevive();
    }//GEN-LAST:event_jMenuItemSafeReviveActionPerformed

    private void jMenuItemReviveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemReviveActionPerformed
        revive();
    }//GEN-LAST:event_jMenuItemReviveActionPerformed

    private void jMenuItemSendMsgActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemSendMsgActionPerformed
        sendMessage();
    }//GEN-LAST:event_jMenuItemSendMsgActionPerformed

    private void jMenuItemTeleportActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemTeleportActionPerformed
        teleportChar();
    }//GEN-LAST:event_jMenuItemTeleportActionPerformed

    private void jMenuItemKickActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemKickActionPerformed
        kickCharacter();
    }//GEN-LAST:event_jMenuItemKickActionPerformed

    private void jMenuItemCopyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemCopyActionPerformed
        try {
            JTextField jTextContents = (JTextField) jPopupClipboard.getInvoker();
            String text = jTextContents.getSelectedText();
            if (text != null) {
                if (!text.isEmpty()) {
                    StringSelection stringSelection = new StringSelection(text);
                    clippy.setContents(stringSelection, null);
                }
            }
        } catch (Exception ex) {
            logger.throwing(this.getClass().getName(), "jMenuItemCopyActionPerformed", ex);
        }
    }//GEN-LAST:event_jMenuItemCopyActionPerformed

    private void jMenuItemPasteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemPasteActionPerformed
        try {
            JTextField jTextContents = (JTextField) jPopupClipboard.getInvoker();
            Transferable transferContents = clippy.getContents(null);
            if (transferContents != null) {
                if (transferContents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    jTextContents.setText((String) transferContents.getTransferData(DataFlavor.stringFlavor));
                }
            }
        } catch (UnsupportedFlavorException | IOException ex) {
            logger.throwing(this.getClass().getName(), "jMenuItemPasteActionPerformed", ex);
        }
    }//GEN-LAST:event_jMenuItemPasteActionPerformed

    private void dbTableAccountMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_dbTableAccountMouseReleased

        if (evt == null || evt.getButton() == MouseEvent.BUTTON1) {
            // Update charater table, based on selected account
            strSelectedAccountName = (String) dbTableAccount.getSelectedItem("username");
            jComboCharField.setSelectedIndex(1);
            updateCharFilter(strSelectedAccountName);
            setAccountMenusEnabled(true);
        }

    }//GEN-LAST:event_dbTableAccountMouseReleased

    private void dbTableCharsMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_dbTableCharsMouseReleased
        if (evt.getButton() == MouseEvent.BUTTON1) {
            setCharMenusEnabled(true);
        }
    }//GEN-LAST:event_dbTableCharsMouseReleased

    private void jMenuItemSendMailActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemSendMailActionPerformed
        sendMail(strSelectedCharName);
    }//GEN-LAST:event_jMenuItemSendMailActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private lib.DBJTableBean dbTableAccount;
    private lib.DBJTableBean dbTableChars;
    private javax.swing.JComboBox jComboAcctField;
    private javax.swing.JComboBox jComboAcctFilter;
    private javax.swing.JComboBox jComboCharField;
    private javax.swing.JComboBox jComboCharFilter;
    private javax.swing.JLabel jLabel31;
    private javax.swing.JLabel jLabel34;
    private javax.swing.JLabel jLabel36;
    private javax.swing.JLabel jLabelNoOfAccts;
    private javax.swing.JLabel jLabelNoOfChars;
    private javax.swing.JMenuItem jMenuItemAcctDelete;
    private javax.swing.JMenuItem jMenuItemAcctExpansion;
    private javax.swing.JMenuItem jMenuItemAcctPriv;
    private javax.swing.JMenuItem jMenuItemBanAccount;
    private javax.swing.JMenuItem jMenuItemBanChar;
    private javax.swing.JMenuItem jMenuItemBanIP;
    private javax.swing.JMenuItem jMenuItemCopy;
    private javax.swing.JMenuItem jMenuItemCopyChar;
    private javax.swing.JMenuItem jMenuItemCreateAccount;
    private javax.swing.JMenuItem jMenuItemCustomise;
    private javax.swing.JMenuItem jMenuItemDelTicket;
    private javax.swing.JMenuItem jMenuItemDeleteChar;
    private javax.swing.JMenuItem jMenuItemKick;
    private javax.swing.JMenuItem jMenuItemLevel;
    private javax.swing.JMenuItem jMenuItemPaste;
    private javax.swing.JMenuItem jMenuItemPasteCharAs;
    private javax.swing.JMenuItem jMenuItemPortal;
    private javax.swing.JMenuItem jMenuItemRefreshAccounts;
    private javax.swing.JMenuItem jMenuItemRefreshChars;
    private javax.swing.JMenuItem jMenuItemRename;
    private javax.swing.JMenuItem jMenuItemRepair;
    private javax.swing.JMenuItem jMenuItemReset;
    private javax.swing.JMenuItem jMenuItemRevive;
    private javax.swing.JMenuItem jMenuItemSafeRevive;
    private javax.swing.JMenuItem jMenuItemSendItems;
    private javax.swing.JMenuItem jMenuItemSendMail;
    private javax.swing.JMenuItem jMenuItemSendMsg;
    private javax.swing.JMenuItem jMenuItemSetPass;
    private javax.swing.JMenuItem jMenuItemTeleport;
    private javax.swing.JMenuItem jMenuItemViewTicket;
    private javax.swing.JPanel jPanelCharacters;
    private javax.swing.JPanel jPanelUsers;
    private javax.swing.JPopupMenu jPopupAccounts;
    private javax.swing.JPopupMenu jPopupChars;
    private javax.swing.JPopupMenu jPopupClipboard;
    private javax.swing.JProgressBar jProgAccounts;
    private javax.swing.JProgressBar jProgChars;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    // End of variables declaration//GEN-END:variables
}
