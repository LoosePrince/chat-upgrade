package com.chat.upgrade.server;

import java.util.Locale;

final class ServerMediaContentPolicy {
    private ServerMediaContentPolicy() {
    }

    static boolean accepts(String typeWire, String contentType, byte[] body) {
        if (typeWire == null || contentType == null || body == null || body.length < 4) {
            return false;
        }
        String normalizedType = typeWire.trim().toLowerCase(Locale.ROOT);
        String normalizedContentType = baseContentType(contentType);
        MediaFormat format = detect(body);
        return format != null && switch (normalizedType) {
            case "image" -> format.image && imageContentTypeMatches(format, normalizedContentType);
            case "audio" -> format.audio && audioContentTypeMatches(format, normalizedContentType);
            case "video" -> format.video && videoContentTypeMatches(format, normalizedContentType);
            default -> false;
        };
    }

    private static MediaFormat detect(byte[] body) {
        if (startsWith(body, new int[] { 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A })) {
            return MediaFormat.PNG;
        }
        if (startsWith(body, new int[] { 0xFF, 0xD8, 0xFF })) {
            return MediaFormat.JPEG;
        }
        if (matches(body, 0, "GIF87a") || matches(body, 0, "GIF89a")) {
            return MediaFormat.GIF;
        }
        if (matches(body, 0, "RIFF") && matches(body, 8, "WEBP")) {
            return MediaFormat.WEBP;
        }
        if (matches(body, 0, "fLaC")) {
            return MediaFormat.FLAC;
        }
        if (matches(body, 0, "RIFF") && matches(body, 8, "WAVE")) {
            return MediaFormat.WAV;
        }
        if (matches(body, 0, "ID3") || ((body[0] & 0xFF) == 0xFF && (body[1] & 0xE0) == 0xE0)) {
            return MediaFormat.MP3;
        }
        if (body.length >= 12 && matches(body, 4, "ftyp")) {
            return MediaFormat.ISO_BASE_MEDIA;
        }
        if (startsWith(body, new int[] { 0x1A, 0x45, 0xDF, 0xA3 })) {
            return MediaFormat.EBML_VIDEO;
        }
        if (matches(body, 0, "OggS")) {
            return MediaFormat.OGG;
        }
        return null;
    }

    private static boolean imageContentTypeMatches(MediaFormat format, String contentType) {
        return switch (format) {
            case PNG -> contentType.equals("image/png");
            case JPEG -> contentType.equals("image/jpeg") || contentType.equals("image/jpg");
            case GIF -> contentType.equals("image/gif");
            case WEBP -> contentType.equals("image/webp");
            default -> false;
        };
    }

    private static boolean audioContentTypeMatches(MediaFormat format, String contentType) {
        return switch (format) {
            case OGG -> contentType.equals("audio/ogg") || contentType.equals("application/ogg");
            case FLAC -> contentType.equals("audio/flac") || contentType.equals("audio/x-flac");
            case WAV -> contentType.equals("audio/wav")
                    || contentType.equals("audio/wave")
                    || contentType.equals("audio/x-wav");
            case MP3 -> contentType.equals("audio/mpeg") || contentType.equals("audio/mp3");
            default -> false;
        };
    }

    private static boolean videoContentTypeMatches(MediaFormat format, String contentType) {
        return switch (format) {
            case OGG -> contentType.equals("video/ogg");
            case ISO_BASE_MEDIA -> contentType.equals("video/mp4")
                    || contentType.equals("video/quicktime")
                    || contentType.equals("video/x-m4v");
            case EBML_VIDEO -> contentType.equals("video/webm") || contentType.equals("video/x-matroska");
            default -> false;
        };
    }

    private static String baseContentType(String contentType) {
        int parameters = contentType.indexOf(';');
        String base = parameters >= 0 ? contentType.substring(0, parameters) : contentType;
        return base.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean startsWith(byte[] body, int[] prefix) {
        if (body.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if ((body[index] & 0xFF) != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean matches(byte[] body, int offset, String value) {
        if (offset < 0 || offset + value.length() > body.length) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if ((body[offset + index] & 0xFF) != value.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private enum MediaFormat {
        PNG(true, false, false),
        JPEG(true, false, false),
        GIF(true, false, false),
        WEBP(true, false, false),
        OGG(false, true, true),
        FLAC(false, true, false),
        WAV(false, true, false),
        MP3(false, true, false),
        ISO_BASE_MEDIA(false, false, true),
        EBML_VIDEO(false, false, true);

        private final boolean image;
        private final boolean audio;
        private final boolean video;

        MediaFormat(boolean image, boolean audio, boolean video) {
            this.image = image;
            this.audio = audio;
            this.video = video;
        }
    }
}