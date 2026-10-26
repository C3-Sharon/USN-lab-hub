#requires -Version 7.0

[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AdminMemberId = "admin",
    [string]$AdminPassword = "admin123",
    [string]$MemberMemberId = "20260001",
    [string]$MemberPassword = "20260001",
    [Parameter(Mandatory = $true)]
    [long]$ProjectId,
    [Parameter(Mandatory = $true)]
    [long]$TemplateVersionId
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")
$runSuffix = [DateTimeOffset]::Now.ToString("yyyyMMddHHmmss")
$tempCsv = Join-Path ([System.IO.Path]::GetTempPath()) "usn-w43-$runSuffix.csv"

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

function Invoke-JsonApi {
    param(
        [ValidateSet("GET", "POST", "PUT", "DELETE")]
        [string]$Method,
        [string]$Path,
        [string]$Token,
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
        $request.Body = $Body | ConvertTo-Json -Depth 20 -Compress
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

function Login {
    param([string]$MemberId, [string]$Password)
    $response = Invoke-JsonApi -Method POST -Path "/usnhub/user/login" -Body @{
        memberId = $MemberId
        password = $Password
    }
    Assert-Equal $response.code 200 "login"
    Assert-True ([bool]$response.data.token) "login token exists"
    return $response.data
}

try {
    "observed_at,voltage,note`n2026-10-22 14:30:00.000,5.12,first`n2026-10-22 14:31:00.000,5.15,second" |
        Set-Content -LiteralPath $tempCsv -Encoding utf8NoBOM

    $admin = Login -MemberId $AdminMemberId -Password $AdminPassword
    $member = Login -MemberId $MemberMemberId -Password $MemberPassword

    # 1. Create an experiment fixed to one published template version.
    $recordCreateKey = New-IdempotencyKey
    $record = Invoke-JsonApi -Method POST -Path "/api/experiment-records" -Token $member.token `
        -IdempotencyKey $recordCreateKey -Body @{
            title = "W43 5V output verification $runSuffix"
            summary = "FILE/MANUAL integration acceptance"
            projectId = $ProjectId
            learningUnitId = $null
            templateVersionId = $TemplateVersionId
            contentJson = @{ voltage = 5.12; isPass = $true }
            purpose = "Verify stable 5V output"
            environment = "Room temperature 24C"
            steps = "Connect the load and record voltage"
            result = "Initial variation below 1 percent"
            anomaly = $null
            conclusion = "Pass"
        }
    Assert-Equal $record.data.status "DRAFT" "new record status"
    Assert-Equal $record.data.templateVersionId $TemplateVersionId "fixed template version"
    $recordId = [long]$record.data.id
    $revisionId = [long]$record.data.currentRevision.id
    $recordVersion = [int]$record.data.version
    $revisionVersion = [int]$record.data.currentRevision.version

    # 2. Only the author can edit; then prove an old optimistic-lock version cannot overwrite it.
    $saveBody = @{
        recordVersion = $recordVersion
        revisionVersion = $revisionVersion
        contentJson = @{ voltage = 5.12; isPass = $true }
        purpose = "Verify stable 5V output"
        environment = "Room temperature 24C"
        steps = "Connect the load and record voltage twice"
        result = "Initial variation below 1 percent"
        anomaly = $null
        conclusion = "Pass"
        changeSummary = $null
    }
    Assert-ApiError -Status 403 -Reason "EXPERIMENT_OPERATION_DENIED" -Label "reviewer cannot edit author content" -RequireTraceId -Action {
        Invoke-JsonApi -Method PUT -Path "/api/experiment-records/$recordId/revisions/$revisionId" `
            -Token $admin.token -Body $saveBody
    }
    $saved = Invoke-JsonApi -Method PUT -Path "/api/experiment-records/$recordId/revisions/$revisionId" `
        -Token $member.token -Body $saveBody
    Assert-Equal $saved.code 200 "save revision"
    Assert-ApiError -Status 409 -Reason "VERSION_CONFLICT" -Label "stale revision save" -RequireTraceId -Action {
        Invoke-JsonApi -Method PUT -Path "/api/experiment-records/$recordId/revisions/$revisionId" `
            -Token $member.token -Body $saveBody
    }
    $recordVersion = [int]$saved.data.recordVersion
    $revisionVersion = [int]$saved.data.revisionVersion

    # 3. FILE plan and batch.
    $filePlan = Invoke-JsonApi -Method POST -Path "/api/projects/$ProjectId/collection-plans" `
        -Token $member.token -IdempotencyKey (New-IdempotencyKey) -Body @{
            name = "W43 CSV plan $runSuffix"
            purpose = "Import voltage observations"
            ownerId = [long]$member.user.id
            sourceType = "FILE"
            metricDefinitions = @(
                @{ code = "voltage"; name = "Voltage"; valueType = "DECIMAL"; unit = "V"; required = $true },
                @{ code = "note"; name = "Note"; valueType = "STRING"; unit = ""; required = $false }
            )
            linkedExperimentRecordId = $recordId
            retentionNote = "Retain with experiment evidence"
        }
    $filePlanId = [long]$filePlan.data.id
    Assert-ApiError -Status 409 -Reason "COLLECTION_PLAN_NOT_READY" -Label "draft plan cannot create batch" -RequireTraceId -Action {
        Invoke-JsonApi -Method POST -Path "/api/collection-plans/$filePlanId/batches" `
            -Token $member.token -IdempotencyKey (New-IdempotencyKey) -Body @{
                planVersion = [int]$filePlan.data.version
                sourceType = "FILE"
            }
    }
    $filePlanReady = Invoke-JsonApi -Method POST -Path "/api/collection-plans/$filePlanId/ready" `
        -Token $member.token -IdempotencyKey (New-IdempotencyKey) -Body @{ version = [int]$filePlan.data.version }
    Assert-Equal $filePlanReady.data.status "READY" "file plan ready"

    $fileBatch = Invoke-JsonApi -Method POST -Path "/api/collection-plans/$filePlanId/batches" `
        -Token $member.token -IdempotencyKey (New-IdempotencyKey) -Body @{
            planVersion = [int]$filePlanReady.data.version
            sourceType = "FILE"
        }
    $fileBatchId = [long]$fileBatch.data.id

    $uploadHeaders = @{
        Authorization = "Bearer $($member.token)"
        "Idempotency-Key" = New-IdempotencyKey
    }
    $upload = Invoke-RestMethod -Uri "$BaseUrl/api/collection-batches/$fileBatchId/file-imports" `
        -Method POST -Headers $uploadHeaders -Form @{ file = Get-Item -LiteralPath $tempCsv }
    Assert-Equal $upload.code 200 "CSV upload"
    Assert-True ([bool]$upload.data.originalFile.sha256) "CSV sha256 exists"
    $importId = [long]$upload.data.id

    # Same bytes with another request key must reuse the import in this batch.
    $duplicateHeaders = @{
        Authorization = "Bearer $($member.token)"
        "Idempotency-Key" = New-IdempotencyKey
    }
    $duplicateUpload = Invoke-RestMethod -Uri "$BaseUrl/api/collection-batches/$fileBatchId/file-imports" `
        -Method POST -Headers $duplicateHeaders -Form @{ file = Get-Item -LiteralPath $tempCsv }
    Assert-Equal $duplicateUpload.data.id $importId "duplicate CSV reuses import"

    $preview = Invoke-JsonApi -Method GET -Path "/api/data-imports/$importId/preview" -Token $member.token
    Assert-Equal $preview.code 200 "CSV preview"

    $mappingV1 = @{
        delimiter = ","
        encoding = "UTF-8"
        headerRow = 1
        timeMapping = @{
            sourceColumn = "observed_at"
            format = "yyyy-MM-dd HH:mm:ss.SSS"
            timezone = "Asia/Shanghai"
        }
        columns = @(
            @{
                sourceColumn = "voltage"
                metricCode = "voltage"
                valueType = "DECIMAL"
                sourceUnit = "V"
                targetUnit = "V"
            }
        )
        ignoredColumns = @("note")
    }
    $mapped = Invoke-JsonApi -Method PUT -Path "/api/data-imports/$importId/mapping" -Token $member.token -Body @{
        version = [int]$upload.data.version
        importVersion = [int]$upload.data.importVersion
        mapping = $mappingV1
    }

    $confirmKey = New-IdempotencyKey
    $confirmed = Invoke-JsonApi -Method POST -Path "/api/data-imports/$importId/confirm" `
        -Token $member.token -IdempotencyKey $confirmKey -Body @{
            version = [int]$mapped.data.version
            importVersion = [int]$mapped.data.importVersion
            batchVersion = [int]$mapped.data.batchVersion
        }
    $confirmedReplay = Invoke-JsonApi -Method POST -Path "/api/data-imports/$importId/confirm" `
        -Token $member.token -IdempotencyKey $confirmKey -Body @{
            version = [int]$mapped.data.version
            importVersion = [int]$mapped.data.importVersion
            batchVersion = [int]$mapped.data.batchVersion
        }
    Assert-Equal $confirmedReplay.data.id $confirmed.data.id "confirm idempotent replay"

    $mappingV2 = $mappingV1.Clone()
    $mappingV2.columns = @(
        $mappingV1.columns[0],
        @{
            sourceColumn = "note"
            metricCode = "note"
            valueType = "STRING"
            sourceUnit = ""
            targetUnit = ""
        }
    )
    $mappingV2.ignoredColumns = @()
    $mappedV2 = Invoke-JsonApi -Method PUT -Path "/api/data-imports/$importId/mapping" -Token $member.token -Body @{
        version = [int]$confirmed.data.version
        importVersion = [int]$confirmed.data.importVersion
        mapping = $mappingV2
    }
    Assert-True ($mappedV2.data.importVersion -gt $confirmed.data.importVersion) "new mapping appends import revision"
    Assert-Equal $mappedV2.data.status "MAPPING_REQUIRED" "new mapping requires confirmation"
    $confirmedV2 = Invoke-JsonApi -Method POST -Path "/api/data-imports/$importId/confirm" `
        -Token $member.token -IdempotencyKey (New-IdempotencyKey) -Body @{
            version = [int]$mappedV2.data.version
            importVersion = [int]$mappedV2.data.importVersion
            batchVersion = [int]$mappedV2.data.batchVersion
        }
    $importDetail = Invoke-JsonApi -Method GET -Path "/api/data-imports/$importId" -Token $member.token
    $supersededRevisions = @($importDetail.data.revisions | Where-Object { $_.status -eq "SUPERSEDED" })
    Assert-True ($supersededRevisions.Count -ge 1) "previous confirmed import revision is superseded"
    $confirmed = $confirmedV2

    $fileCompleted = Invoke-JsonApi -Method POST -Path "/api/collection-batches/$fileBatchId/complete" `
        -Token $member.token -IdempotencyKey (New-IdempotencyKey) -Body @{
            version = [int]$confirmed.data.batchVersion
            completionReason = "CSV mapping and rows confirmed"
        }
    Assert-Equal $fileCompleted.data.status "COMPLETED" "file batch completed"
    $snapshot = Invoke-JsonApi -Method POST -Path "/api/collection-batches/$fileBatchId/snapshots" `
        -Token $member.token -IdempotencyKey (New-IdempotencyKey) -Body @{
            batchVersion = [int]$fileCompleted.data.version
            snapshotType = "FULL_BATCH"
        }
    $snapshotId = [long]$snapshot.data.id

    $bound = Invoke-JsonApi -Method PUT -Path "/api/experiment-records/$recordId/collection-snapshot" `
        -Token $member.token -Body @{
            recordVersion = $recordVersion
            revisionId = $revisionId
            revisionVersion = $revisionVersion
            snapshotId = $snapshotId
        }
    Assert-Equal $bound.data.collectionSnapshotId $snapshotId "snapshot bound"
    $recordVersion = [int]$bound.data.recordVersion
    $revisionVersion = [int]$bound.data.revisionVersion

    # 4. MANUAL plan: validate required provenance, revisions, unfinished snapshot rejection and immutability.
    $manualPlan = Invoke-JsonApi -Method POST -Path "/api/projects/$ProjectId/collection-plans" `
        -Token $member.token -IdempotencyKey (New-IdempotencyKey) -Body @{
            name = "W43 manual plan $runSuffix"
            purpose = "Record manual multimeter readings"
            ownerId = [long]$member.user.id
            sourceType = "MANUAL"
            metricDefinitions = @(
                @{ code = "voltage"; name = "Voltage"; valueType = "DECIMAL"; unit = "V"; required = $true }
            )
            linkedExperimentRecordId = $recordId
        }
    $manualPlanId = [long]$manualPlan.data.id
    $manualPlanReady = Invoke-JsonApi -Method POST -Path "/api/collection-plans/$manualPlanId/ready" `
        -Token $member.token -IdempotencyKey (New-IdempotencyKey) -Body @{ version = [int]$manualPlan.data.version }
    $manualBatch = Invoke-JsonApi -Method POST -Path "/api/collection-plans/$manualPlanId/batches" `
        -Token $member.token -IdempotencyKey (New-IdempotencyKey) -Body @{
            planVersion = [int]$manualPlanReady.data.version
            sourceType = "MANUAL"
        }
    $manualBatchId = [long]$manualBatch.data.id
    Assert-ApiError -Status 409 -Reason "BATCH_NOT_COMPLETED" -Label "unfinished batch snapshot" -RequireTraceId -Action {
        Invoke-JsonApi -Method POST -Path "/api/collection-batches/$manualBatchId/snapshots" `
            -Token $member.token -IdempotencyKey (New-IdempotencyKey) -Body @{
                batchVersion = [int]$manualBatch.data.version
                snapshotType = "FULL_BATCH"
            }
    }

    $manualRecord = Invoke-JsonApi -Method POST -Path "/api/collection-batches/$manualBatchId/manual-records" `
        -Token $member.token -IdempotencyKey (New-IdempotencyKey) -Body @{
            batchVersion = [int]$manualBatch.data.version
            metricCode = "voltage"
            valueType = "DECIMAL"
            value = 5.12
            unit = "V"
            observedAt = "2026-10-22T14:30:00+08:00"
            method = "Digital multimeter DC measurement"
            instrument = "UNI-T UT61E+ / LAB-A-017"
            note = "Room temperature 24C"
        }
    $manualRecordId = [long]$manualRecord.data.id
    $manualUpdated = Invoke-JsonApi -Method PUT -Path "/api/manual-records/$manualRecordId" `
        -Token $member.token -Body @{
            version = [int]$manualRecord.data.version
            batchVersion = [int]$manualRecord.data.batchVersion
            valueType = "DECIMAL"
            value = 5.13
            unit = "V"
            observedAt = "2026-10-22T14:30:00+08:00"
            method = "Digital multimeter DC measurement"
            instrument = "UNI-T UT61E+ / LAB-A-017"
            note = "Corrected transcription"
            changeReason = "Original value was transcribed incorrectly"
        }
    Assert-True ($manualUpdated.data.revisionNo -gt $manualRecord.data.revisionNo) "manual correction appends revision"

    $manualCompleted = Invoke-JsonApi -Method POST -Path "/api/collection-batches/$manualBatchId/complete" `
        -Token $member.token -IdempotencyKey (New-IdempotencyKey) -Body @{
            version = [int]$manualUpdated.data.batchVersion
            completionReason = "Manual reading reviewed"
        }
    $manualSnapshot = Invoke-JsonApi -Method POST -Path "/api/collection-batches/$manualBatchId/snapshots" `
        -Token $member.token -IdempotencyKey (New-IdempotencyKey) -Body @{
            batchVersion = [int]$manualCompleted.data.version
            snapshotType = "FULL_BATCH"
        }
    Assert-True ($null -eq $manualSnapshot.data.sourceRevisionId) "manual snapshot has no synthetic source revision"
    Assert-ApiError -Status 409 -Reason "BATCH_IMMUTABLE" -Label "completed manual batch immutable" -RequireTraceId -Action {
        Invoke-JsonApi -Method PUT -Path "/api/manual-records/$manualRecordId" -Token $member.token -Body @{
            version = [int]$manualUpdated.data.version
            batchVersion = [int]$manualCompleted.data.version
            valueType = "DECIMAL"
            value = 5.14
            unit = "V"
            observedAt = "2026-10-22T14:30:00+08:00"
            method = "Digital multimeter DC measurement"
            instrument = "UNI-T UT61E+ / LAB-A-017"
            changeReason = "Must be rejected after completion"
        }
    }

    # 5. Submit, reject self-review, request changes, resubmit, approve and publish.
    $submitted = Invoke-JsonApi -Method POST -Path "/api/experiment-records/$recordId/submit" `
        -Token $member.token -IdempotencyKey (New-IdempotencyKey) -Body @{
            recordVersion = $recordVersion
            revisionId = $revisionId
            revisionVersion = $revisionVersion
        }
    Assert-Equal $submitted.data.status "IN_REVIEW" "record submitted"
    Assert-ApiError -Status 403 -Reason "FEEDBACK_OPERATION_DENIED" -Label "self approval denied" -RequireTraceId -Action {
        Invoke-JsonApi -Method POST -Path "/api/experiment-records/$recordId/feedback" `
            -Token $member.token -IdempotencyKey (New-IdempotencyKey) -Body @{
                recordVersion = [int]$submitted.data.recordVersion
                revisionId = $revisionId
                type = "APPROVAL"
                content = "Self approval must fail"
                visibility = "AUTHOR_AND_REVIEWERS"
            }
    }

    $changes = Invoke-JsonApi -Method POST -Path "/api/experiment-records/$recordId/feedback" `
        -Token $admin.token -IdempotencyKey (New-IdempotencyKey) -Body @{
            recordVersion = [int]$submitted.data.recordVersion
            revisionId = $revisionId
            type = "CHANGE_REQUEST"
            content = "Add the corrected manual reading to the conclusion"
            visibility = "AUTHOR_AND_REVIEWERS"
        }
    Assert-Equal $changes.data.status "CHANGES_REQUESTED" "changes requested"
    $newRevisionId = [long]$changes.data.newRevision.id
    $newRevisionVersion = [int]$changes.data.newRevision.version
    $recordVersion = [int]$changes.data.recordVersion

    $revised = Invoke-JsonApi -Method PUT -Path "/api/experiment-records/$recordId/revisions/$newRevisionId" `
        -Token $member.token -Body @{
            recordVersion = $recordVersion
            revisionVersion = $newRevisionVersion
            contentJson = @{ voltage = 5.13; isPass = $true }
            purpose = "Verify stable 5V output"
            environment = "Room temperature 24C"
            steps = "Connect the load and record voltage twice"
            result = "CSV and manual readings agree"
            anomaly = $null
            conclusion = "Pass after corrected manual reading"
            changeSummary = "Added corrected manual reading"
        }
    $resubmitted = Invoke-JsonApi -Method POST -Path "/api/experiment-records/$recordId/submit" `
        -Token $member.token -IdempotencyKey (New-IdempotencyKey) -Body @{
            recordVersion = [int]$revised.data.recordVersion
            revisionId = $newRevisionId
            revisionVersion = [int]$revised.data.revisionVersion
        }
    $approved = Invoke-JsonApi -Method POST -Path "/api/experiment-records/$recordId/feedback" `
        -Token $admin.token -IdempotencyKey (New-IdempotencyKey) -Body @{
            recordVersion = [int]$resubmitted.data.recordVersion
            revisionId = $newRevisionId
            type = "APPROVAL"
            content = "Review passed"
            visibility = "AUTHOR_AND_REVIEWERS"
        }
    $published = Invoke-JsonApi -Method POST -Path "/api/experiment-records/$recordId/publish" `
        -Token $admin.token -IdempotencyKey (New-IdempotencyKey) -Body @{
            recordVersion = [int]$approved.data.recordVersion
            revisionId = $newRevisionId
        }
    Assert-Equal $published.data.status "PUBLISHED" "record published"

    # 6. Convert exactly once to the existing PREPARING project model.
    $conversionBody = @{
        recordVersion = [int]$published.data.recordVersion
        code = "EXP-$runSuffix"
        name = "W43 experiment project $runSuffix"
        summary = "Created from experiment record $recordId"
        category = "hardware_project"
    }
    $conversionKey = New-IdempotencyKey
    $projectDraft = Invoke-JsonApi -Method POST -Path "/api/experiment-records/$recordId/project-draft" `
        -Token $member.token -IdempotencyKey $conversionKey -Body $conversionBody
    Assert-Equal $projectDraft.data.status "PREPARING" "converted project status"
    Assert-Equal $projectDraft.data.myRole "OWNER" "converter project role"
    Assert-Equal $projectDraft.data.sourceExperimentRecordId $recordId "project source record"
    Assert-Equal $projectDraft.data.sourcePublishedRevisionId $newRevisionId "project source published revision"
    $projectDetail = Invoke-JsonApi -Method GET -Path "/api/projects/$($projectDraft.data.id)" -Token $member.token
    Assert-Equal $projectDetail.data.sourceExperimentRecordId $recordId "project detail source record"
    Assert-Equal $projectDetail.data.sourcePublishedRevisionId $newRevisionId "project detail source published revision"
    $projectReplay = Invoke-JsonApi -Method POST -Path "/api/experiment-records/$recordId/project-draft" `
        -Token $member.token -IdempotencyKey $conversionKey -Body $conversionBody
    Assert-Equal $projectReplay.data.id $projectDraft.data.id "project conversion idempotent replay"

    # 7. Guest access must still use the global authentication contract.
    Assert-ApiError -Status 401 -Reason "TOKEN_MISSING" -Label "guest experiment list" -Action {
        Invoke-JsonApi -Method GET -Path "/api/experiment-records" -Token ""
    }

    Write-Host "W43 REAL INTEGRATION PASSED" -ForegroundColor Green
    Write-Host "recordId=$recordId fileBatchId=$fileBatchId manualBatchId=$manualBatchId snapshotId=$snapshotId projectId=$($projectDraft.data.id)"
}
finally {
    if (Test-Path -LiteralPath $tempCsv) {
        Remove-Item -LiteralPath $tempCsv -Force
    }
}
