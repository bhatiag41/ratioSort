package com.example.myapplication;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.io.FileInputStream;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int PICK_IMAGES_REQUEST = 1;
    private List<Uri> selectedImages = new ArrayList<>();
    private TextView statusText;

    private final ActivityResultLauncher<Intent> mediaPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();
                    selectedImages.clear();

                    if (data.getClipData() != null) {
                        int count = data.getClipData().getItemCount();
                        for (int i = 0; i < count; i++) {
                            selectedImages.add(data.getClipData().getItemAt(i).getUri());
                        }
                    } else if (data.getData() != null) {
                        selectedImages.add(data.getData());
                    }

                    statusText.setText(selectedImages.size() + " files selected");
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button selectBtn = findViewById(R.id.selectMediaBtn);
        Button sortBtn = findViewById(R.id.sortButton);
        statusText = findViewById(R.id.statusText);

        selectBtn.setOnClickListener(v -> openMediaSelector());
        sortBtn.setOnClickListener(v -> sortSelectedMedia());
    }

    private void openMediaSelector() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        mediaPicker.launch(intent);
    }

    private void sortSelectedMedia() {
        if (selectedImages.isEmpty()) {
            Toast.makeText(this, "Please select media files", Toast.LENGTH_SHORT).show();
            return;
        }

        for (Uri sourceUri : selectedImages) {
            try {
                String mimeType = getContentResolver().getType(sourceUri);
                boolean isVideo = mimeType != null && mimeType.startsWith("video");

                int width, height;
                if (isVideo) {
                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                    retriever.setDataSource(this, sourceUri);
                    width = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                    height = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                    retriever.release();
                } else {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeStream(getContentResolver().openInputStream(sourceUri), null, options);
                    width = options.outWidth;
                    height = options.outHeight;
                }

                boolean isVertical = height > width;
                String folderName = isVertical ? "Vertical" : "Horizontal";
                String subfolderPath = Environment.DIRECTORY_PICTURES + "/MediaSorter/" + folderName;

                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, getFileName(sourceUri));
                values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, subfolderPath);

                Uri collectionUri = isVideo ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                Uri destUri = getContentResolver().insert(collectionUri, values);

                if (destUri != null) {
                    try (InputStream in = getContentResolver().openInputStream(sourceUri);
                         OutputStream out = getContentResolver().openOutputStream(destUri)) {
                        if (in != null && out != null) {
                            byte[] buffer = new byte[8192];
                            int length;
                            while ((length = in.read(buffer)) > 0) {
                                out.write(buffer, 0, length);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
        Toast.makeText(this, "Media sorted successfully", Toast.LENGTH_SHORT).show();
    }
    private String getFileName(Uri uri) {
        String result = null;
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (nameIndex != -1) {
                result = cursor.getString(nameIndex);
            }
            cursor.close();
        }
        return result != null ? result : "image_" + System.currentTimeMillis() + ".jpg";
    }
}