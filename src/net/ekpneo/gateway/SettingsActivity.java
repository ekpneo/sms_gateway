package net.ekpneo.gateway;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.Preference;
import android.preference.EditTextPreference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.util.Log;

public class SettingsActivity extends PreferenceActivity implements OnPreferenceChangeListener {
	public static final String SERVER_INCOMING_URL = "server_incoming_url";
	public static final String SERVER_OUTGOING_URL = "server_outgoing_url";
	
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		addPreferencesFromResource(R.xml.preferences);
		
		EditTextPreference serverIncomingUrl = (EditTextPreference) findPreference(SERVER_INCOMING_URL);
		serverIncomingUrl.setOnPreferenceChangeListener(this);
		serverIncomingUrl.setSummary(serverIncomingUrl.getText());
		
		EditTextPreference serverOutgoingUrl = (EditTextPreference) findPreference(SERVER_OUTGOING_URL);
		serverOutgoingUrl.setOnPreferenceChangeListener(this);
		serverOutgoingUrl.setSummary(serverOutgoingUrl.getText());
	}

	public boolean onPreferenceChange(Preference preference, Object newValue) {
		Log.d("SettingsActivity", "Preference changed");
		if (preference.getKey().equals(SERVER_INCOMING_URL) ||
			preference.getKey().equals(SERVER_OUTGOING_URL)) 
		{
			EditTextPreference url = (EditTextPreference) preference;
			url.setSummary((String) newValue);
		}
		
		return true;
	}
}
