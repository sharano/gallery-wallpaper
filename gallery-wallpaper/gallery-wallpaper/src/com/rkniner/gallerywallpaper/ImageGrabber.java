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

import android.graphics.Bitmap;
import android.net.Uri;

public interface ImageGrabber {
	public Bitmap getNextImage();
	public void refreshImageList();
	public boolean replaceImageList(Uri... images);
	public boolean replaceImageSources(Uri... imagesources);
}
