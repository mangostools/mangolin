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

import lib.GlobalFunctions;
import lib.Localisation;
import lib.SimpleINI;
import lib.AboutDialog;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;

/**
 *
 * @author Alistair Neil, <info@dazzleships.net>
 */
public class MaNGOLinUI extends javax.swing.JFrame {

    private final GlobalFunctions gf = GlobalFunctions.getInstance();
    private static final Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private boolean boolDBConnected = false;
    private SimpleINI simpleIni = null;
    private ConnectionHandler connHandler = null;
    private Timer mainTimer = null;
    private Clipboard clipboard = null;
    private final DialogHandler dh;
    private final JFrame pFrame = this;
    private FileManagementPanel fmPanel;
    private ServerRealmPanel srPanel;
    private AccountsPanel accPanel;
    private BannedPanel banPanel;
    private PortalManager portalManager;

    /**
     * Creates new form MaNGOLin
     *
     * @param args
     */
    public MaNGOLinUI(String args[]) {

        // Application settings storage object
        simpleIni = new SimpleINI(gf.getAppSettingsPath(), "mangolin.ini");

        // Create our dialog handler
        dh = new DialogHandler(this, new Localisation("lang.MessagesBundle"));
        dh.setTitle(gf.getAppName() + " " + dh.getString("title_notification"));

        initComponents();
        initOtherComponents();
        initTimer();
        parseArgs(args);
    }

    /*
     // Creates test accounts and characters, used for testing & debugging only
     // Requires character dump files name char0.pdm through to char9.pdm to be in the server /bin folder
     public void createTestAccounts(int noofaccts, int noofchars) {


     String accountname;
     if (noofchars > 10) {
     noofchars = 10;
     }
     for (int x = 0; x < noofaccts; x++) {
     Random r1 = new Random();
     accountname = "TestAcct" + String.valueOf(x);
     connHandler.getRAConn().createAccount(accountname, "test");
     for (int i = 0; i < noofchars; i++) {
     connHandler.getRAConn().pDumpLoad("char" + String.valueOf(r1.nextInt(10)) + ".pdm", accountname, getRandomName());
     }
     }
     }

     // Rough and ready 12 char max random name generator 
     public String getRandomName() {
     Random r1 = new Random();
     int lengths[] = {8, 9, 10, 11};
     String[] letters = new String[]{"a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z"
     };
     StringBuilder sb = new StringBuilder();

     for (int i = 0; i < 12; i++) {
     sb.append(letters[r1.nextInt(letters.length - 1)]);
     }
     return sb.toString().substring(0, lengths[r1.nextInt(lengths.length - 1)]);
     }
     **/
    private void parseArgs(String... args) {
        logger.setLevel(Level.WARNING);
        String a = null;
        for (String s : args) {
            if (s.contentEquals("debugon")) {
                logger.setLevel(Level.ALL);
                jMenuItemDebugLog.setVisible(true);
            }
        }
    }

    private void initOtherComponents() {

        // Create our server realm panel
        srPanel = new ServerRealmPanel();
        srPanel.setDialogHandler(dh);
        jTabManagement.add("Server & Realm Management", srPanel);

        // Create our accounts panel
        accPanel = new AccountsPanel(clipboard);
        accPanel.setDialogHandler(dh);
        jTabManagement.add("Accounts Management", accPanel);

        // Create our banned panel
        banPanel = new BannedPanel();
        banPanel.setDialogHandler(dh);
        jTabManagement.add("Banned Accounts", banPanel);

        // Create our file management panel
        fmPanel = new FileManagementPanel() {
            @Override
            // Called when a file restore is completed
            public void restoreCompleted(String strFilename, String strDBName) {
                // Update various tables affected by our file restore 
                if (strDBName.contains(connHandler.getActiveSQL().getMangosDBName())) {
                    if (portalManager != null) {
                        portalManager.updatePreDefDestTable();
                    }
                    accPanel.updateAccountTable(0);
                }
                if (strDBName.contains(connHandler.getActiveSQL().getRealmDBName())) {
                    srPanel.updateRealmTable(0);
                    accPanel.updateAccountTable(0);
                    banPanel.updateBanTables(0);
                }
                if (strDBName.contains(connHandler.getActiveSQL().getCharDBName())) {
                    accPanel.updateCharTable(0, 0);
                }
                if (strFilename.contains("portals")) {
                    if (portalManager != null) {
                        portalManager.updateActivePortalsTable();
                        portalManager.updateUserDestTable();
                    }
                }
            }
        };
        fmPanel.setDialogHandler(dh);
        jTabManagement.add("File Management", fmPanel);

        // Create our connection dialog
        connHandler = new ConnectionHandler(this, true, simpleIni) {
            @Override
            public void eventUpdate(int evt) {
                switch (evt) {
                    case ConnectionHandler.RA_LOST:
                        setServerMenusEnabled(true);
                        accPanel.setAccountMenusEnabled(true);
                        accPanel.setCharMenusEnabled(true);
                        banPanel.setActiveBanMenusEnabled(true);
                        closePortalManager();
                        dh.createError("title_remote_lost", "info_remote_lost");
                        dh.setVisible(true);
                        break;

                    case ConnectionHandler.PROFILE_CHANGE:
                        createQuickMenus(getProfileNames());
                        break;

                    case ConnectionHandler.QC_STATUS_CHANGE:
                        if (connHandler != null) {
                            jMenuQuickConn.setEnabled(getQuickConnectEnabled());
                        }
                        break;
                }

            }
        };
        connHandler.setDialogHandler(dh);

        // Get preferences
        fmPanel.loadPreferences(simpleIni);

        // Initialise system tray
        initSystemTray();

        // Get system clipboard
        clipboard = getToolkit().getSystemClipboard();

        setTabEnabled(-1, false);
        setServerMenusEnabled(false);
        setLocationRelativeTo(this);

        // Set app icon and title
        setIconImage(gf.getAppIconImage());
        updateTitle("");
        jMenuItemDebugLog.setVisible(false);
    }

    private void createQuickMenus(String profiles[]) {

        jMenuQuickConn.removeAll();
        JMenuItem jmi;
        for (final String s : profiles) {
            jmi = new JMenuItem();
            jmi.setText(s);
            jmi.addActionListener(new java.awt.event.ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    String profile = ((JMenuItem) e.getSource()).getText();
                    connHandler.setProfile(profile);
                    connectAction(profile);
                    setQuickConnItemEnabled(profile, false);
                }
            });
            jMenuQuickConn.add(jmi);
        }
    }

    private void setQuickConnItemEnabled(String item, boolean enabled) {

        JMenuItem jmi;
        for (int i = 0; i < jMenuQuickConn.getItemCount(); i++) {
            jmi = jMenuQuickConn.getItem(i);
            if (item == null) {
                jmi.setEnabled(enabled);
                continue;
            }
            if (jmi.getText().contentEquals(item)) {
                jmi.setEnabled(enabled);
            }
        }

    }

    private void updateTitle(String additional) {
        this.setTitle(gf.getAppName() + " " + gf.getAppVersion() + additional);

    }

    private void initSystemTray() {
        simpleIni.setGroup("GUI");
        if (simpleIni.getBoolValue("disabletray", false)) {
            gf.unloadSystemTray();
        } else {
            gf.loadSystemTray(pFrame, popupTray, null, null);
        }
    }

    private void updateGUIVisibility() {
        // Bring up gui
        setVisible(false);
        if (simpleIni.getBoolValue("hidetray", false)) {
            gf.setFrameMinimised(pFrame);
            if (gf.isTrayLoaded()) {
                gf.setFrameVisible(pFrame, false);
            }
        } else {
            gf.setFrameVisible(pFrame, true);
        }
    }

    private void initTimer() {

        mainTimer = new Timer(1000, new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                if (mainTimer.getInitialDelay() == 0) {
                    mainTimer.stop();
                    mainTimer.setInitialDelay(1000);
                    // Initital Startup visibility   
                    updateGUIVisibility();
                    // Check web for updates
                    checkForAppUpdates();
                    return;
                }

                // Provide timeing to panels
                srPanel.updateTick();
                accPanel.updateTick();
                banPanel.updateTick();
                fmPanel.updateTick();

            }
        });
        mainTimer.setInitialDelay(0);
        mainTimer.start();
    }

    private void checkForAppUpdates() {
        if (simpleIni.getBoolValue("checkupdate", true)) {
            String version = gf.checkWebForAppUpdates(gf.getHomepage() + "/anapps/mangolin_version.html");
            if (version != null) {
                dh.createInfo("title_update");
                dh.appendInfoText(gf.getAppName() + " "
                        + version + " "
                        + dh.getString("info_update"));
                dh.setVisible(true);
            }
        }
    }

    private void setTabEnabled(int tabindex, boolean enabled) {

        jTabManagement.setEnabled(enabled);
        if (tabindex < 0) {
            for (int i = 0; i < jTabManagement.getTabCount(); i++) {
                jTabManagement.setEnabledAt(i, enabled);
            }
            if (!enabled) {
                jTabManagement.setSelectedIndex(0);
            }
        } else {
            jTabManagement.setEnabledAt(tabindex, enabled);
        }
    }

    private void openPreferences() {
        PrefsDialog prefsDialog = new PrefsDialog(this, simpleIni);
        prefsDialog.setTitle(gf.getAppName() + " " + dh.getString("title_prefs"));
        prefsDialog.setTrayEnabled(gf.isTraySupported());
        prefsDialog.setLocationRelativeTo(this);
        prefsDialog.setVisible(true);

        if (!simpleIni.isChanged()) {
            return;
        }

        ArrayList<String> result = simpleIni.getChangeList();
        if (result.contains("disabletray")) {
            if (simpleIni.getBoolValue("disabletray", false)) {
                gf.unloadSystemTray();
            } else {
                gf.loadSystemTray(pFrame, popupTray, null, null);
            }
        }

        if (result.contains("fontsize")) {
            gf.setUIFontSize(simpleIni.getIntegerValue("fontsize"), 80);
            SwingUtilities.updateComponentTreeUI(this);
            this.pack();
            SwingUtilities.updateComponentTreeUI(connHandler);
            connHandler.pack();
            SwingUtilities.updateComponentTreeUI(dh);
            dh.pack();
            if (portalManager != null) {
                SwingUtilities.updateComponentTreeUI(portalManager);
                portalManager.pack();
            }
        }

        simpleIni.save();

        if (result.contains("theme") || result.contains("windowdec")) {
            dh.createWarn("title_restart", "info_restart");
            dh.setVisible(true);
            if (dh.getReturnStatus() == DialogHandler.OK) {
                quitGUI(true);
            }

        }
    }

    private void connectAction(String profile) {

        if (profile == null) {
            // Test to see if already connected
            if (boolDBConnected) {
                // Disconnect
                boolDBConnected = serverDisconnect();
                return;
            }
            // Pop up our connection dialog
            connHandler.setConnectButtonVisible(true);
            connHandler.setCancelButtonText(dh.getString("butt_cancel"));
            connHandler.setLocationRelativeTo(this);
            connHandler.setVisible(true);
            if (connHandler.getReturnStatus() == ConnectionHandler.RET_CANCEL) {
                return;
            }
        } else {
            // Test to see if already connected
            if (boolDBConnected) {
                // Disconnect
                boolDBConnected = serverDisconnect();
            }
            connHandler.setProfile(profile);
        }

        // Set cursor to busy
        setCursor(new Cursor(Cursor.WAIT_CURSOR));
        boolDBConnected = connHandler.dbConnect();
        if (!boolDBConnected) {
            dh.raiseMySqlError(connHandler.getDBConnectionMessage());
        }

        // Carry out post DB connect initialisation
        if (boolDBConnected) {
            connHandler.getActiveSQL().createMangosStatement("mangos");
            connHandler.getActiveSQL().createMangosStatement("commands");
            setQuickConnItemEnabled(connHandler.getActiveProfile(), false);

            // Attempt to connect to mangos server
            connHandler.getRAConnection().setLogTextOutput(srPanel.getLogOutputArea());
            String result = connHandler.raConnect();
            if (connHandler.getRAStatus() == ConnectionHandler.RA_ERROR) {
                dh.raiseRemoteAccessError(result);
            }

            // Update main form title
            updateTitle(", " + dh.getString("lab_activeprofile") + " "
                    + connHandler.getActiveProfile()
                    + ", " + dh.getString("title_dbhost") + " : "
                    + connHandler.getDBHost()
                    + ", " + dh.getString("title_rahost") + " : "
                    + connHandler.getActiveRAHost());

            // Save our prefs on a successful connection
            fmPanel.updatePreferences();
            connHandler.updatePreferences(null);
            simpleIni.save();

            srPanel.setConnection(connHandler);
            srPanel.updateRealmTable(0);

            portalManager = new PortalManager(connHandler, dh) {
                @Override
                public void portalsSaved() {
                    fmPanel.updateFileTable();
                }
            };
            portalManager.setIconImage(gf.getAppIconImage());

            accPanel.setConnection(connHandler);
            accPanel.updateAccountTable(0);
            accPanel.updateCharTable(0, 0);
            accPanel.setPortalManager(portalManager);

            banPanel.setConnection(connHandler);
            banPanel.updateBanTables(0);

            fmPanel.setConnection(connHandler);
            fmPanel.setTableRowHeight(srPanel.getTableRowHeight());
            fmPanel.updateFileTable();
            fmPanel.setFileTableEnabled(true);

            setTabEnabled(-1, true);
            setServerMenusEnabled(true);
            jMenuItemConnect.setText(dh.getString("butt_disconnect"));
            jMenuItemConnect.setToolTipText(dh.getString("tool_disconnect"));
            mainTimer.start();
        }
        // Restore cursor to normal
        setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
    }

    /**
     * Disconnect from servers
     */
    private boolean serverDisconnect() {
        mainTimer.stop();
        closePortalManager();
        connHandler.disconnect();

        setTabEnabled(-1, false);
        jMenuItemConnect.setText(dh.getString("butt_connect"));
        jMenuItemConnect.setToolTipText(dh.getString("tool_connect"));
        setQuickConnItemEnabled(null, true);

        // Reset our tables
        srPanel.resetPanel();
        accPanel.updateAccountTable(-1);
        accPanel.updateCharTable(-1, 0);
        banPanel.updateBanTables(-1);
        setServerMenusEnabled(false);
        updateTitle("");
        return false;
    }

    private void refreshAllTables() {
        srPanel.updateRealmTable(0);
        accPanel.updateCharTable(0, 0);
        banPanel.updateBanTables(0);
        accPanel.updateAccountTable(0);
        fmPanel.updateFileTable();
        fmPanel.setFileTableEnabled(true);
    }

    private void setServerMenusEnabled(boolean enabled) {

        // Database menu item
        jMenuItemOptimise.setEnabled(enabled);
        jMenuItemCommandsRef.setEnabled(enabled);
        jMenuItemTableRefresh.setEnabled(enabled);

        // Remote access menu items
        enabled &= connHandler.isRAConnected();
        jMenuShutdown.setEnabled(enabled);
        jMenuItemServerInfo.setEnabled(enabled);
        jMenuItemMassMail.setEnabled(enabled);
        jMenuItemMassItems.setEnabled(enabled);
        jMenuItemMOTD.setEnabled(enabled);
        jMenuItemLimits.setEnabled(enabled);
        jMenuItemSaveall.setEnabled(enabled);
        jMenuItemClearCorpses.setEnabled(enabled);
        jMenuItemAnnounce.setEnabled(enabled);
        jMenuItemNotify.setEnabled(enabled);
        jMenuItemReload.setEnabled(enabled);
    }

    private void openAbout() {
        AboutDialog ad = new AboutDialog(this, true);
        ad.setHomeURL(gf.getHomepageSSL());
        ad.setForumsURL(gf.getHomepageSSL() + "/forums");
        ad.setContactURL(gf.getHomepageSSL() + "/contactus");
        ad.setTitle(dh.getString("help_about") + " " + gf.getAppName());
        ad.setAppDescription(dh.getString("appdesc")
                + "\n\n" + gf.getAppVersion() + "\n\nCopyright\nAlistair Neil");
        ad.setAppLogo("/resources/logo.png");
        ad.setLocationRelativeTo(this);
        ad.setVisible(true);
    }

    private void closePortalManager() {
        if (portalManager != null) {
            portalManager.dispose();
            portalManager = null;
        }
    }

    /**
     * Quit UI or Request restart if restart is true
     *
     * @param restart
     */
    private void quitGUI(boolean restart) {
        if (boolDBConnected) {
            serverDisconnect();
        }
        fmPanel.updatePreferences();
        if (simpleIni.isChanged()) {
            simpleIni.save();
        }
        gf.unloadSystemTray();
        exitRequested(restart);
    }

    /**
     * Called when UI wants to close or restart, will be overriden by parent
     * class
     *
     * @param restart
     */
    public void exitRequested(boolean restart) {
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        popupTray = new java.awt.PopupMenu();
        menuItemGUI = new java.awt.MenuItem();
        menuItemQuit = new java.awt.MenuItem();
        jPanelLogging = new javax.swing.JPanel();
        jScrollPane9 = new javax.swing.JScrollPane();
        jTextAreaLogging = new javax.swing.JTextArea();
        jPanelMain = new javax.swing.JPanel();
        jTabManagement = new javax.swing.JTabbedPane();
        jMenuBarMain = new javax.swing.JMenuBar();
        jMenu = new javax.swing.JMenu();
        jMenuItemPrefs = new javax.swing.JMenuItem();
        jMenuItemTableRefresh = new javax.swing.JMenuItem();
        jMenuItemQuit = new javax.swing.JMenuItem();
        jMenuServer = new javax.swing.JMenu();
        jMenuQuickConn = new javax.swing.JMenu();
        jMenuItemConnect = new javax.swing.JMenuItem();
        jMenuItemEditProfiles = new javax.swing.JMenuItem();
        jMenuItemServerInfo = new javax.swing.JMenuItem();
        jMenuItemMassMail = new javax.swing.JMenuItem();
        jMenuItemMassItems = new javax.swing.JMenuItem();
        jMenuItemMOTD = new javax.swing.JMenuItem();
        jMenuItemLimits = new javax.swing.JMenuItem();
        jMenuItemSaveall = new javax.swing.JMenuItem();
        jMenuItemClearCorpses = new javax.swing.JMenuItem();
        jMenuItemAnnounce = new javax.swing.JMenuItem();
        jMenuItemNotify = new javax.swing.JMenuItem();
        jMenuItemReload = new javax.swing.JMenuItem();
        jMenuItemOptimise = new javax.swing.JMenuItem();
        jMenuShutdown = new javax.swing.JMenu();
        jMenuItemShutImm = new javax.swing.JMenuItem();
        jMenuItemShutDelay = new javax.swing.JMenuItem();
        jMenuItemShutIdle = new javax.swing.JMenuItem();
        jMenuHelp = new javax.swing.JMenu();
        jMenuItemHelp = new javax.swing.JMenuItem();
        jMenuItemCommandsRef = new javax.swing.JMenuItem();
        jMenuItemDebugLog = new javax.swing.JMenuItem();
        jMenuItemAbout = new javax.swing.JMenuItem();

        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("lang/MessagesBundle"); // NOI18N
        menuItemGUI.setLabel(bundle.getString("mitem_guivisibility")); // NOI18N
        menuItemGUI.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuItemGUIActionPerformed(evt);
            }
        });
        popupTray.add(menuItemGUI);

        menuItemQuit.setActionCommand("QuitGUI");
        menuItemQuit.setLabel(bundle.getString("mitem_quit")); // NOI18N
        menuItemQuit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                menuItemQuitActionPerformed(evt);
            }
        });
        popupTray.add(menuItemQuit);

        jTextAreaLogging.setColumns(20);
        jTextAreaLogging.setEditable(false);
        jTextAreaLogging.setRows(5);
        jScrollPane9.setViewportView(jTextAreaLogging);

        javax.swing.GroupLayout jPanelLoggingLayout = new javax.swing.GroupLayout(jPanelLogging);
        jPanelLogging.setLayout(jPanelLoggingLayout);
        jPanelLoggingLayout.setHorizontalGroup(
            jPanelLoggingLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelLoggingLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane9, javax.swing.GroupLayout.DEFAULT_SIZE, 638, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanelLoggingLayout.setVerticalGroup(
            jPanelLoggingLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelLoggingLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane9, javax.swing.GroupLayout.DEFAULT_SIZE, 550, Short.MAX_VALUE)
                .addContainerGap())
        );

        setTitle("MaNGOLin");
        setBackground(new java.awt.Color(255, 255, 255));
        setMinimumSize(new java.awt.Dimension(1000, 700));
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });
        getContentPane().setLayout(new javax.swing.BoxLayout(getContentPane(), javax.swing.BoxLayout.LINE_AXIS));

        jPanelMain.setFont(jPanelMain.getFont().deriveFont(jPanelMain.getFont().getStyle() | java.awt.Font.BOLD, jPanelMain.getFont().getSize()+3));
        jPanelMain.setPreferredSize(new java.awt.Dimension(1010, 740));

        jTabManagement.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                jTabManagementStateChanged(evt);
            }
        });

        javax.swing.GroupLayout jPanelMainLayout = new javax.swing.GroupLayout(jPanelMain);
        jPanelMain.setLayout(jPanelMainLayout);
        jPanelMainLayout.setHorizontalGroup(
            jPanelMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelMainLayout.createSequentialGroup()
                .addComponent(jTabManagement)
                .addGap(0, 0, 0))
        );
        jPanelMainLayout.setVerticalGroup(
            jPanelMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabManagement, javax.swing.GroupLayout.DEFAULT_SIZE, 274, Short.MAX_VALUE)
        );

        getContentPane().add(jPanelMain);

        jMenu.setText(bundle.getString("menu_menu")); // NOI18N

        jMenuItemPrefs.setText(bundle.getString("menu_prefs")); // NOI18N
        jMenuItemPrefs.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemPrefsActionPerformed(evt);
            }
        });
        jMenu.add(jMenuItemPrefs);

        jMenuItemTableRefresh.setText(bundle.getString("mitem_refreshtables")); // NOI18N
        jMenuItemTableRefresh.setActionCommand(bundle.getString("mitem_refreshtables")); // NOI18N
        jMenuItemTableRefresh.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemTableRefreshActionPerformed(evt);
            }
        });
        jMenu.add(jMenuItemTableRefresh);

        jMenuItemQuit.setText(bundle.getString("mitem_exit")); // NOI18N
        jMenuItemQuit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemQuitActionPerformed(evt);
            }
        });
        jMenu.add(jMenuItemQuit);

        jMenuBarMain.add(jMenu);

        jMenuServer.setText(bundle.getString("menu_server")); // NOI18N

        jMenuQuickConn.setText(bundle.getString("menu_quickconn")); // NOI18N
        jMenuServer.add(jMenuQuickConn);

        jMenuItemConnect.setText(bundle.getString("server_connect")); // NOI18N
        jMenuItemConnect.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemConnectActionPerformed(evt);
            }
        });
        jMenuServer.add(jMenuItemConnect);

        jMenuItemEditProfiles.setText(bundle.getString("mitem_editprofiles")); // NOI18N
        jMenuItemEditProfiles.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemEditProfilesActionPerformed(evt);
            }
        });
        jMenuServer.add(jMenuItemEditProfiles);

        jMenuItemServerInfo.setText(bundle.getString("getserver_info")); // NOI18N
        jMenuItemServerInfo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemServerInfoActionPerformed(evt);
            }
        });
        jMenuServer.add(jMenuItemServerInfo);

        jMenuItemMassMail.setText(bundle.getString("sendmass_mail")); // NOI18N
        jMenuItemMassMail.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemMassMailActionPerformed(evt);
            }
        });
        jMenuServer.add(jMenuItemMassMail);

        jMenuItemMassItems.setText(bundle.getString("sendmass_items")); // NOI18N
        jMenuItemMassItems.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemMassItemsActionPerformed(evt);
            }
        });
        jMenuServer.add(jMenuItemMassItems);

        jMenuItemMOTD.setText(bundle.getString("server_setmotd")); // NOI18N
        jMenuItemMOTD.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemMOTDActionPerformed(evt);
            }
        });
        jMenuServer.add(jMenuItemMOTD);

        jMenuItemLimits.setText(bundle.getString("server_setplimits")); // NOI18N
        jMenuItemLimits.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemLimitsActionPerformed(evt);
            }
        });
        jMenuServer.add(jMenuItemLimits);

        jMenuItemSaveall.setText(bundle.getString("server_saveall")); // NOI18N
        jMenuItemSaveall.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemSaveallActionPerformed(evt);
            }
        });
        jMenuServer.add(jMenuItemSaveall);

        jMenuItemClearCorpses.setText(bundle.getString("server_clearcorpses")); // NOI18N
        jMenuItemClearCorpses.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemClearCorpsesActionPerformed(evt);
            }
        });
        jMenuServer.add(jMenuItemClearCorpses);

        jMenuItemAnnounce.setText(bundle.getString("server_announce")); // NOI18N
        jMenuItemAnnounce.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemAnnounceActionPerformed(evt);
            }
        });
        jMenuServer.add(jMenuItemAnnounce);

        jMenuItemNotify.setText(bundle.getString("server_notify")); // NOI18N
        jMenuItemNotify.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemNotifyActionPerformed(evt);
            }
        });
        jMenuServer.add(jMenuItemNotify);

        jMenuItemReload.setText(bundle.getString("server_reloadtable")); // NOI18N
        jMenuItemReload.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemReloadActionPerformed(evt);
            }
        });
        jMenuServer.add(jMenuItemReload);

        jMenuItemOptimise.setText(bundle.getString("title_optimisedb")); // NOI18N
        jMenuItemOptimise.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemOptimiseActionPerformed(evt);
            }
        });
        jMenuServer.add(jMenuItemOptimise);

        jMenuShutdown.setText(bundle.getString("server_shutdown")); // NOI18N

        jMenuItemShutImm.setText(bundle.getString("shutdown_imm")); // NOI18N
        jMenuItemShutImm.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemShutImmActionPerformed(evt);
            }
        });
        jMenuShutdown.add(jMenuItemShutImm);

        jMenuItemShutDelay.setText(bundle.getString("shutdown_del")); // NOI18N
        jMenuItemShutDelay.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemShutDelayActionPerformed(evt);
            }
        });
        jMenuShutdown.add(jMenuItemShutDelay);

        jMenuItemShutIdle.setText(bundle.getString("shutdown_idle")); // NOI18N
        jMenuItemShutIdle.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemShutIdleActionPerformed(evt);
            }
        });
        jMenuShutdown.add(jMenuItemShutIdle);

        jMenuServer.add(jMenuShutdown);

        jMenuBarMain.add(jMenuServer);

        jMenuHelp.setText(bundle.getString("menu_help")); // NOI18N

        jMenuItemHelp.setText(bundle.getString("help_contents")); // NOI18N
        jMenuItemHelp.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemHelpActionPerformed(evt);
            }
        });
        jMenuHelp.add(jMenuItemHelp);

        jMenuItemCommandsRef.setText(bundle.getString("help_ref")); // NOI18N
        jMenuItemCommandsRef.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemCommandsRefActionPerformed(evt);
            }
        });
        jMenuHelp.add(jMenuItemCommandsRef);

        jMenuItemDebugLog.setText(bundle.getString("help_debug")); // NOI18N
        jMenuItemDebugLog.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemDebugLogActionPerformed(evt);
            }
        });
        jMenuHelp.add(jMenuItemDebugLog);

        jMenuItemAbout.setText(bundle.getString("help_about")); // NOI18N
        jMenuItemAbout.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemAboutActionPerformed(evt);
            }
        });
        jMenuHelp.add(jMenuItemAbout);

        jMenuBarMain.add(jMenuHelp);

        setJMenuBar(jMenuBarMain);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        if (!gf.isTrayLoaded()) {
            quitGUI(false);
        }
    }//GEN-LAST:event_formWindowClosing

    private void jMenuItemHelpActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemHelpActionPerformed
        gf.launchBrowser(gf.getHomepageSSL() + "/mangolin-help");
    }//GEN-LAST:event_jMenuItemHelpActionPerformed
    private void jMenuItemQuitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemQuitActionPerformed
        quitGUI(false);
    }//GEN-LAST:event_jMenuItemQuitActionPerformed

    private void jMenuItemConnectActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemConnectActionPerformed
        connectAction(null);
    }//GEN-LAST:event_jMenuItemConnectActionPerformed

    private void jMenuItemTableRefreshActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemTableRefreshActionPerformed
        refreshAllTables();
    }//GEN-LAST:event_jMenuItemTableRefreshActionPerformed

    private void jMenuItemCommandsRefActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemCommandsRefActionPerformed
        CommandsFrame cf = new CommandsFrame(connHandler.getActiveSQL());
        cf.setIconImage(gf.getAppIconImage());
        cf.setLocationRelativeTo(this);
        cf.setVisible(true);
    }//GEN-LAST:event_jMenuItemCommandsRefActionPerformed

    private void jMenuItemAboutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemAboutActionPerformed
        openAbout();
    }//GEN-LAST:event_jMenuItemAboutActionPerformed

    private void jMenuItemMOTDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemMOTDActionPerformed
        srPanel.setMotd();
    }//GEN-LAST:event_jMenuItemMOTDActionPerformed
    private void jMenuItemAnnounceActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemAnnounceActionPerformed
        srPanel.systemAnnounce();
    }//GEN-LAST:event_jMenuItemAnnounceActionPerformed

    private void jMenuItemLimitsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemLimitsActionPerformed
        srPanel.playerLimits();
    }//GEN-LAST:event_jMenuItemLimitsActionPerformed

    private void jMenuItemReloadActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemReloadActionPerformed
        srPanel.reloadTable();
    }//GEN-LAST:event_jMenuItemReloadActionPerformed

    private void jMenuItemSaveallActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemSaveallActionPerformed
        srPanel.saveAllPlayers();
    }//GEN-LAST:event_jMenuItemSaveallActionPerformed

    private void jMenuItemNotifyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemNotifyActionPerformed
        srPanel.systemNotify();
    }//GEN-LAST:event_jMenuItemNotifyActionPerformed

    private void jMenuItemClearCorpsesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemClearCorpsesActionPerformed
        srPanel.clearCorpses();
    }//GEN-LAST:event_jMenuItemClearCorpsesActionPerformed

    private void jMenuItemOptimiseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemOptimiseActionPerformed
        srPanel.optimiseDB();
    }//GEN-LAST:event_jMenuItemOptimiseActionPerformed

    private void jMenuItemShutIdleActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemShutIdleActionPerformed
        srPanel.idleShutdown();
    }//GEN-LAST:event_jMenuItemShutIdleActionPerformed

    private void jMenuItemShutDelayActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemShutDelayActionPerformed
        srPanel.shutdownDelay();
    }//GEN-LAST:event_jMenuItemShutDelayActionPerformed

    private void jMenuItemShutImmActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemShutImmActionPerformed
        srPanel.shutdownNow();
    }//GEN-LAST:event_jMenuItemShutImmActionPerformed

    private void menuItemGUIActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuItemGUIActionPerformed
        gf.setFrameVisible(pFrame, !pFrame.isVisible());
}//GEN-LAST:event_menuItemGUIActionPerformed

    private void menuItemQuitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_menuItemQuitActionPerformed
        quitGUI(false);
}//GEN-LAST:event_menuItemQuitActionPerformed

    private void jMenuItemMassItemsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemMassItemsActionPerformed
        accPanel.sendItems(null);
    }//GEN-LAST:event_jMenuItemMassItemsActionPerformed

    private void jMenuItemDebugLogActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemDebugLogActionPerformed
        gf.editFile(gf.getAppSettingsPath() + "status.log");
    }//GEN-LAST:event_jMenuItemDebugLogActionPerformed

    private void jMenuItemPrefsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemPrefsActionPerformed
        openPreferences();
    }//GEN-LAST:event_jMenuItemPrefsActionPerformed

    private void jTabManagementStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_jTabManagementStateChanged
        if (jTabManagement.getSelectedComponent() == banPanel) {
            banPanel.updateBanTables(0);
        }

    }//GEN-LAST:event_jTabManagementStateChanged

    private void jMenuItemEditProfilesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemEditProfilesActionPerformed
        connHandler.setConnectButtonVisible(false);
        connHandler.setCancelButtonText(dh.getString("butt_close"));
        connHandler.pack();
        connHandler.setLocationRelativeTo(this);
        connHandler.setVisible(true);
    }//GEN-LAST:event_jMenuItemEditProfilesActionPerformed

    private void jMenuItemMassMailActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemMassMailActionPerformed
        accPanel.sendMail(null);
    }//GEN-LAST:event_jMenuItemMassMailActionPerformed

    private void jMenuItemServerInfoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemServerInfoActionPerformed
        dh.createInfo("title_serverinfo", connHandler.getRAServerInfo());
        dh.setVisible(true);
    }//GEN-LAST:event_jMenuItemServerInfoActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenu jMenu;
    private javax.swing.JMenuBar jMenuBarMain;
    private javax.swing.JMenu jMenuHelp;
    private javax.swing.JMenuItem jMenuItemAbout;
    private javax.swing.JMenuItem jMenuItemAnnounce;
    private javax.swing.JMenuItem jMenuItemClearCorpses;
    private javax.swing.JMenuItem jMenuItemCommandsRef;
    private javax.swing.JMenuItem jMenuItemConnect;
    private javax.swing.JMenuItem jMenuItemDebugLog;
    private javax.swing.JMenuItem jMenuItemEditProfiles;
    private javax.swing.JMenuItem jMenuItemHelp;
    private javax.swing.JMenuItem jMenuItemLimits;
    private javax.swing.JMenuItem jMenuItemMOTD;
    private javax.swing.JMenuItem jMenuItemMassItems;
    private javax.swing.JMenuItem jMenuItemMassMail;
    private javax.swing.JMenuItem jMenuItemNotify;
    private javax.swing.JMenuItem jMenuItemOptimise;
    private javax.swing.JMenuItem jMenuItemPrefs;
    private javax.swing.JMenuItem jMenuItemQuit;
    private javax.swing.JMenuItem jMenuItemReload;
    private javax.swing.JMenuItem jMenuItemSaveall;
    private javax.swing.JMenuItem jMenuItemServerInfo;
    private javax.swing.JMenuItem jMenuItemShutDelay;
    private javax.swing.JMenuItem jMenuItemShutIdle;
    private javax.swing.JMenuItem jMenuItemShutImm;
    private javax.swing.JMenuItem jMenuItemTableRefresh;
    private javax.swing.JMenu jMenuQuickConn;
    private javax.swing.JMenu jMenuServer;
    private javax.swing.JMenu jMenuShutdown;
    private javax.swing.JPanel jPanelLogging;
    public javax.swing.JPanel jPanelMain;
    private javax.swing.JScrollPane jScrollPane9;
    public javax.swing.JTabbedPane jTabManagement;
    private javax.swing.JTextArea jTextAreaLogging;
    private java.awt.MenuItem menuItemGUI;
    private java.awt.MenuItem menuItemQuit;
    private java.awt.PopupMenu popupTray;
    // End of variables declaration//GEN-END:variables
}
