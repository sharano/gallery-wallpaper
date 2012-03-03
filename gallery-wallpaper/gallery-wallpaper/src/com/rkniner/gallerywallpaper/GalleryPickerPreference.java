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

import java.util.ArrayList;
import java.util.Iterator;

import android.content.Context;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.os.Handler;
import android.preference.DialogPreference;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;

public class GalleryPickerPreference extends DialogPreference {
	
	public static final String[] MODES = new String[] { "images", "dirs" };
	public static final int MODE_IMAGES = 0;
	public static final int MODE_DIRS = 1;
	public static final int DEFAULT_MODE = 0;
	public static final String MODE_ATTRIBUTE = "pickerMode";
	
	private int mode = 0;
	
	private ArrayList<CheckBox> checkboxes = new ArrayList<CheckBox>();
	
	private Handler handler = new Handler();
	
	class ThumbnailLoader implements Runnable {
		private ImageView iv;
		private long id;
		
		public ThumbnailLoader(ImageView image, long image_id) {
			iv = image;
			id = image_id;
		}
		
		@Override
		public void run() {
			iv.setImageBitmap(MediaStore.Images.Thumbnails.getThumbnail(
					GalleryPickerPreference.this.getContext().getContentResolver(), id,
					MediaStore.Images.Thumbnails.MICRO_KIND, null));
		}
	}

	class ViewLoader implements Runnable {
		private TableLayout t;
		private Context c;
		private Handler h;
		
		public ViewLoader(TableLayout view, Context context, Handler handler) {
			t = view;
			c = context;
			h = handler;
		}
		
		private TableRow newEntry(String value, String[] valuesSelected, String id) {
			
			TableRow result = new TableRow(c);
			ImageView image = new ImageView(c);
			ThumbnailLoader thread = new ThumbnailLoader(image, Long.parseLong(id));
			h.post(thread);
			result.addView(image);
			
			CheckBox check = new CheckBox(getContext());
			check.setText(value);
			check.setChecked(false);
			for (int i = 0; i < valuesSelected.length; i++) {
				if (valuesSelected[i].equals(value)) {
					check.setChecked(true);
					break;
				}
			}
			TableRow.LayoutParams layout = new TableRow.LayoutParams(1);
			layout.weight = 1f;
			check.setLayoutParams(layout);
			result.addView(check);
			GalleryPickerPreference.this.checkboxes.add(check);
			
			return result;
		}
		
		@Override
		public void run() {
			ArrayList<TableRow> boxes = new ArrayList<TableRow>();
			
			String[] projection = new String[] {
					MediaStore.Images.ImageColumns._ID,
					MediaStore.Images.ImageColumns.DATA
			};
			Cursor[] cursors = new Cursor[2];
			cursors[0] = c.getContentResolver().query(
					MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
					projection,
					"", (String[])null, MediaStore.Images.ImageColumns.DATA + " ASC");
			cursors[1] = c.getContentResolver().query(
					MediaStore.Images.Media.INTERNAL_CONTENT_URI,
					projection,
					"", (String[])null, MediaStore.Images.ImageColumns.DATA + " ASC");
			switch (mode) {
			case MODE_IMAGES:
				String images = GalleryPickerPreference.this.getPreferenceManager().getSharedPreferences()
					.getString(
						GalleryWallpaper.SHARED_PREFS_IMAGELIST,
						GalleryWallpaper.SHARED_PREFS_IMAGELIST_DEFAULT);
				String[] imagelistarr = GalleryWallpaper.getCSVRow(images);
				for (int i = 0; i < cursors.length; i++) {
					while (cursors[i].moveToNext()) {
						String file = cursors[i].getString(
								cursors[i].getColumnIndex(MediaStore.Images.ImageColumns.DATA));
						String id = cursors[i].getString(
								cursors[i].getColumnIndex(MediaStore.Images.ImageColumns._ID));
						boxes.add(newEntry(file, imagelistarr, id));
					}
				}
				break;
			case MODE_DIRS:
				String dirs = GalleryPickerPreference.this.getPreferenceManager().getSharedPreferences().getString(
						GalleryWallpaper.SHARED_PREFS_IMAGEDIRS,
						GalleryWallpaper.SHARED_PREFS_IMAGEDIRS_DEFAULT);
				String[] imagedirsarr =  GalleryWallpaper.getCSVRow(dirs);
				String lastDir = "";
				for (int i = 0; i < cursors.length; i++) {
					while (cursors[i].moveToNext()) {
						String dir = cursors[i].getString(
								cursors[i].getColumnIndex(MediaStore.Images.ImageColumns.DATA))
								.replaceFirst("(?<!\\\\)/[^/]+$", "");
						String id = cursors[i].getString(
								cursors[i].getColumnIndex(MediaStore.Images.ImageColumns._ID));
						if (!dir.equals(lastDir)) {
							boxes.add(newEntry(dir, imagedirsarr, id));
							lastDir = dir;
						}
					}
				}
				break;
			}
			cursors[0].close();
			cursors[1].close();
			Iterator<TableRow> boxI = boxes.iterator();
			while (boxI.hasNext()) {
				TableRow r = boxI.next();
				t.addView(r);
			}
		}
	}

	public GalleryPickerPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.mode = attrs.getAttributeListValue("http://schemas.android.com/apk/res/com.rkniner.gallerywall", MODE_ATTRIBUTE, MODES, DEFAULT_MODE);
		this.setDialogLayoutResource(R.layout.picker);
	}

	public GalleryPickerPreference(Context context, AttributeSet attrs, int style) {
		super(context, attrs, style);
		this.mode = attrs.getAttributeListValue("http://schemas.android.com/apk/res/com.rkniner.gallerywall", MODE_ATTRIBUTE, MODES, DEFAULT_MODE);
		this.setDialogLayoutResource(R.layout.picker);
	}

	
	@Override
	public void onBindDialogView(View v) {
		TableLayout t = (TableLayout)v.findViewById(R.id.table);
		ViewLoader vl = new ViewLoader(t, this.getContext(), this.handler);
		handler.post(vl);
	}
	
	@Override
	protected void onDialogClosed(boolean positiveResult) {
		if (positiveResult) {
			ArrayList<String> paths = new ArrayList<String>();
			Iterator<CheckBox> i = checkboxes.iterator();
			while (i.hasNext()) {
				CheckBox c = i.next();
				if (c.isChecked())
					paths.add(c.getText().toString());
			}
			String[] patharr = new String[paths.size()];
			String result = GalleryWallpaper.getCSVRow(paths.toArray(patharr));
			Editor edit = getPreferenceManager().getSharedPreferences().edit();
			edit.putString(this.getKey(), result);
			edit.commit();
		}
	}

}
