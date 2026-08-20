package cz.spojenka.lwt.demoapp;

import android.app.Application;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.security.keystore.KeyProperties;
import android.security.keystore.KeyProtection;
import android.text.InputType;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatRadioButton;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import cz.spojenka.android.ui.activity.BaseActivity;
import cz.spojenka.android.ui.dialog.CommonDialogs;
import cz.spojenka.android.util.AsyncUtils;
import cz.spojenka.android.util.DateTimeUtils;
import cz.spojenka.android.util.ViewUtils;
import cz.spojenka.lwt.demoapp.databinding.ActivityClientCertImportBinding;
import cz.spojenka.lwt.util.TLSTrustManager;

public class ClientCertImportActivity extends BaseActivity {

    public static final String EXTRA_TARGET_ALIAS = ClientCertImportActivity.class.getName() + ".EXTRA_TARGET_ALIAS";

    private static final String TAG = ClientCertImportActivity.class.getSimpleName();

    private ActivityClientCertImportBinding binding;

    private ActivityResultLauncher<String> filePickerLauncher;

    private ViewModel viewModel;

    private Dialog currentPasswordDialog;

    private SparseArray<String> rbIdToAliasMap = new SparseArray<>();
    private Map<String, Integer> aliasToRbIdMap = new HashMap<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(this).get(ViewModel.class);

        filePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                binding.tvCertDetails.setText(R.string.cert_import_step2_loading);
                viewModel.loadKeyStoreAsync(uri, null);
            }
        });

        binding = ActivityClientCertImportBinding.inflate(getLayoutInflater());
        setContentView(ViewUtils.wrapInScrollView(binding.getRoot()));

        viewModel.getKeyStoreLoadError().observe(this, throwable -> {
            if (throwable == null) {
                return;
            }
            binding.tvCertDetails.setText(R.string.cert_import_step2_default_text);
            viewModel.ackKeyStoreLoadError();
            if (TLSTrustManager.isWrongPassword(throwable)) {
                promptForKeystorePassword();
            } else {
                Log.e(TAG, "Failed to load keystore", throwable);

                CommonDialogs.newInfoDialog(this, R.string.error, R.string.cert_import_error_desc).show();
            }
        });

        viewModel.getKeyStore().observe(this, keyStore -> {
            if (keyStore == null) {
                binding.btnFinishImport.setEnabled(false);
                return;
            }
            binding.rgAliases.removeAllViews();
            rbIdToAliasMap.clear();

            try {
                StringBuilder desc = new StringBuilder();
                desc.append(getString(R.string.cert_import_step2_desc_start));

                String firstAlias = null;

                var aliases = keyStore.aliases();
                while (aliases.hasMoreElements()) {
                    String alias = aliases.nextElement();
                    if (firstAlias == null) {
                        firstAlias = alias;
                    }
                    Certificate cert = keyStore.getCertificate(alias);
                    if (cert != null && keyStore.isKeyEntry(alias)) {
                        if (cert instanceof X509Certificate x509) {
                            String issuer = x509.getIssuerX500Principal().getName();
                            if (issuer == null || issuer.isEmpty()) {
                                issuer = getString(R.string.cert_import_step2_desc_cert_entry_issuer_unknown);
                            }
                            String subject = x509.getSubjectX500Principal().getName();
                            if (subject == null || subject.isEmpty()) {
                                subject = getString(R.string.cert_import_step2_desc_cert_entry_subject_unknown);
                            }
                            desc.append("\n\n").append(getString(
                                    R.string.cert_import_step2_desc_cert_entry,
                                    alias,
                                    subject,
                                    issuer,
                                    DateTimeUtils.formatDate(LocalDateTime.ofInstant(x509.getNotBefore().toInstant(), ZoneId.systemDefault()).toLocalDate()),
                                    DateTimeUtils.formatDate(LocalDateTime.ofInstant(x509.getNotAfter().toInstant(), ZoneId.systemDefault()).toLocalDate())
                            ));
                        } else {
                            desc.append("\n\n").append(getString(R.string.cert_import_step2_desc_cert_entry_unknown_type, alias, cert.getType()));
                        }
                        if (keyStore.isKeyEntry(alias)) {
                            desc.append(" ").append(getString(R.string.cert_import_step2_desc_cert_entry_has_private_key));
                        }

                        AppCompatRadioButton rb = new AppCompatRadioButton(this);
                        rb.setText(alias);
                        rb.setId(View.generateViewId());
                        rbIdToAliasMap.put(rb.getId(), alias);
                        aliasToRbIdMap.put(alias, rb.getId());
                        binding.rgAliases.addView(rb);
                    }
                }
                viewModel.setSelectedAlias(firstAlias);

                binding.tvCertDetails.setText(desc.toString());
            } catch (KeyStoreException e) {
                Log.e(TAG, "Failed to read keystore", e);
                throw new RuntimeException(e);
            }
        });

        binding.btnOpenCertFile.setOnClickListener(v -> {
            filePickerLauncher.launch("application/x-pkcs12");
        });

        viewModel.getIsBusy().observe(this, isBusy -> {
            binding.btnOpenCertFile.setEnabled(!isBusy);
            binding.btnFinishImport.setEnabled(!isBusy && viewModel.getSelectedAlias().getValue() != null);
        });

        binding.rgAliases.setOnCheckedChangeListener((group, checkedId) -> {
            viewModel.setSelectedAlias(rbIdToAliasMap.get(checkedId));
        });

        viewModel.getSelectedAlias().observe(this, alias -> {
            if (alias != null) {
                Integer rbId = aliasToRbIdMap.get(alias);
                if (rbId != null) {
                    binding.rgAliases.check(rbId);
                }
            }
            binding.btnFinishImport.setEnabled(alias != null && Boolean.FALSE.equals(viewModel.getIsBusy().getValue()));
        });

        binding.btnFinishImport.setOnClickListener(v -> {
            viewModel.finishImportAsync(getIntent().getStringExtra(EXTRA_TARGET_ALIAS));
        });

        viewModel.getImportedAlias().observe(this, alias -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_TARGET_ALIAS, alias));
            finish();
        });

        viewModel.getKeyStoreLoadError().observe(this, throwable -> {
            if (throwable != null) {
                viewModel.ackImportError();
                CommonDialogs.newInfoDialog(this, R.string.error, R.string.cert_import_error_desc).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentPasswordDialog != null) {
            currentPasswordDialog.dismiss();
        }
    }

    private void promptForKeystorePassword() {
        EditText et = new AppCompatEditText(this);
        var dlg = CommonDialogs.newTextInputDialog(this, et)
                .setTitle(R.string.cert_import_password_prompt_title)
                .setMessage(viewModel.isPasswordAttempted
                        ? R.string.cert_import_password_prompt_message_incorrect
                        : R.string.cert_import_password_prompt_message
                )
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    viewModel.loadKeyStoreAsync(viewModel.getLastFileUri(), et.getText().toString());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setOnDismissListener(dialog -> currentPasswordDialog = null)
                .create();

        // must be done after dialog creation to override setSingleLine which changes the transformation method
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        et.requestFocus();

        Objects.requireNonNull(dlg.getWindow()).setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

        dlg.show();
        currentPasswordDialog = dlg;
    }

    public static class ViewModel extends AndroidViewModel {

        private final MutableLiveData<KeyStore> keyStore = new MutableLiveData<>();
        private final MutableLiveData<Throwable> keyStoreLoadError = new MutableLiveData<>();

        private final MutableLiveData<Boolean> isBusy = new MutableLiveData<>(false);

        private Uri lastFileUri;
        private KeyStore lastKeyStore;
        private char[] lastKeystorePassword;
        private boolean isPasswordAttempted = false;

        private final KeyStore androidKeyStore = GlobalTrustManager.getAndroidKeyStore();
        private final MutableLiveData<String> selectedAlias = new MutableLiveData<>();

        private final MutableLiveData<String> importedAlias = new MutableLiveData<>();
        private final MutableLiveData<Throwable> importError = new MutableLiveData<>();

        public ViewModel(@NonNull Application application) {
            super(application);
        }

        public LiveData<KeyStore> getKeyStore() {
            return keyStore;
        }

        public LiveData<Throwable> getKeyStoreLoadError() {
            return keyStoreLoadError;
        }

        public LiveData<Boolean> getIsBusy() {
            return isBusy;
        }

        private void ackKeyStoreLoadError() {
            keyStoreLoadError.setValue(null);
        }

        public char[] getLastKeystorePassword() {
            return lastKeystorePassword;
        }

        public void loadKeyStoreAsync(Uri path, String password) {
            isBusy.setValue(true);
            keyStoreLoadError.setValue(null);
            isPasswordAttempted = password != null;
            lastKeystorePassword = password != null ? password.toCharArray() : null;
            lastFileUri = path;
            AsyncUtils.supplyAsync(() -> {
                try (InputStream in = getApplication().getContentResolver().openInputStream(path)) {
                    return GlobalTrustManager.getInstance(getApplication()).loadPKCS12(in, lastKeystorePassword);
                }
            }).whenCompleteAsync((keyStore1, throwable) -> {
                isBusy.setValue(false);
                if (throwable != null) {
                    keyStoreLoadError.setValue(throwable);
                } else {
                    lastKeyStore = keyStore1;
                    keyStore.setValue(keyStore1);
                }
            }, getApplication().getMainExecutor());
        }

        public boolean isPasswordAttempted() {
            return isPasswordAttempted;
        }

        public Uri getLastFileUri() {
            return lastFileUri;
        }

        public LiveData<String> getSelectedAlias() {
            return selectedAlias;
        }

        public void setSelectedAlias(String alias) {
            selectedAlias.setValue(alias);
        }

        public void finishImportAsync(String targetAlias) {
            isBusy.setValue(true);
            String finalAlias = targetAlias != null ? targetAlias : selectedAlias.getValue();
            AsyncUtils.runAsync(() -> {
                KeyStore.PrivateKeyEntry pkEntry = (KeyStore.PrivateKeyEntry) lastKeyStore.getEntry(
                        selectedAlias.getValue(), new KeyStore.PasswordProtection(lastKeystorePassword));

                androidKeyStore.setEntry(
                        finalAlias,
                        new KeyStore.PrivateKeyEntry(pkEntry.getPrivateKey(), pkEntry.getCertificateChain()),
                        new KeyProtection.Builder(KeyProperties.PURPOSE_SIGN)
                                .setDigests(
                                        // NONE is needed for client auth.
                                        KeyProperties.DIGEST_NONE,
                                        KeyProperties.DIGEST_SHA256,
                                        KeyProperties.DIGEST_SHA384,
                                        KeyProperties.DIGEST_SHA512
                                )
                                .build()
                );
            }).whenCompleteAsync((unused, throwable) -> {
                isBusy.setValue(false);
                if (throwable != null) {
                    Log.e(TAG, "Failed to import certificate", throwable);
                    importError.setValue(throwable);
                } else {
                    importedAlias.setValue(finalAlias);
                }
            }, getApplication().getMainExecutor());
        }

        public LiveData<String> getImportedAlias() {
            return importedAlias;
        }

        public void ackImportError() {
            importError.setValue(null);
        }
    }
}
