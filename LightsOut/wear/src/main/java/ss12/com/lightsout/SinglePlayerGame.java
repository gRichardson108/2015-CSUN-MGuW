package ss12.com.lightsout;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import org.w3c.dom.Text;

import java.util.List;
import java.util.Random;

public class SinglePlayerGame extends Activity implements MessageApi.MessageListener,
        GoogleApiClient.ConnectionCallbacks, SensorEventListener {

    private GoogleApiClient mGoogleApiClient;
    private final String TAG = "Single Player Game"; //tag for logging
    private TextView mTextView;
    private Vibrator vibrator;

    //timer variables
    //begin time limit at 5 seconds
    private int timeLimit = 5000;
    private Random rand;

    //node for mobile device
    private String nodeId = "";

    //sensor variables
    private SensorManager sensorManager;
    private Sensor accel;
    private float[] dataArray = new float[3];
    private double[] gravity =  {0,0,0};
    private double[] acceleration = {0,0,0};
    //max readings for the accelerometer per round
    private int xMax=0,yMax=0,zMax=0;

    //Time limit implemented through Handler
    static private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == 1337) {
                //this only occurs when the message fires off after the delay meaning the user
                //failed to perform the action in time
                ((SinglePlayerGame) msg.obj).roundLoss();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_single_player_game);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mTextView = (TextView) stub.findViewById(R.id.text);
            }
        });
        //keep screen active on wearable
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //register sensors
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        //build apiClient
        createGoogleApiClient();

        //create random number generator
        rand = new Random();

        vibrator = vibrator= (Vibrator) this.getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);


    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    private void createGoogleApiClient(){
        //Basic Google Api Client build
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                        Log.d(TAG, "onConnectionFailed: " + result);
                    }
                })
                        // adding only the wearable API
                .addApi(Wearable.API)
                .build();

    }

    //this method sets the global variable nodeId to the id of the phone for communication
    private void retrieveDeviceNode() {
        //we are using a worker thread to get the nodeId since we do not want to
        // clog up the main thread
        Log.d(TAG,"nodes retrieved");

        new Thread(new Runnable() {
            @Override
            public void run() {

                NodeApi.GetConnectedNodesResult nodesResult =
                        Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();
                final List<Node> nodes = nodesResult.getNodes();
                //we are assuming here that there is only one wearable attached to the phone
                //currently Android only supports having one wearable connected at a time
                if (nodes.size() > 0) {
                    nodeId=nodes.get(0).getId();
                    Log.d(TAG,nodeId);
                }

                Log.d(TAG,nodes.size()+"");
            }
        }).start();
    }

    //game logic for each round
    private void startRound(int action){
        sensorManager.registerListener(this,accel,sensorManager.SENSOR_DELAY_NORMAL);

        //increase time limit randomly up to half a second only if it will result in less than
        //five seconds for the user to react
        if(timeLimit<5000) {
            timeLimit += rand.nextInt(500);
        }
        //decrease time limit up to one second only if it will result in over .8 seconds for the
        //user to react
        if(timeLimit>1800) {
            timeLimit -= rand.nextInt(1000);
        }
        Message msg = mHandler.obtainMessage(1,this);
        mHandler.sendMessageDelayed(msg, timeLimit);
    }

    //game logic for a successful attempt at a round
    private void roundSuccess(){
        //remove the pending message so that the message will not trigger a loss
        mHandler.removeMessages(1337, this);
        sensorManager.unregisterListener(this,accel);

    }

    //game logic for an unsuccessful attempt at a round, called when the Handler receives the
    //delayed message
    private void roundLoss(){
        sensorManager.unregisterListener(this, accel);
    }

    private void compareAxes(double x, double y, double z){

    }

    //auditory and haptic feedback on success or fail
    private void respond(int action){
        //general playing of sounds should occur here
        //probably will want to pass in sound name or path
        switch (action)
        {
            case 0://punch
                vibrator.vibrate(new long[] { 0, 200, 0 }, 0);
                break;
            case 1: //counter
                vibrator.vibrate(new long[] { 0, 200, 0, 200, 0 }, 0);
                break;
            case 2: //push
                vibrator.vibrate(new long[]{0, 200, 0, 200, 0, 200, 0}, 0);
                break;
            default:
                break;
        }

    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "onConnected: " + bundle);
        Wearable.MessageApi.addListener(mGoogleApiClient,this);
        // now we can use the Message API
        //assigns nodeId
        retrieveDeviceNode();
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.d(TAG, "onConnectionSuspended: " + cause);

    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        final String message = messageEvent.getPath();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d("Wearable", "message received");
                Toast.makeText(getApplicationContext(),message,Toast.LENGTH_LONG).show();
                startRound(Integer.parseInt(message));
            }
        });
    }

    @Override
    protected void onStop() {
        Wearable.MessageApi.removeListener(mGoogleApiClient, this);
        super.onStop();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor mySensor = event.sensor;

        if (mySensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            dataArray[0] = event.values[0];
            dataArray[1] = event.values[1];
            dataArray[2] = event.values[2];
        }

        // filter to account for gravity in accelerometer readings
        // alpha is calculated as t / (t + dT)
        // with t, the low-pass filter's time-constant
        // and dT, the event delivery rate

        final double alpha = 0.8;

        gravity[0] = alpha * gravity[0] + (1 - alpha) * dataArray[0];
        gravity[1] = alpha * gravity[1] + (1 - alpha) * dataArray[1];
        gravity[2] = alpha * gravity[2] + (1 - alpha) * dataArray[2];

        acceleration[0] = dataArray[0] - gravity[0];
        acceleration[1] = dataArray[1] - gravity[1];
        acceleration[2] = dataArray[2] - gravity[2];
        TextView textview = (TextView) findViewById(R.id.text);
        textview.setText("x:"+acceleration[0]+"\ny: "+acceleration[1]+"\nz: "+acceleration[2]);
        compareAxes(acceleration[0],acceleration[1],acceleration[2]);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

}