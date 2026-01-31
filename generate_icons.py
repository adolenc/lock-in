#!/usr/bin/env python3
"""
Generate Android app icons from icon.png

Usage: python generate_icons.py [path_to_icon.png]

If no path is provided, defaults to icon.png in the same directory.
Requires Pillow: pip install Pillow
"""

import os
import sys
from PIL import Image

def generate_icons(icon_path):
    script_dir = os.path.dirname(os.path.abspath(__file__))
    res_path = os.path.join(script_dir, "app/src/main/res")
    
    if not os.path.exists(icon_path):
        print(f"Error: Icon file not found: {icon_path}")
        sys.exit(1)
    
    # Standard launcher icon sizes
    launcher_sizes = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    
    # Adaptive icon foreground sizes (108dp base)
    foreground_sizes = {
        "mipmap-mdpi": 108,
        "mipmap-hdpi": 162,
        "mipmap-xhdpi": 216,
        "mipmap-xxhdpi": 324,
        "mipmap-xxxhdpi": 432,
    }
    
    img = Image.open(icon_path)
    if img.mode != 'RGBA':
        img = img.convert('RGBA')
    
    print(f"Generating icons from {icon_path} ({img.size[0]}x{img.size[1]})")
    
    # Generate standard launcher icons
    for folder, size in launcher_sizes.items():
        folder_path = os.path.join(res_path, folder)
        os.makedirs(folder_path, exist_ok=True)
        
        resized = img.resize((size, size), Image.LANCZOS)
        
        # Save launcher icon
        output_path = os.path.join(folder_path, "ic_launcher.png")
        resized.save(output_path, "PNG")
        
        # Save round icon (same for legacy)
        round_path = os.path.join(folder_path, "ic_launcher_round.png")
        resized.save(round_path, "PNG")
        
        print(f"  {folder}: {size}x{size} (launcher + round)")
    
    # Generate adaptive icon foregrounds
    for folder, size in foreground_sizes.items():
        folder_path = os.path.join(res_path, folder)
        resized = img.resize((size, size), Image.LANCZOS)
        output_path = os.path.join(folder_path, "ic_launcher_foreground.png")
        resized.save(output_path, "PNG")
        print(f"  {folder}: {size}x{size} (foreground)")
    
    # Create adaptive icon XML files
    anydpi_path = os.path.join(res_path, "mipmap-anydpi-v26")
    os.makedirs(anydpi_path, exist_ok=True)
    
    adaptive_xml = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
'''
    
    with open(os.path.join(anydpi_path, "ic_launcher.xml"), "w") as f:
        f.write(adaptive_xml)
    with open(os.path.join(anydpi_path, "ic_launcher_round.xml"), "w") as f:
        f.write(adaptive_xml)
    
    print(f"  mipmap-anydpi-v26: adaptive icon XML")
    print("\nDone! Don't forget to set ic_launcher_background color in res/values/colors.xml")

if __name__ == "__main__":
    if len(sys.argv) > 1:
        icon_path = sys.argv[1]
    else:
        script_dir = os.path.dirname(os.path.abspath(__file__))
        icon_path = os.path.join(script_dir, "icon.png")
    
    generate_icons(icon_path)
