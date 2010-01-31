package net.ekpneo.gateway;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
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
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.json.*;

public class SmsService extends Service {
	private static final String TAG = "SmsService";
	
	private static final String SMS_RECEIVE_ACTION = "android.provider.Telephony.SMS_RECEIVED";
	private static final String SMS_SEND_ACTION = "net.ekpneo.gateway.SMS_SENT";
	private static final String SMS_SEND_EXTRA_ID = "net.ekpneo.gateway.MessageIds";
	
	private static final String SERVER_INCOMING_URL = "http://127.0.0.1/sms/incoming";
	private static final String SERVER_OUTGOING_URL = "http://127.0.0.1/sms/outgoing";
	
	private static final int NOTIFICATION_ID = 1;
	private Notification mNotification;
	
	private boolean mEnabled = false;
	
	private BroadcastReceiver mSmsReceivedReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			if (!intent.getAction().equals(SMS_RECEIVE_ACTION))
				return;
			
			Log.d(TAG, "SMSReceivedReceiver - Received SMS(s)");
			
			Bundle bundle = intent.getExtras();
			if (bundle == null)
				return;
			
			Object[] pdus = (Object[]) bundle.get("pdus");
			SmsMessage message;
			
			for (int i = 0; i < pdus.length; i++) {
				message = SmsMessage.createFromPdu((byte[]) pdus[i]);
				Log.d(TAG, "SMSReceivedReceiver - SMS from: " + message.getDisplayOriginatingAddress() + "\n");
				mPushQueue.add(message);
				mPushCondVar.open();
			}
		}
	};
	
	private BroadcastReceiver mSmsSentReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			if (!intent.getAction().equals(SMS_SEND_ACTION))
				return;
			
			Log.d(TAG, "SMSSentReceiver - SMS Sent");
			if (this.getResultCode() != Activity.RESULT_OK) {
				Log.e(TAG, "SMSSentReceiver - Error sending SMS");
				return;
			}

			synchronized (mSentMutex) {
				mSentMessages.add(intent.getStringExtra(SMS_SEND_EXTRA_ID));
			}
		}
	};
	
	private final IGatewayService.Stub mBinder = new IGatewayService.Stub() {
		public boolean enable() {
			if (mEnabled == true) 
				return true;
			
			Log.d(TAG, "Enabling service");

			Log.d(TAG, "Registering receiver.");
			IntentFilter intentFilter = new IntentFilter(SMS_RECEIVE_ACTION);
			registerReceiver(mSmsReceivedReceiver, intentFilter);
			
			intentFilter = new IntentFilter(SMS_SEND_ACTION);
			registerReceiver(mSmsSentReceiver, intentFilter);
			
			Log.d(TAG, "Starting threads");
			startPushThread();
			startSendThread();
			startPollThread();
			
			Intent notificationIntent = new Intent(getApplication(), GatewayActivity.class);
			notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
			PendingIntent contentIntent = PendingIntent.getActivity(SmsService.this, 0, notificationIntent, 0);

			mNotification.setLatestEventInfo(
					getApplicationContext(), 
					getText(R.string.app_name), 
					getText(R.string.notifictation_active), 
					contentIntent);
			
			// Android 1.6
			setForeground(true);
			NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			notificationManager.notify(NOTIFICATION_ID, mNotification);
			
			//startForeground(NOTIFICATION_ID, mNotification); // Android 2.0
			
			mEnabled = true;
			return true;
		}
		
		public boolean disable() {
			Log.d(TAG, "Disabling service");
			
			stopPushThread();
			stopSendThread();
			stopPollThread();
			
			Log.d(TAG, "Unregistering receiver");
			unregisterReceiver(mSmsReceivedReceiver);
			unregisterReceiver(mSmsSentReceiver);
			
			//stopForeground(true); // Android 2.0
			
			// Android 1.6
			setForeground(false);
			NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			notificationManager.cancel(NOTIFICATION_ID);
			
			mEnabled = false;
			return true;
		}
		
		public boolean isEnabled() {
			return mEnabled;
		}

	};
	
	public void onCreate() {
		super.onCreate();
		
		mPushQueue = new ConcurrentLinkedQueue<SmsMessage>();
		mPushCondVar = new ConditionVariable();
		
		mSendQueue = new ConcurrentLinkedQueue<String[]>();
		mSendCondVar = new ConditionVariable();
		
		mNotification = new Notification(R.drawable.status_icon, 
				getText(R.string.notifictation_active), 
				System.currentTimeMillis());
		mNotification.flags |= Notification.FLAG_ONGOING_EVENT;
		mNotification.flags |= Notification.FLAG_NO_CLEAR;
	}
	
	@Override
	public void onDestroy() {
		Log.d(TAG, "Being destroyed");
		super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}
	
	/* PUSH THREAD FOR INCOMING SMS */
	
	// The queue of SMSs received that need to be pushed to the server
	private ConcurrentLinkedQueue<SmsMessage> mPushQueue;
	private ConditionVariable mPushCondVar;
	
	private Thread mPushThread = null;
	private Runnable mPushTask = new Runnable() {	
		public void run() {
			Log.d(TAG, "PushThread - Starting");
			
			while(Thread.currentThread() == mPushThread) {
				if(mPushQueue.size() == 0 ) {
					Log.d(TAG, "PushThread - Queue is empty. Waiting.");
					mPushCondVar.close();
					mPushCondVar.block();
					continue;
				}
				
				Log.d(TAG, "PushThread - Pushing SMS");
				SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(SmsService.this);
				String serverUrl = preferences.getString(SettingsActivity.SERVER_INCOMING_URL, SERVER_INCOMING_URL);
				String serverSecret = preferences.getString(SettingsActivity.SERVER_SECRET, "");
				
				SmsMessage message = mPushQueue.peek();
				HashMap<String,String> queryParams = new HashMap<String, String>();
				queryParams.put("secret", serverSecret);
				queryParams.put("phone", message.getDisplayOriginatingAddress());
				queryParams.put("message", message.getDisplayMessageBody());
				Map<String,String> response = uploadData(serverUrl, queryParams);
				
				if (response != null) {
					if (response.get("code").equals("200"))
						mPushQueue.poll();
					else
						Log.w(TAG, "PushThread - Non-200 response from server");
				} else {
					Log.w(TAG, "PushThread - IOError contacting server.");
				}
			}
			
			Log.d(TAG, "PushThread - Stopping");
		}
	};
	
	public synchronized void startPushThread() {
		if (mPushThread == null) {
			mPushThread = new Thread(mPushTask, "PushThread");
			mPushThread.start();
		}		
	}
	
	
	public synchronized void stopPushThread() {
		if (mPushThread != null) {
			Thread moribund = mPushThread;
			mPushThread = null;
			mPushCondVar.open();
			moribund.interrupt();
		}
	}
	
	/* POLL THREAD TO FIND OUTGOING SMS */
	
	private Object mSentMutex = new Object();
	private Vector<String> mSentMessages = new Vector<String>();
	
	private Thread mPollThread = null;
	private Runnable mPollTask = new Runnable() {	
		public void run() {
			Log.d(TAG, "PollThread - Starting");
			
			while(Thread.currentThread() == mPollThread) {
				Log.d(TAG, "PollThread - Polling");
			
				SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(SmsService.this);
				String serverUrl = preferences.getString(SettingsActivity.SERVER_OUTGOING_URL, SERVER_OUTGOING_URL);
				String serverSecret = preferences.getString(SettingsActivity.SERVER_SECRET, "");
				
				String sentMessages;
				synchronized(mSentMutex) {
					JSONArray array = new JSONArray(mSentMessages);
					sentMessages = array.toString();
					mSentMessages.clear();
				}
				
				HashMap<String,String> queryParams = new HashMap<String, String>();
				queryParams.put("secret", serverSecret);
				queryParams.put("messages", sentMessages);
				Map<String,String> response = uploadData(serverUrl, queryParams);
				
				if (response == null) {
					Log.w(TAG, "PollThread - IOError connecting to server");
				} else if (response != null && response.get("code").equals("200")) {
					handleResponse(response);
				} else {
					Log.w(TAG, "PollThread - Server returned " + response.get("code"));					
				}

				try {
					Thread.sleep(30 * 1000);
				} catch (InterruptedException e) {
					Log.e(TAG, "PollThread - Interrupted: " + e.getMessage());
				}
			}
			
			Log.d(TAG, "PollThread - Stopping");
		}
		
		private void handleResponse(Map<String,String> response) {			
			// Very harsh way of decoding JSON. If it's not perfect, throw a fit
			try {
				JSONObject jsonResponse = new JSONObject(response.get("response"));
				if(jsonResponse.getString("result").equals("ok")) {
					JSONArray messages = jsonResponse.getJSONArray("messages");
					for (int i = 0; i < messages.length(); i++) {
						JSONObject message = messages.getJSONObject(i);
					
						try {
							String[] arrMessage = new String[3];
							arrMessage[0] = message.getString("id");
							arrMessage[1] = message.getString("phone");
							arrMessage[2] = message.getString("message");
							mSendQueue.add(arrMessage);
						} catch(JSONException e) {
							Log.w(TAG, "PollThread - malformed message object, skipping");
							continue;
						}
					}
					
					if (messages.length() > 0) {
						mSendCondVar.open();
					}
				}	
			} catch(JSONException e) {
				Log.e(TAG, "PollThread malformed JSON response" + e.getMessage());
			}
		}
	};
	
	public synchronized void startPollThread() {
		if (mPollThread == null) {
			mPollThread = new Thread(mPollTask, "PollThread");
			mPollThread.start();
		}		
	}
	
	
	public synchronized void stopPollThread() {
		if (mPollThread != null) {
			Thread moribund = mPollThread;
			mPollThread = null;
			moribund.interrupt();
		}
	}
	
	/* SMS SENDING THREAD */
	
	private ConcurrentLinkedQueue<String[]> mSendQueue;
	private ConditionVariable mSendCondVar;
	
	private Thread mSendThread;
	private Runnable mSendTask = new Runnable() {
		public void run() {
			Log.d(TAG, "SendThread - Started");
			
			SmsManager smsManager = SmsManager.getDefault();
			
			while(Thread.currentThread() == mSendThread) {
				if(mSendQueue.size() == 0 ) {
					Log.d(TAG, "SendQueue is empty. Waiting.");
					mSendCondVar.close();
					mSendCondVar.block();
					continue;
				}
				
				String[] message = mSendQueue.poll();
				if (message == null)
					continue;
				
				Log.d(TAG, "SendThread - Sending SMS");
				Intent intent = new Intent(SMS_SEND_ACTION);
				intent.putExtra(SMS_SEND_EXTRA_ID, message[0]);
				PendingIntent sentIntent = PendingIntent.getBroadcast(SmsService.this, 0, intent, 
						PendingIntent.FLAG_ONE_SHOT);
				smsManager.sendTextMessage(message[1], null, message[2], sentIntent, null);
			}
			
			Log.d(TAG, "SendThread - Stopped");
		}
	};
	
	public synchronized void startSendThread() {
		if (mSendThread == null) {
			mSendThread = new Thread(mSendTask, "SendThread");
			mSendThread.start();
		}		
	}
	
	
	public synchronized void stopSendThread() {
		if (mSendThread != null) {
			Thread moribund = mSendThread;
			mSendThread = null;
			mSendCondVar.open();
			moribund.interrupt();
		}
	}
	
	/* UTILITY FUNCTIONS */
	
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
