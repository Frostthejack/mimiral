from PIL import Image
import os

for density in ['mdpi', 'hdpi', 'xhdpi', 'xxhdpi', 'xxxhdpi']:
    path = f'app/src/main/res/mipmap-{density}/ic_launcher.png'
    if os.path.exists(path):
        img = Image.open(path)
        print(f'{density}: {img.size} mode={img.mode} file_size={os.path.getsize(path)}b')
