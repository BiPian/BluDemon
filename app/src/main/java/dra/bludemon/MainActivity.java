package dra.bludemon;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {

    @BindView(R.id.textView1)
    TextView textView1;
    @BindView(R.id.startBlu)
    Button startBlu;
    @BindView(R.id.stopBlu)
    Button stopBlu;
    @BindView(R.id.record)
    Button record;
    @BindView(R.id.stopRecord)
    Button stopRecord;
    @BindView(R.id.startPlay)
    Button startPlay;
    @BindView(R.id.scrollView1)
    ScrollView scrollView1;

    private BluetoothAdapter bA;
    private Set<BluetoothDevice> pairedDevices;
    private ListView lv;
    private static String mFileName = null;
    private MediaRecorder mRecorder = null;
    private AudioManager mAudioManager = null;
    private MediaPlayer mPlayer = null;
    private boolean isFirst;
    private static int versioncode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        //获得文件保存路径。记得添加android.permission.WRITE_EXTERNAL_STORAGE权限
        mFileName = Environment.getExternalStorageDirectory().getAbsolutePath();
        mFileName += "/btrecorder.3gp";

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mPlayer = new MediaPlayer();
        bA = BluetoothAdapter.getDefaultAdapter();

        isFirst = true;


    }

    public void setVisible(View view) {
        Intent getVisible = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        startActivityForResult(getVisible, 0);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * 返回当前程序版本名
     */
    private boolean getVersionName() {
        String s = Build.VERSION.SDK;
        int i = Integer.parseInt(s);
        if (i < 19) {
            Toast.makeText(this, "版本过低，不支持蓝牙", Toast.LENGTH_SHORT).show();
            return false;
        } else {
            return true;
        }

    }

    @OnClick({R.id.startBlu, R.id.stopBlu, R.id.record, R.id.stopRecord, R.id.startPlay})
    public void onViewClicked(View view) {
        if (getVersionName()) {
            switch (view.getId()) {
                //开启蓝牙
                case R.id.startBlu:
                    if (!bA.isEnabled()) {
                        Intent turnOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        startActivityForResult(turnOn, 0);
                        Toast.makeText(getApplicationContext(), "开启蓝牙", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(getApplicationContext(), "已经打开了", Toast.LENGTH_LONG).show();
                    }
                    break;
                // 关闭蓝牙
                case R.id.stopBlu:
                    bA.disable();
                    Toast.makeText(getApplicationContext(), "关闭蓝牙", Toast.LENGTH_LONG);
                    break;
                // 录音
                case R.id.record:
                    if (bA.isEnabled()) {// 判断蓝牙是否开启
                        // 播放录音到A2DP
                        mRecorder = new MediaRecorder();
                        mRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
                        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                        mRecorder.setOutputFile(mFileName);
                        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                        try {
                            mRecorder.prepare();//如果文件打开失败，此步将会出错。
                        } catch (IOException e) {
                            Log.e("main", "prepare() failed");
                        }
                        if (!mAudioManager.isBluetoothScoAvailableOffCall()) {
                            Log.d("main", "系统不支持蓝牙录音");
                            return;
                        }
                        //蓝牙录音的关键，启动SCO连接，耳机话筒才起作用
                        mAudioManager.startBluetoothSco();
                        //蓝牙SCO连接建立需要时间，连接建立后会发出ACTION_SCO_AUDIO_STATE_CHANGED消息，通过接收该消息而进入后续逻辑。
                        //也有可能此时SCO已经建立，则不会收到上述消息，可以startBluetoothSco()前先stopBluetoothSco()
                        registerReceiver(new BroadcastReceiver() {
                            @Override
                            public void onReceive(Context context, Intent intent) {
                                int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1);
                                if (AudioManager.SCO_AUDIO_STATE_CONNECTED == state) {
                                    mAudioManager.setBluetoothScoOn(true);  //打开SCO
                                    mRecorder.start();//开始录音
                                    unregisterReceiver(this);  //别遗漏
                                } else {//等待一秒后再尝试启动SCO
                                    try {
                                        Thread.sleep(1000);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                    mAudioManager.startBluetoothSco();
                                }
                            }
                        }, new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED));
                    } else {
                        Toast.makeText(MainActivity.this, "请先打开蓝牙...", Toast.LENGTH_SHORT).show();
                    }
                    break;
                // 停止录音
                case R.id.stopRecord:
                    mRecorder.stop();
                    mRecorder.release();
                    mRecorder = null;
                    if (mAudioManager.isBluetoothScoOn()) {
                        mAudioManager.setBluetoothScoOn(false);
                        mAudioManager.stopBluetoothSco();
                    }
                    break;
                // 播放/停止播放
                case R.id.startPlay:
                    try {
                        if (!mPlayer.isPlaying()) {
                            if (!mFileName.isEmpty()) {
                                if (!mAudioManager.isBluetoothA2dpOn())
                                    mAudioManager.setBluetoothA2dpOn(true); //如果A2DP没建立，则建立A2DP连接
                                mAudioManager.stopBluetoothSco();//如果SCO没有断开，由于SCO优先级高于A2DP，A2DP可能无声音
                                try {
                                    Thread.sleep(500);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                mAudioManager.setStreamSolo(AudioManager.STREAM_MUSIC, true);
                                //让声音路由到蓝牙A2DP。此方法虽已弃用，但就它比较直接、好用。
                                mAudioManager.setRouting(AudioManager.MODE_NORMAL, AudioManager.ROUTE_BLUETOOTH_A2DP, AudioManager.ROUTE_BLUETOOTH);
                                if (isFirst) {
                                    mPlayer.reset();
                                    mPlayer.setDataSource(mFileName);
                                    mPlayer.prepare();
                                    mPlayer.start();
                                    isFirst = false;
                                } else {
                                    mPlayer.start();
                                }
                            } else {
                                Toast.makeText(MainActivity.this, "文件不存在...", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            mPlayer.pause();
                            mAudioManager.setStreamSolo(AudioManager.STREAM_MUSIC, false);
                        }
                    } catch (IOException e) {
                        Log.e("main", "prepare() failed");
                    }
                    break;
            }
        }

    }
}
