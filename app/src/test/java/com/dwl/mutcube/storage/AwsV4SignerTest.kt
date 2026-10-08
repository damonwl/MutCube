package com.dwl.mutcube.storage

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class AwsV4SignerTest {
    @Test
    fun signerProducesStableAwsV4HeadersAndEncodedPaths() {
        val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse("2026-09-15T08:00:00Z")!!
        val headers = AwsV4Signer.headers(
            URL("https://s3.example.com/my-bucket/a%20b.zip"), "GET", "us-east-1",
            "ACCESS", "SECRET", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", date,
        )

        assertEquals("20260915T080000Z", headers["x-amz-date"])
        assertEquals(
            "AWS4-HMAC-SHA256 Credential=ACCESS/20260915/us-east-1/s3/aws4_request, " +
                "SignedHeaders=host;x-amz-content-sha256;x-amz-date, " +
                "Signature=54f37c53c787b5c419d06f3f830dcd53d76da4cb4b5f382537ef0a0ba23e8104",
            headers["Authorization"],
        )
        assertEquals("a%20b%2F%E4%B8%AD", AwsV4Signer.encode("a b/中"))
    }
}
