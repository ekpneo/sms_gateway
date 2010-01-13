package net.ekpneo.gateway;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.CompoundButton;
import android.widget.ToggleButton;
import android.widget.TextView;
import android.util.Log;

public class GatewayActivity extends Activity {
	
	public static final String TAG = "GatewayActivity";
	
	private TextView mText = null;
	private ToggleButton mToggleButton = null;
	
	private IGatewayService mService;
	private ServiceConnection mServiceConn = new ServiceConnection() {
		public void onServiceConnected(ComponentName name, IBinder service) {
			mService = IGatewayService.Stub.asInterface(service);
			try {
				mToggleButton.setChecked(mService.isEnabled());
			} catch (RemoteException e) {
				Log.d(TAG, "Hi Mom.");
			}
		}
		
		public void onServiceDisconnected(ComponentName name) {
			mService = null;
		}
	};
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.main);
        
        mText = (TextView) findViewById(R.id.txtDebug);
        mText.setText("App Started\n");
        
        mToggleButton = (ToggleButton) findViewById(R.id.btnEnable);
        mToggleButton.setOnCheckedChangeListener(new OnCheckedChangeListener() {

        
			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				
				try{
					if (isChecked) {
						mText.append("Enabling service\n");
						mService.enable();
					} else {
						mText.append("Disabling service\n");
						mService.disable();
					}
				} catch (RemoteException e) {
					Log.d(TAG, "Daddy touches me.");
				}

			}

        });
        
        Intent intent = new Intent();
        intent.setClass(getApplication(), GatewayService.class);
        startService(intent);
    }
    
	@Override
	protected void onResume() {
		super.onResume();
		
		Intent intent = new Intent();
		intent.setClass(getApplication(), GatewayService.class);
		if (!bindService(intent, mServiceConn, Context.BIND_AUTO_CREATE)) {
			Log.d(TAG, "Unable to bind to service");
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		unbindService(mServiceConn);
	}
    
}