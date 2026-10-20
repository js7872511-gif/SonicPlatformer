from pathlib import Path

ROOT = Path('winlator-src')

# Give the prototype a fresh Android package so it can install beside Winlator
# and beside the first EXE→APK test build.
build_gradle = ROOT / 'app/build.gradle'
s = build_gradle.read_text()
s = s.replace("applicationId 'com.winlator'", "applicationId 'com.exetoapk.robloxstudiotest2'")
s = s.replace('versionName "11.2"', 'versionName "EXE-APK-Test-0.2"')
build_gradle.write_text(s)

# Winlator hard-codes its FileProvider authority. Android requires provider
# authorities to be unique across installed apps, so using Winlator's original
# authority causes INSTALL_FAILED_CONFLICTING_PROVIDER when normal Winlator is
# already installed. Give this wrapper its own authority everywhere.
provider_authority = 'com.exetoapk.robloxstudiotest2.FileProvider'
manifest = ROOT / 'app/src/main/AndroidManifest.xml'
s = manifest.read_text()
s = s.replace('android:authorities="com.winlator.FileProvider"', f'android:authorities="{provider_authority}"')
manifest.write_text(s)

file_utils = ROOT / 'app/src/main/java/com/winlator/core/FileUtils.java'
s = file_utils.read_text()
s = s.replace('"com.winlator.FileProvider"', f'"{provider_authority}"')
file_utils.write_text(s)

# Rename the visible app without touching the Java namespace.
strings = ROOT / 'app/src/main/res/values/strings.xml'
s = strings.read_text()
s = s.replace('>Winlator<', '>Roblox Studio EXE→APK Test<', 1)
strings.write_text(s)

# Add a tiny auto-launch helper. It waits for Winlator's bundled rootfs to be
# installed, creates one default container, copies the embedded EXE to C:, and
# launches it through Winlator's XServerDisplayActivity.
auto = ROOT / 'app/src/main/java/com/winlator/AutoLauncher.java'
auto.write_text(r'''package com.winlator;

import android.content.Intent;

import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.core.FileUtils;
import com.winlator.xenvironment.RootFS;
import com.winlator.xenvironment.RootFSInstaller;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AutoLauncher {
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    private AutoLauncher() {}

    public static void startWhenReady(final MainActivity activity) {
        if (STARTED.get()) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            for (int i = 0; i < 300 && !STARTED.get(); i++) {
                try {
                    RootFS rootFS = RootFS.find(activity);
                    if (rootFS.isValid() && rootFS.getVersion() >= RootFSInstaller.LATEST_VERSION) {
                        activity.runOnUiThread(() -> ensureContainer(activity));
                        return;
                    }
                    Thread.sleep(1000);
                }
                catch (Throwable ignored) {
                    try { Thread.sleep(1000); } catch (InterruptedException e) { return; }
                }
            }
        });
    }

    private static void ensureContainer(final MainActivity activity) {
        if (STARTED.get()) return;
        try {
            final ContainerManager manager = new ContainerManager(activity);
            if (!manager.getContainers().isEmpty()) {
                prepareAndLaunch(activity, manager.getContainers().get(0));
                return;
            }

            JSONObject data = new JSONObject();
            data.put("name", "Roblox Studio Installer");
            data.put("wincomponents", Container.DEFAULT_WINCOMPONENTS);
            manager.createContainerAsync(data, container -> {
                if (container != null) prepareAndLaunch(activity, container);
            });
        }
        catch (Throwable t) {
            STARTED.set(false);
        }
    }

    private static void prepareAndLaunch(final MainActivity activity, final Container container) {
        if (!STARTED.compareAndSet(false, true)) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                File driveC = new File(container.getRootDir(), ".wine/drive_c");
                if (!driveC.isDirectory() && !driveC.mkdirs()) throw new Exception("Could not create drive C");

                File exe = new File(driveC, "RobloxStudioInstaller.exe");
                try (InputStream input = activity.getAssets().open("RobloxStudioInstaller.exe");
                     FileOutputStream output = new FileOutputStream(exe, false)) {
                    byte[] buffer = new byte[1024 * 1024];
                    int count;
                    while ((count = input.read(buffer)) > 0) output.write(buffer, 0, count);
                }

                File desktop = new File(container.getUserDir(), "Desktop");
                if (!desktop.isDirectory()) desktop.mkdirs();
                File shortcut = new File(desktop, "RobloxStudioInstaller.desktop");
                String content = "[Desktop Entry]\n" +
                        "Name=Roblox Studio Installer\n" +
                        "Exec=env WINEDEBUG=-all wine C:\\\\RobloxStudioInstaller.exe\n" +
                        "Type=Application\n" +
                        "StartupNotify=true\n";
                FileUtils.writeString(shortcut, content);

                activity.runOnUiThread(() -> {
                    Intent intent = new Intent(activity, XServerDisplayActivity.class);
                    intent.putExtra("container_id", container.id);
                    intent.putExtra("shortcut_path", shortcut.getAbsolutePath());
                    activity.startActivity(intent);
                });
            }
            catch (Throwable t) {
                STARTED.set(false);
                activity.runOnUiThread(() ->
                        android.widget.Toast.makeText(activity,
                                "EXE→APK test failed to prepare the embedded EXE: " + t.getMessage(),
                                android.widget.Toast.LENGTH_LONG).show());
            }
        });
    }
}
''')

main = ROOT / 'app/src/main/java/com/winlator/MainActivity.java'
s = main.read_text()
old = 'if (!requestAppPermissions()) RootFSInstaller.installIfNeeded(this);'
new = '''if (!requestAppPermissions()) {
                RootFSInstaller.installIfNeeded(this);
                AutoLauncher.startWhenReady(this);
            }'''
if old not in s:
    raise SystemExit('MainActivity startup anchor not found')
s = s.replace(old, new, 1)

old2 = '''if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                RootFSInstaller.installIfNeeded(this);
            }'''
new2 = '''if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                RootFSInstaller.installIfNeeded(this);
                AutoLauncher.startWhenReady(this);
            }'''
if old2 not in s:
    raise SystemExit('MainActivity permission anchor not found')
s = s.replace(old2, new2, 1)
main.write_text(s)

print('Patched Winlator source for EXE→APK prototype v0.2')
