package com.ojimasam.safy.report.domain.type;

public enum ReportSubcategory {
	MOVEMENT_DIFFICULTY(ReportCategory.CROWD_CONGESTION),
	EXIT_CONGESTION(ReportCategory.CROWD_CONGESTION),
	QUEUE_INCREASE(ReportCategory.CROWD_CONGESTION),
	CROWD_INCREASING(ReportCategory.CROWD_CONGESTION),
	CRUSH_RISK(ReportCategory.CROWD_CONGESTION),

	TRAFFIC_ACCIDENT(ReportCategory.ACCIDENT_RISK),
	FIRE_SMOKE(ReportCategory.ACCIDENT_RISK),
	PERSON_INJURED(ReportCategory.ACCIDENT_RISK),
	FALL_COLLISION_RISK(ReportCategory.ACCIDENT_RISK),
	VIOLENCE_THREAT(ReportCategory.ACCIDENT_RISK),

	ROAD_FLOODING(ReportCategory.FLOOD_WEATHER),
	BUILDING_FLOODING(ReportCategory.FLOOD_WEATHER),
	HEAVY_RAIN(ReportCategory.FLOOD_WEATHER),
	HEAVY_SNOW_ICE(ReportCategory.FLOOD_WEATHER),
	STRONG_WIND(ReportCategory.FLOOD_WEATHER),

	ROAD_DAMAGE(ReportCategory.FACILITY_ROAD),
	TRAFFIC_SIGNAL_FAILURE(ReportCategory.FACILITY_ROAD),
	STREETLIGHT_FAILURE(ReportCategory.FACILITY_ROAD),
	PUBLIC_FACILITY_DAMAGE(ReportCategory.FACILITY_ROAD),
	ROAD_OBSTRUCTION(ReportCategory.FACILITY_ROAD),

	OTHER_SITUATION(ReportCategory.OTHER);

	private final ReportCategory category;

	ReportSubcategory(ReportCategory category) {
		this.category = category;
	}

	public boolean belongsTo(ReportCategory category) {
		return this.category == category;
	}
}
