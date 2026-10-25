package com.usn.labhub.user.controller;

import com.usn.labhub.user.common.result.Result;
import com.usn.labhub.user.domain.dto.collection.CollectionSnapshotBindingDTO;
import com.usn.labhub.user.domain.dto.collection.CollectionSnapshotCreateDTO;
import com.usn.labhub.user.domain.dto.collection.DataImportConfirmDTO;
import com.usn.labhub.user.domain.dto.collection.DataImportMappingDTO;
import com.usn.labhub.user.domain.dto.collection.ManualRecordCreateDTO;
import com.usn.labhub.user.domain.dto.collection.ManualRecordUpdateDTO;
import com.usn.labhub.user.service.CollectionSnapshotService;
import com.usn.labhub.user.service.CsvImportService;
import com.usn.labhub.user.service.ManualCollectionService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class CollectionDataController {
    private final CsvImportService csv;
    private final ManualCollectionService manual;
    private final CollectionSnapshotService snapshots;

    public CollectionDataController(CsvImportService csv, ManualCollectionService manual,
                                    CollectionSnapshotService snapshots) {
        this.csv = csv;
        this.manual = manual;
        this.snapshots = snapshots;
    }

    @PostMapping(value = "/collection-batches/{id}/file-imports",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> upload(@PathVariable Long id,
            @RequestHeader("Idempotency-Key") String key,
            @RequestPart("file") MultipartFile file) {
        return Result.success(csv.upload(id, key, file));
    }

    @GetMapping("/data-imports/{id}/preview")
    public Result<Map<String, Object>> preview(@PathVariable Long id) {
        return Result.success(csv.preview(id));
    }

    @GetMapping("/data-imports/{id}")
    public Result<Map<String, Object>> importDetail(@PathVariable Long id) {
        return Result.success(csv.detail(id));
    }

    @PutMapping("/data-imports/{id}/mapping")
    public Result<Map<String, Object>> mapping(@PathVariable Long id,
            @Valid @RequestBody DataImportMappingDTO request) {
        return Result.success(csv.saveMapping(id, request));
    }

    @PostMapping("/data-imports/{id}/confirm")
    public Result<Map<String, Object>> confirm(@PathVariable Long id,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody DataImportConfirmDTO request) {
        return Result.success(csv.confirm(id, key, request));
    }

    @PostMapping("/collection-batches/{id}/manual-records")
    public Result<Map<String, Object>> createManual(@PathVariable Long id,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ManualRecordCreateDTO request) {
        return Result.success(manual.create(id, key, request));
    }

    @PutMapping("/manual-records/{id}")
    public Result<Map<String, Object>> updateManual(@PathVariable Long id,
            @Valid @RequestBody ManualRecordUpdateDTO request) {
        return Result.success(manual.update(id, request));
    }

    @GetMapping("/manual-records/{id}/revisions")
    public Result<Map<String, Object>> manualRevisions(@PathVariable Long id) {
        return Result.success(manual.revisions(id));
    }

    @GetMapping("/collection-batches/{id}/records")
    public Result<Map<String, Object>> records(@PathVariable Long id,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "100") int limit) {
        return Result.success(snapshots.records(id, cursor, limit));
    }

    @PostMapping("/collection-batches/{id}/snapshots")
    public Result<Map<String, Object>> snapshot(@PathVariable Long id,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CollectionSnapshotCreateDTO request) {
        return Result.success(snapshots.create(id, key, request));
    }

    @PutMapping("/experiment-records/{id}/collection-snapshot")
    public Result<Map<String, Object>> bindSnapshot(@PathVariable Long id,
            @Valid @RequestBody CollectionSnapshotBindingDTO request) {
        return Result.success(snapshots.bind(id, request));
    }
}
