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

import lib.SocketConnector;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;
import javax.swing.JTextArea;

/**
 *
 * @author Alistair Neil, <info@dazzleships.net>
 */
public final class MangosTelnet extends SocketConnector {

    final static String prompt = "s>";
    private static final Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss - ");
    private JTextArea jtaLog = null;
    private boolean boolDontLog = false;
    private String strLogHide = null;

    public void setLogTextOutput(JTextArea jta) {
        jtaLog = jta;
    }

    public void setHideFromLog(String text) {
        strLogHide = text;
    }

    /**
     * Initiates a mangos telnet session
     *
     * @param host
     * @param sport
     * @param user
     * @param pass
     * @return result as String
     */
    public String Login(String host, String sport, String user, String pass) {
        String result;
        try {
            setWaitPeriod(5000);
            setPrompt(":");
            connect(host, Integer.parseInt(sport));
            setHideFromLog(pass);
            addCmdLogEntry(user);
            result = send(user);
            if (result.isEmpty()) {
                setPrompt(prompt);
                addCmdLogEntry(pass);
                result = send(pass);
            }
        } catch (Exception ex) {
            logger.throwing(this.getClass().getName(), "Login", ex);
            result = ex.toString();
        }
        addResultLogEntry(result);
        return result;
    }

    private void addCmdLogEntry(String text) {
        if (jtaLog != null) {
            if (strLogHide != null) {
                text = text.replace(strLogHide, "*****");
            }
            jtaLog.append(sdf.format(new Date()) + "RA Command Issued - " + text + "\n");
        }
    }

    private void addResultLogEntry(String text) {
        if (jtaLog != null && !boolDontLog) {
            if (!text.endsWith("\n")) {
                text += "\n";
            }
            jtaLog.append(sdf.format(new Date()) + "RA Command Result - " + text);
        }
        boolDontLog = false;
        strLogHide = null;
    }

    /**
     * Sends message to mangos server
     *
     * @param msg The message to be sent
     * @param waitms Override default wait period
     */
    private String sendMangos(String msg, long waitms) {
        long lngWait = getWaitPeriod();
        setWaitPeriod(waitms);
        String result = sendMangos(msg);
        setWaitPeriod(lngWait);
        return result;
    }

    /**
     * Sends message to mangos server
     *
     * @param msg The message to be sent
     */
    private String sendMangos(String msg) {
        try {
            addCmdLogEntry(msg);
            String result = send(msg);
            if (result == null) {
                return "Error: Null result.";
            }
            if (result.isEmpty()) {
                return result;
            }
            if (result.contains("mangos>")) {
                result = result.substring(0, result.length() - 7);
            }
            addResultLogEntry(result);
            return result;
        } catch (Exception ex) {
            logger.throwing(this.getClass().getName(), "sendMangos(" + msg + ")", ex);
            return "Error : " + ex.getMessage();
        }
    }

    /**
     * Sets the specified character for customisation at next login
     *
     * @param charname The character name
     * @return Returns the server reply
     */
    public String setCharCustomise(String charname) {
        return sendMangos("character customize " + charname);
    }

    /**
     * Sets the specified character for rename at next login
     *
     * @param charname The character name
     * @return Returns the server reply
     */
    public String setCharRename(String charname) {
        return sendMangos("character rename " + charname);
    }

    /**
     * Sets the specified character level
     *
     * @param charname The character name
     * @param level The characters level
     * @return Returns the server reply
     */
    public String setCharLevel(String charname, String level) {
        return sendMangos("character level " + charname + " " + level);
    }

    /**
     * Sets Message of the day on the mangos server
     *
     * @param msg The message to be sent
     * @return Returns the server reply
     */
    public String setMotd(String msg) {
        return sendMangos("server set motd " + msg);
    }

    /**
     * Gets Message of the day from the mangos server
     *
     * @return Returns the server reply
     */
    public String getMotd() {
        return sendMangos("server motd");
    }

    /**
     * Reloads scriptdev
     *
     * @param scriptname
     * @return Returns the server reply
     */
    public String loadScripts(String scriptname) {
        return sendMangos("loadscripts " + scriptname);
    }

    /**
     * Broadcasts a system message on online chat log
     *
     * @param msg The message to be broadcast
     * @return Returns the server reply
     */
    public String announce(String msg) {
        return sendMangos("announce " + msg);
    }

    /**
     * Broadcasts a system message on screen
     *
     * @param msg The message to be broadcast
     * @return Returns the server reply
     */
    public String notify(String msg) {
        return sendMangos("notify " + msg);
    }

    /**
     * Gets the mangos version
     *
     * @return Returns the version of mangos being run
     */
    public String getVersion() {
        return sendMangos("version", 1000);
    }

    /**
     * Gets the server info
     *
     * @return Returns server info
     */
    public String getInfo() {
        return sendMangos("server info", 1000);
    }

    /**
     * Gets the player limit
     *
     * @return Returns player limit
     */
    public String getPlayerLimit() {
        return sendMangos("server plimit", 1000);
    }

    /**
     * Sets the player limit
     *
     * @param limit The player limit
     * @return Returns the server reply
     */
    public String setPlayerLimit(String limit) {
        return sendMangos("server plimit " + limit, 1000);
    }

    /**
     * Tells server to shutdown after a specified delay as long as server is
     * idle
     *
     * @param delay The delay in seconds
     * @return result as String
     */
    public String idleShutdown(String delay) {
        String result;
        setPrompt(null);
        result = sendMangos("server idleshutdown " + delay);
        setPrompt(prompt);
        return result;
    }

    /**
     * Tells server to shutdown after a specified delay
     *
     * @param delay The delay in seconds
     * @return result as String
     */
    public String shutdown(String delay) {
        String result;
        setPrompt(null);
        result = sendMangos("server shutdown " + delay);
        setPrompt(prompt);
        return result;
    }

    /**
     * Tells server to shutdown immediately
     *
     * @return result as String
     */
    public String shutdownNow() {
        String result;
        setPrompt(null);
        result = sendMangos("server exit");
        setPrompt(prompt);
        return result;
    }

    /**
     * Create a new user account
     *
     * @param username The name of the account
     * @param password The password for this account
     * @return Returns the server reply
     */
    public String createAccount(String username, String password) {
        return sendMangos("account create " + username + " " + password);
    }

    /**
     * Delete a user account
     *
     * @param username The name of the account
     * @return Returns the server reply
     */
    public String deleteAccount(String username) {
        return sendMangos("account delete " + username);
    }

    /**
     * Kick a character off the server
     *
     * @param charname The name of the character to kick
     * @return result as String
     */
    public String kickChar(String charname) {
        return sendMangos("kick " + charname);
    }

    /**
     * Dump a characters sql data to a file
     *
     * @param filename The name of the file
     * @param charnameorguid The name or guid of the character to dump
     * @return result as String
     */
    public String pDumpWrite(String filename, String charnameorguid) {
        return sendMangos("pdump write " + filename + " " + charnameorguid);
    }

    /**
     * Dump a characters sql data to a file
     *
     * @param filename The name of the file
     * @param acctname The name of the account to load character to
     * @param newcharname The new character name, optional
     * @return result as String
     */
    public String pDumpLoad(String filename, String acctname, String newcharname) {
        return sendMangos("pdump load " + filename + " " + acctname + " " + newcharname);
    }

    /**
     * Ban a user account
     *
     * @param acct The name of the account
     * @param reason The reason for the ban
     * @param duration The duration of the ban
     * @return Returns the server reply
     */
    public String banAcct(String acct, String reason, String duration) {
        return sendMangos("ban account " + acct + " " + duration + " \"" + reason + "\"");
    }

    /**
     * Ban a user account based on the ip
     *
     * @param ipadd The ipaddress of the user
     * @param reason The reason for the ban
     * @param duration The duration of the ban
     * @return Returns the server reply
     */
    public String banIP(String ipadd, String reason, String duration) {
        return sendMangos("ban ip " + ipadd + " " + duration + " \"" + reason + "\"");
    }

    /**
     * Unban a user account
     *
     * @param acct The name of the account
     * @return Returns the server reply
     */
    public String unbanAcct(String acct) {
        return sendMangos("unban account " + acct);
    }

    /**
     * Unban an ip address
     *
     * @param ipadd The ip address
     * @return Returns the server reply
     */
    public String unbanIP(String ipadd) {
        return sendMangos("unban ip " + ipadd);
    }

    /**
     * Ban user account with this character
     *
     * @param character The character name
     * @param reason The reason for the ban
     * @param duration The duration of the ban
     * @return Returns the server reply
     */
    public String banChar(String character, String reason, String duration) {
        return sendMangos("ban character " + character + " " + duration + " \"" + reason + "\"");
    }

    /**
     * Unban account with this character
     *
     * @param character The character name
     * @return Returns the server reply
     */
    public String unbanChar(String character) {
        return sendMangos("unban character " + character);
    }

    /**
     * Delete a character
     *
     * @param character The character name
     * @return Returns the server reply
     */
    public String deleteChar(String character) {
        return sendMangos("character erase " + character);
    }

    /**
     * Reset a character option
     *
     * @param character The character name
     * @param option The option to reset
     * @return Returns the server reply
     */
    public String resetCharOption(String character, String option) {
        return sendMangos("reset " + option + " " + character);
    }

    /**
     * Set the privileges of this account
     *
     * @param acct The account name
     * @param level The privilege level 0,1,2,3
     * @return Returns the server reply
     */
    public String setGM(String acct, String level) {
        return sendMangos("account set gmlevel " + acct + " " + level, 1000);
    }

    /**
     * Triggers a corpse clear out
     *
     * @return Returns the server reply
     */
    public String clearCorpses() {
        return sendMangos("server corpses", 1000);
    }

    /**
     * Save all player data
     *
     * @return Returns the server reply
     */
    public String saveAll() {
        return sendMangos("saveall", 30000);
    }

    /**
     * Set the password of specified account
     *
     * @param acct The account name
     * @param passwd The password
     * @return Returns the server reply
     */
    public String setPassword(String acct, String passwd) {
        setHideFromLog(passwd);
        return sendMangos("account set password " + acct + " " + passwd + " " + passwd, 1000);
    }

    /**
     * Reload specified table
     *
     * @param tablename The table you wish reloaded
     * @return Returns the server reply
     */
    public String reloadTable(String tablename) {
        String result = sendMangos("reload " + tablename);
        return result;
    }

    /**
     * Request help info
     *
     * @param property The property you require help on
     * @return Returns the server reply
     */
    public String help(String property) {
        boolDontLog = true;
        return sendMangos("help " + property, 1000);
    }

    /**
     * Get ticket
     *
     * @param charname The character name
     * @return Returns the server reply
     */
    public String getTicket(String charname) {
        return sendMangos("ticket " + charname);
    }

    /**
     * Delete ticket
     *
     * @param charname The character name
     * @return Returns the server reply
     */
    public String deleteTicket(String charname) {
        return sendMangos("delticket " + charname);
    }

    /**
     * Revive a character
     *
     * @param charname The character name
     * @return Returns the server reply
     */
    public String revive(String charname) {
        String result = sendMangos("revive " + charname);
        if (result.isEmpty()) {
            return charname + " successfully revived.";
        }
        return result;
    }

    /**
     * Repair a characters items
     *
     * @param charname The account name
     * @return Returns the server reply
     */
    public String repairItems(String charname) {
        return sendMangos("repairitems " + charname);
    }

    /**
     * Set the expansion flag for this account
     *
     * @param acct The account name
     * @param expansion The expansion value 0 = normal, 1 = TBC
     * @return Returns the server reply
     */
    public String setExpansion(String acct, String expansion) {
        return sendMangos("account set addon " + acct + " " + expansion);
    }

    /**
     * Send a message to a character
     *
     * @param charname The character
     * @param msg The message
     * @return Returns the server reply
     */
    public String sendMessage(String charname, String msg) {
        if (msg.isEmpty()) {
            return null;
        }
        return sendMangos("send message " + charname + " " + msg, 1000);
    }

    /**
     * Send mail to a character
     *
     * @param charname The character
     * @param subject The subject
     * @param text The mail text
     * @return Returns the server reply
     */
    public String sendMail(String charname, String subject, String text) {
        if (subject == null || subject.isEmpty()) {
            subject = "No Subject";
        }
        if (text == null || text.isEmpty()) {
            text = "No Message";
        }
        return sendMangos("send mail " + charname + " \"" + subject + "\" \"" + text + "\"");
    }

    /**
     * Send mail to a character with money
     *
     * @param charname The character
     * @param subject The subject
     * @param text The mail text
     * @param money The amount of money
     * @return Returns the server reply
     */
    public String sendGold(String charname, String subject, String text, String money) {
        if (subject == null || subject.isEmpty()) {
            subject = "No Subject";
        }
        if (text == null || text.isEmpty()) {
            text = "No Message";
        }
        return sendMangos("send money " + charname + " \"" + subject + "\" \"" + text + "\" " + money);
    }

    /**
     * Send mail to a character containing items
     *
     * @param charname The character
     * @param subject The subject
     * @param text The mail text
     * @param itemids string of space separated ids
     * @return Returns the server reply
     */
    public String sendItems(String charname, String subject, String text, String itemids) {
        if (subject == null || subject.isEmpty()) {
            subject = "No Subject";
        }
        if (text == null || text.isEmpty()) {
            text = "No Message";
        }
        return sendMangos("send items " + charname + " \"" + subject + "\" \"" + text + "\" " + itemids);
    }

    /**
     * Send mass mail to a race or faction
     *
     * @param racefaction The race or faction
     * @param subject The subject
     * @param text The mail text
     * @return Returns the server reply
     */
    public String sendMassMail(String racefaction, String subject, String text) {
        if (subject == null || subject.isEmpty()) {
            subject = "No Subject";
        }
        if (text == null || text.isEmpty()) {
            text = "No Message";
        }
        return sendMangos("send mass mail " + racefaction.toLowerCase() + " \"" + subject + "\" \"" + text + "\"");
    }

    /**
     * Send mass mail to a race or faction containing money
     *
     * @param racefaction The race or faction
     * @param subject The subject
     * @param text The mail text
     * @param money The amount of money
     * @return Returns the server reply
     */
    public String sendMassGold(String racefaction, String subject, String text, String money) {
        if (subject == null || subject.isEmpty()) {
            subject = "No Subject";
        }
        if (text == null || text.isEmpty()) {
            text = "No Message";
        }
        return sendMangos("send mass money " + racefaction.toLowerCase() + " \"" + subject + "\" \"" + text + "\" " + money);
    }

    /**
     * Send mass mail to a race or faction containing items
     *
     * @param racefaction The race or faction
     * @param subject The subject
     * @param text The mail text
     * @param itemids string of space separated ids
     * @return Returns the server reply
     */
    public String sendMassItems(String racefaction, String subject, String text, String itemids) {
        if (subject == null || subject.isEmpty()) {
            subject = "No Subject";
        }
        if (text == null || text.isEmpty()) {
            text = "No Message";
        }
        return sendMangos("send mass items " + racefaction.toLowerCase() + " \"" + subject + "\" \"" + text + "\" " + itemids);
    }

    /**
     * Teleport a character to a location
     *
     * @param charname The character
     * @param location The location name
     * @return Returns the server reply
     */
    public String teleport(String charname, String location) {
        return sendMangos("tele name " + charname + " " + location);
    }

    /**
     * Closes the telnet session
     */
    public void closeConnection() {
        try {
            setPrompt("");
            sendMangos("quit");
            super.disconnect();
        } catch (IOException ex) {
            logger.throwing(this.getClass().getName(), "closeConnection()", ex);
        }
    }
}
