package com.ecarx.mcurootflasher;

import android.Manifest;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilderFactory;

public class MainActivity extends Activity {
    private static final int PERMISSION_REQUEST = 717;
    private static final String LOG_PATH = "/sdcard/mcu_root_flasher.log";
    private static final String ECARX_WH_CERT_SHA256 = "FC:B2:6D:E3:0D:03:3A:1C:16:63:F6:0A:7F:06:17:35:7B:AC:59:C1:E0:7F:75:10:C5:A7:32:87:51:C7:05:1D";

    private EditText pathEdit;
    private TextView packageView;
    private TextView logView;
    private CheckBox confirmBox;
    private Button flashButton;

    private PackageInfo currentPackage;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        buildUi();
        requestStoragePermissionIfNeeded();
        setPath(firstExistingCandidate());
        appendLog("Ready");
        inspectSelectedPackage();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setPadding(0, 0, dp(12), 0);
        root.addView(left, new LinearLayout.LayoutParams(dp(520), -1));

        TextView title = new TextView(this);
        title.setText("MCU Root Flasher");
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        left.addView(title, new LinearLayout.LayoutParams(-1, -2));

        pathEdit = new EditText(this);
        pathEdit.setSingleLine(true);
        pathEdit.setTextSize(14);
        pathEdit.setInputType(InputType.TYPE_CLASS_TEXT);
        pathEdit.setHint("/storage/udisk/mcu_update.zip");
        left.addView(pathEdit, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setPadding(0, dp(8), 0, 0);
        left.addView(row1, new LinearLayout.LayoutParams(-1, -2));

        Button scanButton = button("Scan");
        row1.addView(scanButton, new LinearLayout.LayoutParams(0, dp(52), 1f));
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String found = firstExistingCandidate();
                setPath(found);
                appendLog(found.length() == 0 ? "No mcu_update.zip found in common paths" : "Found " + found);
                inspectSelectedPackage();
            }
        });

        Button inspectButton = button("Inspect ZIP");
        row1.addView(inspectButton, new LinearLayout.LayoutParams(0, dp(52), 1f));
        inspectButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                inspectSelectedPackage();
            }
        });

        Button rootButton = button("Root Check");
        row1.addView(rootButton, new LinearLayout.LayoutParams(0, dp(52), 1f));
        rootButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                runRootCheck();
            }
        });

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0, dp(8), 0, 0);
        left.addView(row2, new LinearLayout.LayoutParams(-1, -2));

        Button prepButton = button("Prepare /tmp");
        row2.addView(prepButton, new LinearLayout.LayoutParams(0, dp(52), 1f));
        prepButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prepareOnly();
            }
        });

        Button releaseButton = button("Release UART");
        row2.addView(releaseButton, new LinearLayout.LayoutParams(0, dp(52), 1f));
        releaseButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                releaseUartOnly();
            }
        });

        Button clearButton = button("Clear Log");
        row2.addView(clearButton, new LinearLayout.LayoutParams(0, dp(52), 1f));
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                logView.setText("");
                appendLog("Log cleared");
            }
        });

        confirmBox = new CheckBox(this);
        confirmBox.setText("I understand this will flash MCU firmware");
        confirmBox.setTextSize(14);
        confirmBox.setPadding(0, dp(10), 0, 0);
        left.addView(confirmBox, new LinearLayout.LayoutParams(-1, -2));

        flashButton = button("FLASH MCU");
        flashButton.setTextSize(18);
        left.addView(flashButton, new LinearLayout.LayoutParams(-1, dp(64)));
        flashButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                flashMcu();
            }
        });

        packageView = new TextView(this);
        packageView.setTextSize(12);
        packageView.setPadding(0, dp(10), 0, 0);
        left.addView(packageView, new LinearLayout.LayoutParams(-1, 0, 1f));

        ScrollView scroll = new ScrollView(this);
        root.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1f));

        logView = new TextView(this);
        logView.setTextSize(12);
        logView.setTextIsSelectable(true);
        scroll.addView(logView, new ScrollView.LayoutParams(-1, -2));

        setContentView(root);
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        return b;
    }

    private void requestStoragePermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, PERMISSION_REQUEST);
        }
    }

    private void setPath(String path) {
        if (path == null) {
            path = "";
        }
        pathEdit.setText(path);
        pathEdit.setSelection(pathEdit.getText().length());
    }

    private String firstExistingCandidate() {
        for (String path : candidatePaths()) {
            if (new File(path).isFile()) {
                return path;
            }
        }
        return "";
    }

    private List<String> candidatePaths() {
        ArrayList<String> paths = new ArrayList<String>();
        paths.add("/storage/udisk/mcu_update.zip");
        paths.add("/storage/udisk1/mcu_update.zip");
        paths.add("/storage/udisk2/mcu_update.zip");
        paths.add("/storage/usb0/mcu_update.zip");
        paths.add("/mnt/media_rw/udisk/mcu_update.zip");
        paths.add("/mnt/media_rw/udisk1/mcu_update.zip");
        paths.add(Environment.getExternalStorageDirectory().getAbsolutePath() + "/mcu_update.zip");
        paths.add(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download/mcu_update.zip");
        return paths;
    }

    private void inspectSelectedPackage() {
        final String path = pathEdit.getText().toString().trim();
        runAsync(new Runnable() {
            @Override public void run() {
                try {
                    PackageInfo info = inspectPackage(new File(path), true);
                    currentPackage = info;
                    showPackage(info);
                    appendLog("Inspected " + path);
                } catch (Exception e) {
                    currentPackage = null;
                    showPackage(null);
                    appendLog("Inspect failed: " + e.getMessage());
                }
            }
        });
    }

    private PackageInfo inspectPackage(File zipFile, boolean extract) throws Exception {
        if (zipFile == null || !zipFile.isFile()) {
            throw new IOException("ZIP file not found");
        }

        PackageInfo info = new PackageInfo();
        info.zipPath = zipFile.getAbsolutePath();
        info.zipSize = zipFile.length();
        info.zipSha256 = sha256(zipFile);
        fillSignatureInfo(zipFile, info);

        File outDir = new File(getCacheDir(), "mcu_payload");
        if (extract) {
            deleteTree(outDir);
            if (!outDir.mkdirs() && !outDir.isDirectory()) {
                throw new IOException("Cannot create cache dir");
            }
        }

        ZipFile zip = new ZipFile(zipFile);
        try {
            ZipEntry manifestEntry = findEntry(zip, "Manifest_SYS.xml");
            if (manifestEntry == null) {
                throw new IOException("Manifest_SYS.xml not found");
            }
            byte[] manifestBytes = readAll(zip.getInputStream(manifestEntry));
            info.manifestXml = new String(manifestBytes, "UTF-8");
            info.project = xmlText(manifestBytes, "project");
            info.systemVersionName = xmlText(manifestBytes, "systemVersionName");
            info.systemVersionCode = xmlText(manifestBytes, "systemVersionCode");

            ZipEntry appEntry = findEntry(zip, "fsl_app.s19");
            ZipEntry upgradeEntry = findEntry(zip, "upgrade.dat");
            if (appEntry == null) {
                throw new IOException("fsl_app.s19 not found");
            }
            if (upgradeEntry == null) {
                throw new IOException("upgrade.dat not found");
            }

            if (extract) {
                info.fslApp = new File(outDir, "fsl_app.s19");
                info.upgradeDat = new File(outDir, "upgrade.dat");
                copy(zip.getInputStream(appEntry), info.fslApp);
                copy(zip.getInputStream(upgradeEntry), info.upgradeDat);
                info.fslSha256 = sha256(info.fslApp);
                info.upgradeSha256 = sha256(info.upgradeDat);
            }

            info.fslSize = appEntry.getSize();
            info.upgradeSize = upgradeEntry.getSize();
            return info;
        } finally {
            zip.close();
        }
    }

    private ZipEntry findEntry(ZipFile zip, String name) {
        ZipEntry exact = zip.getEntry(name);
        if (exact != null) {
            return exact;
        }
        return zip.getEntry("/" + name);
    }

    private String xmlText(byte[] xmlBytes, String tag) {
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(xmlBytes));
            NodeList list = doc.getElementsByTagName(tag);
            if (list.getLength() == 0) {
                return "";
            }
            return list.item(0).getTextContent().trim();
        } catch (Exception e) {
            return "";
        }
    }

    private void showPackage(final PackageInfo info) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                if (info == null) {
                    packageView.setText("No valid package loaded");
                    return;
                }
                StringBuilder sb = new StringBuilder();
                sb.append("ZIP: ").append(info.zipPath).append('\n');
                sb.append("ZIP size: ").append(info.zipSize).append('\n');
                sb.append("ZIP sha256: ").append(info.zipSha256).append('\n');
                sb.append("signature: ").append(info.signatureSummary).append('\n');
                if (!info.ecarxSigned) {
                    sb.append("WARNING: archive is not verified as ECARX_WH signed").append('\n');
                }
                sb.append("project: ").append(info.project).append('\n');
                sb.append("systemVersionName: ").append(info.systemVersionName).append('\n');
                sb.append("systemVersionCode: ").append(info.systemVersionCode).append('\n');
                sb.append("fsl_app.s19: ").append(info.fslSize).append(" bytes").append('\n');
                sb.append("fsl sha256: ").append(nullToEmpty(info.fslSha256)).append('\n');
                sb.append("upgrade.dat: ").append(info.upgradeSize).append(" bytes").append('\n');
                sb.append("upgrade sha256: ").append(nullToEmpty(info.upgradeSha256)).append('\n');
                packageView.setText(sb.toString());
            }
        });
    }

    private void runRootCheck() {
        runAsync(new Runnable() {
            @Override public void run() {
                RootResult result = runRoot("id; ls -l /dev/ecp_uart_1 2>&1 || true; mount | head -20");
                appendLog("Root check exit=" + result.exitCode + "\n" + result.output);
            }
        });
    }

    private void prepareOnly() {
        runAsync(new Runnable() {
            @Override public void run() {
                PackageInfo info = ensurePackage();
                if (info == null) {
                    return;
                }
                RootResult result = runRoot(buildPrepareScript(info, false));
                appendLog("Prepare /tmp exit=" + result.exitCode + "\n" + result.output);
            }
        });
    }

    private void releaseUartOnly() {
        runAsync(new Runnable() {
            @Override public void run() {
                RootResult result = runRoot(buildReleaseUartScript());
                appendLog("Release UART exit=" + result.exitCode + "\n" + result.output);
            }
        });
    }

    private void flashMcu() {
        if (!confirmBox.isChecked()) {
            Toast.makeText(this, "Confirm flashing first", Toast.LENGTH_LONG).show();
            return;
        }
        flashButton.setEnabled(false);
        runAsync(new Runnable() {
            @Override public void run() {
                try {
                    PackageInfo info = ensurePackage();
                    if (info == null) {
                        return;
                    }
                    appendLog("FLASH START " + info.systemVersionName + " zip=" + info.zipSha256);
                    if (!info.ecarxSigned) {
                        appendLog("WARNING: archive is not verified as ECARX_WH signed; flashing is still allowed");
                    }
                    RootResult result = runRoot(buildPrepareScript(info, true));
                    appendLog("FLASH exit=" + result.exitCode + "\n" + result.output);
                } finally {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            flashButton.setEnabled(true);
                        }
                    });
                }
            }
        });
    }

    private PackageInfo ensurePackage() {
        try {
            String path = pathEdit.getText().toString().trim();
            PackageInfo info = inspectPackage(new File(path), true);
            currentPackage = info;
            showPackage(info);
            return info;
        } catch (Exception e) {
            appendLog("Package error: " + e.getMessage());
            return null;
        }
    }

    private String buildPrepareScript(PackageInfo info, boolean runUpgrade) {
        StringBuilder sb = new StringBuilder();
        sb.append("set -u\n");
        sb.append("echo root=$(id)\n");
        sb.append("echo preparing tmpfs /tmp\n");
        sb.append("mount -o remount,rw / 2>/dev/null || true\n");
        sb.append("mkdir -p /tmp\n");
        sb.append("chmod 0777 /tmp\n");
        sb.append("if ! grep -q ' /tmp ' /proc/mounts; then mount -t tmpfs -o mode=0777,size=16m tmpfs /tmp || mount -t tmpfs tmpfs /tmp; fi\n");
        sb.append("chmod 0777 /tmp\n");
        sb.append("mount -o remount,ro / 2>/dev/null || true\n");
        sb.append("grep ' /tmp ' /proc/mounts || echo WARNING_tmp_not_mounted\n");
        sb.append("cp ").append(sh(info.fslApp.getAbsolutePath())).append(" /tmp/fsl_app.s19\n");
        sb.append("cp ").append(sh(info.upgradeDat.getAbsolutePath())).append(" /tmp/upgrade.dat\n");
        sb.append("chmod 0644 /tmp/fsl_app.s19\n");
        sb.append("chmod 06755 /tmp/upgrade.dat\n");
        sb.append("sync\n");
        sb.append(buildReleaseUartScript()).append('\n');
        sb.append("ls -l /tmp/fsl_app.s19 /tmp/upgrade.dat\n");
        if (runUpgrade) {
            sb.append("echo running upgrade.dat\n");
            sb.append("cd /tmp && ./upgrade.dat\n");
            sb.append("rc=$?\n");
            sb.append("echo upgrade.dat exit=$rc\n");
            sb.append("exit $rc\n");
        } else {
            sb.append("echo prepared only\n");
        }
        return sb.toString();
    }

    private String buildReleaseUartScript() {
        StringBuilder sb = new StringBuilder();
        sb.append("echo releasing /dev/ecp_uart_1\n");
        sb.append("if command -v lsof >/dev/null 2>&1; then for p in $(lsof /dev/ecp_uart_1 2>/dev/null | awk 'NR>1 {print $2}' | sort -u); do echo kill_lsof=$p; kill -9 $p 2>/dev/null || true; done; fi\n");
        sb.append("if command -v fuser >/dev/null 2>&1; then for p in $(fuser /dev/ecp_uart_1 2>/dev/null); do echo kill_fuser=$p; kill -9 $p 2>/dev/null || true; done; fi\n");
        sb.append("if command -v toybox >/dev/null 2>&1; then for p in $(toybox fuser /dev/ecp_uart_1 2>/dev/null); do echo kill_toybox_fuser=$p; kill -9 $p 2>/dev/null || true; done; fi\n");
        return sb.toString();
    }

    private RootResult runRoot(String script) {
        RootResult result = runCommand(new String[] {"/system/xbin/su", "0", "sh", "-c", script});
        if (result.startFailed) {
            result = runCommand(new String[] {"su", "0", "sh", "-c", script});
        }
        if (result.startFailed) {
            result = runCommand(new String[] {"su", "-c", script});
        }
        return result;
    }

    private void fillSignatureInfo(File zipFile, PackageInfo info) {
        JarFile jar = null;
        try {
            jar = new JarFile(zipFile, true);
            X509Certificate cert = null;
            cert = firstSignerCert(jar, "Manifest_SYS.xml", cert);
            cert = firstSignerCert(jar, "fsl_app.s19", cert);
            cert = firstSignerCert(jar, "upgrade.dat", cert);
            if (cert == null) {
                info.signatureSummary = "no signer certificate found";
                info.ecarxSigned = false;
                return;
            }
            info.signerSubject = cert.getSubjectX500Principal().getName();
            info.signerSha256 = certFingerprint(cert);
            info.ecarxSigned = ECARX_WH_CERT_SHA256.equalsIgnoreCase(info.signerSha256);
            info.signatureSummary = info.signerSha256 + " " + info.signerSubject;
        } catch (SecurityException e) {
            info.signatureSummary = "signature verification failed: " + e.getMessage();
            info.ecarxSigned = false;
        } catch (Exception e) {
            info.signatureSummary = "signature check error: " + e.getMessage();
            info.ecarxSigned = false;
        } finally {
            if (jar != null) {
                try {
                    jar.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private X509Certificate firstSignerCert(JarFile jar, String entryName, X509Certificate previous) throws Exception {
        JarEntry entry = jar.getJarEntry(entryName);
        if (entry == null) {
            return previous;
        }
        InputStream in = jar.getInputStream(entry);
        byte[] buf = new byte[8192];
        while (in.read(buf) != -1) {
        }
        in.close();
        Certificate[] certs = entry.getCertificates();
        if (certs == null || certs.length == 0) {
            return previous;
        }
        if (certs[0] instanceof X509Certificate) {
            return (X509Certificate) certs[0];
        }
        return previous;
    }

    private static String certFingerprint(X509Certificate cert) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(cert.getEncoded());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digest.length; i++) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(String.format(Locale.US, "%02X", digest[i] & 0xff));
        }
        return sb.toString();
    }

    private RootResult runCommand(String[] cmd) {
        RootResult result = new RootResult();
        StringBuilder out = new StringBuilder();
        try {
            Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append('\n');
                appendLog(line);
            }
            result.exitCode = process.waitFor();
        } catch (Exception e) {
            result.startFailed = true;
            result.exitCode = -1;
            out.append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append('\n');
        }
        result.output = out.toString();
        return result;
    }

    private void runAsync(final Runnable runnable) {
        new Thread(new Runnable() {
            @Override public void run() {
                runnable.run();
            }
        }).start();
    }

    private void appendLog(final String message) {
        final String line = now() + " " + message;
        runOnUiThread(new Runnable() {
            @Override public void run() {
                String old = logView.getText().toString();
                logView.setText(old + line + "\n");
            }
        });
        try {
            FileOutputStream out = new FileOutputStream(LOG_PATH, true);
            out.write((line + "\n").getBytes("UTF-8"));
            out.close();
        } catch (Exception ignored) {
        }
    }

    private String now() {
        return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String sh(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    private static void copy(InputStream in, File outFile) throws IOException {
        try {
            FileOutputStream out = new FileOutputStream(outFile);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.close();
        } finally {
            in.close();
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        FileInputStream in = new FileInputStream(file);
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
        } finally {
            in.close();
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format(Locale.US, "%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteTree(child);
                }
            }
        }
        file.delete();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class PackageInfo {
        String zipPath;
        long zipSize;
        String zipSha256;
        String project;
        String systemVersionName;
        String systemVersionCode;
        String manifestXml;
        boolean ecarxSigned;
        String signerSubject;
        String signerSha256;
        String signatureSummary;
        long fslSize;
        long upgradeSize;
        File fslApp;
        File upgradeDat;
        String fslSha256;
        String upgradeSha256;
    }

    private static final class RootResult {
        boolean startFailed;
        int exitCode;
        String output;
    }
}
