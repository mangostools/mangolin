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

import lib.CryptoLite;
import lib.LineInputDialog;
import lib.SimpleINI;
import java.awt.Component;
import java.awt.Frame;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.swing.DefaultComboBoxModel;

/**
 *
 * @author Alistair Neil, <info@dazzleships.net>
 */
public class ConnectionHandler extends javax.swing.JDialog {

    /**
     * A return status code - returned if Cancel button has been pressed
     */
    public static final int RET_CANCEL = 0;
    public static final int RET_OK = 1;
    public static final int RA_DISABLED = 0;
    public static final int RA_ERROR = 1;
    public static final int RA_LOST = 2;
    public static final int RA_ACTIVE = 3;
    public static final int QC_STATUS_CHANGE = 4;
    public static final int PROFILE_CHANGE = 5;
    private static final String SERVER = "Server-";
    private static final Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private final Pattern patternComma = Pattern.compile(",");
    private final SimpleINI simpleIni;
    private final MangosSql mangosSql;
    private final MangosSql mangosSqlBackup;
    private final MangosTelnet mangosTelnet;
    private DialogHandler dh;
    private String strDBMessage;
    private int intRAStatus = -1;
    private final Frame parent;
    private String strActiveProfile;
    private boolean boolQuickConnEnabled = false;

    /**
     * Creates new form ConnectDialog
     *
     * @param parent
     * @param modal
     * @param simpleIni
     */
    public ConnectionHandler(java.awt.Frame parent, boolean modal, SimpleINI simpleIni) {
        super(parent, modal);
        this.parent = parent;
        this.simpleIni = simpleIni;
        initComponents();
        // Create our sql handling objects
        mangosSql = new MangosSql();
        mangosSqlBackup = new MangosSql();
        mangosTelnet = new MangosTelnet();
        refreshProfilesCombo(false);
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
     * Get active server profile
     *
     * @return active profile as string
     */
    public String getActiveProfile() {
        return strActiveProfile;
    }

    /**
     * Get Remote Access server information
     *
     * @return Mangos server information as string
     */
    public String getRAServerInfo() {

        String result = null;

        if (intRAStatus == RA_ACTIVE) {
            result = getRAConnection().getInfo();
            if (result.contains("Error: Null")) {
                intRAStatus = ConnectionHandler.RA_LOST;
                eventUpdate(intRAStatus);
            } else {
                if (result.indexOf('=') > -1) {
                    result = result.substring(0, result.indexOf('='));
                }
                result += "\n" + getRAConnection().getPlayerLimit();
                result += "\n" + getRAConnection().getMotd();
            }
        }

        switch (intRAStatus) {
            case RA_LOST:
            case RA_ERROR:
                result = dh.getString("servinfo_radisabled_err");
                break;

            case RA_DISABLED:
                result = dh.getString("servinfo_radisabled_user");
                break;
        }

        return result;
    }

    /**
     * Set visibility of the Connect button
     *
     * @param visible
     */
    public void setConnectButtonVisible(boolean visible) {
        jButtConnect.setVisible(visible);
    }

    /**
     * Set Quick connect enabled/disabled
     *
     * @param enabled
     */
    public void setQuickConnectEnabled(boolean enabled) {
        boolQuickConnEnabled = enabled;
        eventUpdate(QC_STATUS_CHANGE);
    }

    /**
     * Get quick connect status
     *
     * @return true if enabled
     */
    public boolean getQuickConnectEnabled() {
        return boolQuickConnEnabled;
    }

    /**
     * Set the text that appears on the cancel button
     *
     * @param text
     */
    public void setCancelButtonText(String text) {
        jButtCancel.setText(text);
    }

    /**
     * Returns array of profile names
     *
     * @return string array
     */
    public String[] getProfileNames() {

        int ic = jComboProfiles.getItemCount();
        String strArr[] = new String[ic];
        for (int i = 0; i < ic; i++) {
            strArr[i] = ((String) jComboProfiles.getItemAt(i));
        }
        return strArr;
    }

    /**
     * Used to provide callback, should be overidden by parent
     *
     * @param evt
     */
    public void eventUpdate(int evt) {
    }

    /**
     * Get the general purpose SQL Connection
     *
     * @return MangosSql connection
     */
    public MangosSql getActiveSQL() {
        return mangosSql;
    }

    /**
     * Get the SQL Connection used for backup duties
     *
     * @return MangosSql connection
     */
    public MangosSql getBackupSQL() {
        return mangosSqlBackup;
    }

    /**
     * Get the RA connection
     *
     * @return Remote Access connection
     */
    public MangosTelnet getRAConnection() {
        return mangosTelnet;
    }

    /**
     * Connect to database server
     *
     * @return true if connected
     */
    public boolean dbConnect() {

        // Get database password as a string
        char passchars[] = getDBPass();
        String ourPass = String.copyValueOf(passchars);

        // Login into database
        strDBMessage = mangosSql.openDB(getDBHost(), getDBPort(),
                getDBUser(), ourPass);
        logger.log(Level.INFO, "serverConnect mangosMySql.openDB {0}", strDBMessage);
        if (!strDBMessage.contains("Connected")) {
            return false;
        }

        // Attempt to connect to database catalogs
        strDBMessage = mangosSql.setDatabases(getDBMangos(), getDBRealm(),
                getDBChar(), getDBScript());
        logger.log(Level.INFO, "serverConnect mangosMySql.setDatabases {0}", strDBMessage);
        if (strDBMessage != null) {
            return false;
        }

        // Login into database
        strDBMessage = mangosSqlBackup.openDB(getDBHost(), getDBPort(),
                getDBUser(), ourPass);
        logger.log(Level.INFO, "serverConnect mangosMySqlBackup.openDB {0}", strDBMessage);

        // Attempt to connect to database catalogs
        strDBMessage = mangosSqlBackup.setDatabases(getDBMangos(), getDBRealm(),
                getDBChar(), getDBScript());
        logger.log(Level.INFO, "serverConnect mangosMySqlBackup.setDatabases {0}", strDBMessage);

        return true;
    }

    /**
     * Returns the database connection messsage
     *
     * @return Database connection status messsage as String
     */
    public String getDBConnectionMessage() {
        return strDBMessage;
    }

    /**
     * Connect to Remote Access server
     *
     * @return Connection message
     */
    public String raConnect() {

        String result = null;

        // Check to see if remote access has been enabled by user
        if (!isRAEnabled()) {
            intRAStatus = RA_DISABLED;
            return result;
        }

        // Remote Access login
        char passchars[] = getRAPass();
        String ourPass = String.copyValueOf(passchars);
        result = mangosTelnet.Login(getRAHost(), getRAPort(),
                getRAUser(), ourPass);

        // Process return result
        if (result.contains("+Logged in")) {
            result = "Logged in as " + getRAUser();
            intRAStatus = RA_ACTIVE;
        } else {
            intRAStatus = RA_ERROR;
        }
        return result;
    }

    /**
     * Get remote access connection status
     *
     * @return Returns status as an integer
     */
    public int getRAStatus() {
        return intRAStatus;
    }

    /**
     * Test to if Remote Access is connected
     *
     * @return true if it is
     */
    public boolean isRAConnected() {
        return (intRAStatus == RA_ACTIVE);
    }

    /**
     * Disconnect from mangos and database servers
     */
    public void disconnect() {
        mangosSql.closeDB();
        mangosSqlBackup.closeDB();
        if (isRAConnected()) {
            mangosTelnet.closeConnection();
        }
    }

    public void setProfile(String profile) {
        jComboProfiles.setEnabled(false);
        jComboProfiles.setSelectedItem(profile);
        loadProfile();
        jComboProfiles.setEnabled(true);
    }

    /**
     * Load connection preferences
     *
     */
    public final void loadProfile() {

        strActiveProfile = (String) jComboProfiles.getSelectedItem();
        if (strActiveProfile == null) {
            return;
        }

        // Test for no defined profiles
        if (strActiveProfile.contentEquals("None")) {
            // Update combobox
            jComboProfiles.removeItem(strActiveProfile);
            strActiveProfile = "Default";
            jComboProfiles.addItem((Object) strActiveProfile);
            simpleIni.setGroup("GUI");
            simpleIni.setValue("servprofiles", strActiveProfile);
            simpleIni.setValue("activeprofile", strActiveProfile);

            // Check for old style profile and convert to new profile if neccessary
            simpleIni.setGroup("Network");
            String dbhost = simpleIni.getStringValue("dbhost");
            if (dbhost != null && !dbhost.isEmpty()) {
                loadPreferences();
                simpleIni.setGroup("Network");
                simpleIni.clearGroupItems();
            }
            simpleIni.removeGroup("Network");
            // Update new profile
            updatePreferences(null);
            simpleIni.save();
        }

        // Load preferences from active profile
        simpleIni.setGroup(SERVER + strActiveProfile);
        loadPreferences();
        if (simpleIni.isChanged()) {
            simpleIni.save();
        }

        // Disable delete button if profile Default selected
        jButtonDelete.setEnabled(!strActiveProfile.contentEquals("Default"));

    }

    private void loadPreferences() {
        CryptoLite cl = new CryptoLite();
        setDBHost(simpleIni.getStringValue("dbhost", "localhost"));
        setDBPort(simpleIni.getStringValue("dbport", "3306"));
        setDBMangos(simpleIni.getStringValue("mangosdb", "mangos"));
        setDBRealm(simpleIni.getStringValue("realmdb", "realmd"));
        setDBChar(simpleIni.getStringValue("chardb", "characters"));
        setDBScript(simpleIni.getStringValue("scriptdb", ""));
        setDBUser(simpleIni.getStringValue("dbusername", "mangos"));
        setRAHost(simpleIni.getStringValue("rahost", "localhost"));
        setRAPort(simpleIni.getStringValue("raport", "3443"));
        setRAUser(simpleIni.getStringValue("rausername", ""));
        setRemoteAccessEnabled(simpleIni.getBoolValue("raenabled", true));
        setSavePasswords(simpleIni.getBoolValue("savepasswords", true));
        if (simpleIni.getBoolValue("savepasswords")) {
            setDBPassword(cl.decryptPassword(simpleIni.getStringValue("dbpass", ""), getDBUser()));
            setRAPassword(cl.decryptPassword(simpleIni.getStringValue("rapass", ""), getRAUser()));
        } else {
            setDBPassword(simpleIni.getStringValue("dbpass", ""));
            setRAPassword(simpleIni.getStringValue("rapass", ""));
        }
    }

    /**
     * Update preferences storage object
     *
     * @param profile
     */
    public void updatePreferences(String profile) {

        if (profile == null) {
            profile = strActiveProfile;
        }
        simpleIni.setGroup(SERVER + profile);
        CryptoLite cl = new CryptoLite();
        simpleIni.setValue("dbhost", getDBHost());
        simpleIni.setValue("dbport", getDBPort());
        simpleIni.setValue("mangosdb", getDBMangos());
        simpleIni.setValue("realmdb", getDBRealm());
        simpleIni.setValue("chardb", getDBChar());
        simpleIni.setValue("scriptdb", getDBScript());
        simpleIni.setValue("dbusername", getDBUser());
        simpleIni.setValue("rahost", getRAHost());
        simpleIni.setValue("raport", getRAPort());
        simpleIni.setValue("rausername", getRAUser());
        simpleIni.setValue("raenabled", isRAEnabled());
        simpleIni.setValue("savepasswords", isSavePasswords());
        if (simpleIni.getBoolValue("savepasswords")) {
            simpleIni.setValue("rapass", cl.encryptPassword(getRAPass(), getRAUser()));
            simpleIni.setValue("dbpass", cl.encryptPassword(getDBPass(), getDBUser()));
        } else {
            simpleIni.setValue("rapass", "");
            simpleIni.setValue("dbpass", "");
            setDBPassword("");
            setRAPassword("");
        }
    }

    /**
     * Save profile, if "as" is true it performs a saveas instead
     *
     * @param as
     */
    private void save(boolean as) {

        String strProfile = strActiveProfile;

        if (as) {
            LineInputDialog lid = new LineInputDialog(parent, true);
            lid.setTitle(dh.getString("title_saveprofileas"));
            lid.setMessageLabel(dh.getString("lab_saveprofileas"));
            lid.setLocationRelativeTo(this);
            lid.setVisible(true);
            if (lid.getReturnStatus() == LineInputDialog.RET_CANCEL) {
                return;
            }
            strProfile = lid.getMessage();
            if (strProfile.isEmpty()) {
                return;
            }
        }

        // Write out new profile
        simpleIni.setGroup("GUI");
        String profiles = simpleIni.getStringValue("servprofiles", "None");
        if (!profiles.contains(strProfile)) {
            profiles += "," + strProfile;
        }
        simpleIni.setValue("servprofiles", profiles);
        simpleIni.setValue("activeprofile", strProfile);
        updatePreferences(strProfile);
        simpleIni.save();
        refreshProfilesCombo(true);
    }

    private void refreshProfilesCombo(boolean withevent) {
        jComboProfiles.setEnabled(withevent);
        simpleIni.setGroup("GUI");
        String activeProfile = simpleIni.getStringValue("activeprofile", "None");
        String profiles = simpleIni.getStringValue("servprofiles", "None");
        jComboProfiles.setModel(new DefaultComboBoxModel(patternComma.split(profiles)));
        jComboProfiles.setSelectedItem(activeProfile);
        jComboProfiles.setEnabled(true);
        // Disable delete button if profile Default selected
        jButtonDelete.setEnabled(!activeProfile.contentEquals("Default"));
        if (!withevent) {
            loadProfile();
        }
        eventUpdate(PROFILE_CHANGE);
    }

    private void deleteProfile() {

        // Issue a deletion warning
        dh.createWarn("title_deleteprofile", "info_deleteprofile");
        dh.appendInfoText(" " + (String) jComboProfiles.getSelectedItem());
        dh.setVisible(true);
        if (dh.getReturnStatus() == DialogHandler.CANCEL) {
            return;
        }

        // Begin profile deletion
        simpleIni.setGroup("GUI");
        String profiles = simpleIni.getStringValue("servprofiles", "None");
        profiles = profiles.replace("," + strActiveProfile, "");
        simpleIni.setValue("servprofiles", profiles);
        simpleIni.setValue("activeprofile", "Default");
        simpleIni.setGroup(SERVER + strActiveProfile);
        simpleIni.clearGroupItems();
        simpleIni.removeGroup(SERVER + strActiveProfile);
        simpleIni.save();
        refreshProfilesCombo(true);
    }

    @Override
    public void setLocationRelativeTo(Component c) {
        pack();
        super.setLocationRelativeTo(c);
    }

    /**
     * @return the return status of this dialog - one of RET_OK or RET_CANCEL
     */
    public int getReturnStatus() {
        return returnStatus;
    }

    public void setRemoteAccessEnabled(boolean enabled) {
        jCheckBoxEnableRA.setSelected(enabled);
    }

    public void setSavePasswords(boolean enabled) {
        jCheckBoxSavePass.setSelected(enabled);
    }

    public boolean isSavePasswords() {
        return jCheckBoxSavePass.isSelected();
    }

    public void setDBHost(String hostname) {
        jTextDBHost.setText(hostname);
    }

    public void setDBPort(String port) {
        jTextDBPort.setText(port);
    }

    public void setDBUser(String name) {
        jTextDBUser.setText(name);
    }

    public void setRAHost(String hostname) {
        jTextRAHost.setText(hostname);
    }

    public void setRAPort(String port) {
        jTextRAPort.setText(port);
    }

    public void setRAUser(String name) {
        jTextRAUser.setText(name);
    }

    public boolean isRAEnabled() {
        return jCheckBoxEnableRA.isSelected();
    }

    public String getActiveRAHost() {
        if (isRAEnabled()) {
            if (isRAConnected()) {
                return jTextRAHost.getText();
            } else {
                return "Not Connected";
            }
        } else {
            return "Disabled";
        }
    }

    public String getRAHost() {
        return jTextRAHost.getText();
    }

    public String getRAPort() {
        return jTextRAPort.getText();
    }

    public String getRAUser() {
        return jTextRAUser.getText();
    }

    public void setDBMangos(String dbname) {
        jTextDBMangos.setText(dbname);
    }

    public void setDBRealm(String dbname) {
        jTextDBRealm.setText(dbname);
    }

    public void setDBChar(String dbname) {
        jTextDBCharacters.setText(dbname);
    }

    public void setDBScript(String dbname) {
        jTextDBScriptdev.setText(dbname);
    }

    public String getDBMangos() {
        return jTextDBMangos.getText();
    }

    public String getDBRealm() {
        return jTextDBRealm.getText();
    }

    public String getDBChar() {
        return jTextDBCharacters.getText();
    }

    public String getDBScript() {
        return jTextDBScriptdev.getText();
    }

    public String getDBHost() {
        return jTextDBHost.getText();
    }

    public String getDBPort() {
        return jTextDBPort.getText();
    }

    public String getDBUser() {
        return jTextDBUser.getText();
    }

    public char[] getDBPass() {
        return jDBPassword.getPassword();
    }

    public char[] getRAPass() {
        return jRAPassword.getPassword();
    }

    public void setDBPassword(String pass) {
        jDBPassword.setText(pass);
    }

    public void setRAPassword(String pass) {
        jRAPassword.setText(pass);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanelMain = new javax.swing.JPanel();
        jPanelMySql = new javax.swing.JPanel();
        jLabelDBHostip = new javax.swing.JLabel();
        jLabelDBPort = new javax.swing.JLabel();
        jLabelDBUser = new javax.swing.JLabel();
        jLabelDBPass = new javax.swing.JLabel();
        jDBPassword = new javax.swing.JPasswordField();
        jTextDBUser = new javax.swing.JTextField();
        jTextDBPort = new javax.swing.JTextField();
        jTextDBHost = new javax.swing.JTextField();
        jLabel24 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel25 = new javax.swing.JLabel();
        jTextDBScriptdev = new javax.swing.JTextField();
        jTextDBCharacters = new javax.swing.JTextField();
        jTextDBRealm = new javax.swing.JTextField();
        jTextDBMangos = new javax.swing.JTextField();
        jPanelRA = new javax.swing.JPanel();
        jLabelRAHostip = new javax.swing.JLabel();
        jLabelRAPort = new javax.swing.JLabel();
        jLabelRAUser = new javax.swing.JLabel();
        jLabelRAPass = new javax.swing.JLabel();
        jRAPassword = new javax.swing.JPasswordField();
        jTextRAUser = new javax.swing.JTextField();
        jTextRAPort = new javax.swing.JTextField();
        jTextRAHost = new javax.swing.JTextField();
        jCheckBoxEnableRA = new javax.swing.JCheckBox();
        jCheckBoxSavePass = new javax.swing.JCheckBox();
        jButtConnect = new javax.swing.JButton();
        jButtCancel = new javax.swing.JButton();
        jPanelProfiles = new javax.swing.JPanel();
        jComboProfiles = new javax.swing.JComboBox();
        jLabel1 = new javax.swing.JLabel();
        jButtonDelete = new javax.swing.JButton();
        jButtonSaveAs1 = new javax.swing.JButton();
        jButtonSave = new javax.swing.JButton();

        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("lang/MessagesBundle"); // NOI18N
        setTitle(bundle.getString("title_connect")); // NOI18N
        setMinimumSize(new java.awt.Dimension(552, 200));
        setResizable(false);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                closeDialog(evt);
            }
        });

        jPanelMain.setFont(jPanelMain.getFont().deriveFont(jPanelMain.getFont().getStyle() | java.awt.Font.BOLD, jPanelMain.getFont().getSize()+3));

        jPanelMySql.setBorder(javax.swing.BorderFactory.createTitledBorder(null, bundle.getString("pan_dblogin"), javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, jPanelMain.getFont(), jPanelMain.getForeground())); // NOI18N
        jPanelMySql.setFont(jPanelMySql.getFont().deriveFont(jPanelMySql.getFont().getStyle() | java.awt.Font.BOLD, jPanelMySql.getFont().getSize()+3));

        jLabelDBHostip.setText(bundle.getString("lab_hostname")); // NOI18N

        jLabelDBPort.setText(bundle.getString("lab_port")); // NOI18N

        jLabelDBUser.setText(bundle.getString("lab_username")); // NOI18N

        jLabelDBPass.setText(bundle.getString("lab_password")); // NOI18N

        jDBPassword.setText("XXXXXXXXXXXXXXX");
        jDBPassword.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jDBPasswordconnectionActionPerformed(evt);
            }
        });

        jTextDBUser.setText("XXXXXXXXXXXXXXX");
        jTextDBUser.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jTextDBUserconnectionActionPerformed(evt);
            }
        });

        jTextDBPort.setText("XXXXXXXXXXXXXXX"); // NOI18N
        jTextDBPort.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jTextDBPortconnectionActionPerformed(evt);
            }
        });

        jTextDBHost.setText("XXXXXXXXXXXXXXX");
        jTextDBHost.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jTextDBHostconnectionActionPerformed(evt);
            }
        });

        jLabel24.setText(bundle.getString("lab_mangosdb")); // NOI18N

        jLabel4.setText(bundle.getString("lab_realmdb")); // NOI18N

        jLabel3.setText(bundle.getString("lab_charsdb")); // NOI18N

        jLabel25.setText(bundle.getString("lab_scriptdb")); // NOI18N

        jTextDBScriptdev.setText("XXXXXXXXXXXXXXX"); // NOI18N
        jTextDBScriptdev.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jTextDBScriptdevconnectionActionPerformed(evt);
            }
        });

        jTextDBCharacters.setText("XXXXXXXXXXXXXXX"); // NOI18N
        jTextDBCharacters.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jTextDBCharactersconnectionActionPerformed(evt);
            }
        });

        jTextDBRealm.setText("XXXXXXXXXXXXXXX"); // NOI18N
        jTextDBRealm.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jTextDBRealmconnectionActionPerformed(evt);
            }
        });

        jTextDBMangos.setText("XXXXXXXXXXXXXXX"); // NOI18N
        jTextDBMangos.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jTextDBMangosconnectionActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanelMySqlLayout = new javax.swing.GroupLayout(jPanelMySql);
        jPanelMySql.setLayout(jPanelMySqlLayout);
        jPanelMySqlLayout.setHorizontalGroup(
            jPanelMySqlLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelMySqlLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelMySqlLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabelDBHostip, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel24, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jTextDBHost)
                    .addComponent(jTextDBMangos))
                .addGap(18, 18, 18)
                .addGroup(jPanelMySqlLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabelDBPort, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jTextDBPort)
                    .addComponent(jTextDBRealm))
                .addGap(18, 18, 18)
                .addGroup(jPanelMySqlLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jLabelDBUser, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel3, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jTextDBUser, javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jTextDBCharacters, javax.swing.GroupLayout.Alignment.LEADING))
                .addGap(18, 18, 18)
                .addGroup(jPanelMySqlLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabelDBPass, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel25, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jDBPassword)
                    .addComponent(jTextDBScriptdev))
                .addContainerGap())
        );
        jPanelMySqlLayout.setVerticalGroup(
            jPanelMySqlLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelMySqlLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelMySqlLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(jLabelDBPass, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabelDBUser, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabelDBPort, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabelDBHostip, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelMySqlLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(jDBPassword)
                    .addComponent(jTextDBUser)
                    .addComponent(jTextDBPort)
                    .addComponent(jTextDBHost))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanelMySqlLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(jLabel25, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel24, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelMySqlLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(jTextDBScriptdev)
                    .addComponent(jTextDBCharacters)
                    .addComponent(jTextDBRealm)
                    .addComponent(jTextDBMangos))
                .addContainerGap())
        );

        jPanelRA.setBorder(javax.swing.BorderFactory.createTitledBorder(null, bundle.getString("pan_ralogin"), javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, jPanelMain.getFont(), jPanelMain.getForeground())); // NOI18N

        jLabelRAHostip.setText(bundle.getString("lab_hostname")); // NOI18N

        jLabelRAPort.setText(bundle.getString("lab_port")); // NOI18N

        jLabelRAUser.setText(bundle.getString("lab_username")); // NOI18N

        jLabelRAPass.setText(bundle.getString("lab_password")); // NOI18N

        jRAPassword.setText("xxxxxxxxxxxxxxx");
        jRAPassword.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jRAPasswordconnectionActionPerformed(evt);
            }
        });

        jTextRAUser.setText("XXXXXXXXXXXXXXX");
        jTextRAUser.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jTextRAUserconnectionActionPerformed(evt);
            }
        });

        jTextRAPort.setText("XXXXXXXXXXXXXXX"); // NOI18N
        jTextRAPort.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jTextRAPortconnectionActionPerformed(evt);
            }
        });

        jTextRAHost.setText("XXXXXXXXXXXXXXX");
        jTextRAHost.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jTextRAHostconnectionActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanelRALayout = new javax.swing.GroupLayout(jPanelRA);
        jPanelRA.setLayout(jPanelRALayout);
        jPanelRALayout.setHorizontalGroup(
            jPanelRALayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelRALayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelRALayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabelRAHostip, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jTextRAHost))
                .addGap(18, 18, 18)
                .addGroup(jPanelRALayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabelRAPort, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jTextRAPort))
                .addGap(18, 18, 18)
                .addGroup(jPanelRALayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabelRAUser, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jTextRAUser))
                .addGap(18, 18, 18)
                .addGroup(jPanelRALayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabelRAPass, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jRAPassword))
                .addContainerGap())
        );
        jPanelRALayout.setVerticalGroup(
            jPanelRALayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelRALayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelRALayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(jLabelRAPass, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabelRAUser, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabelRAPort, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabelRAHostip, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelRALayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(jRAPassword)
                    .addComponent(jTextRAUser)
                    .addComponent(jTextRAPort)
                    .addComponent(jTextRAHost))
                .addContainerGap())
        );

        jCheckBoxEnableRA.setSelected(true);
        jCheckBoxEnableRA.setText(bundle.getString("check_enableremote")); // NOI18N
        jCheckBoxEnableRA.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                jCheckBoxEnableRAItemStateChanged(evt);
            }
        });

        jCheckBoxSavePass.setSelected(true);
        jCheckBoxSavePass.setText(bundle.getString("check_savepass")); // NOI18N

        jButtConnect.setText(bundle.getString("butt_connect")); // NOI18N
        jButtConnect.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtConnectActionPerformed(evt);
            }
        });

        jButtCancel.setText(bundle.getString("butt_cancel")); // NOI18N
        jButtCancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtCancelActionPerformed(evt);
            }
        });

        jPanelProfiles.setBorder(javax.swing.BorderFactory.createTitledBorder(null, bundle.getString("pan_serverprofiles"), javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, jPanelMain.getFont(), jPanelMain.getForeground())); // NOI18N

        jComboProfiles.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                jComboProfilesItemStateChanged(evt);
            }
        });

        jLabel1.setText(bundle.getString("lab_activeprofile")); // NOI18N

        jButtonDelete.setText(bundle.getString("butt_deleteprofile")); // NOI18N
        jButtonDelete.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonDeleteActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanelProfilesLayout = new javax.swing.GroupLayout(jPanelProfiles);
        jPanelProfiles.setLayout(jPanelProfilesLayout);
        jPanelProfilesLayout.setHorizontalGroup(
            jPanelProfilesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelProfilesLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jComboProfiles, javax.swing.GroupLayout.PREFERRED_SIZE, 222, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(jButtonDelete)
                .addContainerGap())
        );
        jPanelProfilesLayout.setVerticalGroup(
            jPanelProfilesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelProfilesLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelProfilesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(jComboProfiles, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButtonDelete))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jButtonSaveAs1.setText(bundle.getString("butt_saveas")); // NOI18N
        jButtonSaveAs1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonSaveAs1ActionPerformed(evt);
            }
        });

        jButtonSave.setText(bundle.getString("butt_save")); // NOI18N
        jButtonSave.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonSaveActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanelMainLayout = new javax.swing.GroupLayout(jPanelMain);
        jPanelMain.setLayout(jPanelMainLayout);
        jPanelMainLayout.setHorizontalGroup(
            jPanelMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelMainLayout.createSequentialGroup()
                .addComponent(jButtonSaveAs1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButtonSave)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jButtCancel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jButtConnect))
            .addComponent(jPanelRA, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jPanelMySql, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jPanelProfiles, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelMainLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jCheckBoxEnableRA)
                .addGap(18, 18, 18)
                .addComponent(jCheckBoxSavePass)
                .addContainerGap())
        );
        jPanelMainLayout.setVerticalGroup(
            jPanelMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelMainLayout.createSequentialGroup()
                .addComponent(jPanelProfiles, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanelMySql, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanelRA, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanelMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jCheckBoxEnableRA, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jCheckBoxSavePass))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanelMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButtCancel)
                    .addComponent(jButtConnect)
                    .addComponent(jButtonSaveAs1)
                    .addComponent(jButtonSave)))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanelMain, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanelMain, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents
    private void jButtConnectActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtConnectActionPerformed
        doClose(RET_OK);
    }//GEN-LAST:event_jButtConnectActionPerformed

    private void jButtCancelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtCancelActionPerformed
        doClose(RET_CANCEL);
    }//GEN-LAST:event_jButtCancelActionPerformed

    /**
     * Closes the dialog
     */
    private void closeDialog(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_closeDialog
        doClose(RET_CANCEL);
    }//GEN-LAST:event_closeDialog

    private void jTextRAPortconnectionActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextRAPortconnectionActionPerformed
        doClose(RET_OK);
    }//GEN-LAST:event_jTextRAPortconnectionActionPerformed

    private void jTextRAHostconnectionActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextRAHostconnectionActionPerformed
        doClose(RET_OK);
    }//GEN-LAST:event_jTextRAHostconnectionActionPerformed

    private void jTextRAUserconnectionActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextRAUserconnectionActionPerformed
        doClose(RET_OK);
    }//GEN-LAST:event_jTextRAUserconnectionActionPerformed

    private void jRAPasswordconnectionActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jRAPasswordconnectionActionPerformed
        doClose(RET_OK);
    }//GEN-LAST:event_jRAPasswordconnectionActionPerformed

    private void jTextDBMangosconnectionActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextDBMangosconnectionActionPerformed
        doClose(RET_OK);
    }//GEN-LAST:event_jTextDBMangosconnectionActionPerformed

    private void jTextDBHostconnectionActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextDBHostconnectionActionPerformed
        doClose(RET_OK);
    }//GEN-LAST:event_jTextDBHostconnectionActionPerformed

    private void jTextDBPortconnectionActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextDBPortconnectionActionPerformed
        doClose(RET_OK);
    }//GEN-LAST:event_jTextDBPortconnectionActionPerformed

    private void jTextDBRealmconnectionActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextDBRealmconnectionActionPerformed
        doClose(RET_OK);
    }//GEN-LAST:event_jTextDBRealmconnectionActionPerformed

    private void jTextDBCharactersconnectionActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextDBCharactersconnectionActionPerformed
        doClose(RET_OK);
    }//GEN-LAST:event_jTextDBCharactersconnectionActionPerformed

    private void jTextDBUserconnectionActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextDBUserconnectionActionPerformed
        doClose(RET_OK);
    }//GEN-LAST:event_jTextDBUserconnectionActionPerformed

    private void jDBPasswordconnectionActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jDBPasswordconnectionActionPerformed
        doClose(RET_OK);
    }//GEN-LAST:event_jDBPasswordconnectionActionPerformed

    private void jTextDBScriptdevconnectionActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextDBScriptdevconnectionActionPerformed
        doClose(RET_OK);
    }//GEN-LAST:event_jTextDBScriptdevconnectionActionPerformed

    private void jCheckBoxEnableRAItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_jCheckBoxEnableRAItemStateChanged
        jPanelRA.setVisible(jCheckBoxEnableRA.isSelected());
        pack();
    }//GEN-LAST:event_jCheckBoxEnableRAItemStateChanged

    private void jButtonSaveAs1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonSaveAs1ActionPerformed
        save(true);
    }//GEN-LAST:event_jButtonSaveAs1ActionPerformed

    private void jComboProfilesItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_jComboProfilesItemStateChanged
        if (jComboProfiles.isEnabled()) {
            loadProfile();
        }
    }//GEN-LAST:event_jComboProfilesItemStateChanged

    private void jButtonDeleteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonDeleteActionPerformed
        deleteProfile();
    }//GEN-LAST:event_jButtonDeleteActionPerformed

    private void jButtonSaveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonSaveActionPerformed
        save(false);
    }//GEN-LAST:event_jButtonSaveActionPerformed

    private void doClose(int retStatus) {
        returnStatus = retStatus;
        setVisible(false);
        dispose();
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButtCancel;
    private javax.swing.JButton jButtConnect;
    private javax.swing.JButton jButtonDelete;
    private javax.swing.JButton jButtonSave;
    private javax.swing.JButton jButtonSaveAs1;
    private javax.swing.JCheckBox jCheckBoxEnableRA;
    private javax.swing.JCheckBox jCheckBoxSavePass;
    private javax.swing.JComboBox jComboProfiles;
    private javax.swing.JPasswordField jDBPassword;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel24;
    private javax.swing.JLabel jLabel25;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabelDBHostip;
    private javax.swing.JLabel jLabelDBPass;
    private javax.swing.JLabel jLabelDBPort;
    private javax.swing.JLabel jLabelDBUser;
    private javax.swing.JLabel jLabelRAHostip;
    private javax.swing.JLabel jLabelRAPass;
    private javax.swing.JLabel jLabelRAPort;
    private javax.swing.JLabel jLabelRAUser;
    private javax.swing.JPanel jPanelMain;
    private javax.swing.JPanel jPanelMySql;
    private javax.swing.JPanel jPanelProfiles;
    private javax.swing.JPanel jPanelRA;
    private javax.swing.JPasswordField jRAPassword;
    private javax.swing.JTextField jTextDBCharacters;
    private javax.swing.JTextField jTextDBHost;
    private javax.swing.JTextField jTextDBMangos;
    private javax.swing.JTextField jTextDBPort;
    private javax.swing.JTextField jTextDBRealm;
    private javax.swing.JTextField jTextDBScriptdev;
    private javax.swing.JTextField jTextDBUser;
    private javax.swing.JTextField jTextRAHost;
    private javax.swing.JTextField jTextRAPort;
    private javax.swing.JTextField jTextRAUser;
    // End of variables declaration//GEN-END:variables
    private int returnStatus = RET_CANCEL;
}
