#requires -Version 7.0

[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AdminMemberId = "admin",
    [string]$AdminPassword = "admin123",
    [string]$MemberMemberId = "20260001",
    [string]$MemberPassword = "20260001"
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")
$runSuffix = [DateTimeOffset]::Now.ToString("yyyyMMddHHmmssfff")
$tempPng = Join-Path ([System.IO.Path]::GetTempPath()) "usn-w44-$runSuffix.png"

function New-IdempotencyKey {
    return [guid]::NewGuid().ToString()
}

function Assert-Equal {
    param($Actual, $Expected, [string]$Label)
    if ($Actual -ne $Expected) {
        throw "$Label expected '$Expected' but got '$Actual'"
    }
}

function Assert-True {
    param([bool]$Condition, [string]$Label)
    if (-not $Condition) {
        throw "Assertion failed: $Label"
    }
}

function Get-ApiData {
    param($Response)
    Assert-Equal $Response.code 200 "API envelope code"
    return $Response.data
}

function Invoke-JsonApi {
    param(
        [ValidateSet("GET", "POST", "PUT", "DELETE")]
        [string]$Method,
        [string]$Path,
        [string]$Token = "",
        $Body = $null,
        [string]$IdempotencyKey = ""
    )

    $headers = @{}
    if ($Token) {
        $headers.Authorization = "Bearer $Token"
    }
    if ($IdempotencyKey) {
        $headers["Idempotency-Key"] = $IdempotencyKey
    }

    $request = @{
        Uri = "$BaseUrl$Path"
        Method = $Method
        Headers = $headers
        ErrorAction = "Stop"
    }
    if ($null -ne $Body) {
        $request.ContentType = "application/json"
        $request.Body = $Body | ConvertTo-Json -Depth 30 -Compress
    }
    return Invoke-RestMethod @request
}

function Assert-ApiError {
    param(
        [scriptblock]$Action,
        [int]$Status,
        [string]$Reason,
        [string]$Label,
        [switch]$RequireTraceId
    )

    try {
        & $Action | Out-Null
    }
    catch {
        $actualStatus = [int]$_.Exception.Response.StatusCode
        $payload = $null
        if ($_.ErrorDetails.Message) {
            $payload = $_.ErrorDetails.Message | ConvertFrom-Json
        }
        Assert-Equal $actualStatus $Status "$Label HTTP status"
        Assert-Equal $payload.reason $Reason "$Label reason"
        if ($RequireTraceId) {
            Assert-True ([bool]$payload.data.traceId) "$Label data.traceId"
        }
        return
    }
    throw "$Label expected HTTP $Status/$Reason but request succeeded"
}

function Assert-NoInfrastructureFields {
    param($Value, [string]$Label)
    $json = $Value | ConvertTo-Json -Depth 30 -Compress
    $forbidden = @("absolutePath", "localPath", "objectKey", "bucket", "endpoint", "provider", "signedUrl", "temporaryUrl")
    foreach ($field in $forbidden) {
        Assert-True (-not $json.Contains(('"' + $field + '"'))) "$Label does not expose $field"
    }
}

function Login {
    param([string]$MemberId, [string]$Password)
    $response = Invoke-JsonApi -Method POST -Path "/usnhub/user/login" -Body @{
        memberId = $MemberId
        password = $Password
    }
    $data = Get-ApiData $response
    Assert-True ([bool]$data.token) "login token exists"
    return $data
}

try {
    # A dependency-free 1x1 PNG used only for the multipart media check.
    [System.IO.File]::WriteAllBytes(
        $tempPng,
        [Convert]::FromBase64String("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=")
    )

    $admin = Login -MemberId $AdminMemberId -Password $AdminPassword
    $member = Login -MemberId $MemberMemberId -Password $MemberPassword

    # 1. Create the minimum catalog hierarchy and a DRAFT component.
    $category = Get-ApiData (Invoke-JsonApi -Method POST -Path "/api/component-categories" `
        -Token $admin.token -IdempotencyKey (New-IdempotencyKey) -Body @{
            code = "W44_$runSuffix"
            name = "W44 acceptance category $runSuffix"
            parentId = $null
            sortOrder = 10
        })
    $categoryId = [long]$category.id

    $manufacturer = Get-ApiData (Invoke-JsonApi -Method POST -Path "/api/component-manufacturers" `
        -Token $admin.token -IdempotencyKey (New-IdempotencyKey) -Body @{
            code = "MFG_$runSuffix"
            name = "W44 manufacturer $runSuffix"
            website = "https://example.com"
        })
    $manufacturerId = [long]$manufacturer.id

    $componentBody = @{
        name = "W44 acceptance component"
        manufacturerId = $manufacturerId
        mpn = "W44-MPN-$runSuffix"
        categoryId = $categoryId
        packageType = "SOT-23-5"
        baseUnit = "PCS"
        description = "W44 real integration acceptance"
    }
    $component = Get-ApiData (Invoke-JsonApi -Method POST -Path "/api/components" `
        -Token $admin.token -IdempotencyKey (New-IdempotencyKey) -Body $componentBody)
    $componentId = [long]$component.id
    $componentVersion = [int]$component.version
    $initialPlaceholderKey = [string]$component.placeholderKey
    Assert-Equal $component.status "DRAFT" "new component status"
    Assert-Equal $component.imageState "PLACEHOLDER" "new component image state"
    Assert-True ([bool]$initialPlaceholderKey) "deterministic placeholder key exists"

    # 2. Same manufacturer + normalized MPN is unique across all statuses.
    $duplicateBody = $componentBody.Clone()
    $duplicateBody.name = "duplicate"
    Assert-ApiError -Status 409 -Reason "COMPONENT_DUPLICATE_MODEL" -Label "duplicate MPN" -RequireTraceId -Action {
        Invoke-JsonApi -Method POST -Path "/api/components" -Token $admin.token `
            -IdempotencyKey (New-IdempotencyKey) -Body $duplicateBody
    }

    # 3. MEMBER cannot enumerate DRAFT or mutate the catalog.
    $memberCreateBody = $componentBody.Clone()
    $memberCreateBody.mpn = "DENIED-$runSuffix"
    Assert-ApiError -Status 403 -Reason "COMPONENT_OPERATION_DENIED" -Label "member create denied" -RequireTraceId -Action {
        Invoke-JsonApi -Method POST -Path "/api/components" -Token $member.token `
            -IdempotencyKey (New-IdempotencyKey) -Body $memberCreateBody
    }
    Assert-ApiError -Status 404 -Reason "COMPONENT_NOT_FOUND" -Label "member draft hidden" -RequireTraceId -Action {
        Invoke-JsonApi -Method GET -Path "/api/components/$componentId" -Token $member.token
    }

    # 4. One successful update advances version; the old version cannot overwrite it.
    $updateBody = @{
        version = $componentVersion
        name = "W44 acceptance component updated"
        manufacturerId = $manufacturerId
        mpn = $componentBody.mpn
        categoryId = $categoryId
        packageType = "SOT-23-5"
        baseUnit = "PCS"
        description = "updated once"
    }
    $updated = Get-ApiData (Invoke-JsonApi -Method PUT -Path "/api/components/$componentId" `
        -Token $admin.token -Body $updateBody)
    Assert-True ([int]$updated.version -gt $componentVersion) "update advances component version"
    Assert-ApiError -Status 409 -Reason "VERSION_CONFLICT" -Label "stale component update" -RequireTraceId -Action {
        Invoke-JsonApi -Method PUT -Path "/api/components/$componentId" -Token $admin.token -Body $updateBody
    }
    $componentVersion = [int]$updated.version

    # 5. Register an external candidate; explicit confirmation creates immutable snapshot v1.
    $candidate = Get-ApiData (Invoke-JsonApi -Method POST -Path "/api/components/$componentId/spec-candidates" `
        -Token $admin.token -IdempotencyKey (New-IdempotencyKey) -Body @{
            componentVersion = $componentVersion
            sourceType = "MANUFACTURER_DATASHEET"
            sourceUrl = "https://example.com/w44-datasheet.pdf"
            sourceTitle = "W44 acceptance datasheet"
            capturedAt = [DateTimeOffset]::Now.ToString("o")
            rawSpecs = @(@{ name = "Supply voltage"; value = "1.8 V to 5.5 V"; unit = "V" })
            normalizedProposal = @(@{
                specCode = "supply_voltage_range"
                label = "供电电压范围"
                valueType = "STRING"
                normalizedValue = "1.8-5.5"
                unit = "V"
                sortOrder = 10
            })
        })
    $candidateId = [long]$candidate.id
    $componentVersion = [int]$candidate.componentVersion
    Assert-Equal $candidate.status "PENDING" "new candidate status"

    $confirmed = Get-ApiData (Invoke-JsonApi -Method POST `
        -Path "/api/component-spec-candidates/$candidateId/confirm" -Token $admin.token `
        -IdempotencyKey (New-IdempotencyKey) -Body @{
            componentVersion = $componentVersion
            candidateVersion = [int]$candidate.version
            specifications = @(@{
                specCode = "supply_voltage_range"
                label = "供电电压范围"
                valueType = "STRING"
                rawValue = "1.8 V to 5.5 V"
                normalizedValue = "1.8-5.5"
                unit = "V"
                sortOrder = 10
            })
            changeSummary = "W44 acceptance candidate confirmation"
        })
    Assert-Equal $confirmed.candidateStatus "CONFIRMED" "confirmed candidate status"
    Assert-Equal ([int]$confirmed.snapshotVersion) 1 "first snapshot version"
    $snapshotV1Id = [long]$confirmed.snapshotId
    $componentVersion = [int]$confirmed.componentVersion

    # 6. Manual fallback creates v2 without overwriting v1.
    $manual = Get-ApiData (Invoke-JsonApi -Method POST `
        -Path "/api/components/$componentId/spec-snapshots/manual" -Token $admin.token `
        -IdempotencyKey (New-IdempotencyKey) -Body @{
            componentVersion = $componentVersion
            sourceType = "MANUAL"
            sourceUrl = $null
            sourceNote = "Source unavailable; verified from the physical package label"
            rawSpecs = @(@{ name = "Package marking"; value = "SOT-23-5"; unit = $null })
            specifications = @(@{
                specCode = "package_marking"
                label = "封装标签"
                valueType = "STRING"
                rawValue = "SOT-23-5"
                normalizedValue = "SOT-23-5"
                unit = $null
                sortOrder = 20
            })
            changeSummary = "W44 manual fallback acceptance"
        })
    Assert-Equal ([int]$manual.snapshotVersion) 2 "manual snapshot version"
    $componentVersion = [int]$manual.componentVersion
    $snapshotV1 = Get-ApiData (Invoke-JsonApi -Method GET `
        -Path "/api/component-spec-snapshots/$snapshotV1Id" -Token $admin.token)
    Assert-Equal ([int]$snapshotV1.snapshotVersion) 1 "snapshot v1 remains queryable"

    # 7. Activate the model; MEMBER can now read it.
    $activated = Get-ApiData (Invoke-JsonApi -Method POST -Path "/api/components/$componentId/activate" `
        -Token $admin.token -IdempotencyKey (New-IdempotencyKey) -Body @{ version = $componentVersion })
    Assert-Equal $activated.status "ACTIVE" "activated component status"
    $componentVersion = [int]$activated.version
    $memberView = Get-ApiData (Invoke-JsonApi -Method GET -Path "/api/components/$componentId" -Token $member.token)
    Assert-Equal $memberView.status "ACTIVE" "member sees active component"
    Assert-Equal $memberView.placeholderKey $initialPlaceholderKey "placeholder remains deterministic"

    # 8. Upload optional media and assert that infrastructure details never leak.
    $uploadHeaders = @{
        Authorization = "Bearer $($admin.token)"
        "Idempotency-Key" = New-IdempotencyKey
    }
    $upload = Invoke-RestMethod -Uri "$BaseUrl/api/components/$componentId/images" -Method POST `
        -Headers $uploadHeaders -Form @{
            file = Get-Item -LiteralPath $tempPng
            componentVersion = $componentVersion
            sortOrder = 10
        }
    $uploadData = Get-ApiData $upload
    Assert-True ([long]$uploadData.media.mediaId -gt 0) "upload returns mediaId"
    Assert-Equal $uploadData.imageState "READY" "image upload state"
    Assert-NoInfrastructureFields $uploadData "image upload response"
    $mediaId = [long]$uploadData.media.mediaId
    $componentVersion = [int]$uploadData.componentVersion

    # 9. Logical deletion returns to the exact same deterministic placeholder.
    $deleted = Get-ApiData (Invoke-JsonApi -Method DELETE `
        -Path "/api/components/$componentId/images/$mediaId" -Token $admin.token -Body @{
            componentVersion = $componentVersion
            mediaVersion = [int]$uploadData.media.version
            reason = "W44 acceptance removes the only image"
        })
    Assert-Equal $deleted.imageState "PLACEHOLDER" "last image deletion restores placeholder"
    Assert-Equal $deleted.placeholderKey $initialPlaceholderKey "placeholder key is stable after deletion"
    Assert-NoInfrastructureFields $deleted "image deletion response"

    # 10. Anonymous access uses the frozen 401 envelope and correlation trace.
    Assert-ApiError -Status 401 -Reason "TOKEN_MISSING" -Label "anonymous catalog access" -RequireTraceId -Action {
        Invoke-JsonApi -Method GET -Path "/api/components/$componentId"
    }

    Write-Host "W44 REAL INTEGRATION PASSED: component=$componentId candidate=$candidateId media=$mediaId"
}
finally {
    if (Test-Path -LiteralPath $tempPng) {
        Remove-Item -LiteralPath $tempPng -Force
    }
}
