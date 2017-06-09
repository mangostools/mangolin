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

import lib.GlobalFunctions;
import lib.SimpleINI;
import lib.Win7Taskbar;
import java.awt.Font;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.LookAndFeel;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/**
 *
 * @author Alistair Neil <info@dazzleships.net>
 */
public class MaNGOLin {

    private static final String DOMAIN = "www.dazzleships.net";
    private static final String HOMEPAGE = "http://" + DOMAIN;
    private static final String HOMEPAGE_SSL = "https://" + DOMAIN;
    private static final String APPNAME = "MaNGOLin";
    private static final String APPVER = "2.07";
    private final GlobalFunctions gf = GlobalFunctions.getInstance();
    private static final Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private MaNGOLinUI mangolinUI;
    private String args[];
    private String iconPath;

    /**
     * This is the class loader for the MaNGOLinUI, it also handles UI restart
     * for theming
     *
     * @param args
     */
    public MaNGOLin(final String args[]) {

        try {
            this.args = args;

            // Initialise some essentialvariables
            // I know this looks a bit odd but trust me this allows us to install and run from anywhere
            String runPath = gf.getUsersCurrentFolder() + gf.getFileSeparator();
            if (runPath.startsWith("/usr/share")) {
                runPath = "/usr/share/";
            }
            iconPath = runPath + "icons" + gf.getFileSeparator() + "mangolin.png";

            // Windows 7 taskbar functionality
            if (gf.isWin7() || gf.isWin8() || gf.isWin10()) {
                Win7Taskbar.setCurrentProcessExplicitAppUserModelID(MaNGOLin.class.getName());
            }

            // General setup
            gf.setAppName(APPNAME);
            gf.setAppVersion(APPVER);
            gf.setHomepage(HOMEPAGE);
            gf.setHomepageSSL(HOMEPAGE_SSL);
            gf.setAppIcon(new javax.swing.ImageIcon(iconPath));

            // Initial logging settings
            initLogging(gf.getAppSettingsPath() + "status.log", false);

            loadUI();
        } catch (Exception ex) {
            logger.throwing(this.getClass().getName(), "MaNGOLin", ex);
        }
    }

    private void loadUI() {
        // Application settings storage object
        SimpleINI simpleIni = new SimpleINI(gf.getAppSettingsPath(), "mangolin.ini");
        // UI style
        simpleIni.setGroup("GUI");
        loadUIStyle(simpleIni.getStringValue("theme", "System"),
                "Dazzleships", simpleIni.getBoolValue("windowdec", true));
        gf.setUIFontSize(simpleIni.getIntegerValue("fontsize", 9), 80);

        mangolinUI = new MaNGOLinUI(args) {
            @Override
            public void exitRequested(boolean restart) {
                mangolinUI.dispose();
                mangolinUI = null;
                System.gc();
                if (restart) {
                    loadUI();
                } else {
                    System.exit(0);
                }
            }
        };
        mangolinUI.pack();
    }

    /**
     * Initialise logging facilities
     *
     * @param logpath
     * @param append
     */
    private void initLogging(String logpath, boolean append) {
        try {
            logger.setLevel(Level.ALL);
            if (logpath != null) {
                FileHandler fh = new FileHandler(logpath, 1000000, 1, append);
                fh.setFormatter(new SimpleFormatter());
                logger.addHandler(fh);
            }
        } catch (SecurityException | IOException ex) {
        }
    }

    /**
     * Initialises the UI Look and Feel
     */
    private void loadUIStyle(String theme, String logo, boolean windec) {

        Properties props;
        String windowDecoration;
        try {

            // First set to a System L&F
            setSystemLookAndFeel();

            if (theme == null || theme.contentEquals("System")) {
                return;
            }

            if (theme.contentEquals("Metal")) {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
                return;
            }

            if (theme.contentEquals("Nimbus")) {
                // Switch to Nimbus theme
                UIManager.setLookAndFeel("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
                return;
            }

            // Get System Font
            UIDefaults defaults = UIManager.getDefaults();
            Font sFont = defaults.getFont("Menu.font");

            // Test for windows decoration
            if (windec) {
                windowDecoration = "on";
            } else {
                windowDecoration = "off";
            }

            if (theme.contentEquals("Acryl")) {
                String subtheme = "Default";
                props = com.jtattoo.plaf.acryl.AcrylLookAndFeel.getThemeProperties(subtheme);
                props.setProperty("windowDecoration", windowDecoration);
                if (gf.isLinux()) {
                    adjustLinuxFonts(sFont, props);
                }
                com.jtattoo.plaf.acryl.AcrylLookAndFeel.setTheme(subtheme, "", logo);
                UIManager.setLookAndFeel("com.jtattoo.plaf.acryl.AcrylLookAndFeel");
                return;
            }

            if (theme.contentEquals("Acryl Green")) {
                String subtheme = "Green";
                props = com.jtattoo.plaf.acryl.AcrylLookAndFeel.getThemeProperties(subtheme);
                props.setProperty("windowDecoration", windowDecoration);
                if (gf.isLinux()) {
                    adjustLinuxFonts(sFont, props);
                }
                com.jtattoo.plaf.acryl.AcrylLookAndFeel.setTheme(subtheme, "", logo);
                UIManager.setLookAndFeel("com.jtattoo.plaf.acryl.AcrylLookAndFeel");
                return;
            }

            if (theme.contentEquals("Acryl Lemmon")) {
                String subtheme = "Lemmon";
                props = com.jtattoo.plaf.acryl.AcrylLookAndFeel.getThemeProperties(subtheme);
                props.setProperty("windowDecoration", windowDecoration);
                if (gf.isLinux()) {
                    adjustLinuxFonts(sFont, props);
                }
                com.jtattoo.plaf.acryl.AcrylLookAndFeel.setTheme(subtheme, "", logo);
                UIManager.setLookAndFeel("com.jtattoo.plaf.acryl.AcrylLookAndFeel");
                return;
            }

            if (theme.contentEquals("Acryl Red")) {
                String subtheme = "Red";
                props = com.jtattoo.plaf.acryl.AcrylLookAndFeel.getThemeProperties(subtheme);
                props.setProperty("windowDecoration", windowDecoration);
                if (gf.isLinux()) {
                    adjustLinuxFonts(sFont, props);
                }
                com.jtattoo.plaf.acryl.AcrylLookAndFeel.setTheme(subtheme, "", logo);
                UIManager.setLookAndFeel("com.jtattoo.plaf.acryl.AcrylLookAndFeel");
                return;
            }

            if (theme.contentEquals("Aero")) {
                String subtheme = "Default";
                props = com.jtattoo.plaf.aero.AeroLookAndFeel.getThemeProperties(subtheme);
                props.setProperty("windowDecoration", windowDecoration);
                if (gf.isLinux()) {
                    adjustLinuxFonts(sFont, props);
                }
                com.jtattoo.plaf.aero.AeroLookAndFeel.setTheme(subtheme, "", logo);
                UIManager.setLookAndFeel("com.jtattoo.plaf.aero.AeroLookAndFeel");
                return;
            }

            if (theme.contentEquals("Aero Gold")) {
                String subtheme = "Gold";
                props = com.jtattoo.plaf.aero.AeroLookAndFeel.getThemeProperties(subtheme);
                props.setProperty("windowDecoration", windowDecoration);
                if (gf.isLinux()) {
                    adjustLinuxFonts(sFont, props);
                }
                com.jtattoo.plaf.aero.AeroLookAndFeel.setTheme(subtheme, "", logo);
                UIManager.setLookAndFeel("com.jtattoo.plaf.aero.AeroLookAndFeel");
                return;
            }

            if (theme.contentEquals("Aero Green")) {
                String subtheme = "Green";
                props = com.jtattoo.plaf.aero.AeroLookAndFeel.getThemeProperties(subtheme);
                props.setProperty("windowDecoration", windowDecoration);
                if (gf.isLinux()) {
                    adjustLinuxFonts(sFont, props);
                }
                com.jtattoo.plaf.aero.AeroLookAndFeel.setTheme(subtheme, "", logo);
                UIManager.setLookAndFeel("com.jtattoo.plaf.aero.AeroLookAndFeel");
                return;
            }

            if (theme.contentEquals("Aluminium")) {
                String subtheme = "Default";
                props = com.jtattoo.plaf.aluminium.AluminiumLookAndFeel.getThemeProperties(subtheme);
                props.setProperty("windowDecoration", windowDecoration);
                if (gf.isLinux()) {
                    adjustLinuxFonts(sFont, props);
                }
                com.jtattoo.plaf.aluminium.AluminiumLookAndFeel.setTheme(subtheme, "", logo);
                UIManager.setLookAndFeel("com.jtattoo.plaf.aluminium.AluminiumLookAndFeel");
                return;
            }

            if (theme.contentEquals("Bernstein")) {
                String subtheme = "Default";
                props = com.jtattoo.plaf.bernstein.BernsteinLookAndFeel.getThemeProperties(subtheme);
                props.setProperty("windowDecoration", windowDecoration);
                if (gf.isLinux()) {
                    adjustLinuxFonts(sFont, props);
                }
                com.jtattoo.plaf.bernstein.BernsteinLookAndFeel.setTheme(subtheme, "", logo);
                UIManager.setLookAndFeel("com.jtattoo.plaf.bernstein.BernsteinLookAndFeel");
                return;
            }

            if (theme.contentEquals("HiFi")) {
                String subtheme = "Default";
                props = com.jtattoo.plaf.hifi.HiFiLookAndFeel.getThemeProperties(subtheme);
                props.setProperty("windowDecoration", windowDecoration);
                if (gf.isLinux()) {
                    adjustLinuxFonts(sFont, props);
                }
                com.jtattoo.plaf.hifi.HiFiLookAndFeel.setTheme(subtheme, "", logo);
                UIManager.setLookAndFeel("com.jtattoo.plaf.hifi.HiFiLookAndFeel");
                return;
            }

            if (theme.contentEquals("Luna")) {
                String subtheme = "Default";
                props = com.jtattoo.plaf.luna.LunaLookAndFeel.getThemeProperties(subtheme);
                props.setProperty("windowDecoration", windowDecoration);
                if (gf.isLinux()) {
                    adjustLinuxFonts(sFont, props);
                }
                com.jtattoo.plaf.luna.LunaLookAndFeel.setTheme(subtheme, "", logo);
                UIManager.setLookAndFeel("com.jtattoo.plaf.luna.LunaLookAndFeel");
                return;
            }

            if (theme.contentEquals("McWin")) {
                String subtheme = "Default";
                props = com.jtattoo.plaf.mcwin.McWinLookAndFeel.getThemeProperties(subtheme);
                props.setProperty("windowDecoration", windowDecoration);
                if (gf.isLinux()) {
                    adjustLinuxFonts(sFont, props);
                }
                com.jtattoo.plaf.mcwin.McWinLookAndFeel.setTheme(subtheme, "", logo);
                UIManager.setLookAndFeel("com.jtattoo.plaf.mcwin.McWinLookAndFeel");
                return;
            }

            if (theme.contentEquals("Mint")) {
                String subtheme = "Default";
                props = com.jtattoo.plaf.mint.MintLookAndFeel.getThemeProperties(subtheme);
                props.setProperty("windowDecoration", windowDecoration);
                if (gf.isLinux()) {
                    adjustLinuxFonts(sFont, props);
                }
                com.jtattoo.plaf.mint.MintLookAndFeel.setTheme(subtheme, "", logo);
                UIManager.setLookAndFeel("com.jtattoo.plaf.mint.MintLookAndFeel");
                return;
            }

            if (theme.contentEquals("Noire")) {
                String subtheme = "Default";
                props = com.jtattoo.plaf.noire.NoireLookAndFeel.getThemeProperties(subtheme);
                props.setProperty("windowDecoration", windowDecoration);
                if (gf.isLinux()) {
                    adjustLinuxFonts(sFont, props);
                }
                com.jtattoo.plaf.noire.NoireLookAndFeel.setTheme(subtheme, "", logo);
                UIManager.setLookAndFeel("com.jtattoo.plaf.noire.NoireLookAndFeel");
                return;
            }

            if (theme.contentEquals("Texture")) {
                String subtheme = "Default";
                props = com.jtattoo.plaf.texture.TextureLookAndFeel.getThemeProperties(subtheme);
                props.setProperty("windowDecoration", windowDecoration);
                if (gf.isLinux()) {
                    adjustLinuxFonts(sFont, props);
                }
                com.jtattoo.plaf.texture.TextureLookAndFeel.setTheme(subtheme, "", logo);
                UIManager.setLookAndFeel("com.jtattoo.plaf.texture.TextureLookAndFeel");
                return;
            }

            if (theme.contentEquals("Smart")) {
                String subtheme = "Default";
                props = com.jtattoo.plaf.smart.SmartLookAndFeel.getThemeProperties(subtheme);
                props.setProperty("windowDecoration", windowDecoration);
                if (gf.isLinux()) {
                    adjustLinuxFonts(sFont, props);
                }
                com.jtattoo.plaf.smart.SmartLookAndFeel.setTheme(subtheme, "", logo);
                UIManager.setLookAndFeel("com.jtattoo.plaf.smart.SmartLookAndFeel");
                return;
            }

            if (theme.contentEquals("Graphite")) {
                String subtheme = "Default";
                props = com.jtattoo.plaf.graphite.GraphiteLookAndFeel.getThemeProperties(subtheme);
                props.setProperty("windowDecoration", windowDecoration);
                if (gf.isLinux()) {
                    adjustLinuxFonts(sFont, props);
                }
                com.jtattoo.plaf.graphite.GraphiteLookAndFeel.setTheme(subtheme, "", logo);
                UIManager.setLookAndFeel("com.jtattoo.plaf.graphite.GraphiteLookAndFeel");
            }

        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ex) {
        }

    }

    private void setSystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
            return;
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ex) {
        }
        try {
            UIManager.setLookAndFeel("com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
            installGtkPopupBugWorkaround();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ex) {
        }
    }

    /**
     * Swing menus are looking pretty bad on Linux when the GTK LaF is used (See
     * bug #6925412). It will most likely never be fixed anytime soon so this
     * method provides a workaround for it. It uses reflection to change the GTK
     * style objects of Swing so popup menu borders have a minimum thickness of
     * 1 and menu separators have a minimum vertical thickness of 1.
     */
    public static void installGtkPopupBugWorkaround() {
        // Get current look-and-feel implementation class
        LookAndFeel laf = UIManager.getLookAndFeel();
        Class<?> lafClass = laf.getClass();

        // Do nothing when not using the problematic LaF
        if (!lafClass.getName().equals(
                "com.sun.java.swing.plaf.gtk.GTKLookAndFeel")) {
            return;
        }

        // We do reflection from here on. Failure is silently ignored. The
        // workaround is simply not installed when something goes wrong here
        try {
            // Access the GTK style factory
            Field field = lafClass.getDeclaredField("styleFactory");
            boolean accessible = field.isAccessible();
            field.setAccessible(true);
            Object styleFactory = field.get(laf);
            field.setAccessible(accessible);

            // Fix the horizontal and vertical thickness of popup menu style
            Object style = getGtkStyle(styleFactory, new JPopupMenu(),
                    "POPUP_MENU");
            fixGtkThickness(style, "yThickness");
            fixGtkThickness(style, "xThickness");

            // Fix the vertical thickness of the popup menu separator style
            style = getGtkStyle(styleFactory, new JSeparator(),
                    "POPUP_MENU_SEPARATOR");
            fixGtkThickness(style, "yThickness");
        } catch (Exception e) {
            // Silently ignored. Workaround can't be applied.
        }
    }

    /**
     * Called internally by installGtkPopupBugWorkaround to fix the thickness of
     * a GTK style field by setting it to a minimum value of 1.
     *
     * @param style The GTK style object.
     * @param fieldName The field name.
     * @throws Exception When reflection fails.
     */
    private static void fixGtkThickness(Object style, String fieldName)
            throws Exception {
        Field field = style.getClass().getDeclaredField(fieldName);
        boolean accessible = field.isAccessible();
        field.setAccessible(true);
        field.setInt(style, Math.max(1, field.getInt(style)));
        field.setAccessible(accessible);
    }

    /**
     * Called internally by installGtkPopupBugWorkaround. Returns a specific GTK
     * style object.
     *
     * @param styleFactory The GTK style factory.
     * @param component The target component of the style.
     * @param regionName The name of the target region of the style.
     * @return The GTK style.
     * @throws Exception When reflection fails.
     */
    private static Object getGtkStyle(Object styleFactory,
            JComponent component, String regionName) throws Exception {
        // Create the region object
        Class<?> regionClass = Class.forName("javax.swing.plaf.synth.Region");
        Field field = regionClass.getField(regionName);
        Object region = field.get(regionClass);

        // Get and return the style
        Class<?> styleFactoryClass = styleFactory.getClass();
        Method method = styleFactoryClass.getMethod("getStyle",
                new Class<?>[]{JComponent.class, regionClass});
        boolean accessible = method.isAccessible();
        method.setAccessible(true);
        Object style = method.invoke(styleFactory, component, region);
        method.setAccessible(accessible);
        return style;
    }

    private void adjustLinuxFonts(Font font, Properties props) {
        props.setProperty("menuTextFont", font.getFontName());
        props.setProperty("userTextFont", font.getFontName());
        props.setProperty("controlTextFont", font.getFontName());
    }

    /**
     * @param args the command line arguments
     */
    public static void main(final String args[]) {

        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                new MaNGOLin(args);
            }
        });
    }
}
