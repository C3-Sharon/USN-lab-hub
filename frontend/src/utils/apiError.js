import { ElMessage, ElMessageBox } from 'element-plus'

/**
 * API 错误归一化
 *
 * 背景：错误有三种形态，页面需要统一处理
 *   1) request.js 拦截器 reject(payload) → { code, msg, reason, data }
 *   2) HTTP 层错误（axios error） → err.response.data = { code, msg, reason }
 *   3) mock 分支直接 reject({ code, msg, reason })
 *
 * 契约来源：docs/contracts/PROJECT_WORKSPACE_API.md §6 / §10
 */

export function normalizeApiError(err) {
  if (!err) return { code: null, reason: null, msg: '', data: null, traceId: '' }
  const payload = err.response?.data || err
  return {
    code: payload?.code ?? err.response?.status ?? null,
    reason: payload?.reason ?? null,
    msg: payload?.msg || err.message || '请求失败',
    data: payload?.data ?? null,
    traceId: payload?.data?.traceId || ''
  }
}

export function isReason(err, reason) {
  return normalizeApiError(err).reason === reason
}

export function isVersionConflict(err) {
  return isReason(err, 'VERSION_CONFLICT')
}

export function isProjectNotFound(err) {
  const { code, reason } = normalizeApiError(err)
  return code === 404 || reason === 'PROJECT_NOT_FOUND' || reason === 'PROJECT_ACCESS_DENIED'
}

/**
 * mock 分支的错误不经 axios 拦截器，需要页面兜底提示；
 * 真实模式下 axios 错误已由 request.js 统一提示，此处跳过避免重复 toast。
 */
export function notifyApiError(err) {
  if (err?.response) return
  const { reason, msg } = normalizeApiError(err)
  if (reason === 'VERSION_CONFLICT') return
  if (msg) ElMessage.error(msg)
}

// ========== 学习实验域错误码（契约 §7） ==========

const LEARNING_NOT_FOUND_REASONS = [
  'LEARNING_ROADMAP_NOT_FOUND',
  'LEARNING_STAGE_NOT_FOUND',
  'LEARNING_UNIT_NOT_FOUND'
]

/** 路线/阶段/单元不存在或不可见（404） */
export function isLearningNotFound(err) {
  const { code, reason } = normalizeApiError(err)
  return code === 404 || LEARNING_NOT_FOUND_REASONS.includes(reason)
}

/** 路线已归档（409） */
export function isLearningArchived(err) {
  return isReason(err, 'LEARNING_ARCHIVED')
}

/** 尚未开始学习该路线（400） */
export function isLearningNotEnrolled(err) {
  return isReason(err, 'LEARNING_NOT_ENROLLED')
}

/** 无学习路线管理权限 / GUEST 不可访问（403） */
export function isLearningDenied(err) {
  const { code, reason } = normalizeApiError(err)
  return code === 403 || reason === 'LEARNING_OPERATION_DENIED' || reason === 'ACCESS_DENIED'
}

/** 空路线无法标记完成（409） */
export function isLearningEmptyRoadmap(err) {
  return isReason(err, 'LEARNING_EMPTY_ROADMAP')
}

/** 非法路线状态转换（409） */
export function isLearningInvalidTransition(err) {
  return isReason(err, 'LEARNING_INVALID_TRANSITION')
}

/**
 * 乐观锁冲突统一提示（契约 §9.6 / §10.3）。
 * 拦截器对 VERSION_CONFLICT 静默，避免通用 toast；此处由页面给出可操作提示。
 * 用户点击「刷新数据」后回调 onRefresh 重新拉取最新数据。
 */
export function promptVersionConflict(onRefresh) {
  return ElMessageBox.confirm('该任务已被他人更新，请刷新后重试。', '版本冲突（409）', {
    confirmButtonText: '刷新数据',
    cancelButtonText: '关闭',
    type: 'warning'
  })
    .then(() => {
      if (typeof onRefresh === 'function') onRefresh()
    })
    .catch(() => {})
}

// ========== 实验模板域错误码（EXPERIMENT_TEMPLATE_API.md §8） ==========

const TEMPLATE_NOT_FOUND_REASONS = ['TEMPLATE_NOT_FOUND', 'TEMPLATE_VERSION_NOT_FOUND']

/** 模板/版本不存在或不可见（404） */
export function isTemplateNotFound(err) {
  const { code, reason } = normalizeApiError(err)
  return code === 404 || TEMPLATE_NOT_FOUND_REASONS.includes(reason)
}

/** 模板版本不存在或不可见（404） */
export function isTemplateVersionNotFound(err) {
  const { code, reason } = normalizeApiError(err)
  return code === 404 || reason === 'TEMPLATE_VERSION_NOT_FOUND'
}

/** 无模板管理权限 / GUEST 不可访问（403） */
export function isTemplateDenied(err) {
  const { code, reason } = normalizeApiError(err)
  return code === 403 || reason === 'TEMPLATE_OPERATION_DENIED' || reason === 'ACCESS_DENIED'
}

/** Schema 不符合受控子集或为空（400） */
export function isSchemaInvalid(err) {
  const { code, reason } = normalizeApiError(err)
  return code === 400 || reason === 'SCHEMA_INVALID' || reason === 'SCHEMA_EMPTY'
}

/** 源模板非系统模板（400） */
export function isTemplateNotSystem(err) {
  return isReason(err, 'TEMPLATE_NOT_SYSTEM')
}

/** 尝试修改 scope / projectId（409） */
export function isTemplateScopeImmutable(err) {
  return isReason(err, 'TEMPLATE_SCOPE_IMMUTABLE')
}

/** 版本不可编辑（409） */
export function isTemplateVersionNotEditable(err) {
  return isReason(err, 'TEMPLATE_VERSION_NOT_EDITABLE')
}

/** 版本不可发布（409） */
export function isTemplateVersionNotPublishable(err) {
  return isReason(err, 'TEMPLATE_VERSION_NOT_PUBLISHABLE')
}

/** 版本不可废弃（409） */
export function isTemplateVersionNotDeprecatable(err) {
  return isReason(err, 'TEMPLATE_VERSION_NOT_DEPRECATABLE')
}

/** 并发发布版本号冲突（409） */
export function isTemplateVersionConflict(err) {
  return isReason(err, 'TEMPLATE_VERSION_CONFLICT')
}

/** 提取 SCHEMA_INVALID 响应中的字段级错误列表 */
export function schemaErrors(err) {
  const { reason, data } = normalizeApiError(err)
  if (reason !== 'SCHEMA_INVALID' || !data || !Array.isArray(data.errors)) return []
  return data.errors
}

// ========== 实验记录与采集域错误码（EXPERIMENT_RECORD_ACQUISITION_API.md §10） ==========

const EXPERIMENT_NOT_FOUND_REASONS = ['EXPERIMENT_RECORD_NOT_FOUND', 'EXPERIMENT_REVISION_NOT_FOUND']
const COLLECTION_NOT_FOUND_REASONS = [
  'COLLECTION_PLAN_NOT_FOUND',
  'COLLECTION_BATCH_NOT_FOUND',
  'COLLECTION_SNAPSHOT_NOT_FOUND',
  'DATA_IMPORT_NOT_FOUND',
  'MANUAL_RECORD_NOT_FOUND'
]

/** 记录/修订不存在或不可见（404，隐藏存在性） */
export function isExperimentNotFound(err) {
  const { code, reason } = normalizeApiError(err)
  return code === 404 || EXPERIMENT_NOT_FOUND_REASONS.includes(reason)
}

/** 无记录写权限（403） */
export function isExperimentDenied(err) {
  const { code, reason } = normalizeApiError(err)
  return code === 403 || reason === 'EXPERIMENT_OPERATION_DENIED'
}

/** 无导师评审权限（403） */
export function isFeedbackDenied(err) {
  return isReason(err, 'FEEDBACK_OPERATION_DENIED')
}

/** 采集资源不存在或不可见（404） */
export function isCollectionNotFound(err) {
  const { code, reason } = normalizeApiError(err)
  return code === 404 || COLLECTION_NOT_FOUND_REASONS.includes(reason)
}

/** 无计划/批次执行权限（403） */
export function isCollectionDenied(err) {
  const { code, reason } = normalizeApiError(err)
  return code === 403 || reason === 'COLLECTION_OPERATION_DENIED'
}

/** 无转项目权限（403） */
export function isProjectConversionDenied(err) {
  return isReason(err, 'PROJECT_CONVERSION_DENIED')
}

/** 非法记录/修订状态转换（409） */
export function isExperimentInvalidTransition(err) {
  return isReason(err, 'EXPERIMENT_INVALID_TRANSITION')
}

/** 修改非 EDITABLE 修订（409） */
export function isRevisionImmutable(err) {
  return isReason(err, 'EXPERIMENT_REVISION_IMMUTABLE')
}

/** 实验内容不符合固定模板版本（400） */
export function isExperimentContentInvalid(err) {
  return isReason(err, 'EXPERIMENT_CONTENT_INVALID')
}

/** 记录已转换项目（409） */
export function isProjectAlreadyConverted(err) {
  return isReason(err, 'PROJECT_ALREADY_CONVERTED')
}

/** 非法批次状态转换（409） */
export function isBatchInvalidTransition(err) {
  return isReason(err, 'BATCH_INVALID_TRANSITION')
}

/** 修改完成/归档批次（409） */
export function isBatchImmutable(err) {
  return isReason(err, 'BATCH_IMMUTABLE')
}

/** 未完成批次不能生成快照（409） */
export function isBatchNotCompleted(err) {
  return isReason(err, 'BATCH_NOT_COMPLETED')
}

/** 当前修订已有快照且未显式替换（409） */
export function isSnapshotAlreadyBound(err) {
  return isReason(err, 'SNAPSHOT_ALREADY_BOUND')
}

/** 导入修订已确认（409） */
export function isImportAlreadyConfirmed(err) {
  return isReason(err, 'IMPORT_ALREADY_CONFIRMED')
}

/** 幂等键复用于不同请求摘要（409） */
export function isIdempotencyKeyReused(err) {
  return isReason(err, 'IDEMPOTENCY_KEY_REUSED')
}

/** 学习单元/项目与模板版本不一致（409） */
export function isTemplateVersionMismatch(err) {
  return isReason(err, 'TEMPLATE_VERSION_MISMATCH')
}

/** CSV 类型/大小/编码限制（400） */
export function isFileUnsupported(err) {
  const { reason } = normalizeApiError(err)
  return ['FILE_TYPE_UNSUPPORTED', 'FILE_TOO_LARGE', 'FILE_ENCODING_UNSUPPORTED'].includes(reason)
}

/** 映射非法（400） */
export function isImportMappingInvalid(err) {
  return isReason(err, 'IMPORT_MAPPING_INVALID')
}

/** 人工观测非法（400） */
export function isManualRecordInvalid(err) {
  return isReason(err, 'MANUAL_RECORD_INVALID')
}

/** 提取错误响应中的 traceId（契约 §10.5） */
export function errorTraceId(err) {
  const { data } = normalizeApiError(err)
  return data?.traceId || ''
}

// ========== 元器件目录与媒体域错误码（COMPONENT_CATALOG_MEDIA_API.md §10） ==========

const COMPONENT_NOT_FOUND_REASONS = [
  'COMPONENT_CATEGORY_NOT_FOUND',
  'COMPONENT_MANUFACTURER_NOT_FOUND',
  'COMPONENT_NOT_FOUND',
  'COMPONENT_SPEC_CANDIDATE_NOT_FOUND',
  'COMPONENT_SPEC_SNAPSHOT_NOT_FOUND',
  'MEDIA_NOT_FOUND'
]

/** 分类/制造商/型号/候选/快照/媒体不存在或不可见（404，隐藏存在性） */
export function isComponentNotFound(err) {
  const { code, reason } = normalizeApiError(err)
  return code === 404 || COMPONENT_NOT_FOUND_REASONS.includes(reason)
}

/** 无目录/规格确认/媒体写权限（403） */
export function isComponentDenied(err) {
  const { code, reason } = normalizeApiError(err)
  return code === 403
    || ['COMPONENT_OPERATION_DENIED', 'SPEC_CONFIRM_DENIED', 'MEDIA_OPERATION_DENIED', 'ACCESS_DENIED'].includes(reason)
}

/** 同制造商下 MPN 重复（409），data.existingComponentId 指向已有型号 */
export function isComponentDuplicateModel(err) {
  return isReason(err, 'COMPONENT_DUPLICATE_MODEL')
}

/** data.existingComponentId，普通成员不会收到该字段 */
export function duplicateComponentId(err) {
  const { reason, data } = normalizeApiError(err)
  if (reason !== 'COMPONENT_DUPLICATE_MODEL') return null
  return data?.existingComponentId ?? null
}

/** 分类编码重复 */
export function isCategoryDuplicateCode(err) {
  return isReason(err, 'CATEGORY_DUPLICATE_CODE')
}

/** 分类层级超过三级 */
export function isCategoryDepthExceeded(err) {
  return isReason(err, 'CATEGORY_DEPTH_EXCEEDED')
}

/** 分类/制造商被引用，不能停用或父级未启用 */
export function isCatalogInUse(err) {
  const { reason } = normalizeApiError(err)
  return ['CATEGORY_IN_USE', 'MANUFACTURER_IN_USE'].includes(reason)
}

/** 制造商编码或名称重复 */
export function isManufacturerDuplicateCode(err) {
  return isReason(err, 'MANUFACTURER_DUPLICATE_CODE')
}

/** 非法元器件状态转换 */
export function isComponentInvalidTransition(err) {
  return isReason(err, 'COMPONENT_INVALID_TRANSITION')
}

/** DRAFT 无正式快照却尝试激活 */
export function isComponentSpecRequired(err) {
  return isReason(err, 'COMPONENT_SPEC_REQUIRED')
}

/** 候选已确认/拒绝/被替代，不能再次处理 */
export function isCandidateAlreadyDecided(err) {
  return isReason(err, 'SPEC_CANDIDATE_ALREADY_DECIDED')
}

/** 正式规格非法（400） */
export function isComponentSpecInvalid(err) {
  return isReason(err, 'COMPONENT_SPEC_INVALID')
}

/** 来源元数据非法（400） */
export function isSourceMetadataInvalid(err) {
  return isReason(err, 'SOURCE_METADATA_INVALID')
}

/** 图片类型/大小/解码限制（400） */
export function isMediaUnsupported(err) {
  const { reason } = normalizeApiError(err)
  return ['MEDIA_TYPE_UNSUPPORTED', 'MEDIA_TOO_LARGE', 'MEDIA_DECODE_FAILED'].includes(reason)
}

/** 每个型号最多 5 张 READY 图片（409） */
export function isMediaLimitExceeded(err) {
  return isReason(err, 'MEDIA_LIMIT_EXCEEDED')
}

/** 媒体存储/处理失败（500） */
export function isMediaProcessingFailed(err) {
  const { reason } = normalizeApiError(err)
  return ['MEDIA_STORAGE_FAILED', 'MEDIA_PROCESSING_FAILED'].includes(reason)
}

/** 提取 EXPERIMENT_CONTENT_INVALID 的字段级错误列表 */
export function experimentContentErrors(err) {
  const { reason, data } = normalizeApiError(err)
  if (reason !== 'EXPERIMENT_CONTENT_INVALID' || !data || !Array.isArray(data.errors)) return []
  return data.errors
}

// ========== 采购与分批到货域错误码（PROCUREMENT_INVENTORY_API.md §11 / §12） ==========

const PROCUREMENT_DENIED_REASONS = [
  'PROCUREMENT_OPERATION_DENIED',
  'PROCUREMENT_APPROVAL_DENIED',
  'PROCUREMENT_POLICY_DENIED',
  'ACCESS_DENIED'
]

/** 采购单不存在或不可见（404，隐藏存在性） */
export function isProcurementOrderNotFound(err) {
  const { code, reason } = normalizeApiError(err)
  return code === 404 || reason === 'PROCUREMENT_ORDER_NOT_FOUND'
}

/** 附件/媒体不存在或不可见（404；不得展示数量、文件名、mediaId 或派生意图） */
export function isProcurementAttachmentHidden(err) {
  const { code, reason } = normalizeApiError(err)
  return code === 404 || reason === 'MEDIA_NOT_FOUND'
}

/** 无采购/审批/策略/附件权限（403） */
export function isProcurementDenied(err) {
  const { code, reason } = normalizeApiError(err)
  return code === 403 || PROCUREMENT_DENIED_REASONS.includes(reason)
}

/** 采购单、到货或附件版本冲突（409） */
export function isProcurementVersionConflict(err) {
  return isVersionConflict(err)
}

/** 非法状态转换（409） */
export function isProcurementInvalidTransition(err) {
  return isReason(err, 'INVALID_TRANSITION')
}

/** 审批人等于申请人或买手、买手等于人工审批人（409） */
export function isProcurementDutyConflict(err) {
  return isReason(err, 'PROCUREMENT_DUTY_CONFLICT')
}

/** 平台订单号已被另一个采购单登记（409） */
export function isPlatformOrderDuplicated(err) {
  return isReason(err, 'PLATFORM_ORDER_NO_DUPLICATED')
}

/** 实付低于应付但未填差异原因（400） */
export function isPaymentDifferenceNoteRequired(err) {
  return isReason(err, 'PAYMENT_DIFFERENCE_NOTE_REQUIRED')
}

/** 到货数量超过剩余（409）；data 含 orderLineId/remainingQuantity/requestedQuantity */
export function isReceiptOverQuantity(err) {
  return isReason(err, 'RECEIPT_OVER_QUANTITY')
}

/** 提取超量冲突的行级数量，便于 Detail 表格定位但不伪造剩余值 */
export function receiptOverQuantityDetail(err) {
  const { reason, data } = normalizeApiError(err)
  if (reason !== 'RECEIPT_OVER_QUANTITY' || !data) return null
  return {
    orderLineId: data.orderLineId ?? null,
    remainingQuantity: data.remainingQuantity ?? null,
    requestedQuantity: data.requestedQuantity ?? null
  }
}

/** 同一采购单已存在 DRAFT 到货记录（409）；data.receiptId 指向草稿 */
export function isReceiptDraftExists(err) {
  return isReason(err, 'RECEIPT_DRAFT_EXISTS')
}

export function receiptDraftId(err) {
  const { reason, data } = normalizeApiError(err)
  return reason === 'RECEIPT_DRAFT_EXISTS' ? (data?.receiptId ?? null) : null
}

/** 到货记录已确认/已取消（409） */
export function isReceiptAlreadyConfirmed(err) {
  return isReason(err, 'RECEIPT_ALREADY_CONFIRMED')
}

/** 入库事务失败（500）；批次、流水、余额和采购数量均不得改变 */
export function isProcurementTransactionFailed(err) {
  return isReason(err, 'PROCUREMENT_TRANSACTION_FAILED')
}

/** 附件数量/类型/大小限制（409/400） */
export function isProcurementAttachmentLimit(err) {
  return isReason(err, 'PROCUREMENT_ATTACHMENT_LIMIT_EXCEEDED')
}

/** 同一 Idempotency-Key 复用于不同请求（409） */
export function isProcurementIdempotencyReused(err) {
  return isReason(err, 'IDEMPOTENCY_KEY_REUSED')
}

/** 项目内审批人/买手缺失（409） */
export function isProcurementApprovalRequired(err) {
  return isReason(err, 'PROCUREMENT_APPROVAL_REQUIRED')
}