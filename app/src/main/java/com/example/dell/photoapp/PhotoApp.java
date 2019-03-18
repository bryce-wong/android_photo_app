package com.example.dell.photoapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ExifInterface;
//import android.support.media.ExifInterface;
import android.media.Image;
import android.media.ImageReader;
//import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import java.io.File;
//import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PhotoApp extends AppCompatActivity {
    private static final String TAG = "OnionSkinApp";
    private Button openGalleryButton;
    private TextureView textureView;
    //private CustomView customView;
    private boolean manualFocusEngaged = false;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest captureRequest;
    private CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;
    private File file;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private boolean mFlashSupported;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    //for zoom gesture
    public float finger_spacing = 0;
    //public int zoom_level = 1;
    //for single gesture
    private GestureDetector gdetect;
    private float alpha = 0.45f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_app);
        textureView = findViewById(R.id.texture);
        //customView = findViewById(R.id.custom);
        assert textureView != null;
        //assert customView != null;
        textureView.setSurfaceTextureListener(textureListener);
        Button takePictureButton = findViewById(R.id.btn_takepicture);
        openGalleryButton = findViewById(R.id.btn_opengallery);
        assert takePictureButton != null;
        assert openGalleryButton != null;
        takePictureButton.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.KITKAT)
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });
        openGalleryButton.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.KITKAT)
            @Override
            public void onClick(View v) {
                openGallery();
            }
        });
        gdetect = new GestureDetector(this, new GestureListener());
        textureView.setOnTouchListener(touchListener);
        //customView.setOnTouchListener(touchCustomListener);
    }

    private final static int PICK_PHOTO_CODE = 1046;

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

        // If you call startActivityForResult() using an intent that no app can handle, your app will crash.
        // So as long as the result is not null, it's safe to use the intent.
        if (intent.resolveActivity(getPackageManager()) != null) {
            // Bring up gallery to select a photo
            startActivityForResult(intent, PICK_PHOTO_CODE);
        }
    }

    private static Bitmap rotateBitmap(Bitmap bitmap, int degrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

//ontouch???

    //Determine the space between the first two fingers
/*    private float getFingerSpacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }*/

    private boolean isMeteringAreaAFSupported() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        CameraCharacteristics characteristics = null;
        try {
            characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) >= 1;
    }

    // This touch listener passes everything on to the gesture detector.
    // That saves us the trouble of interpreting the raw touch events
    // ourselves.
    private final View.OnTouchListener touchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            // pass the events to the gesture detector
            // a return value of true means the detector is handling it
            // a return value of false means the detector didn't
            // recognize the event
            //View#performClick;
            return gdetect.onTouchEvent(event);

        }
    };

    //same, but for custom class
   /* View.OnTouchListener touchCustomListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            // pass the events to the gesture detector
            // a return value of true means the detector is handling it
            // a return value of false means the detector didn't
            // recognize the event
            return customView.onTouchEvent(event);

        }
    };

    textureView.setOnTouchListener(new View.OnTouchListener() {
        @Override
    public boolean onTouch(View view, MotionEvent motionEvent) throws CameraAccessException {
        //return gdetect.onTouchEvent(event);

    }}*/

    //draw rectangle?
    /*private class CustomView extends SurfaceView {

        private final Paint paint;
        private final SurfaceHolder mHolder;
        private final Context context;

        public CustomView(PhotoApp context) {
            super(context.getBaseContext());
            mHolder = getHolder();
            mHolder.setFormat(PixelFormat.TRANSPARENT);
            this.context = context.getBaseContext();
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                invalidate();
                if (mHolder.getSurface().isValid()) {
                    final Canvas canvas = mHolder.lockCanvas();
                    Log.d("touch", "touchRecieved by camera");
                    if (canvas != null) {
                        Log.d("touch", "touchRecieved CANVAS STILL Not Null");
                        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                        canvas.drawColor(Color.TRANSPARENT);
                        canvas.drawCircle(event.getX(), event.getY(), 100, paint);
                        mHolder.unlockCanvasAndPost(canvas);
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                Canvas canvas1 = mHolder.lockCanvas();
                                if(canvas1 !=null){
                                    canvas1.drawColor(0, PorterDuff.Mode.CLEAR);
                                    mHolder.unlockCanvasAndPost(canvas1);
                                }

                            }
                        }, 1000);

                    }
                    mHolder.unlockCanvasAndPost(canvas);


                }
            }


            return false;
        }
    }*/

    //end of ontouch

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_PHOTO_CODE && resultCode == RESULT_OK && data != null) {
            // Get selected gallery image
            Uri contentURI = data.getData();

            // Get and resize profile image
            String[] filePathColumn = {MediaStore.Images.Media.DATA};
            Cursor cursor = this.getContentResolver().query(contentURI, filePathColumn, null, null, null);
            cursor.moveToFirst();

            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            String picturePath = cursor.getString(columnIndex);
            cursor.close();

            Bitmap bitmap = BitmapFactory.decodeFile(picturePath);

            ExifInterface exif = null;
            try {
                File pictureFile = new File(picturePath);
                exif = new ExifInterface(pictureFile.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
            }

            int orientation = ExifInterface.ORIENTATION_NORMAL;

            if (exif != null)
                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    bitmap = rotateBitmap(bitmap, 90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    bitmap = rotateBitmap(bitmap, 180);
                    break;

                case ExifInterface.ORIENTATION_ROTATE_270:
                    bitmap = rotateBitmap(bitmap, 270);
                    break;
            }
            // Do something with the photo based on Uri
            /*Bitmap selectedImage = null;
            try {
                selectedImage = MediaStore.Images.Media.getBitmap(this.getContentResolver(), contentURI);
            } catch (IOException e) {
                e.printStackTrace();
            }

                        try {
                            Activity activity = getActivity();
                            CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
                            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
                            float maxalpha = 1.00f;

                            Rect m = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                            int action = event.getAction();
                            float current_finger_spacing;

                            if (event.getPointerCount() > 1) {
                                // Multi touch logic
                                current_finger_spacing = getFingerSpacing(event);
                                if(finger_spacing != 0){
                                    if(current_finger_spacing > finger_spacing && maxalpha > alpha){
                                        alpha += 0.10f;
                                    } else if (current_finger_spacing < finger_spacing && alpha > 0){
                                        alpha -= 0.10f;
                                    }
                                    int minW = (int) (m.width() / maxzoom);
                                    int minH = (int) (m.height() / maxzoom);
                                    int difW = m.width() - minW;
                                    int difH = m.height() - minH;
                                    change this to be for alpha levels
                                    int cropW = difW /100 *(int)zoom_level;
                                    int cropH = difH /100 *(int)zoom_level;
                                    cropW -= cropW & 3;
                                    cropH -= cropH & 3;
                                    change this to be for adjusting alpha levels in the xml
                                    Rect zoom = new Rect(cropW, cropH, m.width() - cropW, m.height() - cropH);
                                    captureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);
                                    ivPreview.setAlpha(alpha);
                                }
                                finger_spacing = current_finger_spacing;
                            } else{
                                if (action == MotionEvent.ACTION_UP) {
                                    //single touch logic
                                }
                            }
                            try {
                                cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), captureCallbackListener, null);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        } catch (CameraAccessException e) {
                            throw new RuntimeException("can not access camera.", e);
                        }
                        return true;
                    }
                });
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }*/
            // Load the selected image into a preview
            final ImageView ivPreview = findViewById(R.id.image_view);
            ivPreview.setImageBitmap(bitmap);
            ivPreview.setAlpha(alpha);
        }
    }

    private final TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //open your camera here
            openCamera();
        }
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Transform you image captured size according to the surface width and height
        }
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            //This is called when the camera is open
            //Log.e(TAG, "onOpened");
            cameraDevice = camera;
            createCameraPreview();
        }
        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            //Log.e(TAG, "onDisconnected");
            //cameraDevice.close();
            closeCamera();
        }
        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            //Log.e(TAG, "Camera ErrorCode " + error);
            //cameraDevice.close();
            //cameraDevice = null;
            closeCamera();
        }
    };

    final CameraCaptureSession.CaptureCallback captureCallbackListener = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            Toast.makeText(PhotoApp.this, "Saved:" + file, Toast.LENGTH_SHORT).show();
            createCameraPreview();
        }
    };

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    private void takePicture() {
        if(null == cameraDevice) {
            //Log.e(TAG, "cameraDevice is null");
            return;
        }
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes = null;
            if (characteristics != null) {
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            }
            int width = 640;
            int height = 480;
            if (jpegSizes != null && 0 < jpegSizes.length) {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }

            ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = new ArrayList<>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            // Orientation
            final int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            //final File file = new File(Environment.getExternalStorageDirectory()+"/pic.jpg");
            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    // First I get the path to gallery and crate new Album to my app
                    String pathD = Environment.getExternalStorageDirectory() + "/" + Environment.DIRECTORY_PICTURES + "/";
                    File mediaStorageDir = new File(pathD, "OnionSkinAlbum");
                    if (!mediaStorageDir.exists()) {
                        if (!mediaStorageDir.mkdirs()) {
                            //Log.d("PhotoApp", "failed to create directory");
                        }
                    }
                    /*Second I cut mFile = new File(getActivity().getExternalFilesDir(null), "pic.jpg");
                    from onActivityCreated and add here with the new path from my Album */
                    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                    file = new File(mediaStorageDir,"ImageName"+"_"+ timeStamp+".jpeg");

                    //Then the contentValues
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.TITLE, "ImageName");
                    values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
                    values.put(MediaStore.Images.Media.ORIENTATION, ORIENTATIONS.get(rotation));
                    values.put(MediaStore.Images.Media.CONTENT_TYPE,"image/jpeg");
                    values.put("_data", file.getAbsolutePath());
                    ContentResolver cr = getContentResolver();
                    cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    //This line is already in the code
                    //mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), file))

                    try (Image image = reader.acquireLatestImage()) {
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        save(bytes);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                private void save(byte[] bytes) throws IOException {
                    try (OutputStream output = new FileOutputStream(file)) {
                        output.write(bytes);
                    }
                }
            };

            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Toast.makeText(PhotoApp.this, "Saved:" + file, Toast.LENGTH_SHORT).show();
                    //media scanning
                    if(file != null) {
                        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://" + file.getAbsolutePath())));
                    }
                    /*MediaScannerConnection.scanFile(PhotoApp.this,
                            new String[]{file.getAbsolutePath()}, null,
                            new MediaScannerConnection.OnScanCompletedListener() {
                                @Override
                                public void onScanCompleted(String path, Uri uri) {
                                    Log.i("ExternalStorage", "Scanned " + path + ":");
                                    Log.i("ExternalStorage", "-> uri=" + uri);
                                }
                            });*/
                    createCameraPreview();
                }
            };
            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), captureListener, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback(){
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    //The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(PhotoApp.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        //Log.e(TAG, "is camera open");
        try {
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            // Add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(PhotoApp.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }
            manager.openCamera(cameraId, stateCallback, null);
            /*manager.openCamera(cameraId, new CameraDevice.StateCallback(){
                @Override
                public void onOpened(CameraDevice camera) {
                    //This is called when the camera is open
                    Log.e(TAG, "onOpened");
                    cameraDevice = camera;
                    createCameraPreview();
                }
                @Override
                public void onDisconnected(CameraDevice camera) {
                    cameraDevice.close();
                }
                @Override
                public void onError(CameraDevice camera, int error) {
                    cameraDevice.close();
                    cameraDevice = null;
                }
            }, null);*/
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        //Log.e(TAG, "openCamera X");
    }

    private void updatePreview() {
        if(null == cameraDevice) {
            //Log.e(TAG, "updatePreview error, return");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(PhotoApp.this, "Sorry!!!, you can't use this app without granting permission", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        //Log.e(TAG, "onResume");
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }
    @Override
    protected void onPause() {
        //Log.e(TAG, "onPause");
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }



    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        //final float maxalpha = 1.00f;

        @Override
        public boolean onDown(MotionEvent event) {
            //Log.d("TAG","onDown: ");

            // don't return false here or else none of the other
            // gestures will work
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent event) {
//            Log.i("TAG", "onSingleTapConfirmed: ");
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = null;
            try {
                characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            final int actionMasked = event.getActionMasked();
            if (actionMasked != MotionEvent.ACTION_DOWN) {
                return false;
            }
            if (manualFocusEngaged) {
//                Log.d(TAG, "Manual focus already engaged");
                return true;
            }final Rect sensorArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);

            //TODO: here I just flip x,y, but this needs to correspond with the sensor orientation (via SENSOR_ORIENTATION)
            final int y = (int)((event.getX() / (float)textureView.getWidth())  * (float)sensorArraySize.height());
            final int x = (int)((event.getY() / (float)textureView.getHeight()) * (float)sensorArraySize.width());
            final int halfTouchWidth  = 150; //(int)motionEvent.getTouchMajor(); /// /TODO: this doesn't represent actual touch size in pixel. Values range in [3, 10]...
            final int halfTouchHeight = 150; //(int)motionEvent.getTouchMinor();
            MeteringRectangle focusAreaTouch = new MeteringRectangle(Math.max(x - halfTouchWidth,  0),
                    Math.max(y - halfTouchHeight, 0),
                    halfTouchWidth  * 2,
                    halfTouchHeight * 2,
                    MeteringRectangle.METERING_WEIGHT_MAX - 1);

            CameraCaptureSession.CaptureCallback captureCallbackHandler = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    manualFocusEngaged = false;
                    if (request.getTag() == "FOCUS_TAG") {
                        //the focus trigger is complete -
                        //resume repeating (preview surface will get frames), clear AF trigger
                        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, null);
                        try {
                            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, null);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                    }
                }

                @Override
                public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
                    super.onCaptureFailed(session, request, failure);
                    Log.e(TAG, "Manual AF failure: " + failure);
                    manualFocusEngaged = false;
                }
            };

            //first stop the existing repeating request
            try {
                cameraCaptureSessions.stopRepeating();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

            //cancel any existing AF trigger (repeated touches, etc.)
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            try {
                cameraCaptureSessions.capture(captureRequestBuilder.build(), captureCallbackHandler, mBackgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

            //Now add a new AF trigger with focus region
            if (isMeteringAreaAFSupported()) {
                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{focusAreaTouch});
            }
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            captureRequestBuilder.setTag("FOCUS_TAG"); //we'll capture this later for resuming the preview

            //then we ask for a single request (not repeating!)
            try {
                cameraCaptureSessions.capture(captureRequestBuilder.build(), captureCallbackHandler, mBackgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            manualFocusEngaged = true;
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
//            Log.i("TAG", "onLongPress: ");
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            //Log.i("TAG", "onDoubleTap: ");
            if(alpha > 0){
                alpha -= 0.10f;
            }
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                float distanceX, float distanceY) {
            //Log.i("TAG", "onScroll: ");
            return true;
        }

        @Override
        public boolean onFling(MotionEvent event1, MotionEvent event2,
                               float velocityX, float velocityY) {
            //Log.d("TAG", "onFling: ");
            return true;
        }
    }
}

