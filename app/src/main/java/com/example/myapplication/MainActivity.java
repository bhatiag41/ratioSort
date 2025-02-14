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

import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.io.InputStream;
import java.io.OutputStream;

import java.util.ArrayList;

import java.util.List;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.os.Build;
import java.io.IOException;


public class MainActivity extends AppCompatActivity {
    private static final int PICK_IMAGES_REQUEST = 1;
    private static final int PERMISSION_REQUEST_CODE = 123;
    private List<Uri> selectedImages = new ArrayList<>();
    private TextView statusText;
    private MediaPreviewAdapter mediaAdapter;
    private RecyclerView mediaPreviewGrid;

    private final ActivityResultLauncher<Intent> mediaPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    
                    // Create temporary list for new selections
                    List<Uri> newSelections = new ArrayList<>();
                    
                    if (data.getClipData() != null) {
                        int count = data.getClipData().getItemCount();
                        for (int i = 0; i < count; i++) {
                            Uri uri = data.getClipData().getItemAt(i).getUri();
                            newSelections.add(uri);
                        }
                    } else if (data.getData() != null) {
                        newSelections.add(data.getData());
                    }
                    
                    // Add new selections to existing list
                    selectedImages.addAll(newSelections);
                    
                    // Update UI
                    statusText.setText(selectedImages.size() + " files selected");
                    mediaAdapter.notifyDataSetChanged();
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
        mediaPreviewGrid = findViewById(R.id.mediaPreviewGrid);

        // Setup RecyclerView
        mediaPreviewGrid.setLayoutManager(new GridLayoutManager(this, 3));
        mediaAdapter = new MediaPreviewAdapter(this, selectedImages);
        mediaPreviewGrid.setAdapter(mediaAdapter);

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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            Toast.makeText(this, "This feature requires Android 4.1 or higher", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedImages.isEmpty()) {
            Toast.makeText(this, "Please select media files", Toast.LENGTH_SHORT).show();
            return;
        }

        for (Uri sourceUri : selectedImages) {
            try {
                String mimeType = getContentResolver().getType(sourceUri);
                boolean isVideo = mimeType != null && mimeType.startsWith("video");

                int width = 0, height = 0;
                if (isVideo) {
                    try {
                        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                        retriever.setDataSource(this, sourceUri);
                        String widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                        String heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                        String rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                        
                        if (widthStr != null && heightStr != null) {
                            width = Integer.parseInt(widthStr);
                            height = Integer.parseInt(heightStr);
                            
                            if (rotationStr != null) {
                                int rotation = Integer.parseInt(rotationStr);
                                if (rotation == 90 || rotation == 270) {
                                    int temp = width;
                                    width = height;
                                    height = temp;
                                }
                            }
                        }
                        retriever.release();
                    } catch (Exception e) {
                        continue;
                    }
                } else {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeStream(getContentResolver().openInputStream(sourceUri), null, options);
                    width = options.outWidth;
                    height = options.outHeight;
                }

                if (width == 0 || height == 0) {
                    Toast.makeText(this, "Could not determine media dimensions", Toast.LENGTH_SHORT).show();
                    continue;
                }

                boolean isVertical = height > width;
                String folderName = isVertical ? "Vertical" : "Horizontal";
                String subfolderPath = Environment.DIRECTORY_PICTURES + "/MediaSorter/" + folderName;

                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, getFileName(sourceUri));
                values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, subfolderPath);

                Uri collectionUri;
                if (isVideo) {
                    collectionUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, subfolderPath);
                } else {
                    collectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                }

                Uri targetUri = getContentResolver().insert(collectionUri, values);

                if (targetUri != null) {
                    try (InputStream in = getContentResolver().openInputStream(sourceUri);
                         OutputStream out = getContentResolver().openOutputStream(targetUri)) {
                        if (in != null && out != null) {
                            byte[] buffer = new byte[8192];
                            int read;
                            while ((read = in.read(buffer)) != -1) {
                                out.write(buffer, 0, read);
                            }
                        }
                    } catch (IOException e) {
                        getContentResolver().delete(targetUri, null, null);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        Toast.makeText(this, "Media sorted successfully", Toast.LENGTH_SHORT).show();
        selectedImages.clear();
        mediaAdapter.updateMedia(selectedImages);
        statusText.setText("");
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

    public void updateStatus(String status) {
        statusText.setText(status);
    }
}