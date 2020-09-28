// Copyright 2017 Archos SA
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

import android.net.Uri;
import android.util.Log;

import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.RawLister;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

import static com.archos.filecorelibrary.jcifs.JcifsUtils.getSmbFile;


/**
 * returns 
 * @author alexandre
 *
 */
public class JcifsRawLister extends RawLister {

    private final static String TAG = "JcifsRawLister";
    private final static boolean DBG = false;

    public JcifsRawLister(Uri uri) {
        super(uri);
    }

    public List<MetaFile2> getFileList() throws SmbException, MalformedURLException{
        SmbFile[] listFiles = getSmbFile(mUri).listFiles();
        if(listFiles!=null){
            ArrayList<MetaFile2> files = new ArrayList<>();
            for(SmbFile f : listFiles){
                // better verify that it is a file or directory before adding
                if(f.isFile() || f.isDirectory()) {
                    if (DBG) Log.d(TAG, " found " + f.getPath());
                    files.add(new JcifsFile2(f));
                }
            }
            return files;
        }
        return null;
    }
}