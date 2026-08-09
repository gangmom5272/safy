package com.ojimasam.safy.report.presentation.docs;

public final class ReportExamples {
	public static final String CREATE_IMAGE = """
			{
			  "reportType": "IMAGE",
			  "category": "FLOOD_WEATHER",
			  "subcategory": "ROAD_FLOODING",
			  "description": "도로 침수 현장입니다.",
			  "latitude": 37.5665,
			  "longitude": 126.978,
			  "address": "서울특별시 중구 세종대로 110"
			}
			""";

	public static final String CREATE_LIVE = """
			{
			  "reportType": "LIVE",
			  "category": "CROWD_CONGESTION",
			  "subcategory": "CROWD_INCREASING",
			  "description": "강남역 출구 주변에 인파가 몰려 있습니다.",
			  "latitude": 37.4979,
			  "longitude": 127.0276,
			  "address": "서울특별시 강남구 강남대로"
			}
			""";

	private ReportExamples() {
	}
}
