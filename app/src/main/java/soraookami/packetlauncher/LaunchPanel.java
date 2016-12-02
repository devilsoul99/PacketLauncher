package soraookami.packetlauncher;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class LaunchPanel extends AppCompatActivity {

    private static String serverIP,
                          protocol;
    private static int serverPort,
                       frameSize,
                       packetSize,
                       flowSize;
    private static boolean isPacketSequencing,isFrameSequencing;
    private EditText log;
    private static DatagramSocket socket = null;
    private static InetAddress address = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch_panel);
        readPreferences();
    }

    /**
     * Check if the wifi is connected.
     * @return the wifi connected or not
     */
    private boolean isWifiConnected(){
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo ani = cm.getActiveNetworkInfo();
        if(ani != null && ani.getType() == ConnectivityManager.TYPE_WIFI){
            return true;
        }else{
            return false;
        }
    }

    /**
     * This class is used as a helper to updating log in a non-UI thread,
     * pass the message to constructor and start it in runOnUiThread() method.
     */
    public class LogHelper implements Runnable{
        private String msg = "";
        public LogHelper(String m){
            msg = m;
        }
        public void run() {
            log.append(msg);
        }
    }

    public class UDPLaunchClient implements Runnable{
        @Override
        public void run() {
            int frameLaunched = 0;
            int packetCount = 0;
            byte[] frameData = new byte[frameSize];
            while(frameLaunched < flowSize) {
                //Packet load up
                int len = ((packetCount + 1) * packetSize > frameSize) ? frameSize - packetCount * packetSize : packetSize;
                int dataOffset = (isFrameSequencing? 4:0) + (isPacketSequencing? 4:0);
                int seqOffset = 0;
                byte[] packetData = new byte[dataOffset + len];
                System.arraycopy(frameData, packetCount * packetSize, packetData, dataOffset, len);

                if(isFrameSequencing){
                    packetData[seqOffset++] = (byte)(frameLaunched>>24);
                    packetData[seqOffset++] = (byte)(frameLaunched>>16);
                    packetData[seqOffset++] = (byte)(frameLaunched>>8);
                    packetData[seqOffset++] = (byte)frameLaunched;
                }
                if(isPacketSequencing){
                    packetData[seqOffset++] = (byte)(packetCount>>24);
                    packetData[seqOffset++] = (byte)(packetCount>>16);
                    packetData[seqOffset++] = (byte)(packetCount>>8);
                    packetData[seqOffset++] = (byte)packetCount;
                }

                DatagramPacket packet = new DatagramPacket(packetData, dataOffset + len, address, serverPort);
                //Sending
                try{
                    socket.send(packet);
                    runOnUiThread(new LogHelper(getString(R.string.log_packet_success,frameLaunched,packetCount)));
                }catch (Exception e){
                    runOnUiThread(new LogHelper(getString(R.string.log_packet_failed,frameLaunched,packetCount)));
                }
                packetCount++;
                if(packetCount > frameSize / packetSize) {
                    frameLaunched++;
                    packetCount = 0;
                }
            }
            runOnUiThread(new LogHelper(getString(R.string.log_launch_complete)));
            socket.close();
        }
    }

    private void UDPLaunch(){
        try {
            log.append(getString(R.string.log_start_con));
            address = InetAddress.getByName(serverIP);
            socket = new DatagramSocket();
            new Thread(new UDPLaunchClient()).start();
        }catch (Exception e){
            log.append(e.toString());
        }
    }

    private void TCPLaunch(){
        showAlertDialog(getString(R.string.dialog_tcp_title),getString(R.string.dialog_tcp_msg));
    }

    /**
     * This method is called by the startLaunch button, initiate the
     * process of packet testing corresponding the preference arguments.
     * @param view the object that calls
     */
    public void startLaunch(View view){
        log = (EditText) findViewById(R.id.editText);
        log.setText("");
        if(!isWifiConnected()){
            log.append(getString(R.string.log_wifi_warn));
            return;
        }
        String[] protocols = getResources().getStringArray(R.array.pref_protocol_entries);
        if(protocol.contentEquals(protocols[0])){
            UDPLaunch();
        }else{
            TCPLaunch();
        }
    }

    /**
     * Read the preferences and show them on TextView.
     */
    private void readPreferences(){
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        protocol = sharedPref.getString("pref_protocol","UDP");
        serverIP = sharedPref.getString("pref_serverIP","127.0.0.1");
        serverPort = Integer.parseInt(sharedPref.getString("pref_serverPort","50000"));
        frameSize = Integer.parseInt(sharedPref.getString("pref_frameSize","200"));
        packetSize = Integer.parseInt(sharedPref.getString("pref_packetSize","30"));
        flowSize = Integer.parseInt(sharedPref.getString("pref_flow","20"));
        isPacketSequencing = sharedPref.getBoolean("pref_packet_seq",false);
        isFrameSequencing = sharedPref.getBoolean("pref_frame_seq",false);

        TextView subject = (TextView)findViewById(R.id.panel_protocol);
        subject.setText(getString(R.string.pref_protocol_title) + ": " + protocol);
        subject = (TextView)findViewById(R.id.panel_serverIP);
        subject.setText(getString(R.string.pref_serverIP_title) + ": " + serverIP);
        subject = (TextView)findViewById(R.id.panel_serverPort);
        subject.setText(getString(R.string.pref_serverPort_title) + ": " + serverPort);
        subject = (TextView)findViewById(R.id.panel_frameSize);
        subject.setText(getString(R.string.pref_frameSize_title) + ": " + frameSize);
        subject = (TextView)findViewById(R.id.panel_packetSize);
        subject.setText(getString(R.string.pref_packetSize_title) + ": " + packetSize);
        subject = (TextView)findViewById(R.id.panel_flow);
        subject.setText(getString(R.string.pref_flow_title) + ": " + flowSize);
    }

    /**
     * Show a simple dialog for info.
     * @param title the title of the dialog
     * @param msg content message of information
     */
    private void showAlertDialog(String title, String msg){
        AlertDialog.Builder dialog = new AlertDialog.Builder(LaunchPanel.this);
        dialog.setTitle(title);
        dialog.setMessage(msg);
        dialog.setCancelable(false);

        dialog.setPositiveButton(R.string.dialog_confirm, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
            }
        });

        dialog.show();
    }

    /*
    Functions for menu showing and its events.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.up_right_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.urm_settings:
                switchBackButton(true);
                getFragmentManager().beginTransaction().replace(android.R.id.content, new PrefsFragment()).commit();
                return true;
            case R.id.urm_about:
                showAlertDialog(getString(R.string.dialog_about_title),getString(R.string.dialog_about_msg));
                return true;
            case android.R.id.home:
                switchBackButton(false);
                setContentView(R.layout.activity_launch_panel);
                readPreferences();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void switchBackButton(boolean i){
        getSupportActionBar().setDisplayShowHomeEnabled(i);
        getSupportActionBar().setDisplayHomeAsUpEnabled(i);
    }



    public static class PrefsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);
            view.setBackgroundColor(getResources().getColor(android.R.color.background_light));
            return view;
        }
    }
}
