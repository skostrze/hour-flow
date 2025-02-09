package pl.com.slaw;

import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.SystemTray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.imageio.ImageIO;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;



public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static SystemTray systemTray;
    private static final String ICON_PATH = "/hour-flow.png";
    private static ScheduledExecutorService scheduler;
    private static final Preferences prefs = Preferences.userNodeForPackage(Main.class);
    private static final String AUDIO_PATH = "/notification.wav";


    static {
        try {
            setupSystemTray();
        } catch (InterruptedException e) {
            logger.error("Error setting up system tray", e);
        }

    }


    private static void setupSystemTray() throws InterruptedException {

        String os = System.getProperty("os.name");
        logger.info("OS: {}", os);

        if(os.toLowerCase().contains("windows")) {
            SystemTray.FORCE_TRAY_TYPE = SystemTray.TrayType.WindowsNative;
        } else if(os.toLowerCase().contains("mac")) {
            SystemTray.FORCE_TRAY_TYPE = SystemTray.TrayType.Osx;
        } else if(os.toLowerCase().contains("linux")) {
            SystemTray.FORCE_TRAY_TYPE = SystemTray.TrayType.Gtk;
        }

//        sendNotification("HourFlow", "HourFlow started");
        if(prefs.getBoolean("hourDelayEnabled", true))
            TimeUnit.MINUTES.sleep(1);

        systemTray = SystemTray.get();



        if (systemTray == null) {
            logger.warn("SystemTray is not supported");
            sendNotification("HourFlow Error", "SystemTray is not supported. If system is linx try to install libappindicator3-1");
            return;
        }

        try (InputStream is = Main.class.getResourceAsStream(ICON_PATH)) {
            if (is == null) {
                logger.error("Failed to load tray icon: {}", ICON_PATH);
                return;
            }
            BufferedImage icon = ImageIO.read(is);
            systemTray.setImage(icon);

        } catch (IOException e) {
            logger.error("Error loading tray icon", e);
        }

        systemTray.getMenu().add(new MenuItem("Settings", e -> openSettings()));
        systemTray.getMenu().add(new MenuItem("Logs", e -> openLogsFolder()));
        systemTray.getMenu().add(new MenuItem("Exit", e -> exitApplication()));

    }

    private static void sendNotification(String title, String message) {
        logger.info("Sending notification: {} - {}", title, message);

        try {

            if(prefs.getBoolean("hourSoundNotification", true)){
                playSound();
            }

            Settings settings = new Settings();
            settings.sendNotification(title, message);
            settings.dispose();

        } catch (Exception e) {
            logger.error("Failed to send notification", e);
        }
    }

    private static void playSound() {
        try (InputStream audioSrc = Main.class.getResourceAsStream(AUDIO_PATH);  InputStream bufferedIn = new BufferedInputStream(audioSrc);
             AudioInputStream audioStream = AudioSystem.getAudioInputStream(bufferedIn)) {
            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);
            clip.start();
        } catch (Exception e) {
            logger.error("Error playing sound", e);
        }
    }

    private static void exitApplication() {
        logger.info("Exiting application...");
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        if (systemTray != null) {
            systemTray.shutdown();
        }
        logger.info("Application stopped");
        System.exit(0);
    }
    private static void openLogsFolder() {
        try {
            File logFile = new File("logs/hourflow.log").getAbsoluteFile();
            File logDir = logFile.getParentFile();
            java.awt.Desktop.getDesktop().open(logDir);
        } catch (Exception e) {
            sendNotification("Error", "Failed to open logs folder");
        }
    }

    private static void openSettings() {
        SwingUtilities.invokeLater(() -> {
            try {
                Settings settings = new Settings();
                settings.setVisible(true);
            } catch (Exception e) {
                logger.error("Failed to open settings window", e);
            }
        });

    }

    public static void startScheduler() {

        int minutes = prefs.getInt("hourThreshold", 60);
        String message = prefs.get("hourText", "Start your activities");

        logger.info("Starting scheduler: {} minutes, message: {}", minutes, message);

        Duration interval = Duration.ofMinutes(minutes);

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> sendNotification("HourFlow", message), interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(scheduler::shutdownNow));
    }

    public static void main(String[] args) {
        startScheduler();
    }
}