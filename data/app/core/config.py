from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_env: str = "local"
    disaster_api_base_url: str = ""
    disaster_api_key: str = ""
    seoul_citydata_base_url: str = ""
    seoul_citydata_api_key: str = ""
    collection_timeout_seconds: float = 100.0
    disaster_collection_timeout_seconds: float = 30.0

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )


@lru_cache
def get_settings() -> Settings:
    return Settings()
