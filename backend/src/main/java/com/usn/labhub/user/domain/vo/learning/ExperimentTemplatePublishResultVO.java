package com.usn.labhub.user.domain.vo.learning;

public record ExperimentTemplatePublishResultVO(
        ExperimentTemplateVersionActionVO publishedVersion,
        ExperimentTemplateVersionActionVO newDraftVersion) {
}
