package net.ekpneo.gateway;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.CompoundButton;
import android.widget.ToggleButton;
import android.util.Log;

public class GatewayActivity extends Activity {
	
	public static final String TAG = "GatewayActivity";
	
	private static final int MENU_SETTINGS_ID = 1;
	
	private ToggleButton mToggleButton = null;
	
	private IGatewayService mService;
	private boolean mServiceBound = false;
	
	private ServiceConnection mServiceConn = new ServiceConnection() {
		public void onServiceConnected(ComponentName name, IBinder service) {
			mService = IGatewayService.Stub.asInterface(service);
			mServiceBound = true;
			
			try {
				mToggleButton.setChecked(mService.isEnabled());
			} catch (RemoteException e) {
				Log.e(TAG, "Error getting gateway status on bind: " + e.getMessage());
			}
			
			Log.d(TAG, "Bound to gateway service");
		}
		
		public void onServiceDisconnected(ComponentName name) {
			mService = null;
			mServiceBound = false;
			Log.d(TAG, "Disconnected from gateway service");
		}
	};
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.main);
        
        mToggleButton = (ToggleButton) findViewById(R.id.btnEnable);
        mToggleButton.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked == true) {
					enableSmsService();
				} else {
					disableSmsService();
				}
			}

        });
        
        Intent intent = new Intent(this, SmsService.class);
        startService(intent);
    }
    
	@Override
	protected void onResume() {
		super.onResume();
		
		Intent intent = new Intent(this, SmsService.class);
		Log.d(TAG, "Binding to PushService");
		
		mServiceBound = bindService(intent, mServiceConn, Context.BIND_AUTO_CREATE);
		if (!mServiceBound) {
			Log.e(TAG, "Error binding to PushService");
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		
		if (mServiceBound) {
			Log.d(TAG, "Unbinding from gateway service");
			unbindService(mServiceConn);
		}
	}
	
	protected void enableSmsService() {
		if (!mServiceBound) {
			Log.e(TAG, "Service not bound while trying to start PushService");
			return;
		}
			
		try {
			if (mService.isEnabled()) return;
			mService.enable();
		} catch (RemoteException e) {
			Log.e(TAG, "Error while starting PushService: " + e.getMessage());
		}
	}
	
	protected void disableSmsService() {
		if (!mServiceBound) {
			Log.e(TAG, "Service not bound while trying to stop PushService");
			return;
		}
			
		try {
			if (!mService.isEnabled()) return;
			mService.disable();
		} catch (RemoteException e) {
			Log.e(TAG, "Error while stopping PushService: " + e.getMessage());
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		
		MenuItem settingsItem = menu.add(0, MENU_SETTINGS_ID, 0, R.string.menu_settings);
		settingsItem.setIcon(android.R.drawable.ic_menu_preferences);
		
		return true;
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch (item.getItemId()) {
		case MENU_SETTINGS_ID:
			startActivity(new Intent(this, SettingsActivity.class));
			break;
		}
		
		return super.onMenuItemSelected(featureId, item);
	}


	
}