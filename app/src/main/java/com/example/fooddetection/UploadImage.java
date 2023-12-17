package com.example.fooddetection;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class UploadImage extends AppCompatActivity {

    private ImageView imageView;
    private Button uploadButton;

    private static final int PICK_IMAGE_REQUEST = 1;
    private byte[] selectedImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload_image);

        imageView = findViewById(R.id.imageView);
        uploadButton = findViewById(R.id.uploadButton);

        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openImagePicker();
            }
        });

        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedImage != null) {
                    uploadImage();
                } else {
                    openImagePicker(); // Open image picker if no image is selected
                }
            }
        });
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), data.getData());
                imageView.setImageBitmap(bitmap);

                // Convert Bitmap to ByteArray
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
                selectedImage = byteArrayOutputStream.toByteArray();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void uploadImage() {
        // Replace with your actual PHP script URL
        String uploadUrl = "http://192.168.0.106/model/index.php"; // Example IP address, replace with your server's IP

        // Create request body
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", "image.jpg", RequestBody.create(MediaType.parse("image/jpeg"), selectedImage))
                .build();

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(uploadUrl)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (response.isSuccessful()) {
                        final String responseBody = response.body().string();
                        Log.d("API Response Body", responseBody);

                        // Parse the JSON response
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        JSONArray predictions = jsonResponse.getJSONArray("predictions");

                        // Get the image dimensions
                        JSONObject imageInfo = jsonResponse.getJSONObject("image");
                        int originalWidth = imageInfo.getInt("width");
                        int originalHeight = imageInfo.getInt("height");

                        // Resize the image to 450x450
                        BitmapDrawable drawable = (BitmapDrawable) imageView.getDrawable();
                        Bitmap originalBitmap = drawable.getBitmap();
//                        Bitmap resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, 450, 450, true);

                        // Calculate scaling factors for drawing on the ImageView
                        float scaleX = (float) imageView.getWidth() / originalWidth;
                        float scaleY = (float) imageView.getHeight() / originalHeight;

                        // Create a mutable bitmap for drawing
                        Bitmap mutableBitmap = Bitmap.createBitmap(originalBitmap).copy(Bitmap.Config.ARGB_8888, true);

                        // Get the canvas to draw on the bitmap
                        Canvas canvas = new Canvas(mutableBitmap);

                        // Iterate through predictions and draw bounding boxes
                        for (int i = 0; i < predictions.length(); i++) {
                            JSONObject prediction = predictions.getJSONObject(i);

                            float centerX = (float) prediction.getDouble("x");
                            float centerY = (float) prediction.getDouble("y");
                            float width = (float) prediction.getDouble("width");
                            float height = (float) prediction.getDouble("height");

                            // Calculate corner points
                            float x1 = centerX - (width / 2);
                            float y1 = centerY - (height / 2);
                            float x2 = centerX + (width / 2);
                            float y2 = centerY + (height / 2);

                            // Draw bounding box
                            Paint paint = new Paint();
                            paint.setColor(Color.RED);
                            paint.setStyle(Paint.Style.STROKE);
                            paint.setStrokeWidth(5);
                            canvas.drawRect(x1, y1, x2, y2, paint);

                            // Draw class label
                            String label = prediction.getString("class");
                            paint.setColor(Color.RED);
                            paint.setStyle(Paint.Style.FILL);
                            paint.setTextSize(30);
                            canvas.drawText(label, x1, y1 - 10, paint);
                        }

                        // Update the ImageView with the modified bitmap
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                imageView.setImageBitmap(mutableBitmap);
                            }
                        });

                    } else {
                        Log.e("API Error", "Unsuccessful response: " + response.code() + " " + response.message());
                    }
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                } finally {
                    if (response.body() != null) {
                        response.body().close();
                    }
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                Log.e("API Error", "Failed to make API request: " + e.getMessage());
            }
        });
    }
}