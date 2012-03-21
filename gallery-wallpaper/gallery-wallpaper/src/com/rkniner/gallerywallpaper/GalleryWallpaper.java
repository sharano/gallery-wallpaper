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

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

public class GalleryWallpaper extends WallpaperService {

	public static final String SHARED_PREFS_NAME = "GalleryWall";
	
	public static final String SHARED_PREFS_IMAGESEARCH = "imageSearch";
	public static final String SHARED_PREFS_IMAGESEARCH_DEFAULT = "imageDirs";
	public static final String SHARED_PREFS_IMAGESEARCH_LIST = "imageList";
	public static final String SHARED_PREFS_IMAGESEARCH_MEDIASTORE = "imageDirs";

	public static final String SHARED_PREFS_IMAGELIST = "imageList";
	public static final String SHARED_PREFS_IMAGELIST_DEFAULT = "";
	public static final String SHARED_PREFS_IMAGEDIRS = "imageDirs";
	public static final String SHARED_PREFS_IMAGEDIRS_DEFAULT = "";

	public static final String SHARED_PREFS_REPLACEIMAGE = "replaceImage";
	public static final String SHARED_PREFS_REPLACEIMAGE_DEFAULT = "300000000";
	public static final String SHARED_PREFS_SYNC = "sync";
	public static final String SHARED_PREFS_SYNC_DEFAULT = "10800000000";
	
	@Override
	public WallpaperService.Engine onCreateEngine() {
		return new GalleryWallpaper.Engine();
	}
	
	protected static String[] getCSVRow(String s) {
		ArrayList<String> records = new ArrayList<String>();
		String[] result;
		String record;
		int start = 0;
		boolean escaped = false;
		for (int i = 0; i < s.length(); i++) {
			switch (s.charAt(i)) {
			case '"':
				escaped = !escaped;
				break;
			case ',':
				if (!escaped) {
					record = s.substring(start, i);
					start = i + 1;
					records.add(record);
				}
			}
		}
		record = s.substring(start, s.length());
		records.add(record);
		
		for (int i = 0; i < records.size(); i++) {
			while (i < records.size() && records.get(i).equals("")) {
				records.remove(i);
			}
			if (i == records.size()) break;
			String j = records.get(i); 
			if (j.matches("^\".*\"$"))
				records.set(i, j.substring(1, j.length() - 1));
			records.set(i, records.get(i).replaceAll("\"\"", "\""));
		}
		result = new String[records.size()];
		return records.toArray(result);
	}

	protected static String getCSVRow(String... s) {
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < s.length; i++) {
			String str = s[i];
			if (str.indexOf('"') != -1) {
				str = "\"" + str.replaceAll("\"", "\"\"") + "\"";
			}
			if (i > 0)
				result.append(',');
			result.append(str);
		}
		return result.toString();
	}
	
	private class Engine extends WallpaperService.Engine
			implements SharedPreferences.OnSharedPreferenceChangeListener {
		
		private long refreshDelay = 300000;
		private long resyncDelay = 10800000;
		private boolean replaceRequired = true;
		private boolean redrawRequired = true;
		private boolean syncRequired = true;
		private boolean changedSearch = false;
		private SharedPreferences prefs;
		private Bitmap currentImage;
		private ImageGrabber grabber;
		
		private int xSteps = 1;
		private int ySteps = 1;
		private float xPos = 0f;
		private float yPos = 0f;
		private String errorMsg = "";
		private int displayWidth = 0;
		private int displayHeight = 0;
		private boolean slideOpposite = false;
		
		private Paint backgroundPaint = new Paint();
		private Paint textPaint = new Paint();
		
		private Runnable timerRefresh = new Runnable() {
			public void run() {
				synchronized(Engine.this) {
					Engine.this.replaceRequired = true;
					if (Engine.this.isVisible()) 
						Engine.this.handler.postDelayed(this, Engine.this.refreshDelay);
				}
				Engine.this.redrawImage();
			}
		};
		private Runnable timerGetImages = new Runnable() {
			public void run() {
				synchronized(Engine.this) {
					Engine.this.syncRequired = true;
					Engine.this.handler.postDelayed(this, Engine.this.resyncDelay);
				}
				if (Engine.this.isVisible()) Engine.this.recreateImageList(false);
			}
		};
		private Handler handler = new Handler();
		
		class DoubleTapListener extends GestureDetector.SimpleOnGestureListener {
			@Override
            public boolean onDoubleTap(MotionEvent e) {
				Engine.this.replaceRequired = true;
				redrawImage();
                return true;
            }
            public boolean onDown(MotionEvent e) {
                return true;
            }
		}
		
		private GestureDetector gestureDetector = new GestureDetector(
				GalleryWallpaper.this,
				new DoubleTapListener());
		
		Engine() {
			super();
			prefs = GalleryWallpaper.this.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
			prefs.registerOnSharedPreferenceChangeListener(this);
			this.onSharedPreferenceChanged(prefs, GalleryWallpaper.SHARED_PREFS_REPLACEIMAGE);
			this.onSharedPreferenceChanged(prefs, GalleryWallpaper.SHARED_PREFS_SYNC);
            onSharedPreferenceChanged(prefs, null);
            backgroundPaint.setFilterBitmap(false);
            backgroundPaint.setAntiAlias(true);
            backgroundPaint.setColor(Color.BLACK);
            textPaint.setAntiAlias(true);
            textPaint.setColor(Color.WHITE);
            textPaint.setShadowLayer(5f, 1f, 1f, Color.GRAY);
            textPaint.setSubpixelText(true);
            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setTextSize(18);
            textPaint.setTypeface(Typeface.MONOSPACE);
            this.setTouchEventsEnabled(true);
            if (Build.VERSION.SDK_INT >= 14) {
            	try {
            		// hinting = textPaint.HINTING_ON;
            		int hinting = textPaint.getClass().getField("HINTING_ON").getInt(textPaint);
            		// textPaint.setHinting(hinting);
            		textPaint.getClass().getMethod("setHinting", int.class).invoke(textPaint, hinting);
            	} catch (IllegalAccessException e) {
            		System.err.println(e.toString());
            	} catch (InvocationTargetException e) {
            		System.err.println(e.toString());
            	} catch (NoSuchMethodException e) {
            		System.err.println(e.toString());
            	} catch (NoSuchFieldException e) {
            		System.err.println(e.toString());
            	}
            }
		}
		
		private void recreateImageList(boolean reparse) {
			if (grabber == null) {
				grabber = new MediaStoreImageGrabber(GalleryWallpaper.this);
				reparse = true;
			}
			if (reparse) {
				String imageSearchPref = prefs.getString(
						GalleryWallpaper.SHARED_PREFS_IMAGESEARCH,
						GalleryWallpaper.SHARED_PREFS_IMAGESEARCH_DEFAULT);
				if (imageSearchPref.equals(GalleryWallpaper.SHARED_PREFS_IMAGESEARCH_LIST)) {
					String imagelist = prefs.getString(
							GalleryWallpaper.SHARED_PREFS_IMAGELIST,
							GalleryWallpaper.SHARED_PREFS_IMAGELIST_DEFAULT);
					if (imagelist.equals("")) {
						grabber.replaceImageList(new Uri[] {Uri.EMPTY});
					} else {
						ArrayList<Uri> uris = new ArrayList<Uri>();
						String[] imagelistarr = GalleryWallpaper.getCSVRow(imagelist);
						for (int i = 0; i < imagelistarr.length; i++) {
							String thisimg = imagelistarr[i];
							Uri uri = Uri.parse(thisimg);
							uris.add(uri);
						}
						Uri[] uriArray = new Uri[uris.size()];
						uris.toArray(uriArray);
						grabber.replaceImageList(uriArray);
					}
				} else if (imageSearchPref.equals(GalleryWallpaper.SHARED_PREFS_IMAGESEARCH_MEDIASTORE)) {
					String searchlist = prefs.getString(
							GalleryWallpaper.SHARED_PREFS_IMAGEDIRS,
							GalleryWallpaper.SHARED_PREFS_IMAGEDIRS_DEFAULT);
					if (searchlist.equals("")) {
						grabber.replaceImageSources(new Uri[] {Uri.EMPTY});
					} else {
						ArrayList<Uri> uris = new ArrayList<Uri>();
						String[] searchlistarr = GalleryWallpaper.getCSVRow(searchlist);
						for (int i = 0; i < searchlistarr.length; i++) {
							Uri uri = Uri.parse(searchlistarr[i]);
							uris.add(uri);
						}
						Uri[] uriArray = new Uri[uris.size()];
						uris.toArray(uriArray);
						grabber.replaceImageSources(uriArray);
					}
				}
				this.changedSearch = false;
			}
			grabber.refreshImageList();
			this.syncRequired = false;
		}
		
		
		private void replaceImage() {
			Bitmap nextImage = grabber.getNextImage();
			if (nextImage != null) {
				this.errorMsg = "";
				currentImage = nextImage;
			} else if (this.currentImage == null) {
				this.errorMsg = "Could not load image";
			}
			this.replaceRequired = false;
			this.redrawRequired = true;
			rescaleImage();
			handler.postDelayed(timerRefresh, refreshDelay);
		}
		
		private void rescaleImage() {
			int dh = this.displayHeight;
            int dw = this.displayWidth;
			float imgAspect = (float)(this.currentImage.getWidth())
					/ (float)(this.currentImage.getHeight());
			float screenAspect = (float)dw / (float)dh;
			if (this.currentImage == null) {
				int[] colors = {Color.BLACK};
				Bitmap errImage = Bitmap.createBitmap(colors, 1, 1, Bitmap.Config.RGB_565);
				this.currentImage =  Bitmap.createScaledBitmap(errImage, dw, dh, true);
			}
			this.slideOpposite = false;
			if (this.xSteps == 1 && this.ySteps == 1) {
				int scaleHeight = dh;
				int scaleWidth = dw;
				if (imgAspect > screenAspect) {
					scaleHeight = Math.round((float)scaleWidth / imgAspect); 
				} else {
					scaleWidth = Math.round((float)scaleHeight * imgAspect);
				}

				this.currentImage = Bitmap.createScaledBitmap(this.currentImage,
						scaleWidth, scaleHeight, true);
			} else if (this.ySteps == 1) {
				if (imgAspect < screenAspect) {
					int scaleWidth = dw;
					int scaleHeight = Math.round((float)scaleWidth / imgAspect);
					this.currentImage = Bitmap.createScaledBitmap(this.currentImage,
							scaleWidth, scaleHeight, true);
					this.slideOpposite = true;
				} else {
					int scaleHeight = dh;
					int scaleWidth = Math.round((float)scaleHeight * imgAspect);
					this.currentImage = Bitmap.createScaledBitmap(this.currentImage,
							scaleWidth, scaleHeight, true);
				}
			} else if (this.xSteps == 1) {
				if (imgAspect > screenAspect) {
					int scaleHeight = dh;
					int scaleWidth = Math.round((float)scaleHeight * imgAspect);
					this.currentImage = Bitmap.createScaledBitmap(this.currentImage,
							scaleWidth, scaleHeight, true);
					this.slideOpposite = true;
				} else {
					int scaleWidth = dw;
					int scaleHeight = Math.round((float)scaleWidth / imgAspect);
					this.currentImage = Bitmap.createScaledBitmap(this.currentImage,
							scaleWidth, scaleHeight, true);
				}
			} else {
				int scaleHeight = dh;
				int scaleWidth = dw;
				if (imgAspect < screenAspect) {
					scaleHeight = Math.round((float)scaleWidth / imgAspect); 
				} else {
					scaleWidth = Math.round((float)scaleHeight * imgAspect);
				}
				this.currentImage = Bitmap.createScaledBitmap(this.currentImage,
						scaleWidth, scaleHeight, true);
			}
		}
		
		private void drawFrame() {
			final SurfaceHolder holder = getSurfaceHolder();
            Canvas c = null;
            int dh = this.displayHeight;
            int dw = this.displayWidth;
            try {
                c = holder.lockCanvas();
                if (c != null) {
            		Rect dst = new Rect(0, 0, this.displayWidth, this.displayHeight);
            		c.drawRect(dst, this.backgroundPaint);
                	if (this.xSteps == 1 && this.ySteps == 1) {
                		int l = 0; 
                		int t = 0;
                		int r = this.currentImage.getWidth(); 
                		int b = this.currentImage.getHeight();
                		Rect src = new Rect(l, t, r, b);
                		dst = new Rect(
                				(dst.right - dst.left - r) / 2,
                				(dst.bottom - dst.top - b) / 2,
                				(dst.right - dst.left + r) / 2,
                				(dst.bottom - dst.top + b) / 2);
                		c.drawBitmap(this.currentImage, src, dst, this.backgroundPaint);
                	} else if ((this.xSteps == 1 && !this.slideOpposite)
                			|| (this.ySteps == 1 && this.slideOpposite)) {
                		dst = new Rect(
                				(dst.right - dst.left - dw) / 2,
                				(dst.bottom - dst.top - dh) / 2,
                				(dst.right - dst.left + dw) / 2,
                				(dst.bottom - dst.top + dh) / 2);
                		int l = 0; 
                		int t = this.currentImage.getHeight() - dh;
                		t = Math.round((float)t * (this.slideOpposite? this.xPos: this.yPos));
                		int r = l + this.currentImage.getWidth(); 
                		int b = t + dh;
                		Rect src = new Rect(l, t, r, b);
                		c.drawBitmap(this.currentImage, src, dst, this.backgroundPaint);
                	} else if ((this.ySteps == 1 && !this.slideOpposite)
                			|| (this.xSteps == 1 && this.slideOpposite)) {
                		dst = new Rect(
                				(dst.right - dst.left - dw) / 2,
                				(dst.bottom - dst.top - dh) / 2,
                				(dst.right - dst.left + dw) / 2,
                				(dst.bottom - dst.top + dh) / 2);
                		int l = this.currentImage.getWidth() - dw;
                		int t = 0;
                		l = Math.round((float)l * (this.slideOpposite? this.yPos: this.xPos));
                		int r = l + dw; 
                		int b = t + this.currentImage.getHeight();
                		Rect src = new Rect(l, t, r, b);
                		c.drawBitmap(this.currentImage, src, dst, this.backgroundPaint);
                	} else {
                		dst = new Rect(
                				(dst.right - dst.left - dw) / 2,
                				(dst.bottom - dst.top - dh) / 2,
                				(dst.right - dst.left + dw) / 2,
                				(dst.bottom - dst.top + dh) / 2);
                		int l = this.currentImage.getWidth() - dst.right + dst.left; 
                		int t = this.currentImage.getHeight() - dst.bottom + dst.top;
                		l = Math.round((float)l * this.xPos);
                		t = Math.round((float)t * this.yPos);
                		int r = l + dw; 
                		int b = t + dh;
                		Rect src = new Rect(l, t, r, b);
                		c.drawBitmap(this.currentImage, src, dst, this.backgroundPaint);
                	}
                	if (this.errorMsg.length() != 0) {
                		Rect textBounds = new Rect();
                		textPaint.getTextBounds(this.errorMsg, 0, this.errorMsg.length(), textBounds);
                		textBounds.top += 20;
                		textBounds.bottom += 28;
                		textBounds.right += 8;
                		c.drawRect(textBounds, this.backgroundPaint);
                		c.drawText(this.errorMsg, 4f, 4f, this.textPaint);
                	}
                }
            } catch (Exception e) {
            	this.redrawRequired = true;
            } finally {
                if (c != null) holder.unlockCanvasAndPost(c);
            }
		}
		
		private void redrawImage() {
			synchronized (this) {
				if (!this.isVisible()) {
					this.redrawRequired = true;
					return;
				}
				if (this.syncRequired) recreateImageList(this.changedSearch);
				if (this.replaceRequired) replaceImage();
				drawFrame();
			}
		}
		
		@Override
		public void onCreate (
				SurfaceHolder surfaceHolder
				) {
			super.onCreate(surfaceHolder);
			this.replaceRequired = true;
			updateSize();
		}

		@Override
		public void onDestroy() {
			super.onDestroy();
			handler.removeCallbacks(timerRefresh);
			handler.removeCallbacks(timerGetImages);
}
		
		@Override
		public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
			super.onSurfaceChanged(holder, format, width, height);
			this.replaceRequired = true;
			updateSize();
		}
		
		@Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
			super.onSurfaceDestroyed(holder);
			handler.removeCallbacks(timerRefresh);
			handler.removeCallbacks(timerGetImages);
        }

		
		@Override
		public void onDesiredSizeChanged (
				int desiredWidth,
				int desiredHeight
				) {
			super.onDesiredSizeChanged(desiredWidth, desiredHeight);
			synchronized(this) {
				updateSize();
				this.redrawRequired = true;
				this.replaceRequired = true;
			}
			redrawImage();
		}
		
		@Override
		public void onOffsetsChanged (
				float xOffset,
				float yOffset,
				float xOffsetStep,
				float yOffsetStep,
				int xPixelOffset,
				int yPixelOffset) {
			super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset);
			int xStep = 1;
			int yStep = 1;
			if (xOffsetStep > 0)
				xStep = Math.round(1f / xOffsetStep) + 1;
			if (yOffsetStep > 0)
				yStep = Math.round(1f / yOffsetStep) + 1;
			if (xOffsetStep == 1 && xOffset == 0.5)
				xStep = 1;
			if (yOffsetStep == 1 && yOffset == 0.5)
				yStep = 1;
			if (yStep != this.ySteps || xStep != this.xSteps) this.replaceRequired = true;
			synchronized (this) {
				this.xSteps = xStep;
				this.ySteps = yStep;
				this.xPos = xOffset;
				this.yPos = yOffset;
				this.redrawRequired = true;
				updateSize();
			}
			redrawImage();
		}
	
		@Override
		public void onTouchEvent(MotionEvent event) {
			gestureDetector.onTouchEvent(event);
		}
		
		@Override
		public void onVisibilityChanged(boolean visible) {
			super.onVisibilityChanged(visible);
			if (visible) updateSize();
			if (visible && this.redrawRequired) redrawImage();
		}

		@Override
		public void onSharedPreferenceChanged(SharedPreferences spref,
				String key) {
			if (key == null) return;
			if (key.equals(GalleryWallpaper.SHARED_PREFS_IMAGEDIRS)
					|| key.equals(GalleryWallpaper.SHARED_PREFS_IMAGESEARCH)
					|| key.equals(GalleryWallpaper.SHARED_PREFS_IMAGELIST)) {
				this.changedSearch = true;
			} else if (key.equals(GalleryWallpaper.SHARED_PREFS_REPLACEIMAGE)) {
				this.refreshDelay = Long.valueOf(prefs.getString(
						GalleryWallpaper.SHARED_PREFS_REPLACEIMAGE,
						GalleryWallpaper.SHARED_PREFS_REPLACEIMAGE_DEFAULT));
			} else if (key.equals(GalleryWallpaper.SHARED_PREFS_SYNC)) {
				this.resyncDelay = Long.valueOf(prefs.getString(
						GalleryWallpaper.SHARED_PREFS_SYNC,
						GalleryWallpaper.SHARED_PREFS_SYNC_DEFAULT));
			}
		}
		
		private void updateSize() {
			int dh = this.getDesiredMinimumHeight();
            int dw = this.getDesiredMinimumWidth();
           	Rect dr = this.getSurfaceHolder().getSurfaceFrame();
           	if (dr.right != 0 && dr.bottom != 0) {
	           	if (dh <= 0 || dr.bottom - dr.top < dh) dh = dr.bottom - dr.top;
	           	if (dw <= 0 || dr.right - dr.left < dw) dw = dr.right - dr.left;
	           	this.displayWidth = dw;
	           	this.displayHeight = dh;
           	} else if (this.displayWidth == 0 || this.displayHeight == 0) {
               	this.displayWidth = dw;
               	this.displayHeight = dh;
           	}
		}
	}
}
