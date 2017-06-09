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
import lib.SqlAccess;
import java.sql.ResultSet;
import java.util.logging.Logger;
import javax.swing.JComboBox;

/**
 * @author Alistair Neil, <info@dazzleships.net>
 */
public final class MangosSql extends SqlAccess {

    private static final Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    /**
     * Constant for Realm database write data + struct
     */
    public static final int WRITE_REALM = 0;
    /**
     * Constant for Character write data + struct
     */
    public static final int WRITE_CHAR = 1;
    /**
     * Constant for Mangos database write data + struct
     */
    public static final int WRITE_MANGOS = 2;
    /**
     * Constant for Scriptdev database write data + struct
     */
    public static final int WRITE_SCRIPTDEV = 3;
    /**
     * Constant for Realm database write data only
     */
    public static final int WRITE_REALM_DATA = 4;
    /**
     * Constant for Character write data only
     */
    public static final int WRITE_CHAR_DATA = 5;
    /**
     * Constant for Mangos database write data only
     */
    public static final int WRITE_MANGOS_DATA = 6;
    /**
     * Constant for Scriptdev write data only
     */
    public static final int WRITE_SCRIPTDEV_DATA = 7;
    /**
     * Constants for DB Versions
     */
    public static final int MANGOSZERO = 0;
    public static final int MANGOSONE = 1;
    public static final int MANGOSTWO = 2;
    private static final String DBNOTFOUND = "Database catalog not found.";
    private String mangosDb;
    private String realmDb;
    private String charDb;
    private String scriptDb;
    private String mangosDBInfo;
    private String scriptdevDBInfo;
    private String realmDBInfo;
    private String clientVersion = "";
    private int mangosDBVersion;

    /**
     * Constructor
     */
    public MangosSql() {
    }

    /**
     * Constructor
     *
     * @param path , the default file path for the backup folder
     */
    public MangosSql(String path) {
        setBackupPath(path);
    }

    public String setDatabases(String mangosdb, String realmdb, String chardb, String scriptdb) {
        charDb = chardb;
        realmDb = realmdb;
        scriptDb = scriptdb;
        mangosDb = mangosdb;
        String result = createStatement(mangosDb, mangosDb);
        if (result != null) {
            return result + " " + DBNOTFOUND;
        }
        result = createStatement(realmDb, realmDb);
        if (result != null) {
            return result + " " + DBNOTFOUND;
        }
        result = createStatement(charDb, charDb);
        if (result != null) {
            return result + " " + DBNOTFOUND;
        }
        // Its possible that script db is not used if this is the case then set it to null
        if (!scriptdb.isEmpty()) {
            result = createStatement(scriptDb, scriptDb);
            if (result != null) {
                return result + " " + DBNOTFOUND;
            }
        } else {
            scriptDb = null;
        }
        updateDBVersions();
        return null;
    }

    public void setGeneratedBy(String text) {
        clientVersion = text;
    }

    public void createRealmStatement(String key) {
        createStatement(key, realmDb);
    }

    public void createMangosStatement(String key) {
        createStatement(key, mangosDb);
    }

    public void createScriptStatement(String key) {
        createStatement(key, scriptDb);
    }

    public void createCharStatement(String key) {
        createStatement(key, charDb);
    }

    /**
     * Execute a query that returns results
     *
     * @param query The SQL query
     * @return Returns a resultset
     */
    @Override
    public ResultSet executeQuery(String query) {
        // Do DB name replacement
        query = query.replace("*realm*", realmDb);
        query = query.replace("*mangos*", mangosDb);
        query = query.replace("*char*", charDb);
        return super.executeQuery(query);
    }

    /**
     * Execute an update query
     *
     * @param query The SQL query
     * @return Returns either row count for data manipulation queries or 0, or
     * -1 if error
     */
    @Override
    public int executeUpdate(String query) {
        // Do DB name replacement
        query = query.replace("*realm*", realmDb);
        query = query.replace("*mangos*", mangosDb);
        query = query.replace("*char*", charDb);
        return super.executeUpdate(query);
    }

    /**
     * @return returns the database name as a String based on the contents of
     * filename
     * @param filename The filename
     */
    public String getDBNameFromFilename(String filename) {

        int idx = filename.indexOf('_') + 1;
        if (idx == -1) {
            return "None";
        }
        String dbname = filename.substring(idx);
        idx = dbname.indexOf("_");
        dbname = dbname.substring(0, idx);
        if (idx == -1) {
            idx = dbname.indexOf('.');
        }
        if (idx == -1) {
            return "None";
        }
        return dbname.substring(0, idx);
    }

    /**
     * Populates the supplied JComboBox with the database names
     *
     * @param acombo The combobox to be populated
     */
    public void getDatabases(JComboBox acombo) {

        setStatement(mangosDb);
        acombo.removeAllItems();
        acombo.addItem("None");
        try {
            executeQuery("show databases");
            while (next()) {
                acombo.addItem(getString(1));
            }
        } catch (Exception ex) {
            logger.throwing(this.getClass().getName(), "getDatabases", ex);
        }
    }

    /**
     * Get the mangos database catalog name
     *
     * @return String
     */
    public String getMangosDBName() {
        return mangosDb;
    }

    /**
     * Get the realm database catalog name
     *
     * @return String
     */
    public String getRealmDBName() {
        return realmDb;
    }

    /**
     * Get the character database catalog name
     *
     * @return String
     */
    public String getCharDBName() {
        return charDb;
    }

    /**
     * Get the script database catalog name
     *
     * @return String
     */
    public String getScriptDBName() {
        return scriptDb;
    }

    /**
     * @return Returns the version info for the Mangos database
     */
    public String getMangosDBInfo() {
        return mangosDBInfo;
    }

    /**
     * @return Returns the version info for the Realm database
     */
    public String getRealmDBInfo() {
        return realmDBInfo;
    }

    /**
     * @return Returns the version info for the Scriptdev database
     */
    public String getScriptDBInfo() {
        return scriptdevDBInfo;
    }

    public int getMangosDBVersion() {
        return mangosDBVersion;
    }

    public void deleteCharacter(String guid) {

        String[] sqlStatements = {
            "START TRANSACTION",
            "DELETE FROM *char*.petition WHERE ownerguid = '*GUID*'",
            "DELETE FROM *char*.petition_sign WHERE ownerguid = '*GUID*'",
            "COMMIT",
            "START TRANSACTION",
            "DELETE FROM *char*.characters WHERE guid = '*GUID*'",
            "DELETE FROM *char*.character_account_data WHERE guid = '*GUID*'",
            "DELETE FROM *char*.character_declinedname WHERE guid = '*GUID*'",
            "DELETE FROM *char*.character_action WHERE guid = '*GUID*'",
            "DELETE FROM *char*.character_aura WHERE guid = '*GUID*'",
            "DELETE FROM *char*.character_battleground_data WHERE guid = '*GUID*'",
            "DELETE FROM *char*.character_gifts WHERE guid = '*GUID*'",
            "DELETE FROM *char*.character_glyphs WHERE guid = '*GUID*'",
            "DELETE FROM *char*.character_homebind WHERE guid = '*GUID*'",
            "DELETE FROM *char*.character_instance WHERE guid = '*GUID*'",
            "DELETE FROM *char*.group_instance WHERE leaderGuid = '*GUID*'",
            "DELETE FROM *char*.character_inventory WHERE guid = '*GUID*'",
            "DELETE FROM *char*.character_queststatus WHERE guid = '*GUID*'",
            "DELETE FROM *char*.character_queststatus_daily WHERE guid = '*GUID*'",
            "DELETE FROM *char*.character_queststatus_weekly WHERE guid = '*GUID*'",
            "DELETE FROM *char*.character_reputation WHERE guid = '*GUID*'",
            "DELETE FROM *char*.character_skills WHERE guid = '*GUID*'",
            "DELETE FROM *char*.character_spell WHERE guid = '*GUID*'",
            "DELETE FROM *char*.character_spell_cooldown WHERE guid = '*GUID*'",
            "DELETE FROM *char*.character_talent WHERE guid = '*GUID*'",
            "DELETE FROM *char*.character_ticket WHERE guid = '*GUID*'",
            "DELETE FROM *char*.item_instance WHERE owner_guid = '*GUID*'",
            "DELETE FROM *char*.character_social WHERE guid = '*GUID*' OR friend='*GUID*'",
            "DELETE FROM *char*.mail WHERE receiver = '*GUID*'",
            "DELETE FROM *char*.mail_items WHERE receiver = '*GUID*'",
            "DELETE FROM *char*.character_pet WHERE owner = '*GUID*'",
            "DELETE FROM *char*.character_pet_declinedname WHERE owner = '*GUID*'",
            "DELETE FROM *char*.character_achievement WHERE guid = '*GUID*'",
            "DELETE FROM *char*.character_achievement_progress WHERE guid = '*GUID*'",
            "DELETE FROM *char*.character_equipmentsets WHERE guid = '*GUID*'",
            "DELETE FROM *char*.guild_eventlog WHERE PlayerGuid1 = '*GUID*' OR PlayerGuid2 = '*GUID*'",
            "DELETE FROM *char*.guild_bank_eventlog WHERE PlayerGuid = '*GUID*'",
            "COMMIT",
            "START TRANSACTION",
            "DELETE FROM *realm*.realmcharacters WHERE acctid= '*ACCTID*' AND realmid = '*REALMID*'",
            "INSERT INTO *realm*.realmcharacters (numchars, acctid, realmid) VALUES (SELECT COUNT(guid) FROM *char*.characters WHERE account = *ACCTID*,*ACCTID*,*REALMID*)",
            "COMMIT"
        };

        try {
            setStatement(charDb);
            String acctid = getAcctIDFromCharID(guid);
            String realmid = getRealmIDFromAcctID(acctid);
            for (String s : sqlStatements) {
                s = s.replace("*GUID*", guid);
                s = s.replace("*ACCTID*", acctid);
                s = s.replace("*REALMID*", realmid);
                executeUpdate(s);
            }
        } catch (Exception ex) {
            logger.throwing(this.getClass().getName(), "deleteCharacter(String guid)", ex);
        }
    }

    public void optimizeRealmCharsDB(Object progress) {
        setStatement(realmDb);
        optimiseDatabase(progress);
        setStatement(charDb);
        optimiseDatabase(progress);
    }

    /**
     * @return Returns true if the specified char is online
     * @param name The character name
     */
    public boolean isCharOnline(String name) {
        String result = null;
        String query = "select online from characters where name='" + name + "'";
        setStatement(charDb);
        try {
            executeQuery(query);
            next();
            result = getString("online");
        } catch (Exception ex) {
            logger.throwing(this.getClass().getName(), "isCharOnline", ex);
        }
        if (result == null) {
            return false;
        }
        return !(result.compareTo("0") == 0);
    }

    /**
     * Test for an existing character
     *
     * @param charname
     * @return true if character already exists
     */
    public boolean characterExists(String charname) {

        String query = "select name from characters";
        setStatement(charDb);
        try {
            executeQuery(query);
            while (next()) {
                if (getString("name").contentEquals(charname)) {
                    return true;
                }
            }
        } catch (Exception ex) {
            logger.throwing(this.getClass().getName(), "characterExists", ex);
        }
        return false;
    }

    /**
     * Get character name from guid
     *
     * @param guid
     * @return character name
     */
    public String getCharNameFromGUID(String guid) {
        String query = "select name from characters where guid='" + guid + "'";
        setStatement(charDb);
        try {
            executeQuery(query);
            next();
            return getString("name");
        } catch (Exception ex) {
            logger.throwing(this.getClass().getName(), "getCharNameFromGUID", ex);
            return null;
        }
    }

    /**
     * @return Returns the account id from the character guid
     * @param id Numerical id associated with the account
     */
    public String getAcctIDFromCharID(String id) {
        String query = "select account from characters where guid='" + id + "'";
        setStatement(charDb);
        try {
            executeQuery(query);
            next();
            return getString("account");
        } catch (Exception ex) {
            logger.throwing(this.getClass().getName(), "getAcctIDFromCharID", ex);
            return null;
        }
    }

    /**
     * @return Returns the numerical account id from the account name
     * @param name the account name
     */
    public String getAcctIDFromAcctName(String name) {
        String query = "select id from account where username='" + name + "'";
        setStatement(realmDb);
        try {
            executeQuery(query);
            next();
            return getString("id");
        } catch (Exception ex) {
            logger.throwing(this.getClass().getName(), "getAcctIDFromAcctName", ex);
            return null;
        }
    }

    /**
     * @return Returns the numerical realm id from the account id
     * @param id the account id
     */
    public String getRealmIDFromAcctID(String id) {
        String query = "select realmid from realmcharacters where acctid='" + id + "'";
        setStatement(realmDb);
        try {
            executeQuery(query);
            next();
            return getString("realmid");
        } catch (Exception ex) {
            logger.throwing(this.getClass().getName(), "getRealmIDFromAcctID", ex);
            return null;
        }

    }

    /**
     * Gets the list of user defined realms as a hash string
     *
     * @return HashString of realms
     */
    public HashString getRealms() {

        HashString result = new HashString();
        setStatement(realmDb);
        try {
            executeQuery("select id,name from realmlist");
            while (next()) {
                result.putStringValue(getString("id"), getString("name"));
            }
            if (!result.containsKey("0")) {
                result.putStringValue("0", "Offline");
            }
        } catch (Exception ex) {
            result = null;
            logger.throwing(this.getClass().getName(), "getRealms", ex);
        }
        return result;
    }

    /**
     * Updates the db version variables
     */
    private void updateDBVersions() {
        try {
            setStatement(mangosDb);
            executeQuery("select * from db_version");
            next();
            mangosDBInfo = getString(1);
            setStatement(realmDb);
            executeQuery("select * from realmd_db_version");
            next();
            realmDBInfo = getString(1);
            // Its possible that script db is not used so check for null
            if (scriptDb != null) {
                setStatement(scriptDb);
                executeQuery("select * from sd2_db_version");
                next();
                scriptdevDBInfo = getString(1);
            }

            // Adjust for db version
            if (mangosDBInfo.toLowerCase().contains("mangoszero")) {
                mangosDBVersion = MANGOSZERO;
                return;
            }
            if (mangosDBInfo.toLowerCase().contains("mangosone")) {
                mangosDBVersion = MANGOSONE;
                return;
            }
            if (mangosDBInfo.toLowerCase().contains(" mangos ")
                    || mangosDBInfo.toLowerCase().contains(" cmangos ")
                    || mangosDBInfo.toLowerCase().contains("mangostwo")) {
                mangosDBVersion = MANGOSTWO;
            }
        } catch (Exception ex) {
            logger.throwing(this.getClass().getName(), "updateDBVersions", ex);
        }
    }

    /**
     * Writes an entire database
     *
     * @param action The action to be performed, see the defined constants for
     * this object
     */
    public void writeSQLDatabase(int action) {

        String version = null;
        boolean dataonly = false;
        updateDBVersions();

        switch (action) {
            case WRITE_REALM:
                setStatement(realmDb);
                version = mangosDBInfo;
                break;
            case WRITE_CHAR:
                setStatement(charDb);
                version = mangosDBInfo;
                break;
            case WRITE_SCRIPTDEV:
                setStatement(scriptDb);
                version = scriptdevDBInfo;
                break;
            case WRITE_MANGOS:
                setStatement(mangosDb);
                version = mangosDBInfo;
                break;
            case WRITE_REALM_DATA:
                setStatement(realmDb);
                version = mangosDBInfo;
                dataonly = true;
                break;
            case WRITE_CHAR_DATA:
                setStatement(charDb);
                version = mangosDBInfo;
                dataonly = true;
                break;
            case WRITE_SCRIPTDEV_DATA:
                setStatement(scriptDb);
                version = scriptdevDBInfo;
                dataonly = true;
                break;
            case WRITE_MANGOS_DATA:
                setStatement(mangosDb);
                version = mangosDBInfo;
                dataonly = true;
                break;
        }
        setVersionInfo(clientVersion, version);
        writeSQLDatabase(dataonly);
    }

    public double getPortalVersion(String filename) {

        int idxs;
        try {
            getFileIO().setReadFilename(filename);
            getFileIO().openBufferedRead();
            String text = getFileIO().readFromFile();
            while (true) {
                text = getFileIO().readFromFile();
                if (text == null) {
                    getFileIO().closeBufferedRead();
                    return 0;
                }
                if (text.contains("Portal Version")) {
                    break;
                }
            }
            getFileIO().closeBufferedRead();
            idxs = text.indexOf("=");
            if (idxs < 0) {
                return 0;
            }
            idxs++;
            text = text.substring(idxs, text.indexOf(" */"));
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
        }
        return 0;
    }

    public void writePortalSQL(String[] spellids) {

        updateDBVersions();
        setStatement(mangosDb);
        setVersionInfo(clientVersion, mangosDBInfo);
        String filename = createUniqueFilename(getHostname() + "_", "_portals");
        getFileIO().setWriteFilename(filename);
        getFileIO().openBufferedWrite();
        writeVersionInfo();
        getFileIO().writeToFile("/* Portal Version = 1.1 */", 2);

        getFileIO().writeToFile("/* Clear out current portal data */", 1);
        getFileIO().writeToFile("DELETE FROM `gameobject_template` WHERE entry >= 400000 AND entry < 400020;", 1);
        String strWhere = "";
        String strOr = "";
        for (String s : spellids) {
            strWhere += (strOr + "id=" + s);
            strOr = " or ";
        }
        getFileIO().writeToFile("DELETE FROM `spell_target_position` where " + strWhere + ";", 1);
        getFileIO().writeToFile("DELETE FROM `gameobject` WHERE guid >= 300000 AND guid < 300200;", 2);
        writeSQLTable("spell_target_position", true, 1, strWhere);
        writeSQLTable("gameobject", true, 1, "guid >= 300000 and guid < 300200");
        writeSQLTable("gameobject_template", true, 1, "entry >= 400000 and entry < 400020");
        getFileIO().closeBufferedWrite();
    }

    public void readSQLFile(String filename, String dbname) {
        createStatement("readSQLFile", dbname);
        setStatement("readSQLFile");
        readSQLFile(filename, true);
        removeStatement("readSQLFile");
        updateDBVersions();
    }
}
