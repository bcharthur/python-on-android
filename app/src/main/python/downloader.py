import yt_dlp
import os
import ssl
import certifi

# Configurer le contexte SSL pour utiliser les certificats de certifi
ssl_context = ssl.create_default_context(cafile=certifi.where())

def get_video_info(url, thumbnail_dir):
    ydl_opts = {
        'skip_download': True,
        'writesubtitles': False,
        'writeautomaticsub': False,
        'format': 'best',
        'nocheckcertificate': False,  # Activer la vérification SSL
        'verbose': True,
        'ssl_context': ssl_context,    # Utiliser le contexte SSL configuré
    }

    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info_dict = ydl.extract_info(url, download=False)
        title = info_dict.get('title', None)
        thumbnail = info_dict.get('thumbnail', None)

        return {'title': title, 'thumbnail': thumbnail}

def download_video(url, output_path):
    ydl_opts = {
        'format': 'best',
        'outtmpl': output_path,
        'nocheckcertificate': False,  # Activer la vérification SSL
        'verbose': True,
        'ssl_context': ssl_context,    # Utiliser le contexte SSL configuré
    }

    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        ydl.download([url])
