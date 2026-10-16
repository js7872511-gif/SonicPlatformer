package com.sonicredux.reborn;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import com.sonicredux.reborn.core.ModManifest;
import com.sonicredux.reborn.core.ZipOverlayBuilder;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipFile;

public class MainActivity extends Activity {
    private static final int PICK_BASE = 1001;
    private static final int PICK_MOD = 1002;
    private static final String ORIGINAL_PACKAGE = "com.SpecialTeams.SonicRedux";
    private static final String REBORN_GAME_PACKAGE = "com.RebornXTeams.SonicRedux";
    private static final String PREFS = "reborn_mods";

    private TextView status;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Sonic Redux Re-born");
        setContentView(buildUi());
        updateStatus();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(8, 15, 30));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(26), dp(22), dp(26));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("SONIC REDUX\nRE-BORN");
        title.setTextColor(Color.WHITE);
        title.setTextSize(32);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText("Android Mod Manager • v0.1");
        subtitle.setTextColor(Color.rgb(110, 200, 255));
        subtitle.setTextSize(15);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, dp(6), 0, dp(24));
        root.addView(subtitle, subLp);

        status = new TextView(this);
        status.setTextColor(Color.rgb(220, 230, 245));
        status.setTextSize(14);
        status.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        root.addView(button("1. SELECT ORIGINAL SONIC REDUX APK", v -> chooseBase()));
        root.addView(button("2. IMPORT MOD", v -> chooseMod()));
        root.addView(button("INSTALLED MODS", v -> showMods()));
        root.addView(button("BUILD / INSTALL RE-BORN", v -> buildAndInstall()));
        root.addView(button("PLAY RE-BORN", v -> launchPackage(REBORN_GAME_PACKAGE)));
        root.addView(button("PLAY ORIGINAL", v -> launchPackage(ORIGINAL_PACKAGE)));

        TextView note = new TextView(this);
        note.setText("Your original APK is copied into this app's private storage and never edited in place. Every mod build starts from that clean copy.");
        note.setTextColor(Color.rgb(150, 165, 190));
        note.setTextSize(12);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
        noteLp.setMargins(0, dp(18), 0, 0);
        root.addView(note, noteLp);
        return scroll;
    }

    private Button button(String text, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(58));
        lp.setMargins(0, dp(9), 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void chooseBase() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/vnd.android.package-archive");
        startActivityForResult(i, PICK_BASE);
    }

    private void chooseMod() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, PICK_MOD);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == PICK_BASE) importBase(uri);
        else if (requestCode == PICK_MOD) importMod(uri);
    }

    private void importBase(Uri uri) {
        setStatus("Checking Sonic Redux APK…");
        worker.execute(() -> {
            try {
                File dir = rebornDir();
                File tmp = new File(dir, "clean-base.tmp.apk");
                copyUri(uri, tmp);
                validateBase(tmp);
                File base = new File(dir, "clean-base.apk");
                replaceFile(tmp, base);
                runOnUiThread(() -> { toast("Clean Sonic Redux base saved."); updateStatus(); });
            } catch (Exception e) { showError("Base APK rejected", e); }
        });
    }

    private void importMod(Uri uri) {
        setStatus("Validating mod package…");
        worker.execute(() -> {
            File tmp = null;
            try {
                File mods = modsDir();
                tmp = new File(mods, "import.tmp");
                copyUri(uri, tmp);
                ModManifest manifest = ZipOverlayBuilder.validateMod(tmp);
                String safe = safeName(manifest.id) + "__" + safeName(manifest.version) + ".sreduxmod";
                File destination = new File(mods, safe);
                replaceFile(tmp, destination);
                prefs().edit().putBoolean("enabled:" + destination.getName(), true).apply();
                runOnUiThread(() -> { toast("Imported " + manifest.name); updateStatus(); });
            } catch (Exception e) {
                if (tmp != null) tmp.delete();
                showError("Mod rejected", e);
            }
        });
    }

    private void showMods() {
        File[] mods = listMods();
        if (mods.length == 0) { toast("No mods imported yet."); return; }
        String[] labels = new String[mods.length];
        boolean[] checked = new boolean[mods.length];
        for (int i = 0; i < mods.length; i++) {
            try {
                ModManifest m = ZipOverlayBuilder.validateMod(mods[i]);
                labels[i] = m.name + "  v" + m.version + "\nby " + m.author;
            } catch (Exception e) { labels[i] = mods[i].getName() + " (invalid)"; }
            checked[i] = prefs().getBoolean("enabled:" + mods[i].getName(), true);
        }
        new AlertDialog.Builder(this)
                .setTitle("Installed Mods")
                .setMultiChoiceItems(labels, checked, (d, which, enabled) -> {
                    prefs().edit().putBoolean("enabled:" + mods[which].getName(), enabled).apply();
                    updateStatus();
                })
                .setPositiveButton("Done", null)
                .show();
    }

    private void buildAndInstall() {
        File base = new File(rebornDir(), "clean-base.apk");
        if (!base.isFile()) { toast("Select your original Sonic Redux APK first."); return; }
        setStatus("Building Re-born from the clean base…");
        worker.execute(() -> {
            try {
                List<File> enabled = new ArrayList<>();
                for (File f : listMods()) if (prefs().getBoolean("enabled:" + f.getName(), true)) enabled.add(f);
                File workUnsigned = new File(getCacheDir(), "reborn-unsigned.apk");
                File workSigned = new File(getCacheDir(), "reborn-signed.apk");
                workUnsigned.delete(); workSigned.delete();
                ZipOverlayBuilder.build(base, enabled, workUnsigned, ORIGINAL_PACKAGE, REBORN_GAME_PACKAGE);
                RebornSigner.sign(this, workUnsigned, workSigned);
                File promote = new File(rebornDir(), "last-successful.tmp.apk");
                copyFile(workSigned, promote);
                File last = new File(rebornDir(), "last-successful.apk");
                replaceFile(promote, last);
                runOnUiThread(() -> { setStatus("Build complete. Opening Android installer…"); requestInstall(last); });
            } catch (Exception e) { showError("Build failed — previous successful build was kept", e); }
        });
    }

    private void requestInstall(File apk) {
        if (android.os.Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
            startActivity(settings);
            toast("Allow installs for Re-born, then tap BUILD / INSTALL again.");
            return;
        }
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", apk);
        Intent install = new Intent(Intent.ACTION_VIEW);
        install.setDataAndType(uri, "application/vnd.android.package-archive");
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(install);
    }

    private void launchPackage(String packageName) {
        Intent i = getPackageManager().getLaunchIntentForPackage(packageName);
        if (i == null) { toast(packageName.equals(REBORN_GAME_PACKAGE) ? "Build and install Re-born first." : "Original Sonic Redux is not installed."); return; }
        startActivity(i);
    }

    private void validateBase(File apk) throws Exception {
        String[] required = {
                "AndroidManifest.xml", "resources.arsc",
                "lib/arm64-v8a/libunity.so", "lib/arm64-v8a/libil2cpp.so", "lib/arm64-v8a/libmain.so",
                "lib/armeabi-v7a/libunity.so", "lib/armeabi-v7a/libil2cpp.so", "lib/armeabi-v7a/libmain.so",
                "assets/bin/Data/Managed/Metadata/global-metadata.dat", "assets/bin/Data/data.unity3d"
        };
        try (ZipFile z = new ZipFile(apk)) {
            for (String p : required) if (z.getEntry(p) == null) throw new IllegalArgumentException("Not the expected Redux build; missing " + p);
            byte[] manifest = readAll(z.getInputStream(z.getEntry("AndroidManifest.xml")));
            byte[] pkg = ORIGINAL_PACKAGE.getBytes(StandardCharsets.UTF_16LE);
            if (!contains(manifest, pkg)) throw new IllegalArgumentException("APK package is not " + ORIGINAL_PACKAGE);
        }
    }

    private static boolean contains(byte[] data, byte[] pattern) {
        outer: for (int i = 0; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) if (data[i + j] != pattern[j]) continue outer;
            return true;
        }
        return false;
    }

    private File rebornDir() {
        File d = new File(getFilesDir(), "reborn");
        if (!d.exists() && !d.mkdirs()) throw new IllegalStateException("Could not create Re-born storage");
        return d;
    }

    private File modsDir() {
        File d = new File(rebornDir(), "mods");
        if (!d.exists() && !d.mkdirs()) throw new IllegalStateException("Could not create mods storage");
        return d;
    }

    private File[] listMods() {
        File[] files = modsDir().listFiles((dir, name) -> name.endsWith(".sreduxmod") || name.endsWith(".zip"));
        if (files == null) return new File[0];
        Arrays.sort(files, Comparator.comparing(File::getName));
        return files;
    }

    private SharedPreferences prefs() { return getSharedPreferences(PREFS, MODE_PRIVATE); }

    private void copyUri(Uri uri, File out) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri); FileOutputStream fos = new FileOutputStream(out)) {
            if (in == null) throw new IllegalArgumentException("Unable to open selected file");
            byte[] buf = new byte[64 * 1024]; int n;
            while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
        }
    }

    private static void copyFile(File in, File out) throws Exception {
        try (FileInputStream fis = new FileInputStream(in); FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[64 * 1024]; int n;
            while ((n = fis.read(buf)) != -1) fos.write(buf, 0, n);
            fos.getFD().sync();
        }
    }

    private static void replaceFile(File source, File dest) throws Exception {
        File backup = new File(dest.getParentFile(), dest.getName() + ".backup");
        backup.delete();
        if (dest.exists() && !dest.renameTo(backup)) throw new IllegalStateException("Could not protect previous file");
        if (!source.renameTo(dest)) {
            if (backup.exists()) backup.renameTo(dest);
            throw new IllegalStateException("Could not promote new file");
        }
        backup.delete();
    }

    private static byte[] readAll(InputStream in) throws Exception {
        try (InputStream stream = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192]; int n;
            while ((n = stream.read(b)) != -1) out.write(b, 0, n);
            return out.toByteArray();
        }
    }

    private static String safeName(String s) { return s.replaceAll("[^A-Za-z0-9._-]", "_"); }

    private void updateStatus() {
        if (status == null) return;
        boolean base = new File(rebornDir(), "clean-base.apk").isFile();
        int total = listMods().length, enabled = 0;
        for (File f : listMods()) if (prefs().getBoolean("enabled:" + f.getName(), true)) enabled++;
        status.setText((base ? "✓ Clean Redux base ready" : "○ Original Redux APK not selected") + "\n" + enabled + " / " + total + " mods enabled");
    }

    private void setStatus(String text) { if (status != null) status.setText(text); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    private void showError(String title, Exception e) { runOnUiThread(() -> { setStatus(title + ": " + e.getMessage()); new AlertDialog.Builder(this).setTitle(title).setMessage(e.getMessage()).setPositiveButton("OK", null).show(); }); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
