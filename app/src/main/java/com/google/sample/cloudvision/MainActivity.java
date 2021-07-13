/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.sample.cloudvision;

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequest;
import com.google.api.services.vision.v1.VisionRequestInitializer;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;
import com.google.api.services.vision.v1.model.ImageContext;
import com.google.api.services.vision.v1.model.TextAnnotation;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;


public class MainActivity extends AppCompatActivity {
    private static final String CLOUD_VISION_API_KEY = "AIzaSyDQDuEkKniY2lYGi3ZCZrvnwWDuHJBctrk";
    public static final String FILE_NAME = "temp.jpg";
    private static final String ANDROID_CERT_HEADER = "X-Android-Cert";
    private static final String ANDROID_PACKAGE_HEADER = "X-Android-Package";
    private static final int MAX_LABEL_RESULTS = 10;
    private static final int MAX_DIMENSION = 1200;

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int GALLERY_PERMISSIONS_REQUEST = 0;
    private static final int GALLERY_IMAGE_REQUEST = 1;
    public static final int CAMERA_PERMISSIONS_REQUEST = 2;
    public static final int CAMERA_IMAGE_REQUEST = 3;

    private TextView mImageDetails;
    private ImageView mMainImage;


    @Override
    //Activityの初期化
    protected void onCreate(Bundle savedInstanceState) {
        //スーパークラスを継承、ライフサイクルの初期化
        super.onCreate(savedInstanceState);
        //ビューの定義、ファイルの場所の指定
        setContentView(R.layout.activity_main);
        //ツールバーをアクションバーとしてセット
        Toolbar toolbar = findViewById(R.id.toolbar);
        //クラスの呼び出し
        setSupportActionBar(toolbar);

        //フローティングアクションボタン
        FloatingActionButton fab = findViewById(R.id.fab);
        //フローティングボタンが押されたときの処理
        fab.setOnClickListener(view -> {
            //アラート処理
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder
                    .setMessage(R.string.dialog_select_prompt)
                    .setPositiveButton(R.string.dialog_select_gallery, (dialog, which) -> startGalleryChooser())
                    .setNegativeButton(R.string.dialog_select_camera, (dialog, which) -> startCamera());
            builder.create().show();
        });

        //ビューの指定
        mImageDetails = findViewById(R.id.image_details);
        mMainImage = findViewById(R.id.main_image);
    }

    //ギャラリーを参照する場合
    public void startGalleryChooser() {
        //ストレージにアクセスリクエスト
        if (PermissionUtils.requestPermission(this, GALLERY_PERMISSIONS_REQUEST, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            //インテントを作成する
            Intent intent = new Intent();
            //画像のみ取り込めるように
            intent.setType("image/*");
            //動作の指定
            intent.setAction(Intent.ACTION_GET_CONTENT);
            //結果に対するアクティビティを起動する
            startActivityForResult(Intent.createChooser(intent, "Select a photo"),
                    GALLERY_IMAGE_REQUEST);
        }
    }

    //カメラで写真を撮る場合
    public void startCamera() {
        if (PermissionUtils.requestPermission(
                this,
                CAMERA_PERMISSIONS_REQUEST,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA)) {
            //カメラ起動アクティビティー
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            //撮った写真の場所
            Uri photoUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", getCameraFile());
            //撮った写真の保存場所
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            //一時的なアクセス権限付与
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            //結果に対するアクティビティを起動する
            startActivityForResult(intent, CAMERA_IMAGE_REQUEST);
        }
    }

    //カメラファイルを作成
    public File getCameraFile() {
        File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return new File(dir, FILE_NAME);
    }

    @Override
    //アクティビティの結果に対する処理
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //スーパークラスを継承、結果を取得
        super.onActivityResult(requestCode, resultCode, data);
        //写真がギャラリーから選択された場合
        if (requestCode == GALLERY_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            uploadImage(data.getData());
            //写真が撮られた場合
        } else if (requestCode == CAMERA_IMAGE_REQUEST && resultCode == RESULT_OK) {
            Uri photoUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", getCameraFile());
            uploadImage(photoUri);
        }
    }

    @Override
    //アクセス権限のリクエスト（写真とギャラリーに対して）
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case CAMERA_PERMISSIONS_REQUEST:
                if (PermissionUtils.permissionGranted(requestCode, CAMERA_PERMISSIONS_REQUEST, grantResults)) {
                    startCamera();
                }
                break;
            case GALLERY_PERMISSIONS_REQUEST:
                if (PermissionUtils.permissionGranted(requestCode, GALLERY_PERMISSIONS_REQUEST, grantResults)) {
                    startGalleryChooser();
                }
                break;
        }
    }

    //写真の処理
    public void uploadImage(Uri uri) {
        //対象の画像がある場合
        if (uri != null) {
            //例外が発生したときの対応
            try {
                // 帯域幅を節約するために画像を縮小する
                Bitmap bitmap =
                        scaleBitmapDown(
                                MediaStore.Images.Media.getBitmap(getContentResolver(), uri),
                                MAX_DIMENSION);

                callCloudVision(bitmap);
                mMainImage.setImageBitmap(bitmap);
                //ファイルが読み込めなかった場合
            } catch (IOException e) {
                //原因のログを出力
                Log.d(TAG, "Image picking failed because " + e.getMessage());
                //小さなポップアップを表示
                Toast.makeText(this, R.string.image_picker_error, Toast.LENGTH_LONG).show();
            }
            //画像が選択されなかった場合
        } else {
            Log.d(TAG, "Image picker gave us a null image.");
            Toast.makeText(this, R.string.image_picker_error, Toast.LENGTH_LONG).show();
        }
    }

    //リクエストを実行する
    private Vision.Images.Annotate prepareAnnotationRequest(Bitmap bitmap) throws IOException {
        //HttpTransport と JsonFactory　から Vision.Builder を生成する
        HttpTransport httpTransport = AndroidHttp.newCompatibleTransport();
        JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

        //API_KEY から初期化クラスを生成する
        VisionRequestInitializer requestInitializer = new VisionRequestInitializer(CLOUD_VISION_API_KEY) {
            /**
             * これをオーバーライドして、重要な識別フィールドをHTTPヘッダーに挿入できるようにします。
             * これにより、制限付きのクラウドプラットフォームAPIキーを使用できるようになります。
             */
            @Override
            //リクエストの初期化クラス
            protected void initializeVisionRequest(VisionRequest<?> visionRequest) throws IOException {
                super.initializeVisionRequest(visionRequest);

                String packageName = getPackageName();
                visionRequest.getRequestHeaders().set(ANDROID_PACKAGE_HEADER, packageName);

                String sig = PackageManagerUtils.getSignature(getPackageManager(), packageName);

                visionRequest.getRequestHeaders().set(ANDROID_CERT_HEADER, sig);
            }
        };

        Vision.Builder builder = new Vision.Builder(httpTransport, jsonFactory, null);
        //requestInitializer で初期化する
        builder.setVisionRequestInitializer(requestInitializer);

        //Vision を生成する
        Vision vision = builder.build();

        //BatchAnnotateImagesRequest:複数のannotateImageRequestを1つのサービスコールにまとめるクラス
        BatchAnnotateImagesRequest batchAnnotateImagesRequest =
                new BatchAnnotateImagesRequest();
        //
        batchAnnotateImagesRequest.setRequests(new ArrayList<AnnotateImageRequest>() {{
            //AnnotateImageRequest:ユーザー提供の画像とユーザーが要求した機能からリクエストを生成するクラスを生成する
            AnnotateImageRequest annotateImageRequest = new AnnotateImageRequest();

            Image base64EncodedImage = new Image();
            // Bitmapをjpegに変換
            // Androidが理解できる形式であるが、CloudVisionである場合に備えてBitmapをbyteArrayに変換する
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
            byte[] imageBytes = byteArrayOutputStream.toByteArray();

            // JPEGをBase64でエンコード
            base64EncodedImage.encodeContent(imageBytes);
            annotateImageRequest.setImage(base64EncodedImage);

            // どの特徴量を検出するかを設定*******************************************************************************
            annotateImageRequest.setFeatures(new ArrayList<Feature>() {{
                Feature textDetection = new Feature();
                textDetection.setType("TEXT_DETECTION");
                textDetection.setMaxResults(MAX_LABEL_RESULTS);
                add(textDetection);
            }});

            //より早く文字認識させるため、日本語を優先-----------------------------4行追加
            ImageContext imageContext = new ImageContext();
            String[] languages = {"ja"};
            imageContext.setLanguageHints(Arrays.asList(languages));
            annotateImageRequest.setImageContext(imageContext);

            // リクエストにリストを追加
            add(annotateImageRequest);
        }});

        //Vision.Images.Annotate を生成する
        Vision.Images.Annotate annotateRequest = vision.images().annotate(batchAnnotateImagesRequest);
        // バグのため : 大きな画像の場合でのVision APIへのリクエストは、GZipped時に失敗する
        annotateRequest.setDisableGZipContent(true);
        Log.d(TAG, "created Cloud Vision request object, sending request");

        return annotateRequest;
    }

    //AsyncTask : Android の非同期実行するクラス.リクエストを実行して、レスポンスを受ける
    private static class LableDetectionTask extends AsyncTask<Object, Void, String> {
        private final WeakReference<MainActivity> mActivityWeakReference;
        private Vision.Images.Annotate mRequest;

        LableDetectionTask(MainActivity activity, Vision.Images.Annotate annotate) {
            mActivityWeakReference = new WeakReference<>(activity);
            mRequest = annotate;
        }

        @Override

        //メインスレッドとは別のスレッドで実行される。非同期で処理したい内容を記述、このメソッドだけは必ず実装する必要あり
        protected String doInBackground(Object... params) {
            try {
                Log.d(TAG, "created Cloud Vision request object, sending request");
                BatchAnnotateImagesResponse response = mRequest.execute();
                return convertResponseToString(response);

            } catch (GoogleJsonResponseException e) {
                Log.d(TAG, "failed to make API request because " + e.getContent());
            } catch (IOException e) {
                Log.d(TAG, "failed to make API request because of other IOException " + e.getMessage());
            }
            return "Cloud Vision API request failed. Check logs for details.";
        }

        //doInBackgroundメソッドの実行前にメインスレッドで実行される。非同期処理前に何か処理を行いたい時などに使う。
        protected void onPostExecute(String result) {
            MainActivity activity = mActivityWeakReference.get();
            if (activity != null && !activity.isFinishing()) {
                TextView imageDetail = activity.findViewById(R.id.image_details);
                imageDetail.setText(result);
            }
        }
    }

    //CloudVision APIの呼び出し
    private void callCloudVision(final Bitmap bitmap) {
        // テキストを読み込みに切り替える
        mImageDetails.setText(R.string.loading_message);

        // とにかくネットワークを使用する必要があるため、非同期タスクで実際の作業を行う
        try {
            AsyncTask<Object, Void, String> labelDetectionTask = new LableDetectionTask(this, prepareAnnotationRequest(bitmap));
            labelDetectionTask.execute();
        } catch (IOException e) {
            Log.d(TAG, "failed to make API request because of other IOException " +
                    e.getMessage());
        }
    }

    //必要に応じて、帯域幅を節約するために画像を縮小する
    private Bitmap scaleBitmapDown(Bitmap bitmap, int maxDimension) {

        int originalWidth = bitmap.getWidth();
        int originalHeight = bitmap.getHeight();
        int resizedWidth = maxDimension;
        int resizedHeight = maxDimension;

        if (originalHeight > originalWidth) {
            resizedHeight = maxDimension;
            resizedWidth = (int) (resizedHeight * (float) originalWidth / (float) originalHeight);
        } else if (originalWidth > originalHeight) {
            resizedWidth = maxDimension;
            resizedHeight = (int) (resizedWidth * (float) originalHeight / (float) originalWidth);
        } else if (originalHeight == originalWidth) {
            resizedHeight = maxDimension;
            resizedWidth = maxDimension;
        }
        return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false);
    }

    //返ってきた文字列のリストを表示するメソッド
    private static String convertResponseToString(BatchAnnotateImagesResponse response) {
        StringBuilder message = new StringBuilder("以下の添加物が見つかりました:\n\n");
        TextAnnotation label = response.getResponses().get(0).getFullTextAnnotation();

        if (label != null) {
            //callGet用URL
            String geturl = "https://script.google.com/macros/s/AKfycbweJFfBqKUs5gGNnkV2xwTZtZPptI6ebEhcCU2_JvOmHwM2TCk/exec?text=\"" + label.getText() + "\"&source=ja&target=en";

            //callGetの戻り値を格納
            String callGetStr = callGet(geturl);
            //callGetからの戻り値を入れる配列
            String[] callGetStrSplit;
            //callGetからの戻り値を分割するパターン
            String regex  = ",| ";

            //callGetからの戻り値から改行コードを削除
            callGetStr = callGetStr.replace("\n","");
            //callGetからの戻り値を分割して配列に格納
            callGetStrSplit = callGetStr.split(regex, 0);

            for (int i = 0 ; i < callGetStrSplit.length ; i++){

                //callget
//                geturl = "https://en.wikipedia.org/wiki/List_of_food_additives";
//                String callGetStrWiki = callGet(geturl);
//                for (int j = 0 ; j < callGetStrWiki.length() ; j++){
//                    if (callGetStrSplit[i].contains(callGetStrWiki)){
//                        message.append(callGetStrWiki);
//                    }
//                }

                //scraping
                String scrapingStr = scraping(callGetStrSplit[i]);
                if (scrapingStr != null) {
                    message.append(callGetStrSplit[i] + "\n\n");
                    message.append(scrapingStr + "\n\n");
                }
            }

            //テキスト表示
            //message.append(label.getText());

            //json表示
            //message.append(label);
        } else {
            message.append("何もありません。");
        }

        return message.toString();
    }

    //スクレイピング
    //<li><a href="/wiki/Ammonium_bicarbonate" title="Ammonium bicarbonate">Ammonium bicarbonate</a> – mineral salt</li>
    public static String scraping(String text) {

        String elementStr = null;
        String elementStrOut = null;

        try {
            Document doc = null;
            // jsoupを使用してwikiにアクセス
            doc = Jsoup.connect("https://en.wikipedia.org/wiki/List_of_food_additives").get();
            Elements elements = doc.select("li");
            for (Element element : elements) {
                elementStr = element.toString();
                if (elementStr.contains(text)) {
                    System.out.println(element.outerHtml());
                    elementStrOut = element.text();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return elementStrOut;
    }

    //HttpURLConnection : GETリクエスト
    public static String callGet(String strGetUrl){

        HttpURLConnection con = null;
        StringBuffer result = new StringBuffer();
        String[] food_additives = new String[0];

        try {

            URL url = new URL(strGetUrl);

            con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.connect();

            // HTTPレスポンスコード
            final int status = con.getResponseCode();
            if (status == HttpURLConnection.HTTP_OK) {
                // 通信に成功した
                // テキストを取得する
                final InputStream in = con.getInputStream();
                String encoding = con.getContentEncoding();
                if(null == encoding){
                    encoding = "UTF-8";
                }
                final InputStreamReader inReader = new InputStreamReader(in, encoding);
                final BufferedReader bufReader = new BufferedReader(inReader);
                String line = null;
                // 1行ずつテキストを読み込む
                while((line = bufReader.readLine()) != null) {
                    result.append(line);
                }
                bufReader.close();
                inReader.close();
                in.close();
            }else{
                System.out.println(status);
            }

        }catch (Exception e1) {
            e1.printStackTrace();
        } finally {
            if (con != null) {
                // コネクションを切断
                con.disconnect();
            }
        }
        System.out.println("result=" + result.toString());

        return result.toString();
    }

    //HttpURLConnection : POSTリクエスト
    public static String callPost(String strPostUrl, String strContentType, String formParam){

        HttpURLConnection con = null;
        StringBuffer result = new StringBuffer();

        try {

            URL url = new URL(strPostUrl);

            con.setDoOutput(true);
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", strContentType);
            OutputStreamWriter out = new OutputStreamWriter(con.getOutputStream());
            out.write(formParam);
            out.close();
            con.connect();

            // HTTPレスポンスコード
            final int status = con.getResponseCode();
            if (status == HttpURLConnection.HTTP_OK) {
                // 通信に成功した
                // テキストを取得する
                final InputStream in = con.getInputStream();
                String encoding = con.getContentEncoding();
                if(null == encoding){
                    encoding = "UTF-8";
                }
                final InputStreamReader inReader = new InputStreamReader(in, encoding);
                final BufferedReader bufReader = new BufferedReader(inReader);
                String line = null;
                // 1行ずつテキストを読み込む
                while((line = bufReader.readLine()) != null) {
                    result.append(line);
                }
                bufReader.close();
                inReader.close();
                in.close();
            }else{
                System.out.println(status);
            }

        }catch (Exception e1) {
            e1.printStackTrace();
        } finally {
            if (con != null) {
                // コネクションを切断
                con.disconnect();
            }
        }
        System.out.println("result=" + result.toString());

        return result.toString();
    }
}