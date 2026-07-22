package com.ojimasam.safy.report.presentation.docs;

public final class ReportExamples {
	public static final String CREATE_TEXT = """
			{
			  "reportType": "TEXT",
			  "description": "도로에 큰 포트홀이 있습니다.",
			  "latitude": 37.5665,
			  "longitude": 126.978,
			  "address": "서울특별시 중구 세종대로 110"
			}
			""";

	public static final String CREATE_IMAGE = """
			{
			  "reportType": "IMAGE",
			  "description": "도로 침수 현장입니다.",
			  "latitude": 37.5665,
			  "longitude": 126.978,
			  "address": "서울특별시 중구 세종대로 110",
			  "mediaAssetIds": [10, 11]
			}
			""";

	private ReportExamples() {
	}
}
