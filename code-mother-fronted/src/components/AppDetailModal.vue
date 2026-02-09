<template>
  <a-modal v-model:open="visible" title="应用详情" :footer="null" width="500px">
    <div class="app-detail-content">
      <!-- 应用基础信息 -->
      <div class="app-basic-info">
        <div class="info-item">
          <span class="info-label">创建者：</span>
          <UserInfo :user="app?.user" size="small" />
        </div>
        <div class="info-item">
          <span class="info-label">创建时间：</span>
          <span>{{ formatTime(app?.createTime) }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">生成类型：</span>
          <a-tag v-if="app?.codeGenType" color="blue">
            {{ formatCodeGenType(app.codeGenType) }}
          </a-tag>
          <span v-else>未知类型</span>
        </div>
      </div>

      <!-- 使用量统计信息 -->
      <div class="usage-statistics">
        <div class="statistics-title">使用统计</div>
        <div class="statistics-content">
          <div class="info-item">
            <span class="info-label">更新时间：</span>
            <span>{{ formatTime(app?.updateTime) }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">输入Token：</span>
            <span>{{ formatNumber(app?.totalInputTokens) }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">输出Token：</span>
            <span>{{ formatNumber(app?.totalOutputTokens) }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">总消耗Token：</span>
            <span class="highlight">{{ formatNumber(Number(app?.totalInputTokens || 0) + Number(app?.totalOutputTokens || 0)) }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">总消耗时间：</span>
            <span>{{ formatDuration(app?.totalConsumeTime) }}</span>
          </div>
        </div>
      </div>

      <!-- 操作栏（仅本人或管理员可见） -->
      <div v-if="showActions" class="app-actions">
        <a-space>
          <a-button type="primary" @click="handleEdit">
            <template #icon>
              <EditOutlined />
            </template>
            修改
          </a-button>
          <a-popconfirm
            title="确定要删除这个应用吗？"
            @confirm="handleDelete"
            ok-text="确定"
            cancel-text="取消"
          >
            <a-button danger>
              <template #icon>
                <DeleteOutlined />
              </template>
              删除
            </a-button>
          </a-popconfirm>
        </a-space>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import {computed} from 'vue'
import {DeleteOutlined, EditOutlined} from '@ant-design/icons-vue'
import UserInfo from './UserInfo.vue'
import {formatTime} from '@/utils/time'
import {formatCodeGenType} from "../utils/codeGenTypes.ts";

interface Props {
  open: boolean
  app?: API.AppVO
  showActions?: boolean
}

interface Emits {
  (e: 'update:open', value: boolean): void
  (e: 'edit'): void
  (e: 'delete'): void
}

const props = withDefaults(defineProps<Props>(), {
  showActions: false,
})

const emit = defineEmits<Emits>()

const visible = computed({
  get: () => props.open,
  set: (value) => emit('update:open', value),
})

// 格式化数字 - 添加千位分隔符
const formatNumber = (num?: number): string => {
  if (num === undefined || num === null) return '0'
  return num.toLocaleString('en-US')
}

// 格式化时间长度
const formatDuration = (ms?: number): string => {
  if (ms === undefined || ms === null) return '0ms'
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(2)}s`
  return `${(ms / 60000).toFixed(2)}min`
}

const handleEdit = () => {
  emit('edit')
}

const handleDelete = () => {
  emit('delete')
}
</script>

<style scoped>
.app-detail-content {
  padding: 8px 0;
}

.app-basic-info {
  margin-bottom: 24px;
}

.usage-statistics {
  margin: 24px 0;
  padding: 16px;
  background-color: #fafafa;
  border-radius: 4px;
  border-left: 3px solid #1890ff;
}

.statistics-title {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 12px;
  color: #1890ff;
}

.statistics-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.info-item {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}

.info-label {
  width: 90px;
  color: #666;
  font-size: 14px;
  flex-shrink: 0;
}

.highlight {
  color: #ff4d4f;
  font-weight: 600;
}

.app-actions {
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}
</style>
