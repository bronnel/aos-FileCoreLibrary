// Copyright 2019 Courville Software
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.archos.filecorelibrary.jcifs;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.archos.environment.NetworkState;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;

import jcifs.CIFSException;
import jcifs.context.BaseContext;
import jcifs.CIFSContext;
import jcifs.config.PropertyConfiguration;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbAuthException;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.HashMap;
import java.util.Properties;


public class JcifsUtils {

    private final static String TAG = "JcifsUtils";
    private final static boolean DBG = true;

    // when enabling LIMIT_PROTOCOL_NEGO smbFile will use strict SMBv1 or SMBv2 contexts to avoid SMBv1 negotiations or SMBv2 negotiations
    // this is a hack to get around some issues seen with jcifs-ng
    public final static boolean LIMIT_PROTOCOL_NEGO = true;

    private static Properties prop = null;
    private static CIFSContext baseContextSmb1 = createContext(false);
    private static CIFSContext baseContextSmb2 = createContext(true);

    private static CIFSContext baseContextSmb1Only = createContextOnly(false);
    private static CIFSContext baseContextSmb2Only = createContextOnly(true);

    private static Context mContext;

    // singleton, volatile to make double-checked-locking work correctly
    private static volatile JcifsUtils sInstance;

    // get the instance, context is used for initial context injection
    public static JcifsUtils getInstance(Context context) {
        if (sInstance == null) {
            synchronized(JcifsUtils.class) {
                if (sInstance == null) sInstance = new JcifsUtils(context.getApplicationContext());
            }
        }
        return sInstance;
    }

    /** may return null but no Context required */
    public static JcifsUtils peekInstance() {
        return sInstance;
    }

    private JcifsUtils(Context context) {
        mContext = context;
    }

    private static CIFSContext createContext(boolean isSmb2) {
        prop = new Properties();
        prop.putAll(System.getProperties());

        prop.put("jcifs.smb.client.enableSMB2", String.valueOf(isSmb2));
        // must remain false to be able to talk to smbV1 only
        prop.put("jcifs.smb.client.useSMB2Negotiation", "false");
        prop.put("jcifs.smb.client.disableSMB1", "false");
        // resolve in this order to avoid netbios name being also a foreign DNS entry resulting in bad resolution
        // do not change resolveOrder for now
        prop.put("jcifs.resolveOrder", "BCAST,DNS");
        // get around https://github.com/AgNO3/jcifs-ng/issues/40
        prop.put("jcifs.smb.client.ipcSigningEnforced", "false");
        // allow plaintext password fallback
        prop.put("jcifs.smb.client.disablePlainTextPasswords", "false");
        // disable dfs makes win10 shares with ms account work
        prop.put("jcifs.smb.client.dfs.disabled", "true");
        // make Guest work on Win10 https://github.com/AgNO3/jcifs-ng/issues/186
        prop.put("jcifs.smb.client.disableSpnegoIntegrity", "false");

        PropertyConfiguration propertyConfiguration = null;
        try {
            propertyConfiguration = new PropertyConfiguration(prop);
        } catch (CIFSException e) {
            //Log.e(TAG, "CIFSException: ", e);
            Log.d(TAG, "CIFSException caught PropertyConfiguration");
        }
        return new BaseContext(propertyConfiguration);
    }

    private static CIFSContext createContextOnly(boolean isSmb2) {
        prop = new Properties();
        prop.putAll(System.getProperties());

        if (isSmb2) {
            prop.put("jcifs.smb.client.disableSMB1", "true");
            prop.put("jcifs.smb.client.enableSMB2", "true");
            // note that connectivity with smbV1 will not be working
            prop.put("jcifs.smb.client.useSMB2Negotiation", "true");
        } else {
            prop.put("jcifs.smb.client.disableSMB1", "false");
            prop.put("jcifs.smb.client.enableSMB2", "false");
            prop.put("jcifs.smb.client.useSMB2Negotiation", "false");
        }

        // resolve in this order to avoid netbios name being also a foreign DNS entry resulting in bad resolution
        // do not change resolveOrder for now
        prop.put("jcifs.resolveOrder", "BCAST,DNS");

        // get around https://github.com/AgNO3/jcifs-ng/issues/40
        prop.put("jcifs.smb.client.ipcSigningEnforced", "false");
        // allow plaintext password fallback
        prop.put("jcifs.smb.client.disablePlainTextPasswords", "false");
        // disable dfs makes win10 shares with ms account work
        prop.put("jcifs.smb.client.dfs.disabled", "true");
        // make Guest work on Win10 https://github.com/AgNO3/jcifs-ng/issues/186
        prop.put("jcifs.smb.client.disableSpnegoIntegrity", "false");

        PropertyConfiguration propertyConfiguration = null;
        try {
            propertyConfiguration = new PropertyConfiguration(prop);
        } catch (CIFSException e) {
            //Log.e(TAG, "CIFSException: ", e);
            Log.d(TAG, "CIFSException caught PropertyConfiguration");
        }
        return new BaseContext(propertyConfiguration);
    }

    public static CIFSContext getBaseContext(boolean isSmb2) {
        return isSmb2 ? baseContextSmb2 : baseContextSmb1;
    }

    public static CIFSContext getBaseContextOnly(boolean isSmb2) {
        return isSmb2 ? baseContextSmb2Only : baseContextSmb1Only;
    }

    private static HashMap<String, Boolean> ListServers = new HashMap<>();

    public static void declareServerSmbV2(String server, boolean isSmbV2) {
        if (DBG) Log.d(TAG, "declareServerSmbV2 for " + server + " " + isSmbV2);
        ListServers.put(server, isSmbV2);
    }

    // isServerSmbV2 returns true/false/null, null is do not know
    public static Boolean isServerSmbV2(String server) throws MalformedURLException {
        Boolean isSmbV2 = ListServers.get(server);
        if (DBG) Log.d(TAG, "isServerSmbV2 for " + server + " " + isSmbV2);
        if (isSmbV2 == null) { // let's probe server root
            Uri uri = Uri.parse("smb://" + server + "/");
            NetworkCredentialsDatabase.Credential cred = NetworkCredentialsDatabase.getInstance().getCredential(uri.toString());
            NtlmPasswordAuthenticator auth = null;
            if (cred != null)
                auth = new NtlmPasswordAuthenticator("", cred.getUsername(), cred.getPassword());
            else auth = new NtlmPasswordAuthenticator("", "GUEST", "");
            CIFSContext context = null;
            SmbFile smbFile = null;
            try {
                if (DBG) Log.d(TAG, "isServerSmbV2: probing " + uri + " to check if smbV2");
                context = getBaseContextOnly(true);
                smbFile = new SmbFile(uri.toString(), context.withCredentials(auth));
                smbFile.list(); // getType is pure smbV1, only list provides a result
                declareServerSmbV2(server, true);
                return true;
            } catch (SmbAuthException authE) {
                if (DBG) Log.d(TAG, "isServerSmbV2: caught SmbAutException in probing");
                return null;
            } catch (SmbException smbE) {
                if (DBG) Log.d(TAG, "isServerSmbV2: caught SmbException " + smbE);
                try {
                    if (DBG) Log.d(TAG, "isServerSmbV2: it is not smbV2 probing " + uri + " to check if smbV1");
                    context = getBaseContextOnly(false);
                    smbFile = new SmbFile(uri.toString(), context.withCredentials(auth));
                    smbFile.list(); // getType is pure smbV1, only list provides a result
                    declareServerSmbV2(server, false);
                    return false;
                } catch (SmbException ce2) {
                    if (DBG) Log.d(TAG, "isServerSmbV2: caught SmbAutException in probing");
                    return null;
                }
            }
        } else
            return isSmbV2;
    }

    public static SmbFile getSmbFile(Uri uri) throws MalformedURLException {
        if (isSMBv2Enabled() && LIMIT_PROTOCOL_NEGO)
            return getSmbFileStrictNego(uri);
        else
            return getSmbFileAllProtocols(uri, isSMBv2Enabled());
    }

    public static SmbFile getSmbFileStrictNego(Uri uri) throws MalformedURLException {
        NetworkCredentialsDatabase.Credential cred = NetworkCredentialsDatabase.getInstance().getCredential(uri.toString());
        NtlmPasswordAuthenticator auth = null;
        if (cred != null)
            auth = new NtlmPasswordAuthenticator("", cred.getUsername(), cred.getPassword());
        else
            auth = new NtlmPasswordAuthenticator("","GUEST", "");
        Boolean isSmbV2 = isServerSmbV2(uri.getHost());
        CIFSContext context = null;
        if (isSmbV2 == null) { // server type not identified, default to smbV2
            context = getBaseContext(true);
            if (DBG) Log.d(TAG, "getSmbFile: server NOT identified passing smbv2/smbv1 capable context for uri " + uri);
        } else if (isSmbV2) { // provide smbV2 only
            context = getBaseContextOnly(true);
            if (DBG) Log.d(TAG, "getSmbFile: server already identified as smbv2 processing uri " + uri);
        } else { // if dont't know (null) or smbV2 provide smbV2 only to try out. Fallback needs to be implemented in each calls
            context = getBaseContextOnly(false);
            if (DBG) Log.d(TAG, "getSmbFile: server already identified as smbv1 processing uri " + uri);
        }
        return new SmbFile(uri.toString(), context.withCredentials(auth));
    }

    public static SmbFile getSmbFileAllProtocols(Uri uri, Boolean isSMBv2) throws MalformedURLException {
        NetworkCredentialsDatabase.Credential cred = NetworkCredentialsDatabase.getInstance().getCredential(uri.toString());
        NtlmPasswordAuthenticator auth = null;
        if (cred != null)
            auth = new NtlmPasswordAuthenticator("", cred.getUsername(), cred.getPassword());
        else
            auth = new NtlmPasswordAuthenticator("","GUEST", "");
        CIFSContext context = getBaseContext(isSMBv2);
        return new SmbFile(uri.toString(), context.withCredentials(auth));
    }

    public static boolean isSMBv2Enabled() {
        if (DBG) Log.d(TAG, "isSMBv2Enabled=" + PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("pref_smbv2", false));
        return PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("pref_smbv2", false);
    }

}
