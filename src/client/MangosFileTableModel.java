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

import lib.FileTableModel;
import lib.SimpleFileIO;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Vector;

/**
 *
 * @author Alistair Neil, <info@dazzleships.net>
 */
public final class MangosFileTableModel extends FileTableModel {

    private final String[] colNames = {"Filename", "DB Version", "Last Modified"};
    private Vector<Object> cache = new Vector<>(0);
    private String[] record;
    private SimpleFileIO ourFileIO = null;

    /**
     * Creates a new instance of FileTableModel
     */
    public MangosFileTableModel() {
        super();
    }

    /**
     * Returns the Object at a given row,col within the TableModel
     *
     * @param row The row index
     * @param col The column index
     * @return Returns the object value
     */
    @Override
    public Object getValueAt(int row, int col) {
        return ((String[]) (cache.get(row)))[col];
    }

    /**
     * Returns the column name as a String at column
     *
     * @param column The column index
     * @return Returns the column name
     */
    @Override
    public String getColumnName(int column) {
        return colNames[column];
    }

    /**
     * Return the total number of columns stored within this TableModel
     *
     * @return Returns the column count
     */
    @Override
    public int getColumnCount() {
        return colNames.length;
    }

    /**
     * Refreshes the table
     */
    @Override
    public void refreshTable() {

        ourFileIO = new SimpleFileIO();
        File[] fileList = getFileList();
        cache = new Vector<>(0);
        if (fileList != null) {
            for (int i = 0; i < fileList.length; i++) {
                record = new String[3];
                record[0] = fileList[i].getName();
                record[1] = getDBVersion(fileList[i]);
                record[2] = getDateFormat().format(new Date(fileList[i].lastModified()));
                cache.add(record);
            }
        }
        // Our table has changed so fire of a changed event
        fireTableChanged(null);
    }

    /**
     * Get the DB version from our SQL file
     *
     * @param ourFile The File
     * @return Returns the version info if found, else returns "Not Found"
     */
    private String getDBVersion(File ourFile) {
        String result;
        int start;
        try {
            ourFileIO.setReadFilename(ourFile.getCanonicalPath());
            ourFileIO.openBufferedRead();
            ourFileIO.readFromFile();
            ourFileIO.readFromFile();
            result = ourFileIO.readFromFile();

            start = result.indexOf("-- DBVersion = ");
            if (start < 0) {
                start = result.indexOf("/* DBVersion = ");
            }
            if (start != -1) {
                result = result.substring(15, result.length() - 2);
            } else {
                result = "Not Found";
            }
            ourFileIO.closeBufferedRead();
            return result;
        } catch (IOException ex) {
            return "Not Found";
        }
    }
}
