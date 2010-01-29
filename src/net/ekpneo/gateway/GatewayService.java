package net.ekpneo.gateway;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.telephony.SmsMessage;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public class GatewayService extends Service {
	private static final String TAG = "GatewayService";
	
	private static final String SMS_ACTION = "android.provider.Telephony.SMS_RECEIVED";
	private static final String SERVER_URL = "http://127.0.0.1/sms/";
	
	private static final int NOTIFICATION_ID = 1;
	private Notification mNotification;
	
	private boolean mEnabled = false;
	
	private ConcurrentLinkedQueue<SmsMessage> mUploadQueue;
	private ConditionVariable mCondVar;
	
	private BroadcastReceiver mSmsReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			if (!intent.getAction().equals(SMS_ACTION))
				return;
			
			Log.d(TAG, "Received SMS(s)");
			
			Bundle bundle = intent.getExtras();
			if (bundle == null)
				return;
			
			Object[] pdus = (Object[]) bundle.get("pdus");
			SmsMessage message;
			
			for (int i = 0; i < pdus.length; i++) {
				message = SmsMessage.createFromPdu((byte[]) pdus[i]);
				Log.d(TAG, "SMS from: " + message.getDisplayOriginatingAddress() + "\n");
				mUploadQueue.add(message);
				mCondVar.open();
			}
		}
	};
	
	private final IGatewayService.Stub mBinder = new IGatewayService.Stub() {
		public boolean enable() {
			if (mEnabled == true) 
				return true;
			
			Log.d(TAG, "Enabling service");

			Log.d(TAG, "Registering receiver.");
			IntentFilter intentFilter = new IntentFilter(SMS_ACTION);
			registerReceiver(mSmsReceiver, intentFilter);
			
			startThread();
			
			Intent notificationIntent = new Intent(getApplication(), GatewayActivity.class);
			notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
			PendingIntent contentIntent = PendingIntent.getActivity(GatewayService.this, 0, notificationIntent, 0);

			mNotification.setLatestEventInfo(
					getApplicationContext(), 
					getText(R.string.app_name), 
					getText(R.string.notifictation_active), 
					contentIntent);
			startForeground(NOTIFICATION_ID, mNotification);
			
			mEnabled = true;
			return true;
		}
		
		public boolean disable() {
			Log.d(TAG, "Disabling service");
			
			stopThread();
			
			Log.d(TAG, "Unregistering receiver");
			unregisterReceiver(mSmsReceiver);
			
			stopForeground(true);
			mEnabled = false;
			return true;
		}
		
		public boolean isEnabled() {
			return mEnabled;
		}

	};
	
	private Thread mUploadThread;
	private Runnable mUploadTask = new Runnable() {	
		public void run() {
			while(Thread.currentThread() == mUploadThread) {
				if(mUploadQueue.size() == 0 ) {
					Log.d(TAG, "Queue is empty. Waiting.");
					mCondVar.close();
					mCondVar.block();
					continue;
				}
				
				SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(GatewayService.this);
				String serverUrl = preferences.getString(SettingsActivity.SERVER_INCOMING_URL, SERVER_URL);
				
				SmsMessage message = mUploadQueue.peek();
				HashMap<String,String> queryParams = new HashMap<String, String>();
				queryParams.put("from", message.getDisplayOriginatingAddress());
				queryParams.put("text", message.getDisplayMessageBody());
				Map<String,String> response = uploadData(serverUrl, queryParams);
				
				if (response != null) {
					mUploadQueue.poll();
				}
			}
		}
	};

	public synchronized void startThread() {
		if (mUploadThread == null) {
			mUploadThread = new Thread(mUploadTask, "UploadThread");
			mUploadThread.start();
		}
	}
	
	public synchronized void stopThread() {
		if (mUploadThread != null) {
			Thread moribund = mUploadThread;
			mUploadThread = null;
			mCondVar.open();
			moribund.interrupt();
		}
	}
	
	public void onCreate() {
		super.onCreate();
		
		mUploadQueue = new ConcurrentLinkedQueue<SmsMessage>();
		mCondVar = new ConditionVariable();
		
		mNotification = new Notification(R.drawable.status_icon, 
				getText(R.string.notifictation_active), 
				System.currentTimeMillis());
		mNotification.flags |= Notification.FLAG_ONGOING_EVENT;
		mNotification.flags |= Notification.FLAG_NO_CLEAR;
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	
    public Map<String,String> uploadData(String uri, Map<String,String> vars) {

        try {
            boolean useSSL = false;
            if (uri.startsWith("https")) {
                useSSL = true;
            }

            HttpPost post = new HttpPost();
            Log.d(TAG, "Posting to URL " + uri);
            Log.d(TAG, "Posting with args " + vars.toString());
            Map<String,String> response = post.post(uri, useSSL, vars, "", null, null);
            Log.d(TAG, "POST response: " + response.toString());
            
            return response;
        } 
        catch (FileNotFoundException e) {
            Log.e(TAG, "FileNotFoundException: " + e);
            return null;
        }         
        catch (IOException e) {
            Log.e(TAG, "IOException: " + e);
            return null;
        }
        catch (NullPointerException e) {
            Log.e(TAG, "NullPointerException: " + e);
            return null;
        }

    }
}
