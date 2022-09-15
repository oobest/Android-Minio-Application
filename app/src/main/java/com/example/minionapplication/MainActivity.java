package com.example.minionapplication;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.bumptech.glide.Glide;
import com.example.minionapplication.databinding.ActivityMainBinding;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleEmitter;
import io.reactivex.rxjava3.core.SingleOnSubscribe;
import io.reactivex.rxjava3.internal.observers.ConsumerSingleObserver;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

public class MainActivity extends AppCompatActivity {


    private ActivityMainBinding binding;

    private static final int PHOTO_PICKER_REQUEST_CODE = 2;


    private static final String ACCESS_KEY = "android-upload-test";
    private static final String SECRET_KEY = "272149DD9DE90C2729C57B884F051B33";
    private static final String END_POINT = "http://192.168.2.138:9011";
    private static final String BUCKET_NAME = "check-bridge";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);

        binding.photoButton.setOnClickListener(v -> {
            selectPhoto();
        });

        binding.uploadButton.setOnClickListener(v -> {
            uploadPhoto();
        });
    }

    /**
     * 选择图片
     */
    private void selectPhoto() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, PHOTO_PICKER_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null) {
            return;
        }
        if (requestCode == PHOTO_PICKER_REQUEST_CODE) {
            Uri fileUri = data.getData();
            showImage(fileUri);
        }
    }

    private void showImage(Uri imageUri) {
        Glide.with(this).load(imageUri).into(binding.imageView);
        binding.imageView.setTag(imageUri);
    }

    private void uploadPhoto() {
        Uri fileUri = (Uri) binding.imageView.getTag();
        if (fileUri == null) {
            Snackbar.make(binding.imageView, "请选择要上传的图片", Snackbar.LENGTH_SHORT).show();
            return;
        }
        Single.create(new SingleOnSubscribe<Boolean>() {
                    @Override
                    public void subscribe(@NonNull SingleEmitter<Boolean> emitter) throws Throwable {
                        uploadFile(fileUri);
                        emitter.onSuccess(true);
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new ConsumerSingleObserver<>(
                        success -> {
                            Snackbar.make(binding.uploadButton, "上传成功", Snackbar.LENGTH_SHORT).show();
                        },
                        throwable -> {
                            Snackbar.make(binding.uploadButton, "上传失败", Snackbar.LENGTH_SHORT).show();
                            Timber.e(throwable.getLocalizedMessage());
                        }));
    }


    private void uploadFile(Uri uri) throws IOException {
        AmazonS3 s3 = new AmazonS3Client(new AWSCredentials() {
            @Override
            public String getAWSAccessKeyId() {
                return ACCESS_KEY;
            }

            @Override
            public String getAWSSecretKey() {
                return SECRET_KEY;
            }
        }, Region.getRegion(Regions.CN_NORTH_1));
        s3.setEndpoint(END_POINT);

        boolean isExist = s3.doesBucketExist(BUCKET_NAME);
        if (!isExist) {
            s3.createBucket(BUCKET_NAME);
        }

        ContentResolver contentResolver = getContentResolver();
        InputStream inputStream = contentResolver.openInputStream(uri);
        long contentLength = inputStream.available();

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType("application/octet-stream"); // 设置content-type。
        metadata.setContentLength(contentLength);

        String key = System.currentTimeMillis() + ".jpg";
        PutObjectRequest putObjectRequest = new PutObjectRequest(BUCKET_NAME, System.currentTimeMillis() + ".jpg", inputStream, metadata)
                .withGeneralProgressListener(new ProgressListener() {
                    int readByte = 0;

                    @Override
                    public void progressChanged(ProgressEvent progressEvent) {
                        readByte += progressEvent.getBytesTransferred();
                        Timber.d("progressChanged：" + key + "文件上传进度：" + ((float) (readByte / (float) contentLength) * 100 + "%"));
                    }
                });
        s3.putObject(putObjectRequest);
        GeneratePresignedUrlRequest urlRequest = new GeneratePresignedUrlRequest(BUCKET_NAME, key);
        URL url = s3.generatePresignedUrl(urlRequest);
        Timber.d("文件地址：url=>" + url);
    }
}