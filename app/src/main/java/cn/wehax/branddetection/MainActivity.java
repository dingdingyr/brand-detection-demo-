package cn.wehax.branddetection;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import roboguice.activity.RoboActionBarActivity;
import roboguice.inject.InjectView;
import wehax.util.StringUtils;
import wehax.util.ToastUtils;

@SuppressWarnings("unused")
public class MainActivity extends RoboActionBarActivity {
    private static final String TAG = "detect";
    // msg code
    private static final int MSG_DETECT_SUCCESS = 10000;
    private static final int MSG_DETECT_FAIL = 10001;

    // extra key
    private static final String KEY_DETECT_RESULT = "KEY_DETECT_RESULT";

    // request code
    public static int REQUEST_GET_PIC_FROM_CAMERA = 1;
    public static int REQUEST_GET_PIC_FROM_ALBUM = 2;

    // server interface url
    private final String DETECT_PIC_URL = "http://172.16.0.12:8080/LogoDetectServer/LogoDetectAndroid";

    ActionBar actionbar;

    @InjectView(R.id.brand_detect_btn)
    private Button detectBtn;

    @InjectView(R.id.src_pic_iv)
    private ImageView srcPicIv;

    PaintKit myPaintKit;

    // 选择的图片绝对路径
    private String selectedPicPath;
    // 选择的图片Uri
    private Uri imageUri;
    // 图片Bitmap数据
    Bitmap selectPicBitmap;

    /**
     * 选择图片大小
     */
    Point selectPicSize = new Point();
    /**
     * ImageView控件尺寸
     */
    Point imageViewSize = new Point();

    private Handler myHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            setDetectBtn(false);
            if (msg.what == MSG_DETECT_SUCCESS) {
                String result = msg.getData().getString(KEY_DETECT_RESULT);
                Log.e(TAG, "MSG_DETECT_SUCCESS");
                Log.e(TAG, "result = " + result);
                showDetectResult(result);
                return true;
            } else if (msg.what == MSG_DETECT_FAIL) {
                ToastUtils.show(MainActivity.this, R.string.detect_fail);
                return true;
            }
            return false;
        }
    });

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initActionbar();
        initView();
    }

    private void initActionbar() {
        actionbar = getSupportActionBar();
        actionbar.show();
    }

    private void initView() {
        myPaintKit = new PaintKit(this);

        detectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (StringUtils.isEmpty(selectedPicPath)) {
                    ToastUtils.show(MainActivity.this, "请先选择图片");
                    return;
                }

                detectPictureBrand();
//                showDetectResult("Served at: /LogoDetectServer{\"rect\":[{\"x\":343,\"width\":90,\"y\":189,\"height\":90},{\"x\":343,\"width\":90,\"y\":189,\"height\":90}]}");
            }
        });

        // 获取ImageView尺寸
        ViewTreeObserver vto = srcPicIv.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                srcPicIv.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                imageViewSize.set(srcPicIv.getWidth(), srcPicIv.getHeight());
            }
        });
    }

    /**
     * 检测图片商标
     */
    private void detectPictureBrand() {
        setDetectBtn(true);

        new Thread(new Runnable() {
            @Override
            public void run() {
                String end = "\r\n";
                String twoHyphens = "--";
                String boundary = "*****";
                try {
                    URL url = new URL(DETECT_PIC_URL);
                    HttpURLConnection connection = (HttpURLConnection) url
                            .openConnection();
                    connection.setDoInput(true);
                    connection.setDoOutput(true);
                    connection.setUseCaches(false);
                    connection.setConnectTimeout(5000);
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Connection", "Keep-Alive");
                    connection.setRequestProperty("Charset", "UTF-8");
                    connection.setRequestProperty("Content-Type",
                            "multipart/form-data;boundary=" + boundary);

                    // 上传图片数据给服务器
                    DataOutputStream ds = new DataOutputStream(
                            connection.getOutputStream());

                    ds.writeBytes(twoHyphens + boundary + end);
                    ds.writeBytes("Content-Disposition: form-data; "
                            + "name=\"file1\";filename=\"image.jpg\"" + end);
                    ds.writeBytes(end);

                    FileInputStream fis = new FileInputStream(selectedPicPath);
                    int bufferSize = 1024;
                    byte[] buffer = new byte[bufferSize];
                    int length;
                    while ((length = fis.read(buffer)) != -1) {
                        ds.write(buffer, 0, length);
                    }

                    ds.writeBytes(end);
                    ds.writeBytes(twoHyphens + boundary + twoHyphens + end);

                    fis.close();
                    ds.flush();
                    ds.close();

                    // 获取服务器返回的商标检测数据
                    BufferedReader bufferReader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream()));
                    StringBuilder result = new StringBuilder();
                    String line;
                    while ((line = bufferReader.readLine()) != null) {
                        result.append(line);
                    }
                    Log.e(TAG, "detectPictureBrand/result = " + result.toString());
                    bufferReader.close();

                    connection.disconnect();

                    Message msg = myHandler.obtainMessage(MSG_DETECT_SUCCESS);
                    Bundle bundle = new Bundle();
                    bundle.putString(KEY_DETECT_RESULT, result.toString());
                    msg.setData(bundle);
                    myHandler.sendMessage(msg);
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e(TAG, "detectPictureBrand-exception/" + e.toString());
                    myHandler.sendEmptyMessage(MSG_DETECT_FAIL);
                }
            }
        }).start();
    }

    /**
     * 显示检测结果
     *
     * @param result
     */
    private void showDetectResult(String result) {
        try {
            List<Rect> rectList = processResult(result);
            if (rectList.isEmpty()) {
                ToastUtils.show(this, "未检测到任何商标");
                return;
            }

            if (selectPicBitmap == null)
                throw new Exception("selectPicBitmap is null");

            Bitmap newBmp = selectPicBitmap.copy(Bitmap.Config.ARGB_8888, true);

            Canvas canvas = new Canvas(newBmp);
            Paint paint = myPaintKit.getIdentificationGraphPaint(imageViewSize, selectPicSize);
            for (Rect item : rectList) {
                canvas.drawRect(item, paint);
            }
            canvas.save();

            srcPicIv.setImageBitmap(newBmp);
        } catch (JSONException e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "", e);
        }
    }

    /**
     * 将Json数据转化为矩形列表
     *
     * @param result
     * @return
     * @throws JSONException
     */
    private List<Rect> processResult(String result) throws JSONException {
        result = result.substring(result.indexOf("{"), result.length());
        JSONArray ja = new JSONObject(result).optJSONArray("rect");
        List<Rect> rectList = new ArrayList<>();
        for (int i = 0; i < ja.length(); ++i) {
            JSONObject jo = ja.getJSONObject(i);
            int left = jo.getInt("x");
            int top = jo.getInt("y");
            int right = left + jo.getInt("width");
            int bottom = top + jo.getInt("height");
            Rect rect = new Rect(left, top, right, bottom);
            rectList.add(rect);
        }
        return rectList;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_GET_PIC_FROM_CAMERA && resultCode == Activity.RESULT_OK) {
            // 处理拍照返回图片
            Options op = new Options();
            op.inSampleSize = 1;
            try {
                selectPicBitmap = BitmapFactory.decodeStream(this.getContentResolver()
                        .openInputStream(imageUri), null, op);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            if (selectPicBitmap != null) {
                selectPicSize.set(selectPicBitmap.getWidth(), selectPicBitmap.getHeight());
                srcPicIv.setImageBitmap(selectPicBitmap);
            }
        } else if (requestCode == REQUEST_GET_PIC_FROM_ALBUM
                && resultCode == Activity.RESULT_OK && data != null) {
            // 处理相册返回图片
            imageUri = data.getData();
            Options op = new Options();
            op.inSampleSize = 1;
            try {
                selectPicBitmap = BitmapFactory.decodeStream(this
                        .getContentResolver().openInputStream(imageUri), null, op);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            selectedPicPath = getRealPathFromURI(imageUri);
            if (selectPicBitmap != null) {
                selectPicSize.set(selectPicBitmap.getWidth(), selectPicBitmap.getHeight());
                srcPicIv.setImageBitmap(selectPicBitmap);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.get_pic_from_camera) {
            getPicFromCamera();
        } else if (id == R.id.get_pic_from_album) {
            getPicFromAlbum();
        }

        return super.onOptionsItemSelected(item);
    }

    private void getPicFromCamera() {
        File f = new File(Environment.getExternalStorageDirectory(),
                "temp.jpg");
        imageUri = Uri.fromFile(f);
        selectedPicPath = f.getAbsolutePath();

        Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        camera.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        startActivityForResult(camera, REQUEST_GET_PIC_FROM_CAMERA);
    }

    private void getPicFromAlbum() {
        Intent album = new Intent();
        album.setType("image/*");
        album.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(album, REQUEST_GET_PIC_FROM_ALBUM);
    }

    public String getRealPathFromURI(Uri contentUri) {
        try {
            String[] proj = {MediaStore.Images.Media.DATA};
            Cursor cursor = getContentResolver().query(contentUri, proj, null,
                    null, null);
            int column_index = cursor
                    .getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        } catch (Exception e) {
            return contentUri.getPath();
        }
    }

    private void setDetectBtn(Boolean isDetecting) {
        if (isDetecting) {
            detectBtn.setEnabled(false);
            detectBtn.setText(R.string.brand_detecting);
        } else {
            detectBtn.setEnabled(true);
            detectBtn.setText(R.string.brand_detect);
        }
    }

    /**
     * 绘制工具箱
     */
    public static class PaintKit {
        Activity activity;
        Paint paintIdentificationGraph;

        int baseStrokeWidth;

        public PaintKit(Activity activity) {
            this.activity = activity;

            baseStrokeWidth = activity.getResources().getDimensionPixelSize(R.dimen.identification_graph_width);
        }

        private Paint getIdentificationGraphPaint(Point imageViewSize, Point selectPicSize) {
            if (paintIdentificationGraph == null) {
                paintIdentificationGraph = new Paint();
                paintIdentificationGraph.setAntiAlias(true);
                paintIdentificationGraph.setStyle(Paint.Style.STROKE);
                paintIdentificationGraph.setColor(activity.getResources().getColor(R.color.identification_graph_color));
            }

            paintIdentificationGraph.setStrokeWidth(getStrokeWidth(imageViewSize, selectPicSize));
            return paintIdentificationGraph;
        }

        /**
         * 获取标识图形边框宽度
         *
         * @param imageViewSize
         * @param selectPicSize
         * @return
         */
        int getStrokeWidth(Point imageViewSize, Point selectPicSize) {
            // 根据图片缩放比例，获取合适的标识图形边框宽度
            float widthRadio = (float) selectPicSize.x / imageViewSize.x;
            float heightRadio = (float) selectPicSize.y / imageViewSize.y;
            float realRadio = widthRadio >= heightRadio ? widthRadio : heightRadio;

            return (int) (baseStrokeWidth * realRadio);
        }
    }
}
