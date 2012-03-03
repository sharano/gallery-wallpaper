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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;

public class MediaStoreImageGrabber implements ImageGrabber {
	
	private Cursor cursorExternal;
	private Cursor cursorInternal;
	private Uri[] sources;
	private Uri[] images;
	private boolean useSources;
	private Context context;
	
	public MediaStoreImageGrabber(Context c) {
		this.setContext(c);
	}
	
	public void setContext(Context c) {
		this.context = c;
	}
	
	private void rebuildQuery() {
		if (this.useSources) {
			StringBuilder whereClause = new StringBuilder();
			if (sources != null && sources.length > 0) {
				whereClause = whereClause.append(MediaStore.Images.ImageColumns.DATA)
					.append(" LIKE '").append(
								sources[0].toString().replace("\\", "\\\\")
								.replace("%", "\\%").replace("_", "\\_"))
					.append("%' ESCAPE '\\'");
				for (int i = 0; i < sources.length; i++) {
					whereClause = whereClause.append(" OR ").append(MediaStore.Images.ImageColumns.DATA)
					.append(" LIKE '").append(
								sources[0].toString().replace("\\", "\\\\")
								.replace("%", "\\%").replace("_", "\\_"))
					.append("%' ESCAPE '\\'");
					
				}
			}
			cursorExternal = context.getContentResolver().query(
					MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
					new String[] {MediaStore.Images.ImageColumns._ID},
					whereClause.toString(),
					(String[])null,
					MediaStore.Images.ImageColumns._ID + " ASC");
			cursorInternal = context.getContentResolver().query(
					MediaStore.Images.Media.INTERNAL_CONTENT_URI,
					new String[] {MediaStore.Images.ImageColumns._ID},
					whereClause.toString(),
					(String[])null,
					MediaStore.Images.ImageColumns._ID + " ASC");
		} else {
			cursorExternal = null;
			cursorInternal = null;
		}
	}
	
	@Override
	public Bitmap getNextImage() {
		Bitmap result = null;
		if (this.useSources) {
			for (int i = 0; i < 3; i++)
				try {
					if (this.cursorExternal != null && this.cursorInternal != null) {
						int nextIndex = (int)(Math.random()
								* (this.cursorExternal.getCount() + this.cursorInternal.getCount()));
						if (nextIndex >= this.cursorExternal.getCount()) {
							nextIndex -= this.cursorExternal.getCount();
							this.cursorInternal.moveToFirst();
							this.cursorInternal.move(nextIndex);
							Uri uri = MediaStore.Images.Media.INTERNAL_CONTENT_URI
									.buildUpon().appendPath(this.cursorInternal.getString(
									this.cursorInternal.getColumnIndex(MediaStore.Images.ImageColumns._ID)))
									.build();
							result = MediaStore.Images.Media.getBitmap(
									this.context.getContentResolver(),
									uri);
						} else {
							this.cursorExternal.moveToFirst();
							this.cursorExternal.move(nextIndex);
							Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
									.buildUpon().appendPath(this.cursorExternal.getString(
									this.cursorExternal.getColumnIndex(MediaStore.Images.ImageColumns._ID)))
									.build();
							result = MediaStore.Images.Media.getBitmap(
									this.context.getContentResolver(),
									uri);
						}
						if (result == null) continue;
					}
					break;
				} catch (IOException e) {
					continue;
				}
		} else {
			for (int i = 0; i < 3; i++) {
				int nextIndex = (int)(Math.random() * this.images.length);
				try {
					InputStream is = context.getContentResolver().openInputStream(this.images[nextIndex]);
					result = BitmapFactory.decodeStream(is);
				} catch (FileNotFoundException e) {
					try {
						result = BitmapFactory.decodeFile(this.images[nextIndex].toString());
					} catch (Exception f) {
						continue;
					}
				}
				if (result == null) continue;
				break;
			}
		}
		if (result == null) {
			result = BitmapFactory.decodeResource(context.getResources(), R.drawable.default_wallpaper);
		}
		return result;
	}

	@Override
	public void refreshImageList() {
		if (cursorExternal != null) cursorExternal.close();
		if (cursorInternal != null) cursorInternal.close();
		rebuildQuery();
	}

	@Override
	public boolean replaceImageList(Uri... images) {
		ArrayList<Uri> newImages = new ArrayList<Uri>();
		for (int i = 0; i < images.length; i++) {
			try {
				newImages.add(images[i]);
			} catch (Exception e) {
				return false;
			}
		}
		synchronized (this) {
			this.images = new Uri[newImages.size()];
			this.images = newImages.toArray(this.images);
			this.sources = null;
			this.useSources = false;
		}
		this.refreshImageList();
		return true;
	}

	@Override
	public boolean replaceImageSources(Uri... imagesources) {
		ArrayList<Uri> newSources = new ArrayList<Uri>();
		for (int i = 0; i < imagesources.length; i++) {
			try {
				newSources.add(imagesources[i]);
			} catch (Exception e) {
				return false;
			}
		}
		synchronized (this) {
			this.sources = new Uri[newSources.size()];
			this.sources = newSources.toArray(this.sources);
			this.images = null;
			this.useSources = true;
		}
		this.refreshImageList();
		return true;
	}

}
