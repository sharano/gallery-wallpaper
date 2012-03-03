/*
 * 	Copyright (c) 2012 Richard K Niner
 *	
 *	This file is part of Gallery Wallpaper.
 *	
 *	Gallery Wallpaper is a free software: you can redistribute it and/or
 *	modify it under the terms of the GNU General Public License as
 *	published by the Free Software Foundation, either version 2 of the
 *	License, or (at your option) any later version.
 *	
 *	This program is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *	GNU General Public License for more details.
 *	
 *	You should have received a copy of the GNU General Public License
 *	along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.rkniner.gallerywallpaper;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.preference.PreferenceActivity;

public class GalleryWallpaperSettings extends PreferenceActivity
	implements SharedPreferences.OnSharedPreferenceChangeListener {
	
	private DialogPreference imageListScreen;
	private DialogPreference imageDirsScreen;
	
	@Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        getPreferenceManager().setSharedPreferencesName(
                GalleryWallpaper.SHARED_PREFS_NAME);
        addPreferencesFromResource(R.xml.gallerywallpaper_settings);
        this.imageListScreen =
        		(DialogPreference)getPreferenceScreen().findPreference(
        				GalleryWallpaper.SHARED_PREFS_IMAGELIST);
        this.imageDirsScreen =
        		(DialogPreference)getPreferenceScreen().findPreference(
        				GalleryWallpaper.SHARED_PREFS_IMAGEDIRS);
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(
                this);
        this.onSharedPreferenceChanged(getPreferenceManager().getSharedPreferences(), null);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(
                this);
        super.onDestroy();
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
    	String imageSearchPref = sharedPreferences.getString(
    			GalleryWallpaper.SHARED_PREFS_IMAGESEARCH,
    			GalleryWallpaper.SHARED_PREFS_IMAGESEARCH_DEFAULT); 
    	if (imageSearchPref.equals(GalleryWallpaper.SHARED_PREFS_IMAGESEARCH_LIST)) {
    		this.imageDirsScreen.setEnabled(false);
    		this.imageListScreen.setEnabled(true);
       	} else if (imageSearchPref.equals(GalleryWallpaper.SHARED_PREFS_IMAGESEARCH_MEDIASTORE)) {
    		this.imageDirsScreen.setEnabled(true);
    		this.imageListScreen.setEnabled(false);
    	}
    }

}
