# Third-party components

QuietDLP is free software under GPL-3.0. It uses and distributes these components, each under its own license.

| component | used for | license | source |
| --- | --- | --- | --- |
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) | video and audio downloads | Unlicense (public domain) | https://github.com/yt-dlp/yt-dlp |
| [youtubedl-android](https://github.com/yausername/youtubedl-android) | packages yt-dlp, Python, QuickJS and ffmpeg for Android | GPL-3.0 | https://github.com/yausername/youtubedl-android |
| Python | interpreter that runs yt-dlp and gallery-dl (included in the library above) | PSF License | https://www.python.org |
| FFmpeg | audio and video processing (included in the library above) | GPL-3.0 (depending on the build) | https://ffmpeg.org |
| [gallery-dl](https://github.com/mikf/gallery-dl) | photo and gallery downloads | **GPL-2.0-only** | https://github.com/mikf/gallery-dl |
| [requests](https://github.com/psf/requests) | gallery-dl dependency | Apache-2.0 | https://github.com/psf/requests |
| [urllib3](https://github.com/urllib3/urllib3) | gallery-dl dependency | MIT | https://github.com/urllib3/urllib3 |
| [certifi](https://github.com/certifi/python-certifi) | root certificates, gallery-dl dependency | MPL-2.0 | https://github.com/certifi/python-certifi |
| [idna](https://github.com/kjd/idna) | gallery-dl dependency | BSD-3-Clause | https://github.com/kjd/idna |
| [charset-normalizer](https://github.com/jawah/charset_normalizer) | gallery-dl dependency | MIT | https://github.com/jawah/charset_normalizer |
| Jetpack Compose, AndroidX, Kotlin | UI and language | Apache-2.0 | https://developer.android.com/jetpack |
| [Coil](https://github.com/coil-kt/coil) | thumbnail loading | Apache-2.0 | https://github.com/coil-kt/coil |

## About gallery-dl (GPL-2.0-only)

gallery-dl ships inside the app as a separate, unmodified program (`assets/gdl_pack.zip`), and QuietDLP runs it as its own process, without linking or mixing its code with QuietDLP's. This is mere aggregation. Its license text is in the original package: https://github.com/mikf/gallery-dl/blob/master/LICENSE

Newer gallery-dl versions that the app downloads later (from PyPI, with SHA-256 verification) are likewise used unmodified.

## Inspiration

The feature set was studied clean-room from the public documentation of [YTDLnis](https://github.com/deniscerri/ytdlnis) (GPL-3.0): what it does is rebuilt, not its code, name, icons or text.
