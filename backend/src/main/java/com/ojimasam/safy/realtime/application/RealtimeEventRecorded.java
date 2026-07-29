package com.ojimasam.safy.realtime.application;
import com.ojimasam.safy.realtime.application.dto.RealtimeEventView;

public record RealtimeEventRecorded(Long recipientUserId, RealtimeEventView event) {
}
