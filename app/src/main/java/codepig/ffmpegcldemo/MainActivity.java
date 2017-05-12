package codepig.ffmpegcldemo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import codepig.ffmpegcldemo.config.deviceInfo;
import codepig.ffmpegcldemo.ffmpegCentre.ffmpegCommandCentre;
import codepig.ffmpegcldemo.listener.fileListener;
import codepig.ffmpegcldemo.net.imageLoader;
import codepig.ffmpegcldemo.utils.FileUtil;
import codepig.ffmpegcldemo.utils.ThreadPoolUtils;
import codepig.ffmpegcldemo.utils.bitmapFactory;
import codepig.ffmpegcldemo.utils.mathFactory;
import codepig.ffmpegcldemo.values.videoInfo;

public class MainActivity extends AppCompatActivity {
    private Context context;
    private EditText title_t,author_t,description_t;
    private TextView skip_t,currentTime_t,totalTime_t;
    private SeekBar seekBar;
    private Button cameraBtn,stopCameraBtn,imgBtn,movBtn,musicBtn,makeBtn,switchCameraBtn,enter_Btn,titleBtn;
    private LinearLayout titlePlan,controlPlan,bufferIcon,timePlan;
    private FrameLayout recodePlan;
    private ImageView imgPreview;
    private SurfaceView surfaceView;
    private MediaPlayer mPlayer,aPlayer;
    private Bitmap imageBitmap;
    private Handler mHandler;
    private SurfaceHolder sfHolder;
    private Uri imageUri=null;
    private Uri audioUri=null;
    private Uri videoUri=null;
    private String videoUrl="";//源视频文件(本例中是产生的录像文件)
    private String imageUrl="";//水印图文件
    private String musicUrl="";//音乐文件
    private String outputUrl="";//输出视频文件
    private String textMarkUrl="";//文字水印图
    private int file_type=0;
    private String recordFilename="testVideo";
    private String outputFilename="outputVideo";
    private String textMarkFilename="textMark";
    private Camera camera;
    private File videoFile;// 录制的视频文件
    private MediaRecorder mRecorder;
    private boolean hasCamera=false;
    private int camIdx=Camera.CameraInfo.CAMERA_FACING_FRONT;
    private fileListener writeFileListener;
    private int currentTime=0;//当前已录制时间
    private int totalTime=0;//音乐文件总时间
    private Timer presTimer=new Timer();
    private TimerTask presTask;

    private final int IMAGE_FILE=1;
    private final int MUSIC_FILE=2;
    private final int VIDEO_FILE=3;
    private final int TIMECOUNT=4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context=this;
        setContentView(R.layout.activity_main);
        findView();
        initSurfaceView();
    }

    private void findView(){
        cameraBtn=(Button) findViewById(R.id.cameraBtn);
        stopCameraBtn=(Button) findViewById(R.id.stopCameraBtn);
        switchCameraBtn=(Button) findViewById(R.id.switchCameraBtn);
        titleBtn=(Button) findViewById(R.id.titleBtn);
        titlePlan=(LinearLayout) findViewById(R.id.titlePlan);
        controlPlan=(LinearLayout) findViewById(R.id.controlPlan);
        timePlan=(LinearLayout) findViewById(R.id.timePlan);
        imgBtn=(Button) findViewById(R.id.imgBtn);
        movBtn=(Button) findViewById(R.id.movBtn);
        musicBtn=(Button) findViewById(R.id.musicBtn);
        makeBtn=(Button) findViewById(R.id.makeBtn);
        imgPreview=(ImageView) findViewById(R.id.imgPreview);
        bufferIcon=(LinearLayout) findViewById(R.id.bufferIcon);
        recodePlan=(FrameLayout) findViewById(R.id.recodePlan);
        title_t=(EditText) findViewById(R.id.title_t);
        skip_t=(TextView) findViewById(R.id.skip_t);
        seekBar=(SeekBar) findViewById(R.id.seekBar);
        currentTime_t=(TextView) findViewById(R.id.currentTime_t);
        totalTime_t=(TextView) findViewById(R.id.totalTime_t);
        author_t=(EditText) findViewById(R.id.author_t);
        description_t=(EditText) findViewById(R.id.description_t);
        enter_Btn=(Button) findViewById(R.id.enter_Btn);

        bufferIcon.setVisibility(View.GONE);
        makeBtn.setVisibility(View.GONE);
        controlPlan.setVisibility(View.GONE);
        recodePlan.setVisibility(View.GONE);
        stopCameraBtn.setVisibility(View.GONE);
        totalTime_t.setVisibility(View.GONE);
        seekBar.setVisibility(View.GONE);
        timePlan.setVisibility(View.GONE);

        imgBtn.setOnClickListener(clickBtn);
        movBtn.setOnClickListener(clickBtn);
        musicBtn.setOnClickListener(clickBtn);
        makeBtn.setOnClickListener(clickBtn);
        switchCameraBtn.setOnClickListener(clickBtn);
        cameraBtn.setOnClickListener(clickBtn);
        stopCameraBtn.setOnClickListener(clickBtn);
        enter_Btn.setOnClickListener(clickBtn);
        titleBtn.setOnClickListener(clickBtn);
        skip_t.setOnClickListener(clickBtn);

        //初始化播放器
        mPlayer=new MediaPlayer();
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {//UI处理
                switch (msg.what){
                    case 0:
                        imgPreview.setImageBitmap(imageBitmap);
                        break;
                    case 1:
                        bufferIcon.setVisibility(View.VISIBLE);
                        break;
                    case 2:
                        bufferIcon.setVisibility(View.GONE);
                        makeBtn.setVisibility(View.GONE);
                        recodePlan.setVisibility(View.VISIBLE);
                        break;
                    case TIMECOUNT:
                        currentTime+=1;
                        currentTime_t.setText(mathFactory.ms2HMS(currentTime*1000));
                        if(aPlayer!=null && aPlayer.isPlaying()){
                            long _pec = currentTime * 100000 / totalTime;
                            seekBar.setProgress((int) _pec);
                        }
                        break;
                    default:
                        break;
                }
            }
        };

        //输出文件地址
        outputUrl= FileUtil.getPath() + "/"+outputFilename+".mp4";
        //检测是否存在摄像头
        hasCamera=checkCameraHardware(context);

        //隐藏系统导航栏(android4.1及以上)
//        View decorView = getWindow().getDecorView();
//        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
//        decorView.setSystemUiVisibility(uiOptions);
    }

    /**
     * 初始化surfaceView
     */
    private void initSurfaceView(){
        surfaceView = (SurfaceView) this.findViewById(R.id.surfaceView);
        sfHolder=surfaceView.getHolder();
        // 设置分辨率
        sfHolder.setFixedSize(deviceInfo.screenWtdth, deviceInfo.screenHeight);
        sfHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                Log.d("LOGCAT", "surfaceDestroyed");
                surfaceView=null;
                sfHolder=null;
                if(mRecorder!=null) {
                    mRecorder.stop();
                    mRecorder.release();
                    mRecorder = null;
                }
            }

            //必须监听surfaceView的创建，创建完毕后才可以处理播放
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Log.d("LOGCAT", "surfaceCreated");
                if(hasCamera) {
                    openCamera();//接收到Surface的回调后启用摄像头。
                }else{
                    Toast.makeText(context, "没有摄像头，退散吧！", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
//                Log.d("LOGCAT", "surfaceChanged");
            }
        });
    }

    /**
     * 检测摄像头
     * @param context
     * @return
     */
    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * 初始化摄像头
     */
    private void openCamera() {
        if(camera!=null){
            camera.stopPreview();
            camera.release();
            camera=null;
        }
        try {
            camera = Camera.open(camIdx);
            Parameters cP=camera.getParameters();
            cP.setPreviewSize(deviceInfo.screenWtdth,deviceInfo.screenHeight);
            camera.setParameters(cP);
            camera.setPreviewDisplay(sfHolder);//通过SurfaceView显示取景画面
            camera.startPreview(); //开始预览
        } catch (IOException e) {
            Log.d("LOGCAT", "IOException:"+e.toString());
        }
    }

    /**
     * 按钮监听
     */
    private View.OnClickListener clickBtn = new Button.OnClickListener(){
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                //播放器区域按钮
                case R.id.switchCameraBtn:
                    if(camIdx==Camera.CameraInfo.CAMERA_FACING_FRONT){
                        camIdx=Camera.CameraInfo.CAMERA_FACING_BACK;
                    }else{
                        camIdx=Camera.CameraInfo.CAMERA_FACING_FRONT;
                    }
                    openCamera();
                    break;
                case R.id.cameraBtn:
                    startRecode();
                    break;
                case R.id.stopCameraBtn:
                    stopRecode();
                    break;
                case R.id.imgBtn:
                    file_type=IMAGE_FILE;
                    chooseFile();
                    break;
                case R.id.musicBtn:
                    file_type=MUSIC_FILE;
                    chooseFile();
                    break;
                case R.id.movBtn:
                    file_type=VIDEO_FILE;
                    chooseFile();
                    break;
                case R.id.makeBtn:
                    //开始合并
                    makeVideo();
                    recodePlan.setVisibility(View.GONE);
                    break;
                case R.id.enter_Btn:
                    videoInfo.vTitle=title_t.getText().toString();
                    videoInfo.author=author_t.getText().toString();
                    videoInfo.description=description_t.getText().toString();
                    titlePlan.setVisibility(View.GONE);
                    controlPlan.setVisibility(View.VISIBLE);
                    recodePlan.setVisibility(View.VISIBLE);
                    //先生成文字水印图片
                    final String _title= videoInfo.vTitle;
                    if(_title!=null &&! _title.equals("")) {
                        textMarkUrl=FileUtil.getPath() + "/" + textMarkFilename + ".png";
                        Runnable bmpR=new Runnable() {
                            @Override
                            public void run() {
                                bitmapFactory.writeImage(textMarkUrl, _title);
                            }
                        };
                        ThreadPoolUtils.execute(bmpR);
                    }
                    break;
                case R.id.skip_t:
                    titlePlan.setVisibility(View.GONE);
                    controlPlan.setVisibility(View.VISIBLE);
                    recodePlan.setVisibility(View.VISIBLE);
                    break;
                case R.id.titleBtn:
                    titlePlan.setVisibility(View.VISIBLE);
                    controlPlan.setVisibility(View.GONE);
                    recodePlan.setVisibility(View.GONE);
                    break;
                default:
                    break;
            }
        }
    };

    /**
     * 开始录制
     */
    private void startRecode(){
        cameraBtn.setVisibility(View.GONE);
        stopCameraBtn.setVisibility(View.VISIBLE);
        controlPlan.setVisibility(View.GONE);
        //录制
//        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);//保持屏幕常亮
        if(audioUri!=null) {
            playMusic(musicUrl);
        }
        timePlan.setVisibility(View.VISIBLE);
        try {
            Log.i("LOGCAT", "Start recording...");
            currentTime=0;
            switchCameraBtn.setVisibility(View.GONE);
            // 创建保存录制视频的视频文件
            videoFile = new File(FileUtil.getPath() + "/"+recordFilename+".mp4");
            videoUrl=FileUtil.getPath() + "/"+recordFilename+".mp4";
            camera.unlock();
            // 设置该组件让屏幕不会自动关闭
            sfHolder.setKeepScreenOn(true);
            mRecorder = new MediaRecorder();
            mRecorder.reset();
            mRecorder.setCamera(camera);
            // 设置从麦克风采集声音(麦克风声音MIC,录像机的声音AudioSource.CAMCORDER,系统声音REMOTE_SUBMIX)
//            mRecorder.setAudioSource(MediaRecorder.AudioSource.REMOTE_SUBMIX);
            // 设置从摄像头采集图像
            mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            //录制角度
//            mRecorder.setOrientationHint(90);
            // 设置视频文件的输出格式
            // 必须在设置声音编码格式、图像编码格式之前设置
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            // 设置声音编码的格式
//            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            // 设置图像编码的格式
            mRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mRecorder.setVideoEncodingBitRate(5*deviceInfo.screenWtdth*deviceInfo.screenHeight);
            mRecorder.setVideoSize(deviceInfo.screenWtdth, deviceInfo.screenHeight);
            // 每秒24帧
            mRecorder.setVideoFrameRate(24);
            mRecorder.setOutputFile(videoFile.getAbsolutePath());
            // 指定使用SurfaceView来预览视频
            mRecorder.setPreviewDisplay(sfHolder.getSurface());
            //这个监听可以用来控制录制时长
//            mRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
//                @Override
//                public void onInfo(MediaRecorder mediaRecorder, int i, int i1) {
//                    Log.d("LOGCAT", "RecordService got MediaRecorder onInfo callback with what: " + i + " extra: " + i1);
//                }
//            });
            mRecorder.prepare();
            // 开始录制
            mRecorder.start();
            //监听文件的写入
            writeFileListener = new fileListener(videoFile.getPath(),this);
            writeFileListener.startWatching();
            startPresTimer();
        }catch (IOException e){
        }
    }

    /**
     * 停止录制
     */
    private void stopRecode(){
        stopCameraBtn.setVisibility(View.GONE);
        cameraBtn.setVisibility(View.VISIBLE);
        controlPlan.setVisibility(View.VISIBLE);
        makeBtn.setVisibility(View.VISIBLE);
        stopPresTimer();
        seekBar.setProgress(0);
        timePlan.setVisibility(View.GONE);
        switchCameraBtn.setVisibility(View.VISIBLE);
        stopPlayer();
        // 设置该组件让屏幕不会自动关闭
        surfaceView.getHolder().setKeepScreenOn(false);
        Log.i("LOGCAT", "录制完毕， 存储为 " + videoFile.getPath());
        // 停止录制
        mRecorder.stop();
        // 释放资源
        mRecorder.release();
        mRecorder = null;
    }

    /**
     * 打开文件
     */
    private void chooseFile(){
        Intent intent = new Intent();
        //使用ACTION_PICK时google原生5.1系统音频选择会报错，使用ACTION_GET_CONTENT时小米系统获得的是空指针
        intent.setAction(Intent.ACTION_GET_CONTENT);
        Log.d("LOGCAT", "file type:" + file_type);
        switch (file_type) {
            case IMAGE_FILE:
                intent.setData(MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                intent.setType("image/*");
                break;
            case MUSIC_FILE:
                intent.setData(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI);
                intent.setType("audio/*");
                break;
            case VIDEO_FILE:
                intent.setData(MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
                intent.setType("video/*");
                break;
        }
        startActivityForResult(intent, 0x1);
    }

    /**
     * 监听文件选择
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 0x1 && resultCode == Activity.RESULT_OK && data!=null) {
            switch (file_type){
                case IMAGE_FILE://不同机型系统，得到的fileUri.getPath()值不同，所以以不同的方式获取地址
                    try{
                        imageUri = data.getData();
                        Log.d("LOGCAT", "uri path:"+imageUri.getPath()+"   "+imageUri.toString());
                        String[] pojo = {MediaStore.Images.Media.DATA};
                        Cursor cursor = getContentResolver().query(imageUri, pojo, null, null, null);
                        if (cursor != null) {
                            /*这部分代码在ACTION_GET_CONTENT模式下为空，在ACTION_PICK模式下可以得到具体地址
                            int colunm_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                            cursor.moveToFirst();
                            imageUrl = cursor.getString(colunm_index);
                            cursor.close();
                            //以上代码获取图片路径
                            Log.d("LOGCAT","path:"+imageUrl);
                            */
                            imgPreview.setImageURI(imageUri);
                        }else{
                            imageUrl=imageUri.getPath();
                            Log.d("LOGCAT","path:"+imageUrl);
                            Runnable bmpR=new Runnable() {
                                @Override
                                public void run() {
                                    imageBitmap = imageLoader.returnBitMapLocal(imageUrl, 300, 200);
                                    if (imageBitmap != null){
                                        Message msg = new Message();
                                        msg.what = 0;
                                        mHandler.sendMessage(msg);
                                    }
                                }
                            };
                            ThreadPoolUtils.execute(bmpR);
                        }
                    }catch (Exception e){
                        Log.d("LOGCAT",e.toString());
                    }
                    break;
                case MUSIC_FILE:
                    try{
                        audioUri = data.getData();
                        Log.d("LOGCAT", "uri path:"+audioUri.getPath()+"   "+audioUri.toString());
                        String[] pojo = {MediaStore.Audio.Media.DATA};
                        Cursor cursor = getContentResolver().query(audioUri, pojo, null, null, null);
                        if (cursor != null) {
                        }else{
                            musicUrl=audioUri.getPath();
                            seekBar.setVisibility(View.VISIBLE);
                            totalTime_t.setVisibility(View.VISIBLE);
                            Log.d("LOGCAT","path:"+musicUrl);
                        }
                    }catch (Exception e){
                        Log.d("LOGCAT",e.toString());
                    }
                    break;
                case VIDEO_FILE:
                    try{
                        videoUri = data.getData();
                        Log.d("LOGCAT", "uri path:"+videoUri.getPath()+"   "+videoUri.toString());
                        String[] pojo = {MediaStore.Video.Media.DATA};
                        Cursor cursor = getContentResolver().query(videoUri, pojo, null, null, null);
                        if (cursor != null) {
                        }else{
                            videoUrl=videoUri.getPath();
                            Log.d("LOGCAT","path:"+videoUrl);
                            playVideo(videoUrl);
                        }
                    }catch (Exception e){
                        Log.d("LOGCAT",e.toString());
                    }
                    break;
                default:
                    break;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * 播放视频
     * @param _url
     */
    public void playVideo(String _url){
        if(_url!=""){
            try {
                Log.d("LOGCAT", "play:" + _url);
                if(mPlayer==null){
                    Log.d("LOGCAT", "new player");
                    mPlayer=new MediaPlayer();
                }else{
                    Log.d("LOGCAT", "reset player");
                    mPlayer.reset();
                }
                mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                //设置需要播放的视频
                mPlayer.setDataSource(_url);
                mPlayer.prepareAsync();
                mPlayer.setOnBufferingUpdateListener(bufferingListener);
                mPlayer.setOnPreparedListener(preparedListener);
            } catch (Exception e) {
                // TODO: handle exception
            }
        }
    }

    /**
     * 播放音乐
     */
    private void playMusic(String _url){
        if(aPlayer==null){
            try {
                aPlayer = new MediaPlayer();
                aPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                aPlayer.setDataSource(_url);
                aPlayer.prepareAsync();
                Log.d("LOGCAT","set audioPlayer");
                aPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mp) {
                        // 装载完毕 开始播放流媒体
                        totalTime=aPlayer.getDuration();
                        totalTime_t.setText(mathFactory.ms2HMS(totalTime));
                        Log.d("LOGCAT","audioPlayer totalTime:"+totalTime);
                        aPlayer.start();
                    }
                });
                //音乐播放完毕结束录制
                aPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mediaPlayer) {
                        stopRecode();
                    }
                });
            }catch (Exception e){
                Log.d("LOGCAT","err:"+e.toString());
            }
        }else{
            Log.d("LOGCAT","has audioPlayer");
            if(!aPlayer.isPlaying()) {
                Log.d("LOGCAT","reStart");
                aPlayer.start();
            }
        }
    }

    /**
     * 停止播放
     */
    private void stopPlayer(){
        if(mPlayer!=null && mPlayer.isPlaying()){
            mPlayer.stop();
        }
        if(aPlayer!=null && aPlayer.isPlaying()){
            aPlayer.stop();
        }
    }

    /**
     * 监听缓冲进度更新
     */
    MediaPlayer.OnBufferingUpdateListener bufferingListener=new MediaPlayer.OnBufferingUpdateListener() {
        @Override
        public void onBufferingUpdate(MediaPlayer mp, int percent) {
        }
    };

    /**
     * prepare监听
     */
    MediaPlayer.OnPreparedListener preparedListener=new MediaPlayer.OnPreparedListener(){
        @Override
        public void onPrepared(MediaPlayer mp)
        {
            bufferIcon.setVisibility(View.GONE);
            //播放
            mPlayer.start();
        }
    };

    /**
     * ffmpeg操作
     */
    private void makeVideo(){
        if(imageUri==null && audioUri==null && textMarkUrl.equals("")) {
            Toast.makeText(this, "少年，不加点什么吗？", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] commands= ffmpegCommandCentre.makeVideo(textMarkUrl,imageUrl,musicUrl,videoUrl,outputUrl,currentTime);
        final String[] _commands=commands;
        Runnable compoundRun=new Runnable() {
            @Override
            public void run() {
                FFmpegKit.execute(_commands, new FFmpegKit.KitInterface() {
                    @Override
                    public void onStart() {
                        Log.d("FFmpegLog LOGCAT","FFmpeg 命令行开始执行了...");
                        Message msg = new Message();
                        msg.what = 1;
                        mHandler.sendMessage(msg);
                    }

                    @Override
                    public void onProgress(int progress) {
                        Log.d("FFmpegLog LOGCAT","done com"+"FFmpeg 命令行执行进度..."+progress);
                    }

                    @Override
                    public void onEnd(int result) {
                        Log.d("FFmpegLog LOGCAT","FFmpeg 命令行执行完成...");
                        Message msg = new Message();
                        msg.what = 2;
                        mHandler.sendMessage(msg);
                    }
                });
            }
        };
        ThreadPoolUtils.execute(compoundRun);
    }

    /**
     * 确保视频保保存完毕后才可执行合成操作，否则可能引发ffmpeg的空指针错误
     */
    public void fileClosed(){
        makeBtn.setVisibility(View.VISIBLE);
        writeFileListener.stopWatching();
    }

    /**
     * 计时器的开始和关闭
     */
    private void startPresTimer(){
        presTask = new TimerTask() {
            @Override
            public void run() {
                // TODO Auto-generated method stub
                if(mHandler!=null) {
                    Message message = new Message();
                    message.what = TIMECOUNT;
                    mHandler.sendMessage(message);
                }
            }
        };
        if(presTimer==null){
            presTimer=new Timer();
        }
        presTimer.schedule(presTask, 1000, 1000);
    }
    private void stopPresTimer(){
        if(presTimer!=null) {
            presTimer.cancel();
            presTimer=null;
        }
        if(presTask!=null) {
            presTask.cancel();
            presTask=null;
        }
    }

    @Override
    public void onPause() {
        Log.d("LOGCAT", "player onPause");
        camera.release();
        camera=null;
        super.onPause();
    }

    @Override
    public void onResume() {
        Log.d("LOGCAT", "player onResume");
        initSurfaceView();
        openCamera();
        super.onResume();
    }

    @Override
    public void onDestroy() {
        Log.d("LOGCAT", "player onDestroy");
        super.onDestroy();
    }
}
