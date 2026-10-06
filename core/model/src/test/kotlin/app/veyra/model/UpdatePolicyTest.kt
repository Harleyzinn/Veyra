package app.veyra.model

import kotlin.test.*

class UpdatePolicyTest {
    @Test fun numericVersionOrderingRejectsDowngradesAndPreReleases(){
        assertTrue(UpdatePolicy.newer("v1.10.0","1.2.0"));assertTrue(UpdatePolicy.newer("2.0.0","1.99.99"));assertFalse(UpdatePolicy.newer("1.2.0","1.2.0"));assertFalse(UpdatePolicy.newer("1.1.9","1.2.0"));assertFalse(UpdatePolicy.newer("v1.3.0-beta","1.2.0"));assertFalse(UpdatePolicy.newer("latest","1.2.0"))
    }
    @Test fun onlyCanonicalOfficialReleaseAssetIsAccepted(){
        val url="https://github.com/Harleyzinn/Veyra/releases/download/v1.2.0/Veyra-1.2.0.apk"
        assertTrue(UpdatePolicy.assetUrl("v1.2.0",url));assertFalse(UpdatePolicy.assetUrl("v1.2.0",url.replace("Harleyzinn","other")));assertFalse(UpdatePolicy.assetUrl("v1.2.0",url+"?redirect=evil"));assertFalse(UpdatePolicy.assetUrl("v1.3.0",url))
    }
    @Test fun redirectsRejectHttpUserInfoAndLookalikeHosts(){
        assertTrue(UpdatePolicy.trustedDownload("https://release-assets.githubusercontent.com/github-production-release-asset/123"))
        for(url in listOf("http://github.com/file","https://github.com.evil.test/file","https://evil@github.com/file","https://github.com:1234/file","https://evil.test/"))assertFalse(UpdatePolicy.trustedDownload(url),url)
    }
    @Test fun requiresFullSha256(){assertEquals("a".repeat(64),UpdatePolicy.digest("sha256:"+"A".repeat(64)));assertNull(UpdatePolicy.digest("sha256:123"));assertNull(UpdatePolicy.digest("g".repeat(64)))}
}
