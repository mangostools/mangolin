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

import lib.InfoDialog;
import lib.Localisation;
import java.awt.Frame;

/**
 *
 * @author Alistair Neil, <info@dazzleships.net>
 */
public final class DialogHandler extends InfoDialog {

    private final Localisation local;
    private boolean boolContinueMsg = false;

    public DialogHandler(Frame frame, Localisation local) {
        super(frame);
        this.local = local;
        super.setButtonsText(local.getString("butt_cancel"),
                local.getString("butt_close"), local.getString("butt_continue"));
    }

    /**
     * Convenience method to get localised string
     *
     * @param key
     * @return String
     */
    public String getString(String key) {
        return local.getString(key);
    }

    /**
     * Convenience method to get multiple localised strings
     *
     * @param keys
     * @return String
     */
    public String[] getStrings(String... keys) {
        return local.getStrings(keys);
    }

    /**
     * Convenience methods for create info
     *
     * @param titlekey
     */
    @Override
    public void createInfo(String titlekey) {
        super.createInfo(local.getString(titlekey), null);
        boolContinueMsg = false;
    }

    /**
     *
     * @param titlekey
     * @param infokey
     */
    @Override
    public void createInfo(String titlekey, String infokey) {
        super.createInfo(local.getString(titlekey), local.getString(infokey));
        boolContinueMsg = false;
    }

    /**
     *
     * @param titlekey
     */
    @Override
    public void createWarn(String titlekey) {
        super.createWarn(local.getString(titlekey), null);
        boolContinueMsg = true;
    }

    /**
     *
     * @param titlekey
     * @param infokey
     */
    @Override
    public void createWarn(String titlekey, String infokey) {
        super.createWarn(local.getString(titlekey), local.getString(infokey));
        boolContinueMsg = true;
    }

    /**
     *
     * @param titlekey
     */
    @Override
    public void createError(String titlekey) {
        super.createError(local.getString(titlekey), null);
        boolContinueMsg = false;
    }

    /**
     *
     * @param titlekey
     * @param infokey
     */
    @Override
    public void createError(String titlekey, String infokey) {
        super.createError(local.getString(titlekey), local.getString(infokey));
        boolContinueMsg = false;
    }

    @Override
    public void setVisible(boolean visible) {
        if (boolContinueMsg & visible) {
            setAckText(local.getString("butt_continue"));
            appendInfoText("\n\n" + local.getString("info_continue"));
        }
        super.setVisible(visible);
    }

    public void enableContinueMessage(boolean enabled) {
        boolContinueMsg = enabled;
    }

    public void raiseMySqlError(String error) {

        String infokey = null;
        if (error.contains("is not allowed")) {
            infokey = "sql_err_notallowed";
        }
        if (error.contains("Connection refused")) {
            infokey = "sql_err_port";
        }
        if (error.contains("Access denied")) {
            infokey = "sql_err_userpass";
        }
        if (error.contains("link failure") || error.contains("Could not connect")) {
            infokey = "sql_err_hostip";
        }
        if (error.contains("does not exist")) {
            infokey = "sql_err_dblink";
        }

        if (infokey == null) {
            // Pass on error
            this.createError("title_sql_err", error);
        } else {
            // Pass on instructions
            this.createError("title_sql_err", infokey);
        }
        setVisible(true);
    }

    public void raiseRemoteAccessError(String error) {

        String infokey = null;
        if (error.contains("UnknownHost") || error.contains("Network is unreachable")) {
            infokey = "rem_err_hostip";
        }
        if (error.contains("ConnectException")) {
            infokey = "rem_err_port";
        }
        if (error.contains("User not found")) {
            infokey = "rem_err_user";
        }
        if (error.contains("Incorrect password")) {
            infokey = "rem_err_pass";
        }

        if (infokey == null) {
            // Pass on error
            this.createError("title_remote_err", error);
        } else {
            // Pass on instructions
            this.createError("title_remote_err", infokey);
        }
        setVisible(true);
    }
}
