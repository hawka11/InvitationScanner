package com.hawka11.invitationscanner;

import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Scanner;

public class InvitationScannerActivityMain extends Activity {

    static final int REQUEST_ACCOUNT_PICKER = 1;
    static final int REQUEST_AUTHORIZATION = 2;
    static final int CAPTURE_IMAGE = 3;

    private static Uri fileUri;
    private static Drive service;
    private GoogleAccountCredential credential;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        credential = GoogleAccountCredential.usingOAuth2(this, DriveScopes.DRIVE);

        Button button = (Button) findViewById(R.id.fromCamera);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivityForResult(credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
            }
        });
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        switch (requestCode) {
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null && data.getExtras() != null) {
                    String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        credential.setSelectedAccountName(accountName);
                        service = getDriveService(credential);
                        startCameraIntent();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == Activity.RESULT_OK) {
                    saveFileToDrive();
                } else {
                    startActivityForResult(credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
                }
                break;
            case CAPTURE_IMAGE:
                if (resultCode == Activity.RESULT_OK) {
                    saveFileToDrive();
                }
        }
    }

    private void startCameraIntent() {
        String mediaStorageDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES).getPath();
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        fileUri = Uri.fromFile(new java.io.File(mediaStorageDir + java.io.File.separator + "IMG_"
                + timeStamp + ".jpg"));

        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
        startActivityForResult(cameraIntent, CAPTURE_IMAGE);
    }

    private void saveFileToDrive() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // File's binary content
                    java.io.File fileContent = new java.io.File(fileUri.getPath());
                    FileContent mediaContent = new FileContent("image/jpeg", fileContent);

                    // File's metadata.
                    File body = new File();
                    body.setTitle(fileContent.getName());
                    body.setMimeType("image/jpeg");

                    File file = service.files()
                            .insert(body, mediaContent)
                            .setOcr(Boolean.TRUE)
                            .execute();

                    if (file != null) {
                        String ocrText = extractOcrText(file);

                        showToast("Photo uploaded: " + file.getTitle());
                        startCameraIntent();
                    }
                } catch (UserRecoverableAuthIOException e) {
                    startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            private String extractOcrText(File file) throws IOException {
                String exportLink = file.getExportLinks().get("text/plain");
                GenericUrl url = new GenericUrl(exportLink);
                HttpResponse response = service.getRequestFactory().buildGetRequest(url).execute();

                final StringBuilder sb = new StringBuilder();
                final Scanner scanner = new Scanner(response.getContent()).useDelimiter("\\A");
                while (scanner.hasNext()) {
                    sb.append(filterKnownCharactors(scanner.next()));
                }
                return sb.toString();
            }

            private String filterKnownCharactors(String string) {
                return string.replaceAll("^[-\\\\w.\\s]+", "");
            }
        });
        t.start();
    }

    private Drive getDriveService(GoogleAccountCredential credential) {
        return new Drive.Builder(AndroidHttp.newCompatibleTransport(), new GsonFactory(), credential)
                .build();
    }

    public void showToast(final String toast) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), toast, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
