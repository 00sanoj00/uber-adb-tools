package at.favre.tools.uberadb.actions;

import at.favre.tools.uberadb.AdbLocationFinder;
import at.favre.tools.uberadb.AdbTool;
import at.favre.tools.uberadb.parser.AdbDevice;
import at.favre.tools.uberadb.parser.PackageMatcher;
import at.favre.tools.uberadb.ui.Arg;
import at.favre.tools.uberadb.util.CmdUtil;
import at.favre.tools.uberadb.util.MiscUtil;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

public class BugReport {
    public static void create(AdbLocationFinder.LocationResult adbLocation, Arg arguments, List<CmdUtil.Result> executedCommands, AdbDevice device, List<String> allPackages) throws Exception {
        Commons.logLoud("create bug report:");

        String dateTimeString = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS").format(new Date());

        File outFolder;
        if (arguments.mainArgument != null && !arguments.mainArgument.isEmpty()) {
            outFolder = new File(arguments.mainArgument);
            if (!outFolder.exists() && !outFolder.mkdirs()) {
                throw new IllegalStateException("could not create directory " + arguments.mainArgument);
            }
        } else {
            outFolder = new File(AdbTool.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
        }

        if (arguments.reportFilterIntent != null && arguments.reportFilterIntent.length >= 2) {
            Set<String> filteredPackages = new PackageMatcher(allPackages).findMatches(
                    PackageMatcher.parseFiltersArg(arguments.reportFilterIntent[0]));

            for (String filteredPackage : filteredPackages) {
                String[] copy = Arrays.copyOfRange(arguments.reportFilterIntent, 1, arguments.reportFilterIntent.length);
                for (int i = 0; i < copy.length; i++) {
                    copy[i] = copy[i].replace("${package}", filteredPackage);
                }

                CmdUtil.Result uninstallCmdResult = Commons.runAdbCommand(CmdUtil.concat(new String[]{"-s", device.serial, "shell", "am",}, copy), adbLocation);
                executedCommands.add(uninstallCmdResult);

                String intentStatus = "\texecute command for " + filteredPackage + " - adb shell am " + Arrays.toString(copy);
                Commons.log(intentStatus, arguments);
                Thread.sleep(100);
            }
        }

        String tempFileScreenshot = "/sdcard/bugreport_tempfile_screenshot.png";
        String tempFileLogcat = "/sdcard/bugreport_tempfile_logcat";
        File localTempFileScreenshot = new File(outFolder.getAbsolutePath(), "screen-" + device.model + "-" + dateTimeString + ".png");
        File localTempFileLogcat = new File(outFolder.getAbsolutePath(), "logcat-" + device.model + "-" + dateTimeString + ".txt");
        File zipFile = new File(outFolder, "bugreport-" + device.model + "-" + dateTimeString + ".zip");

        Commons.log("\twake up screen and take screenshot", arguments);
        executedCommands.add(Commons.runAdbCommand(new String[]{"-s", device.serial, "shell", "input", "keyevent", "KEYCODE_WAKEUP"}, adbLocation));
        executedCommands.add(Commons.runAdbCommand(new String[]{"-s", device.serial, "shell", "screencap", tempFileScreenshot}, adbLocation));
        executedCommands.add(Commons.runAdbCommand(new String[]{"-s", device.serial, "pull", tempFileScreenshot, localTempFileScreenshot.getAbsolutePath()}, adbLocation));
        Commons.log("\tcreate logcat file and pull from device", arguments);
        executedCommands.add(Commons.runAdbCommand(new String[]{"-s", device.serial, "logcat", "-d", "-f", tempFileLogcat}, adbLocation));
        executedCommands.add(Commons.runAdbCommand(new String[]{"-s", device.serial, "pull", tempFileLogcat, localTempFileLogcat.getAbsolutePath()}, adbLocation));
        Commons.log(String.format(Locale.US, "\t%.2fkB screenshot, %.2fkB logcat",
                (double) localTempFileScreenshot.length() / 1024.0, (double) localTempFileLogcat.length() / 1024.0), arguments);
        executedCommands.add(Commons.runAdbCommand(new String[]{"-s", device.serial, "shell", "rm", "-f", tempFileScreenshot}, adbLocation));
        executedCommands.add(Commons.runAdbCommand(new String[]{"-s", device.serial, "shell", "rm", "-f", tempFileLogcat}, adbLocation));

        MiscUtil.zip(zipFile, Arrays.asList(localTempFileScreenshot, localTempFileLogcat));
        localTempFileScreenshot.delete();
        localTempFileLogcat.delete();

        if (!zipFile.exists()) {
            throw new IllegalStateException("could not create zip file " + zipFile);
        }
        Commons.log(String.format(Locale.US, "\ttemp files removed and zip %s (%.2fkB) created", zipFile.getAbsolutePath(), (double) zipFile.length() / 1024.0), arguments);
    }
}
