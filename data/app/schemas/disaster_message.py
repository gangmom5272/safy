from pydantic import BaseModel, ConfigDict, Field, field_validator
from datetime import datetime
from typing import Literal

class DisasterMessageRaw(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    serial_number: str = Field(alias="SN")
    created_at: str = Field(alias="CRT_DT")
    message: str = Field(alias="MSG_CN")
    received_regions: str = Field(alias="RCPTN_RGN_NM")
    emergency_level: str = Field(alias="EMRG_STEP_NM")
    disaster_type: str = Field(alias="DST_SE_NM")
    registered_at: str = Field(alias="REG_YMD")
    modified_at: str = Field(alias="MDFCN_YMD")
    

    @field_validator("serial_number", mode="before")
    @classmethod
    def convert_serial_number_to_string(cls, value: object) -> str:
        return str(value)
    
class DisasterMessage(BaseModel):
    external_id: str
    sent_at: datetime
    message: str
    regions: list[str]
    emergency_level: str
    disaster_type: str
    source: Literal["safetydata.go.kr"] = "safetydata.go.kr"