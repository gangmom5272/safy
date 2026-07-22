package com.ojimasam.safy.media.application;

public final class ImageContentTypeDetector {
	private ImageContentTypeDetector() {
	}

	public static String detect(byte[] bytes) {
		if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) return "image/jpeg";
		if (startsWith(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) return "image/png";
		if (bytes.length >= 12 && startsWith(bytes, 0x52, 0x49, 0x46, 0x46)
				&& bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50) {
			return "image/webp";
		}
		throw new IllegalArgumentException("실제 이미지 파일만 업로드할 수 있습니다.");
	}

	private static boolean startsWith(byte[] bytes, int... signature) {
		if (bytes.length < signature.length) return false;
		for (int index = 0; index < signature.length; index++) {
			if (Byte.toUnsignedInt(bytes[index]) != signature[index]) return false;
		}
		return true;
	}
}
