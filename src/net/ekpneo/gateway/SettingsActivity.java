package net.ekpneo.gateway;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.Preference;
import android.preference.EditTextPreference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.util.Log;

public class SettingsActivity extends PreferenceActivity implements OnPreferenceChangeListener {
	public static final String SERVER_URL = "server_url";
	
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		addPreferencesFromResource(R.xml.preferences);
		
		EditTextPreference serverUrl = (EditTextPreference) findPreference(SERVER_URL);
		serverUrl.setOnPreferenceChangeListener(this);
		serverUrl.setSummary(serverUrl.getText());
	}

	public boolean onPreferenceChange(Preference preference, Object newValue) {
		Log.d("SettingsActivity", "Preference changed");
		if (preference.getKey().equals(SERVER_URL)) {
			EditTextPreference url = (EditTextPreference) preference;
			url.setSummary((String) newValue);
		}
		
		return true;
	}
}
