import os
import boto3
from botocore.config import Config


def _s3():
    return boto3.client(
        "s3",
        endpoint_url=os.environ["R2_ENDPOINT_URL"],
        aws_access_key_id=os.environ["R2_ACCESS_KEY_ID"],
        aws_secret_access_key=os.environ["R2_SECRET_ACCESS_KEY"],
        region_name="auto",
        config=Config(
            # R2 requires path-style URLs
            s3={"addressing_style": "path"},
            # Recent boto3/botocore versions attach CRC32 checksums by default;
            # R2 does not support them — restrict to operations that require it.
            request_checksum_calculation="when_required",
            response_checksum_validation="when_required",
        ),
    )


def download_pdf(storage_key: str) -> bytes:
    response = _s3().get_object(Bucket=os.environ["R2_BUCKET_NAME"], Key=storage_key)
    return response["Body"].read()
