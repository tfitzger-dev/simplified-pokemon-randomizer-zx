package com.dabomstew.pkrandom.cli;

import com.dabomstew.pkrandom.FileFunctions;
import com.dabomstew.pkrandom.RandomSource;
import com.dabomstew.pkrandom.Randomizer;
import com.dabomstew.pkrandom.Settings;
import com.dabomstew.pkrandom.romhandlers.*;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.IntStream;

public class CliRandomizer {

    private final static ResourceBundle bundle = java.util.ResourceBundle.getBundle("com/dabomstew/pkrandom/newgui/Bundle");

    private static boolean performDirectRandomization(String settingsFilePath, String sourceRomFilePath,
                                                      String destinationRomFilePath, boolean saveAsDirectory,
                                                      String updateFilePath, boolean saveLog) {
        // borrowed directly from NewRandomizerGUI()
        RomHandler.Factory[] checkHandlers = new RomHandler.Factory[] {
                new Gen1RomHandler.Factory(),
                new Gen2RomHandler.Factory(),
                new Gen3RomHandler.Factory()
        };

        Settings settings;
        try {
            File fh = new File(settingsFilePath);
            FileInputStream fis = new FileInputStream(fh);
            settings = Settings.read(fis);
            // taken from com.dabomstew.pkrandom.newgui.NewRandomizerGUI.saveROM, set distinctly from all other settings
            settings.setCustomNames(FileFunctions.getCustomNames());
            fis.close();
        } catch (UnsupportedOperationException | IllegalArgumentException | IOException ex) {
            ex.printStackTrace();
            return false;
        }

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream log;
        try {
            log = new PrintStream(baos, false, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log = new PrintStream(baos);
        }

        final PrintStream verboseLog = log;

        try {
            File romFileHandler = new File(sourceRomFilePath);
            RomHandler romHandler;

            for (RomHandler.Factory rhf : checkHandlers) {
                if (rhf.isLoadable(romFileHandler.getAbsolutePath())) {
                    romHandler = rhf.create(RandomSource.instance());
                    romHandler.loadRom(romFileHandler.getAbsolutePath());

                    File fh = new File(destinationRomFilePath);
                    if (!saveAsDirectory) {
                        List<String> extensions = new ArrayList<>(Arrays.asList("sgb", "gbc", "gba", "nds", "cxi"));
                        extensions.remove(romHandler.getDefaultExtension());

                        fh = FileFunctions.fixFilename(fh, romHandler.getDefaultExtension(), extensions);
                    }

                    String filename = fh.getAbsolutePath();

                    Randomizer randomizer = new Randomizer(settings, romHandler);
                    randomizer.randomize(filename, verboseLog);
                    verboseLog.close();
                    byte[] out = baos.toByteArray();

                    System.out.println("Randomized successfully!");
                    // this is the only successful exit, everything else will return false at the end of the function
                    return true;
                }
            }
            // if we get here it means no rom handlers matched the ROM file
            System.err.printf(bundle.getString("GUI.unsupportedRom") + "%n", romFileHandler.getName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static int invoke(String[] args) {
        String settingsFilePath = null;
        String sourceRomFilePath = null;
        String outputRomFilePath = null;
        boolean saveAsDirectory = false;
        String updateFilePath = null;
        boolean saveLog = false;

        List<String> allowedFlags = Arrays.asList("-i", "-o", "-s", "-d", "-u", "-l", "--help");
        for (int i = 0; i < args.length; i++) {
            if (allowedFlags.contains(args[i])) {
                switch(args[i]) {
                    case "-i":
                        sourceRomFilePath = args[i + 1];
                        break;
                    case "-o":
                        outputRomFilePath = args[i + 1];
                        break;
                    case "-s":
                        settingsFilePath = args[i + 1];
                        break;
                    default:
                        break;
                }
            }
        }

        boolean processResult = CliRandomizer.performDirectRandomization(
                settingsFilePath,
                sourceRomFilePath,
                outputRomFilePath,
                saveAsDirectory,
                updateFilePath,
                saveLog
        );
        return 0;
    }

    private static void printWarning(String text) {
        System.err.println("WARNING: " + text);
    }

    private static void printUsage() {
        System.err.println("Usage: java [-Xmx4096M] -jar PokeRandoZX.jar cli -s <path to settings file> " +
                "-i <path to source ROM> -o <path for new ROM> [-d][-u <path to 3DS game update>][-l]");
        System.err.println("-d: Save 3DS game as directory (LayeredFS)");
    }

    public static void main(String[] args) {
        String baseDir = "D:\\pkmn";
        File settingsDir = new File(baseDir + "\\settings");
        File sourceDir = new File(baseDir + "\\source");
        String randomizedDir = baseDir + "\\randomized\\";

        Arrays.stream(sourceDir.listFiles()).forEach(genDir -> {
            int iters = (int) Math.max(Math.ceil(Integer.parseInt(args[0]) / genDir.listFiles().length / settingsDir.listFiles().length), 1);
            Arrays.stream(genDir.listFiles()).forEach(srcFile -> {
                Arrays.stream(settingsDir.listFiles()).forEach(settings -> {
                    IntStream.range(0, iters).forEach((idx) -> {
                        System.out.print(String.format("[%s] %s - %s (%d/%d): ", genDir.getName(), srcFile.getName(), settings.getName().split("\\.")[0], idx + 1, iters));
                        invoke(new String[]{"-s", settings.getAbsolutePath(), "-i", srcFile.getAbsolutePath(), "-o", randomizedDir + srcFile.getName()});
                    });
                });

            });

        });

    }
}
