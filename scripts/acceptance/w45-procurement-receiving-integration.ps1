#requires -Version 7.0

[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AdminMemberId = "admin",
    [string]$AdminPassword = "admin123",
    [string]$RequesterMemberId = "20260001",
    [string]$RequesterPassword = "20260001",
    [string]$ApproverMemberId = $env:USN_W45_APPROVER_MEMBER_ID,
    [string]$ApproverPassword = $env:USN_W45_APPROVER_PASSWORD,
    [string]$StockKeeperMemberId = $env:USN_W45_STOCK_KEEPER_MEMBER_ID,
    [string]$StockKeeperPassword = $env:USN_W45_STOCK_KEEPER_PASSWORD,
    [Parameter(Mandatory = $true)]
    [long]$ComponentId,
    [Parameter(Mandatory = $true)]
    [long]$SecondComponentId,
    [long]$ProjectId = 0,
    [switch]$EnableTestFaultInjection
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")
$runSuffix = [DateTimeOffset]::Now.ToString("yyyyMMddHHmmssfff")
$tempPng = Join-Path ([System.IO.Path]::GetTempPath()) "usn-w45-$runSuffix.png"

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
    param($Response, [string]$Label = "API response")
    Assert-Equal $Response.code 200 "$Label envelope code"
    return $Response.data
}

function Invoke-JsonApi {
    param(
        [ValidateSet("GET", "POST", "PUT", "DELETE")]
        [string]$Method,
        [string]$Path,
        [string]$Token = "",
        $Body = $null,
        [string]$IdempotencyKey = "",
        [hashtable]$ExtraHeaders = @{}
    )

    $headers = @{}
    if ($Token) {
        $headers.Authorization = "Bearer $Token"
    }
    if ($IdempotencyKey) {
        $headers["Idempotency-Key"] = $IdempotencyKey
    }
    foreach ($entry in $ExtraHeaders.GetEnumerator()) {
        $headers[$entry.Key] = $entry.Value
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
        return $payload
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
    $data = Get-ApiData $response "login"
    Assert-True ([bool]$data.token) "login token exists"
    return $data
}

try {
    [System.IO.File]::WriteAllBytes(
        $tempPng,
        [Convert]::FromBase64String("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=")
    )

    $admin = Login -MemberId $AdminMemberId -Password $AdminPassword
    $requester = Login -MemberId $RequesterMemberId -Password $RequesterPassword
    if ($ApproverMemberId -and $ApproverPassword) {
        $approver = Login -MemberId $ApproverMemberId -Password $ApproverPassword
    }
    else {
        $approver = $admin
        Write-Warning "No TEACHER/SYSTEM_ADMIN approver credentials supplied; using the admin account. Full role gate requires USN_W45_APPROVER_* credentials."
    }
    if ($StockKeeperMemberId -and $StockKeeperPassword) {
        $stockKeeper = Login -MemberId $StockKeeperMemberId -Password $StockKeeperPassword
    }
    else {
        $stockKeeper = $admin
        Write-Warning "No STOCK_KEEPER credentials supplied; using the admin account. Full role gate requires USN_W45_STOCK_KEEPER_* credentials."
    }

    # 1. Small multi-line order: exact create replay and key-reuse conflict.
    $smallCreateKey = New-IdempotencyKey
    $smallBody = @{
        title = "W45 small procurement $runSuffix"
        projectId = if ($ProjectId -gt 0) { $ProjectId } else { $null }
        fundingSource = "W45 acceptance"
        shippingAmount = 1.00
        discountAmount = 0.00
        lines = @(
            @{ componentId = $ComponentId; orderedQuantity = 2.000; unitPrice = 10.0000 },
            @{ componentId = $SecondComponentId; orderedQuantity = 1.000; unitPrice = 20.0000 }
        )
    }
    $small = Get-ApiData (Invoke-JsonApi -Method POST -Path "/api/procurement-orders" `
        -Token $requester.token -IdempotencyKey $smallCreateKey -Body $smallBody) "small order create"
    Assert-Equal $small.status "DRAFT" "small order initial status"
    Assert-Equal ([decimal]$small.itemSubtotal) ([decimal]40.00) "small item subtotal"
    Assert-Equal ([decimal]$small.payableAmount) ([decimal]41.00) "small payable amount"
    Assert-Equal $small.lines.Count 2 "small order line count"

    $smallReplay = Get-ApiData (Invoke-JsonApi -Method POST -Path "/api/procurement-orders" `
        -Token $requester.token -IdempotencyKey $smallCreateKey -Body $smallBody) "small create replay"
    Assert-Equal $smallReplay.id $small.id "create replay order id"
    $changedSmallBody = $smallBody.Clone()
    $changedSmallBody.title = "changed idempotency payload"
    Assert-ApiError -Status 409 -Reason "IDEMPOTENCY_KEY_REUSED" -Label "create key reused with changed content" -RequireTraceId -Action {
        Invoke-JsonApi -Method POST -Path "/api/procurement-orders" -Token $requester.token `
            -IdempotencyKey $smallCreateKey -Body $changedSmallBody
    } | Out-Null

    # 2. Small ordinary order is auto-approved with an explicit approval record.
    $smallSubmit = Get-ApiData (Invoke-JsonApi -Method POST -Path "/api/procurement-orders/$($small.id)/submit" `
        -Token $requester.token -IdempotencyKey (New-IdempotencyKey) -Body @{ version = [int]$small.version }) "small order submit"
    Assert-Equal $smallSubmit.status "APPROVED" "small order auto approval status"
    Assert-Equal $smallSubmit.approvalRecord.decision "AUTO_APPROVED" "small order approval fact"

    # 3. A high-value order requires manual approval; requester cannot self-approve.
    $highBody = @{
        title = "W45 high procurement $runSuffix"
        projectId = if ($ProjectId -gt 0) { $ProjectId } else { $null }
        fundingSource = "W45 acceptance"
        shippingAmount = 0.00
        discountAmount = 0.00
        lines = @(
            @{ componentId = $ComponentId; orderedQuantity = 10.000; unitPrice = 100.0000 },
            @{ componentId = $SecondComponentId; orderedQuantity = 5.000; unitPrice = 10.0000 }
        )
    }
    $high = Get-ApiData (Invoke-JsonApi -Method POST -Path "/api/procurement-orders" `
        -Token $requester.token -IdempotencyKey (New-IdempotencyKey) -Body $highBody) "high order create"
    $highId = [long]$high.id
    $highSubmit = Get-ApiData (Invoke-JsonApi -Method POST -Path "/api/procurement-orders/$highId/submit" `
        -Token $requester.token -IdempotencyKey (New-IdempotencyKey) -Body @{ version = [int]$high.version }) "high order submit"
    Assert-Equal $highSubmit.status "PENDING_APPROVAL" "high order approval status"
    Assert-True ([bool]$highSubmit.approvalRequired) "high order approval required"
    Assert-ApiError -Status 403 -Reason "PROCUREMENT_SELF_APPROVAL" -Label "requester self approval" -RequireTraceId -Action {
        Invoke-JsonApi -Method POST -Path "/api/procurement-orders/$highId/approve" `
            -Token $requester.token -IdempotencyKey (New-IdempotencyKey) `
            -Body @{ version = [int]$highSubmit.version; comment = "self approval must fail" }
    } | Out-Null

    $approved = Get-ApiData (Invoke-JsonApi -Method POST -Path "/api/procurement-orders/$highId/approve" `
        -Token $approver.token -IdempotencyKey (New-IdempotencyKey) `
        -Body @{ version = [int]$highSubmit.version; comment = "W45 acceptance approval" }) "manual approval"
    Assert-Equal $approved.status "APPROVED" "manual approval status"

    # 4. Register ordering and reject duplicate platform order number on another order.
    $platformOrderNo = "W45-$runSuffix"
    $ordered = Get-ApiData (Invoke-JsonApi -Method POST -Path "/api/procurement-orders/$highId/mark-ordered" `
        -Token $stockKeeper.token -IdempotencyKey (New-IdempotencyKey) -Body @{
            version = [int]$approved.version
            buyerId = [long]$requester.user.id
            supplierName = "W45 acceptance supplier"
            platformType = "OTHER"
            platformOrderNo = $platformOrderNo
            actualPaidAmount = 1050.00
            paymentDifferenceNote = $null
        }) "mark ordered"
    Assert-Equal $ordered.status "ORDERED" "ordered status"

    Assert-ApiError -Status 409 -Reason "PROCUREMENT_PLATFORM_ORDER_DUPLICATE" `
        -Label "duplicate platform order number" -RequireTraceId -Action {
        Invoke-JsonApi -Method POST -Path "/api/procurement-orders/$($small.id)/mark-ordered" `
            -Token $stockKeeper.token -IdempotencyKey (New-IdempotencyKey) -Body @{
                version = [int]$smallSubmit.version
                buyerId = [long]$admin.user.id
                supplierName = "W45 duplicate supplier"
                platformType = "OTHER"
                platformOrderNo = $platformOrderNo
                actualPaidAmount = 41.00
                paymentDifferenceNote = $null
            }
    } | Out-Null

    $line1Id = [long]$high.lines[0].id
    $line2Id = [long]$high.lines[1].id

    # 5. First partial receipt. MEMBER is denied; admin confirmation creates inventory once.
    $firstReceipt = Get-ApiData (Invoke-JsonApi -Method POST -Path "/api/procurement-orders/$highId/receipts" `
        -Token $stockKeeper.token -IdempotencyKey (New-IdempotencyKey) -Body @{
            orderVersion = [int]$ordered.version
            receivedAt = [DateTimeOffset]::Now.ToString("o")
            note = "W45 first partial receipt"
            lines = @(
                @{ orderLineId = $line1Id; quantity = 4.000; supplierLotNo = "W45-A"; productionDate = $null; expiryDate = $null; locationNote = "A"; qualityNote = "OK" },
                @{ orderLineId = $line2Id; quantity = 2.000; supplierLotNo = "W45-B"; productionDate = $null; expiryDate = $null; locationNote = "B"; qualityNote = "OK" }
            )
        }) "first receipt create"
    Assert-Equal $firstReceipt.status "DRAFT" "first receipt status"

    $firstConfirmBody = @{
        receiptVersion = [int]$firstReceipt.version
        orderVersion = [int]$firstReceipt.orderVersion
    }
    Assert-ApiError -Status 403 -Reason "PROCUREMENT_RECEIPT_DENIED" -Label "member receipt confirmation" -RequireTraceId -Action {
        Invoke-JsonApi -Method POST -Path "/api/procurement-receipts/$($firstReceipt.id)/confirm" `
            -Token $requester.token -IdempotencyKey (New-IdempotencyKey) -Body $firstConfirmBody
    } | Out-Null

    $firstConfirmKey = New-IdempotencyKey
    $firstConfirmed = Get-ApiData (Invoke-JsonApi -Method POST -Path "/api/procurement-receipts/$($firstReceipt.id)/confirm" `
        -Token $stockKeeper.token -IdempotencyKey $firstConfirmKey -Body $firstConfirmBody) "first receipt confirm"
    Assert-Equal $firstConfirmed.receiptStatus "CONFIRMED" "first confirmed receipt status"
    Assert-Equal $firstConfirmed.orderStatus "PARTIALLY_RECEIVED" "partial order status"
    Assert-Equal $firstConfirmed.batches.Count 2 "first receipt batch count"
    Assert-Equal $firstConfirmed.movements.Count 2 "first receipt movement count"

    $firstReplay = Get-ApiData (Invoke-JsonApi -Method POST -Path "/api/procurement-receipts/$($firstReceipt.id)/confirm" `
        -Token $stockKeeper.token -IdempotencyKey $firstConfirmKey -Body $firstConfirmBody) "first receipt replay"
    Assert-Equal $firstReplay.batches[0].batchId $firstConfirmed.batches[0].batchId "receipt replay batch id"
    Assert-Equal $firstReplay.movements[0].movementId $firstConfirmed.movements[0].movementId "receipt replay movement id"
    Assert-Equal $firstReplay.movements[0].balanceAfter $firstConfirmed.movements[0].balanceAfter "receipt replay balance"
    Assert-ApiError -Status 409 -Reason "RECEIPT_ALREADY_CONFIRMED" -Label "new-key duplicate confirmation" -RequireTraceId -Action {
        Invoke-JsonApi -Method POST -Path "/api/procurement-receipts/$($firstReceipt.id)/confirm" `
            -Token $stockKeeper.token -IdempotencyKey (New-IdempotencyKey) -Body $firstConfirmBody
    } | Out-Null

    # 6. An over-quantity receipt rolls back as a whole, then its draft is canceled.
    $balanceBeforeOver = Get-ApiData (Invoke-JsonApi -Method GET `
        -Path "/api/inventory/components/$ComponentId" -Token $stockKeeper.token) "balance before over receipt"
    $overReceipt = Get-ApiData (Invoke-JsonApi -Method POST -Path "/api/procurement-orders/$highId/receipts" `
        -Token $stockKeeper.token -IdempotencyKey (New-IdempotencyKey) -Body @{
            orderVersion = [int]$firstConfirmed.orderVersion
            receivedAt = [DateTimeOffset]::Now.ToString("o")
            note = "W45 over receipt must fail"
            lines = @(@{ orderLineId = $line1Id; quantity = 7.000; supplierLotNo = "OVER"; productionDate = $null; expiryDate = $null; locationNote = $null; qualityNote = $null })
        }) "over receipt create"
    Assert-ApiError -Status 409 -Reason "RECEIPT_OVER_QUANTITY" -Label "over receipt confirmation" -RequireTraceId -Action {
        Invoke-JsonApi -Method POST -Path "/api/procurement-receipts/$($overReceipt.id)/confirm" `
            -Token $stockKeeper.token -IdempotencyKey (New-IdempotencyKey) -Body @{
                receiptVersion = [int]$overReceipt.version
                orderVersion = [int]$overReceipt.orderVersion
            }
    } | Out-Null
    $balanceAfterOver = Get-ApiData (Invoke-JsonApi -Method GET `
        -Path "/api/inventory/components/$ComponentId" -Token $stockKeeper.token) "balance after over receipt"
    Assert-Equal $balanceAfterOver.onHandQuantity $balanceBeforeOver.onHandQuantity "over receipt leaves inventory unchanged"
    $overCanceled = Get-ApiData (Invoke-JsonApi -Method POST -Path "/api/procurement-receipts/$($overReceipt.id)/cancel" `
        -Token $stockKeeper.token -IdempotencyKey (New-IdempotencyKey) -Body @{
            version = [int]$overReceipt.version
            orderVersion = [int]$overReceipt.orderVersion
            reason = "Cancel failed over-quantity draft"
        }) "over receipt cancel"
    Assert-Equal $overCanceled.status "CANCELED" "over receipt canceled"

    # 7. Optional test-profile fault proves database rollback after a mid-transaction failure.
    $orderAfterOver = Get-ApiData (Invoke-JsonApi -Method GET -Path "/api/procurement-orders/$highId" -Token $stockKeeper.token) "order after over receipt"
    if ($EnableTestFaultInjection) {
        $faultReceipt = Get-ApiData (Invoke-JsonApi -Method POST -Path "/api/procurement-orders/$highId/receipts" `
            -Token $stockKeeper.token -IdempotencyKey (New-IdempotencyKey) -Body @{
                orderVersion = [int]$orderAfterOver.version
                receivedAt = [DateTimeOffset]::Now.ToString("o")
                note = "W45 test-profile fault injection"
                lines = @(@{ orderLineId = $line1Id; quantity = 1.000; supplierLotNo = "FAULT"; productionDate = $null; expiryDate = $null; locationNote = $null; qualityNote = $null })
            }) "fault receipt create"
        $balanceBeforeFault = Get-ApiData (Invoke-JsonApi -Method GET `
            -Path "/api/inventory/components/$ComponentId" -Token $stockKeeper.token) "balance before fault"
        Assert-ApiError -Status 500 -Reason "PROCUREMENT_TRANSACTION_FAILED" -Label "test-profile transaction fault" -RequireTraceId -Action {
            Invoke-JsonApi -Method POST -Path "/api/procurement-receipts/$($faultReceipt.id)/confirm" `
                -Token $stockKeeper.token -IdempotencyKey (New-IdempotencyKey) `
                -ExtraHeaders @{ "X-USN-Test-Fault" = "AFTER_FIRST_MOVEMENT" } -Body @{
                    receiptVersion = [int]$faultReceipt.version
                    orderVersion = [int]$faultReceipt.orderVersion
                }
        } | Out-Null
        $faultAfter = Get-ApiData (Invoke-JsonApi -Method GET `
            -Path "/api/procurement-receipts/$($faultReceipt.id)" -Token $stockKeeper.token) "fault receipt after rollback"
        $balanceAfterFault = Get-ApiData (Invoke-JsonApi -Method GET `
            -Path "/api/inventory/components/$ComponentId" -Token $stockKeeper.token) "balance after fault"
        Assert-Equal $faultAfter.status "DRAFT" "failed transaction leaves receipt draft"
        Assert-Equal $faultAfter.batches.Count 0 "failed transaction leaves no batches"
        Assert-Equal $faultAfter.movements.Count 0 "failed transaction leaves no movements"
        Assert-Equal $balanceAfterFault.onHandQuantity $balanceBeforeFault.onHandQuantity "failed transaction leaves inventory unchanged"
        Get-ApiData (Invoke-JsonApi -Method POST -Path "/api/procurement-receipts/$($faultReceipt.id)/cancel" `
            -Token $stockKeeper.token -IdempotencyKey (New-IdempotencyKey) -Body @{
                version = [int]$faultAfter.version
                orderVersion = [int]$faultAfter.orderVersion
                reason = "Cancel fault-injection draft"
            }) "fault receipt cancel" | Out-Null
        $orderAfterOver = Get-ApiData (Invoke-JsonApi -Method GET -Path "/api/procurement-orders/$highId" -Token $stockKeeper.token) "order after fault test"
    }
    else {
        Write-Warning "Transaction-failure scenario NOT RUN. Re-run against the test profile with -EnableTestFaultInjection for the full W45 gate."
    }

    # 8. Optional attachments use mediaId only and do not gate receipt confirmation.
    $uploadHeaders = @{
        Authorization = "Bearer $($requester.token)"
        "Idempotency-Key" = New-IdempotencyKey
    }
    $attachmentUpload = Invoke-RestMethod -Uri "$BaseUrl/api/procurement-orders/$highId/attachments" `
        -Method POST -Headers $uploadHeaders -Form @{
            file = Get-Item -LiteralPath $tempPng
            purpose = "ORDER_SCREENSHOT"
            orderVersion = [int]$orderAfterOver.version
            note = "W45 acceptance order screenshot"
        }
    $attachmentData = Get-ApiData $attachmentUpload "order attachment upload"
    Assert-NoInfrastructureFields $attachmentData "order attachment response"
    $attachmentMediaId = if ($attachmentData.mediaId) { [long]$attachmentData.mediaId } else { [long]$attachmentData.media.mediaId }
    Assert-True ($attachmentMediaId -gt 0) "attachment returns mediaId"
    $orderAfterAttachment = Get-ApiData (Invoke-JsonApi -Method GET `
        -Path "/api/procurement-orders/$highId" -Token $stockKeeper.token) "order after attachment"

    # 9. Receive every remaining quantity, then explicitly complete the order.
    $finalReceipt = Get-ApiData (Invoke-JsonApi -Method POST -Path "/api/procurement-orders/$highId/receipts" `
        -Token $stockKeeper.token -IdempotencyKey (New-IdempotencyKey) -Body @{
            orderVersion = [int]$orderAfterAttachment.version
            receivedAt = [DateTimeOffset]::Now.ToString("o")
            note = "W45 final receipt"
            lines = @(
                @{ orderLineId = $line1Id; quantity = 6.000; supplierLotNo = "W45-A2"; productionDate = $null; expiryDate = $null; locationNote = "A"; qualityNote = "OK" },
                @{ orderLineId = $line2Id; quantity = 3.000; supplierLotNo = "W45-B2"; productionDate = $null; expiryDate = $null; locationNote = "B"; qualityNote = "OK" }
            )
        }) "final receipt create"
    $finalConfirmed = Get-ApiData (Invoke-JsonApi -Method POST -Path "/api/procurement-receipts/$($finalReceipt.id)/confirm" `
        -Token $stockKeeper.token -IdempotencyKey (New-IdempotencyKey) -Body @{
            receiptVersion = [int]$finalReceipt.version
            orderVersion = [int]$finalReceipt.orderVersion
        }) "final receipt confirm"
    Assert-Equal $finalConfirmed.orderStatus "RECEIVED" "fully received order status"
    $completed = Get-ApiData (Invoke-JsonApi -Method POST -Path "/api/procurement-orders/$highId/complete" `
        -Token $stockKeeper.token -IdempotencyKey (New-IdempotencyKey) -Body @{ version = [int]$finalConfirmed.orderVersion }) "order complete"
    Assert-Equal $completed.status "COMPLETED" "completed order status"

    # 10. Anonymous access uses the global 401 envelope.
    Assert-ApiError -Status 401 -Reason "TOKEN_MISSING" -Label "anonymous procurement access" -RequireTraceId -Action {
        Invoke-JsonApi -Method GET -Path "/api/procurement-orders/$highId"
    } | Out-Null

    Write-Host "W45 REAL INTEGRATION PASSED: order=$highId firstReceipt=$($firstReceipt.id) finalReceipt=$($finalReceipt.id) media=$attachmentMediaId"
}
finally {
    if (Test-Path -LiteralPath $tempPng) {
        Remove-Item -LiteralPath $tempPng -Force
    }
}
