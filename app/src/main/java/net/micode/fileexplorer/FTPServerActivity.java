package net.micode.fileexplorer;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

import be.ppareit.swiftp.FsService;
import be.ppareit.swiftp.FsSettings;
import be.ppareit.swiftp.server.FtpUser;
import be.ppareit.swiftp.utils.AllowedFolders;

public class FTPServerActivity extends AppCompatActivity {
    private static final String TAG = "FTPServerActivity";
    private static final int REQUEST_SHARED_FOLDER = 1;
    private static final int REQUEST_NOTIFICATIONS = 2;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mStateUpdater = new Runnable() {
        @Override
        public void run() {
            updateServerState();
            mHandler.postDelayed(this, 1000);
        }
    };

    private TextInputLayout mUsernameLayout;
    private TextInputLayout mPasswordLayout;
    private TextInputEditText mUsernameInput;
    private TextInputEditText mPasswordInput;
    private TextView mSharedFolders;
    private TextView mServerStatus;
    private TextView mServerAddress;
    private MaterialButton mSaveAccount;
    private MaterialButton mAddFolder;
    private MaterialButton mToggleServer;
    private FtpUser mEditingUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ftp_server_activity);
        SystemBarInsets.apply(this, findViewById(android.R.id.content));

        MaterialToolbar toolbar = findViewById(R.id.ftp_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(view -> finish());

        mUsernameLayout = findViewById(R.id.ftp_username_layout);
        mPasswordLayout = findViewById(R.id.ftp_password_layout);
        mUsernameInput = findViewById(R.id.ftp_username);
        mPasswordInput = findViewById(R.id.ftp_password);
        mSharedFolders = findViewById(R.id.ftp_shared_folders);
        mServerStatus = findViewById(R.id.ftp_server_status);
        mServerAddress = findViewById(R.id.ftp_server_address);
        mSaveAccount = findViewById(R.id.ftp_save_account);
        mAddFolder = findViewById(R.id.ftp_add_folder);
        mToggleServer = findViewById(R.id.ftp_toggle_server);

        loadAccount();
        mSaveAccount.setOnClickListener(view -> saveAccount());
        mAddFolder.setOnClickListener(view -> chooseSharedFolder());
        mToggleServer.setOnClickListener(view -> toggleServer());
        updateSharedFolders();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mHandler.removeCallbacks(mStateUpdater);
        mStateUpdater.run();
    }

    @Override
    protected void onPause() {
        mHandler.removeCallbacks(mStateUpdater);
        super.onPause();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadAccount() {
        mEditingUser = null;
        for (FtpUser user : getUsers()) {
            if (!isDefaultUser(user)) {
                mEditingUser = user;
                break;
            }
        }
        if (mEditingUser == null && !getUsers().isEmpty()) {
            mEditingUser = getUsers().get(0);
        }

        if (mEditingUser != null && !isDefaultUser(mEditingUser)) {
            mUsernameInput.setText(mEditingUser.getUsername());
            mPasswordInput.setText(mEditingUser.getPassword());
        } else {
            mUsernameInput.setText("");
            mPasswordInput.setText("");
        }
    }

    private List<FtpUser> getUsers() {
        List<FtpUser> users = FsSettings.getUsers();
        if (users == null) {
            throw new IllegalStateException("SwiFTP returned a null user list");
        }
        return users;
    }

    private boolean isDefaultUser(FtpUser user) {
        return TextUtils.equals(user.getUsername(), getString(be.ppareit.swiftp.R.string.username_default))
                && TextUtils.equals(user.getPassword(), getString(be.ppareit.swiftp.R.string.password_default));
    }

    private boolean hasConfiguredAccount() {
        boolean hasAccount = false;
        for (FtpUser user : getUsers()) {
            if (isDefaultUser(user)) {
                return false;
            }
            hasAccount = true;
        }
        return hasAccount && !FsSettings.allowAnonymous();
    }

    private void saveAccount() {
        mUsernameLayout.setError(null);
        mPasswordLayout.setError(null);

        String username = textOf(mUsernameInput).trim();
        String password = textOf(mPasswordInput);
        if (username.isEmpty() || password.isEmpty()
                || containsLineBreak(username) || containsLineBreak(password)) {
            mPasswordLayout.setError(getString(R.string.ftp_invalid_credentials));
            return;
        }

        String defaultUsername = getString(be.ppareit.swiftp.R.string.username_default);
        String defaultPassword = getString(be.ppareit.swiftp.R.string.password_default);
        if (TextUtils.equals(username, defaultUsername)
                && TextUtils.equals(password, defaultPassword)) {
            mPasswordLayout.setError(getString(R.string.ftp_default_credentials));
            return;
        }

        List<FtpUser> currentUsers = getUsers();
        String editingUsername = mEditingUser == null ? null : mEditingUser.getUsername();
        for (FtpUser user : currentUsers) {
            if (!TextUtils.equals(user.getUsername(), editingUsername)
                    && TextUtils.equals(user.getUsername(), username)) {
                mUsernameLayout.setError(getString(R.string.ftp_username_exists));
                return;
            }
        }

        FtpUser oldUser = mEditingUser;
        for (FtpUser user : new ArrayList<>(currentUsers)) {
            if (isDefaultUser(user)) {
                FsSettings.removeUser(user.getUsername(), false);
            }
        }

        File chroot = FsSettings.getDefaultChrootDir();
        FtpUser updatedUser = new FtpUser(username, password, chroot.getPath(), "");
        if (oldUser != null && !isDefaultUser(oldUser)) {
            FsSettings.modifyUser(oldUser.getUsername(), updatedUser);
        } else {
            FsSettings.addUser(updatedUser);
        }
        FsSettings.setAllowAnonymous(false);
        mEditingUser = updatedUser;
        mPasswordInput.setText(updatedUser.getPassword());
        Toast.makeText(this, R.string.ftp_account_saved, Toast.LENGTH_SHORT).show();
        updateServerState();
    }

    private boolean containsLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\0') >= 0;
    }

    private String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    private void chooseSharedFolder() {
        if (FsService.isRunning()) {
            Toast.makeText(this, R.string.ftp_server_stop_before_storage, Toast.LENGTH_SHORT).show();
            return;
        }
        if (AllowedFolders.isEmpty() && FileExplorerTabActivity.hasFileSystemAccess(this)) {
            List<StorageLocations.Location> locations = StorageLocations.mounted(this);
            if (!locations.isEmpty()) {
                List<String> options = new ArrayList<>();
                for (StorageLocations.Location location : locations) {
                    options.add(location.primary
                            ? getString(R.string.storage_internal)
                            : location.name);
                }
                options.add(getString(R.string.ftp_add_folder));
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.storage_select_title)
                        .setItems(options.toArray(new String[0]), (dialog, which) -> {
                            if (which == locations.size()) {
                                chooseSafFolder();
                            } else if (which < locations.size()) {
                                FsSettings.setDefaultChrootDir(locations.get(which).path);
                                updateEditingUserChroot();
                                updateSharedFolders();
                            }
                        })
                        .show();
                return;
            }
        }
        chooseSafFolder();
    }

    private void chooseSafFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_SHARED_FOLDER);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, R.string.ftp_folder_picker_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_SHARED_FOLDER || resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri treeUri = data.getData();
        if (treeUri == null) {
            Toast.makeText(this, R.string.ftp_folder_failed, Toast.LENGTH_LONG).show();
            return;
        }

        try {
            AllowedFolders.takeGrant(this, treeUri);
            updateEditingUserChroot();
            updateSharedFolders();
            Toast.makeText(this, R.string.ftp_folder_added, Toast.LENGTH_SHORT).show();
        } catch (SecurityException exception) {
            Toast.makeText(this, R.string.ftp_folder_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void updateEditingUserChroot() {
        if (mEditingUser == null || isDefaultUser(mEditingUser)) {
            return;
        }
        File chroot = FsSettings.getDefaultChrootDir();
        FtpUser updatedUser = new FtpUser(mEditingUser.getUsername(), mEditingUser.getPassword(),
                chroot.getPath(), mEditingUser.getUriString());
        FsSettings.modifyUser(mEditingUser.getUsername(), updatedUser);
        mEditingUser = updatedUser;
    }

    private void updateSharedFolders() {
        List<String> names = AllowedFolders.names();
        if (names.isEmpty()) {
            StorageLocations.Location location = StorageLocations.containing(
                    StorageLocations.mounted(this), FsSettings.getDefaultChrootDir().getPath());
            if (location != null && !location.primary) {
                mSharedFolders.setText(location.name);
            } else {
                mSharedFolders.setText(R.string.ftp_shared_folders_empty);
            }
        } else {
            mSharedFolders.setText(TextUtils.join(", ", names));
        }
    }

    private void toggleServer() {
        if (FsService.isRunning()) {
            FsService.stop();
            updateServerState();
            return;
        }
        if (!hasConfiguredAccount()) {
            Toast.makeText(this, R.string.ftp_credentials_required, Toast.LENGTH_LONG).show();
            return;
        }
        if (!isWifiConnected()) {
            Toast.makeText(this, R.string.ftp_network_required, Toast.LENGTH_LONG).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS);
            return;
        }
        startServer();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_NOTIFICATIONS) {
            return;
        }
        if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.ftp_notifications_denied, Toast.LENGTH_LONG).show();
        }
        startServer();
    }

    private void startServer() {
        FsService.start();
        mHandler.postDelayed(() -> {
            updateServerState();
            if (!FsService.isRunning()) {
                Toast.makeText(this, R.string.ftp_server_start_failed, Toast.LENGTH_LONG).show();
            }
        }, 1500);
    }

    private boolean isWifiConnected() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected()
                && networkInfo.getType() == ConnectivityManager.TYPE_WIFI;
    }

    private String getWifiAddress() {
        WifiManager wifiManager = (WifiManager) getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            return null;
        }
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ipAddress = wifiInfo == null ? 0 : wifiInfo.getIpAddress();
        if (ipAddress != 0) {
            return String.format(Locale.US, "%d.%d.%d.%d", ipAddress & 0xff,
                    (ipAddress >> 8) & 0xff, (ipAddress >> 16) & 0xff,
                    (ipAddress >> 24) & 0xff);
        }

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return null;
            }
            String fallbackAddress = null;
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                String interfaceName = networkInterface.getName().toLowerCase(Locale.US);
                boolean wifiInterface = interfaceName.startsWith("wlan")
                        || interfaceName.startsWith("wifi") || interfaceName.startsWith("ap");
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!(address instanceof Inet4Address) || address.isLoopbackAddress()
                            || address.isLinkLocalAddress()) {
                        continue;
                    }
                    if (wifiInterface) {
                        return address.getHostAddress();
                    }
                    if (fallbackAddress == null) {
                        fallbackAddress = address.getHostAddress();
                    }
                }
            }
            return fallbackAddress;
        } catch (SocketException exception) {
            Log.w(TAG, "Unable to determine the FTP server address", exception);
            return null;
        }
    }

    private void updateServerState() {
        boolean running = FsService.isRunning();
        boolean wifiConnected = isWifiConnected();
        mServerStatus.setText(running ? R.string.ftp_server_running : R.string.ftp_server_stopped);
        mToggleServer.setText(running ? R.string.ftp_stop_server : R.string.ftp_start_server);
        mToggleServer.setEnabled(running || (wifiConnected && hasConfiguredAccount()));
        mSaveAccount.setEnabled(!running);
        mAddFolder.setEnabled(!running);
        mUsernameInput.setEnabled(!running);
        mPasswordInput.setEnabled(!running);

        if (running) {
            String address = getWifiAddress();
            if (address == null) {
                mServerAddress.setText(R.string.ftp_address_unavailable);
            } else {
                mServerAddress.setText(getString(R.string.ftp_enter_address) + "\nftp://"
                        + address + ":" + FsSettings.getPortNumber());
            }
            mServerAddress.setVisibility(View.VISIBLE);
        } else {
            mServerAddress.setVisibility(View.GONE);
        }
    }
}
