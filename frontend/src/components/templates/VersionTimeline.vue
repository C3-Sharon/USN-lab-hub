<template>
  <ol class="version-timeline">
    <li
      v-for="version in versions"
      :key="version.id"
      class="version-item"
      :class="{
        'version-item--draft': version.status === 'DRAFT',
        'version-item--deprecated': version.status === 'DEPRECATED',
        'version-item--selected': version.id === selectedId
      }"
      @click="$emit('select', version.id)"
    >
      <span class="version-item__dot" aria-hidden="true" />

      <div class="version-item__body">
        <header class="version-item__head">
          <span class="version-item__number" :class="{ 'is-deprecated': version.status === 'DEPRECATED' }">
            {{ version.version }}
          </span>
          <VersionStatusTag :status="version.status" />
          <span class="version-item__fields text-help muted">{{ version.fieldCount }} 个字段</span>
        </header>

        <p class="version-item__changelog">{{ version.changelog || '暂无变更说明' }}</p>

        <p class="version-item__meta text-help muted">
          <template v-if="version.status === 'DRAFT'">更新于 {{ formatDateTime(version.updateTime) }}</template>
          <template v-else>
            {{ version.publishedByName || '未知发布人' }} 发布于 {{ formatDateTime(version.publishedAt) }}
          </template>
        </p>

        <p v-if="version.status === 'DEPRECATED'" class="version-item__deprecated text-help">
          废弃原因：{{ version.deprecatedReason || '未填写' }}（{{ formatDateTime(version.deprecatedAt) }}）
        </p>

        <div v-if="manage" class="version-item__actions" @click.stop>
          <template v-if="version.status === 'DRAFT'">
            <el-button size="small" type="primary" plain @click="$emit('edit', version)">编辑草稿</el-button>
            <el-button size="small" type="primary" @click="$emit('publish', version)">发布</el-button>
          </template>
          <el-button v-else-if="version.status === 'PUBLISHED'" size="small" @click="$emit('deprecate', version)">
            废弃
          </el-button>
          <span v-else class="text-help muted">已废弃，不可再变更</span>
        </div>
      </div>
    </li>
  </ol>
</template>

<script setup>
import { formatDateTime } from '@/utils/format'
import VersionStatusTag from '@/components/templates/VersionStatusTag.vue'

defineProps({
  versions: { type: Array, default: () => [] },
  selectedId: { type: Number, default: null },
  manage: { type: Boolean, default: false }
})

defineEmits(['select', 'edit', 'publish', 'deprecate'])
</script>

<style scoped>
.version-timeline {
  list-style: none;
  margin: 0;
  padding: 0;
  position: relative;
}

.version-item {
  position: relative;
  padding-left: 32px;
  padding-bottom: var(--usn-space-4);
  cursor: pointer;
}

.version-item::before {
  content: '';
  position: absolute;
  left: 7px;
  top: 20px;
  bottom: 0;
  width: 2px;
  background: var(--usn-line);
}

.version-item:last-child::before {
  display: none;
}

.version-item__dot {
  position: absolute;
  left: 0;
  top: 6px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--usn-surface);
  border: 2px solid var(--usn-line);
}

.version-item--draft .version-item__dot {
  border-color: var(--usn-blue-600);
  background: var(--usn-blue-100);
}

.version-item--deprecated .version-item__dot {
  border-color: var(--usn-ink-500);
}

.version-item__body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: var(--usn-space-3);
  border: 1px solid var(--usn-line);
  border-radius: var(--usn-radius-md);
  background: var(--usn-surface);
  transition: border-color 0.2s, box-shadow 0.2s;
}

.version-item--selected .version-item__body {
  border-color: var(--usn-blue-600);
  box-shadow: 0 0 0 2px var(--usn-blue-100);
}

.version-item__head {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
}

.version-item__number {
  font-size: var(--usn-font-size-body);
  font-weight: 700;
  color: var(--usn-ink-900);
  font-variant-numeric: tabular-nums;
}

/* 已废弃版本号加删除线（契约 §9.9） */
.version-item__number.is-deprecated {
  color: var(--usn-ink-500);
  text-decoration: line-through;
}

.version-item__fields {
  margin-left: auto;
}

.version-item__changelog {
  margin: 0;
  font-size: var(--usn-font-size-help);
  color: var(--usn-ink-700);
  line-height: 1.5;
}

.version-item__meta {
  margin: 0;
}

.version-item__deprecated {
  margin: 0;
  color: var(--usn-ink-500);
}

.version-item__actions {
  display: flex;
  align-items: center;
  gap: var(--usn-space-2);
  flex-wrap: wrap;
  margin-top: var(--usn-space-1);
}
</style>
