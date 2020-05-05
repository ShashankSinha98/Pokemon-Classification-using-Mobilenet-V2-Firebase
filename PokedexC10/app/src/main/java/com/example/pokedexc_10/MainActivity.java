package com.example.pokedexc_10;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.common.FirebaseMLException;
import com.google.firebase.ml.common.modeldownload.FirebaseModelDownloadConditions;
import com.google.firebase.ml.common.modeldownload.FirebaseModelManager;
import com.google.firebase.ml.custom.FirebaseCustomLocalModel;
import com.google.firebase.ml.custom.FirebaseCustomRemoteModel;
import com.google.firebase.ml.custom.FirebaseModelDataType;
import com.google.firebase.ml.custom.FirebaseModelInputOutputOptions;
import com.google.firebase.ml.custom.FirebaseModelInputs;
import com.google.firebase.ml.custom.FirebaseModelInterpreter;
import com.google.firebase.ml.custom.FirebaseModelInterpreterOptions;
import com.google.firebase.ml.custom.FirebaseModelOutputs;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity {

    private FirebaseCustomRemoteModel remoteModel;
    private String TAG = MainActivity.class.getSimpleName();
    private FirebaseModelInterpreter interpreter;

    private FirebaseCustomLocalModel localModel;
    private FirebaseModelInputOutputOptions inputOutputOptions;

    private ImageView mImageView;

    private Bitmap bitmap;

    private TextView resultDisplayTV;
    private Button chooseImage, scanImage;
    private Uri uri;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mImageView = findViewById(R.id.pok_image);
        resultDisplayTV = findViewById(R.id.results_tv);
        chooseImage = findViewById(R.id.add_img_btn);
        scanImage = findViewById(R.id.scan_image_btn);

        FirebaseApp.initializeApp(this);

        // Choose Img
        chooseImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                {
                    if(ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                    {
                        ActivityCompat.requestPermissions(MainActivity.this,new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},1);

                    } else {

                        bringImagePicker();
                    }
                } else {

                    bringImagePicker();
                }
            }
        });

        // Search Code
        scanImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resultDisplayTV.setText("");

                // Getting bitmap from Imageview
                BitmapDrawable drawable = (BitmapDrawable) mImageView.getDrawable();
                bitmap = drawable.getBitmap();

                runImageRecoginition();

            }
        });

        remoteModel = new FirebaseCustomRemoteModel.Builder("pokedexc10").build();

        FirebaseModelDownloadConditions conditions = new FirebaseModelDownloadConditions.Builder().requireWifi().build();

        FirebaseModelManager.getInstance().download(remoteModel, conditions)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        // Success.
                        if(task.isSuccessful()){
                            Log.d(TAG+"_DWNLD TSK: ","Task Successfull");
                        } else {
                            Log.d(TAG+"_DWNLD TSK: ","Task UnSuccessfull");
                        }
                    }
                });

        localModel = new FirebaseCustomLocalModel.Builder()
                .setAssetFilePath("pokemon_mobilenetv2.tflite")
                .build();

        try {
            FirebaseModelInterpreterOptions options =
                    new FirebaseModelInterpreterOptions.Builder(localModel).build();

            interpreter = FirebaseModelInterpreter.getInstance(options);

            Log.d(TAG+"_LCL MDL DWNLD STS: ","Local Model Loaded");

        } catch (FirebaseMLException e) {

            Log.d(TAG+"_LCL MDL DWNLD STS: ","Error Loading local model :"+e.getMessage());
        }

        FirebaseModelManager.getInstance().download(remoteModel, conditions)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void v) {
                        // Download complete. Depending on your app, you could enable
                        // the ML feature, or switch from the local model to the remote
                        // model, etc.

                        Log.d(TAG+"_RMT MDL DWNLD STS","Remote Model Downloaded Successfully");
                    }
                });

        FirebaseModelManager.getInstance().isModelDownloaded(remoteModel)
                .addOnSuccessListener(new OnSuccessListener<Boolean>() {
                    @Override
                    public void onSuccess(Boolean isDownloaded) {
                        FirebaseModelInterpreterOptions options;
                        if (isDownloaded) {
                            options = new FirebaseModelInterpreterOptions.Builder(remoteModel).build();
                            Log.d(TAG+"_INTRPTR LOADED: ","Remote model is being used");
                        } else {
                            options = new FirebaseModelInterpreterOptions.Builder(localModel).build();
                            Log.d(TAG+"_INTRPTR LOADED: ","Local model is being used");
                        }

                        try {
                            interpreter = FirebaseModelInterpreter.getInstance(options);
                        } catch (FirebaseMLException e) {
                            Log.d(TAG+"_ERROR LODNG INTRPTR: ","Error loading interpeter: "+e.getMessage());
                        }
                        // ...
                    }
                });


        try {
            inputOutputOptions =
                    new FirebaseModelInputOutputOptions.Builder()
                            .setInputFormat(0, FirebaseModelDataType.FLOAT32, new int[]{1, 224, 224, 3})
                            .setOutputFormat(0, FirebaseModelDataType.FLOAT32, new int[]{1, 10})
                            .build();
        } catch (FirebaseMLException e) {
            Log.d(TAG+"_ERROR: ","Error setting up FirebaseModelInputOutputOptions: "+e.getMessage());
        }



        bitmap = ((BitmapDrawable)mImageView.getDrawable()).getBitmap();
        bitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true);


    }

    private void runImageRecoginition() {


        int batchNum = 0;
        float[][][][] input = new float[1][224][224][3];

        try {
            for (int x = 0; x < 224; x++) {
                for (int y = 0; y < 224; y++) {
                    int pixel = bitmap.getPixel(x, y);

                    // Not Normalizing Image Data
                    input[batchNum][x][y][0] = Color.red(pixel);
                    input[batchNum][x][y][1] = Color.green(pixel);
                    input[batchNum][x][y][2] = Color.blue(pixel);
                }
            }
        } catch(Exception e){
            Log.d(TAG+"_ERROR","Error while converting bitmap to pixels : "+e.getMessage());
            resultDisplayTV.setText("Couldn't process Img. Please try again with different aspect ratio.");
        }

        FirebaseModelInputs inputs = null;
        try {
            inputs = new FirebaseModelInputs.Builder()
                    .add(input)  // add() as many input arrays as your model requires
                    .build();
            Log.d(TAG+"INPT STS: ","Success in generating FirebaseModelInputs");
        } catch (FirebaseMLException e) {
            Log.d(TAG+"_ERROR:","Error in building FirebaseModelInputs : "+e.getMessage());
        }


        interpreter.run(inputs, inputOutputOptions)
                .addOnSuccessListener(
                        new OnSuccessListener<FirebaseModelOutputs>() {
                            @Override
                            public void onSuccess(FirebaseModelOutputs result) {
                                Log.d(TAG+"_INTRPTR STS:","Success in running interpreter on input image");

                                float[][] output = result.getOutput(0);
                                float[] probabilities = output[0];

                                BufferedReader reader = null;

                                try {
                                    reader = new BufferedReader(
                                            new InputStreamReader(getAssets().open("pokedex_c10_label.txt")));

                                    String res="";
                                    for (int i = 0; i < probabilities.length; i++) {
                                        String label = null;

                                        try {
                                            label = reader.readLine();
                                        } catch (IOException e1) {
                                            Log.d(TAG+"_ERROR: ","Error reading line in label file: "+e1.getMessage());
                                        }

                                        res+=String.format("%s: %1.4f", label, probabilities[i])+"\n";

                                        Log.i(TAG+"_MLKit",String.format("%s: %1.4f", label, probabilities[i]));
                                    }
                                    resultDisplayTV.setText(res);

                                } catch (IOException e2) {
                                    Log.d(TAG+"_ERROR: ","Error reading label file: "+e2.getMessage());
                                }

                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.d(TAG+"_ERROR: ","Error running interpreter on input image: "+e.getMessage());
                            }
                        });


    }

    private void bringImagePicker() {
        CropImage.activity()
                .setGuidelines(CropImageView.Guidelines.ON).setAspectRatio(1,1).setMinCropResultSize(224,224)
                .setRequestedSize(224,224)
                .setCropShape(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? CropImageView.CropShape.RECTANGLE : CropImageView.CropShape.OVAL)
                .start(MainActivity.this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            if (resultCode == RESULT_OK) {

                uri = result.getUri();
                resultDisplayTV.setText("");
                mImageView.setImageURI(result.getUri());


            } else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {

                Exception error = result.getError();
                resultDisplayTV.setText("Error Loading Image: " + error);

            }
        }
    }


}
