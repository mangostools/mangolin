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
import lib.SpinnerInputDialog;
import java.awt.Cursor;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.util.regex.Pattern;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;

/**
 *
 * @author Alistair Neil, <info@dazzleships.net>
 */
public final class SendItemsDialog extends javax.swing.JDialog {

    /**
     * A return status code - returned if Cancel button has been pressed
     */
    public static final int RET_CANCEL = 0;
    /**
     * A return status code - returned if OK button has been pressed
     */
    public static final int RET_OK = 1;
    private MangosSql ourdbio = null;
    private int intClassBitMask = 0;
    private int intRaceBitMask = 0;
    private String strLevel = "";
    private final DefaultListModel listModel = new DefaultListModel();
    private DialogHandler dh;

    /**
     * Creates new form TeleportDialog
     */
    /**
     * @param parent parent frame
     * @param modal whether dialog is modal or not, true means program flow is
     * halted
     */
    public SendItemsDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);
        initComponents();
        this.setIconImage(new javax.swing.ImageIcon(
                getClass().getResource("/resources/logo.png")).getImage());
        jListItems.setModel(listModel);
        pack();
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
     * @return the return status of this dialog - one of RET_OK or RET_CANCEL
     */
    public int getReturnStatus() {
        return returnStatus;
    }

    public String getToField() {
        return (String) jComboTo.getSelectedItem();
    }

    /**
     * Set character properties
     *
     * @param cname Character name
     * @param crace Character race
     * @param cclass Character class
     * @param clevel Character level
     */
    public void setCharProperties(String cname, String crace, String cclass, String clevel) {

        setCursor(new Cursor(Cursor.WAIT_CURSOR));
        intClassBitMask = -1;
        intRaceBitMask = -1;
        if (cclass != null) {
            intClassBitMask = 1 << (Integer.parseInt(cclass) - 1);
        }
        if (crace != null) {
            intRaceBitMask = 1 << (Integer.parseInt(crace) - 1);
        }

        strLevel = clevel;
        if (clevel == null) {
            jCheckIgnore.setSelected(true);
            jCheckIgnore.setEnabled(false);
        }

        if (cname != null) {
            jComboTo.removeAllItems();
            jComboTo.addItem(cname);
            jComboTo.setSelectedIndex(0);
            jComboTo.setEnabled(false);
        } else {
            jComboTo.insertItemAt("Horde", 0);
            jComboTo.insertItemAt("Alliance", 0);
            jComboTo.insertItemAt("All", 0);
            jComboTo.setSelectedIndex(0);
            jComboTo.setEnabled(true);
        }
        updateItemsTable();
        setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
    }

    public void populateRace(HashString hsRace) {
        jComboTo.removeAllItems();
        if (hsRace != null) {
            jComboTo.setModel(new DefaultComboBoxModel(hsRace.getAllValues(false)));
        }
    }

    /**
     * @return returns the subject contents as a string
     */
    public String getSubject() {
        return jTextSubject.getText();
    }

    /**
     * @return returns the body contents as a string
     */
    public String getBody() {
        return jTextAreaBody.getText();
    }

    private void updateItemsTable() {

        setCursor(new Cursor(Cursor.WAIT_CURSOR));
        String like = "'%" + jTextSearchField.getText() + "%'";
        ourdbio.setStatement("mangos");
        String query = "select entry,name,itemlevel, quality from item_template where "
                + "name like " + like;
        if (!jCheckIgnore.isSelected()) {
            query += " and (allowableclass & " + String.valueOf(intClassBitMask) + ")";
            query += " and (allowablerace & " + String.valueOf(intRaceBitMask) + ")";
            query += " and requiredlevel <= " + strLevel;
        }
        dbTableItems.getTableHeader().setFont(jLabelSearch.getFont());
        dbTableItems.getModel().setResultSet(ourdbio.executeQuery(query));
        dbTableItems.getModel().setPrimaryKey("entry");
        dbTableItems.enableNumericReplacement("quality", "Poor", "Common", "UnCommon",
                "Rare", "Epic", "Legendary", "Artifact", "Heirloom");
        dbTableItems.getModel().refreshTableContents();
        adjustTableColumnWidth(0, "AAAAA");
        adjustTableColumnWidth(1, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        adjustTableColumnWidth(2, "AAAAAAAA");
        setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
    }

    private void addItemToList() {

        int selIdx = dbTableItems.getSelectedRow();
        String strIdDesc;
        String strId = ((Integer) dbTableItems.getValueAt(selIdx, 0)).toString();
        strId = String.format("%1$-6s", strId);
        String strDesc = (String) dbTableItems.getValueAt(selIdx, 1);
        strIdDesc = strId + " : " + strDesc + " : ";
        SpinnerInputDialog sidQuantity = new SpinnerInputDialog((Frame) super.getParent(), true);
        sidQuantity.setCancelButtonText(dh.getString("butt_cancel"));
        sidQuantity.setOkButtonText(dh.getString("butt_continue"));
        sidQuantity.setMinValue(1);
        sidQuantity.setMaxValue(240);
        sidQuantity.setValue(1);
        sidQuantity.setEntryLabel(dh.getString("lab_itemquantity"));
        sidQuantity.setTitle(dh.getString("title_itemquantity"));
        sidQuantity.setLocationRelativeTo(this);
        sidQuantity.setVisible(true);
        if (sidQuantity.getReturnStatus() == SpinnerInputDialog.RET_OK) {
            if (listModel.size() < 12) {
                listModel.addElement(strIdDesc + sidQuantity.getValue());
            }
        }
    }

    private void removeFromList() {
        int selIdx = jListItems.getSelectedIndex();
        if (selIdx < 0) {
            return;
        }
        listModel.removeElementAt(selIdx);
    }

    public String getItems() {
        String strResult = "";
        String strTemp;
        Pattern patt = Pattern.compile(":");
        String strArr[];
        for (Object o : listModel.toArray()) {
            strTemp = (String) o;
            strArr = patt.split(strTemp);
            strTemp = strArr[0].trim() + ":" + strArr[2].trim();
            strResult += strTemp + " ";
        }
        return strResult;
    }

    private void adjustTableColumnWidth(int col, String test) {
        FontMetrics ourFontMetrics = getFontMetrics(dbTableItems.getTableHeader().getFont());
        dbTableItems.getColumn(dbTableItems.getColumnName(col)).setPreferredWidth(ourFontMetrics.stringWidth(test));
    }

    /**
     * @param dbio Sets the object that handles all the IO with our database,
     * required for upadateList method
     */
    public void setDatabaseIO(MangosSql dbio) {
        ourdbio = dbio;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanelMain = new javax.swing.JPanel();
        jPanel1 = new javax.swing.JPanel();
        jTextSearchField = new javax.swing.JTextField();
        jTextArea1 = new javax.swing.JTextArea();
        jLabelSearch = new javax.swing.JLabel();
        jCheckIgnore = new javax.swing.JCheckBox();
        jScrollPane3 = new javax.swing.JScrollPane();
        dbTableItems = new lib.DBJTableBean();
        jPanel2 = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        jListItems = new javax.swing.JList();
        jLabelItemId = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTextAreaBody = new javax.swing.JTextArea();
        jLabelBody = new javax.swing.JLabel();
        jTextSubject = new javax.swing.JTextField();
        jLabelSubject = new javax.swing.JLabel();
        jLabelTo = new javax.swing.JLabel();
        sendButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();
        jLabelInfoID = new javax.swing.JLabel();
        jComboTo = new javax.swing.JComboBox();

        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("lang/MessagesBundle"); // NOI18N
        setTitle(bundle.getString("title_sendmail")); // NOI18N
        setResizable(false);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                closeDialog(evt);
            }
        });

        jPanelMain.setFont(jPanelMain.getFont().deriveFont(jPanelMain.getFont().getStyle() | java.awt.Font.BOLD, jPanelMain.getFont().getSize()+3));

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder(null, bundle.getString("pan_itemselection"), javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, jPanelMain.getFont(), jPanelMain.getForeground())); // NOI18N
        jPanel1.setFont(jPanel1.getFont().deriveFont(jPanel1.getFont().getStyle() | java.awt.Font.BOLD, jPanel1.getFont().getSize()+3));

        jTextSearchField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jTextSearchFieldActionPerformed(evt);
            }
        });

        jTextArea1.setEditable(false);
        jTextArea1.setColumns(20);
        jTextArea1.setFont(jTextArea1.getFont().deriveFont(jTextArea1.getFont().getStyle() | java.awt.Font.BOLD));
        jTextArea1.setForeground(jLabelBody.getForeground());
        jTextArea1.setLineWrap(true);
        jTextArea1.setRows(3);
        jTextArea1.setText(bundle.getString("text_items")); // NOI18N
        jTextArea1.setWrapStyleWord(true);
        jTextArea1.setBorder(null);
        jTextArea1.setOpaque(false);

        jLabelSearch.setFont(jLabelSearch.getFont().deriveFont(jLabelSearch.getFont().getStyle() | java.awt.Font.BOLD));
        jLabelSearch.setText(bundle.getString("lab_search")); // NOI18N

        jCheckIgnore.setFont(jCheckIgnore.getFont().deriveFont(jCheckIgnore.getFont().getStyle() | java.awt.Font.BOLD));
        jCheckIgnore.setText(bundle.getString("check_ignorecharlev")); // NOI18N
        jCheckIgnore.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jCheckIgnoreActionPerformed(evt);
            }
        });

        dbTableItems.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                dbTableItemsMouseClicked(evt);
            }
        });
        jScrollPane3.setViewportView(dbTableItems);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jLabelSearch)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jScrollPane3)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(jTextSearchField, javax.swing.GroupLayout.PREFERRED_SIZE, 324, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(jCheckIgnore)
                                .addGap(0, 0, Short.MAX_VALUE))
                            .addComponent(jTextArea1))
                        .addContainerGap())))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabelSearch)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jTextSearchField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jCheckIgnore))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 471, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jTextArea1, javax.swing.GroupLayout.PREFERRED_SIZE, 39, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder(null, bundle.getString("pan_mail"), javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, jPanelMain.getFont(), jPanelMain.getForeground())); // NOI18N

        jListItems.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jListItemsMouseClicked(evt);
            }
        });
        jScrollPane2.setViewportView(jListItems);

        jLabelItemId.setFont(jLabelItemId.getFont().deriveFont(jLabelItemId.getFont().getStyle() | java.awt.Font.BOLD));
        jLabelItemId.setText(bundle.getString("lab_itemiddesc")); // NOI18N

        jTextAreaBody.setColumns(20);
        jTextAreaBody.setLineWrap(true);
        jTextAreaBody.setRows(5);
        jTextAreaBody.setWrapStyleWord(true);
        jScrollPane1.setViewportView(jTextAreaBody);

        jLabelBody.setFont(jLabelBody.getFont().deriveFont(jLabelBody.getFont().getStyle() | java.awt.Font.BOLD));
        jLabelBody.setText(bundle.getString("lab_body")); // NOI18N

        jLabelSubject.setFont(jLabelSubject.getFont().deriveFont(jLabelSubject.getFont().getStyle() | java.awt.Font.BOLD));
        jLabelSubject.setText(bundle.getString("lab_subject")); // NOI18N

        jLabelTo.setFont(jLabelTo.getFont().deriveFont(jLabelTo.getFont().getStyle() | java.awt.Font.BOLD));
        jLabelTo.setText(bundle.getString("lab_to")); // NOI18N

        sendButton.setText(bundle.getString("butt_send")); // NOI18N
        sendButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sendButtonActionPerformed(evt);
            }
        });

        cancelButton.setText(bundle.getString("butt_cancel")); // NOI18N
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        jLabelInfoID.setFont(jLabelInfoID.getFont().deriveFont(jLabelInfoID.getFont().getStyle() | java.awt.Font.BOLD));
        jLabelInfoID.setText(bundle.getString("lab_itemidinfo")); // NOI18N

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1)
                    .addComponent(jScrollPane2)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(jLabelInfoID)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(cancelButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(sendButton))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(jLabelItemId)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(jLabelBody, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jLabelTo, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jLabelSubject, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 1, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jComboTo, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jTextSubject))))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelTo)
                    .addComponent(jComboTo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelSubject)
                    .addComponent(jTextSubject, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabelBody)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 226, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabelItemId)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 200, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cancelButton)
                    .addComponent(sendButton)
                    .addComponent(jLabelInfoID))
                .addContainerGap())
        );

        javax.swing.GroupLayout jPanelMainLayout = new javax.swing.GroupLayout(jPanelMain);
        jPanelMain.setLayout(jPanelMainLayout);
        jPanelMainLayout.setHorizontalGroup(
            jPanelMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelMainLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        jPanelMainLayout.setVerticalGroup(
            jPanelMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelMainLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelMainLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanelMain, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanelMain, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents
    private void sendButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sendButtonActionPerformed
        doClose(RET_OK);
    }//GEN-LAST:event_sendButtonActionPerformed

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        doClose(RET_CANCEL);
    }//GEN-LAST:event_cancelButtonActionPerformed

    /**
     * Closes the dialog
     */
    private void closeDialog(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_closeDialog
        doClose(RET_CANCEL);
    }//GEN-LAST:event_closeDialog

    private void jCheckIgnoreActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jCheckIgnoreActionPerformed
        updateItemsTable();
    }//GEN-LAST:event_jCheckIgnoreActionPerformed

    private void jTextSearchFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextSearchFieldActionPerformed
        updateItemsTable();
    }//GEN-LAST:event_jTextSearchFieldActionPerformed

    private void dbTableItemsMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_dbTableItemsMouseClicked
        if (evt.getClickCount() == 2) {
            addItemToList();
        }
    }//GEN-LAST:event_dbTableItemsMouseClicked

    private void jListItemsMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jListItemsMouseClicked
        if (evt.getClickCount() == 2) {
            removeFromList();
        }
    }//GEN-LAST:event_jListItemsMouseClicked

    private void doClose(int retStatus) {
        returnStatus = retStatus;
        setVisible(false);
        dispose();
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancelButton;
    private lib.DBJTableBean dbTableItems;
    private javax.swing.JCheckBox jCheckIgnore;
    private javax.swing.JComboBox jComboTo;
    private javax.swing.JLabel jLabelBody;
    private javax.swing.JLabel jLabelInfoID;
    private javax.swing.JLabel jLabelItemId;
    private javax.swing.JLabel jLabelSearch;
    private javax.swing.JLabel jLabelSubject;
    private javax.swing.JLabel jLabelTo;
    private javax.swing.JList jListItems;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanelMain;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JTextArea jTextArea1;
    private javax.swing.JTextArea jTextAreaBody;
    private javax.swing.JTextField jTextSearchField;
    private javax.swing.JTextField jTextSubject;
    private javax.swing.JButton sendButton;
    // End of variables declaration//GEN-END:variables
    private int returnStatus = RET_CANCEL;
}
