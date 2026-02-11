<template>
  <div class="code-preview-panel">
    <!-- 文件标签栏 (IDE Tabs) -->
    <div class="file-tabs-bar">
      <div class="tabs-container">
        <div
          v-for="(file, index) in fileList"
          :key="file.path"
          class="file-tab"
          :class="{ 'active': selectedFile === file.path }"
          @click="handleFileSelect(file.path)"
        >
          <span class="file-icon" v-html="getFileIcon(file.name)"></span>
          <span class="file-name">{{ file.name }}</span>
        </div>
      </div>
      <div class="tabs-actions">
        <a-button
          type="text"
          size="small"
          @click="copyCode"
          :disabled="!fileContent || !fileContent.content"
          title="复制代码"
        >
          <template #icon>
            <CopyOutlined />
          </template>
        </a-button>
        <a-button
          type="text"
          size="small"
          @click="refreshFileTree"
          :loading="loadingFileTree"
          title="刷新"
        >
          <template #icon>
            <ReloadOutlined />
          </template>
        </a-button>
      </div>
    </div>

    <!-- 代码编辑区 -->
    <div class="code-editor-area">
      <div v-if="loadingFileContent" class="code-loading">
        <a-spin size="small" />
        <span>加载中...</span>
      </div>
      <div v-else-if="!selectedFile" class="code-placeholder">
        <span>选择文件查看代码</span>
      </div>
      <div v-else-if="fileContent && fileContent.content" class="code-display">
        <pre class="code-pre"><code v-html="highlightedCode"></code></pre>
      </div>
      <div v-else class="code-error">
        <span>无法加载文件</span>
        <a-button type="link" size="small" @click="handleFileSelect(selectedFile)">重试</a-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { CopyOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import hljs from 'highlight.js'
import 'highlight.js/styles/vs2015.css'

interface Props {
  fileTree: API.FileTreeNodeVO | null
  fileContent: API.FileContentVO | null
  selectedFilePath: string
  loadingFileTree: boolean
  loadingFileContent: boolean
}

interface Emits {
  (e: 'select-file', filePath: string): void
  (e: 'refresh-tree'): void
}

const props = withDefaults(defineProps<Props>(), {
  fileTree: null,
  fileContent: null,
  selectedFilePath: '',
  loadingFileTree: false,
  loadingFileContent: false,
})

const emit = defineEmits<Emits>()
const selectedFile = ref(props.selectedFilePath)

// 只获取文件（不含目录）
const fileList = computed(() => {
  if (!props.fileTree) return []
  const files: Array<{ path: string; name: string }> = []

  const collectFiles = (node: API.FileTreeNodeVO) => {
    if (!node) return
    if (node.type === 'file') {
      files.push({ path: node.path || '', name: node.name || '' })
    }
    if (node.children) {
      node.children.forEach(collectFiles)
    }
  }

  collectFiles(props.fileTree)
  return files
})

// 文件图标 SVG
const fileIconSvgs: { [key: string]: string } = {
  vue: `<svg viewBox="0 0 128 128" width="16" height="16"><path fill="#42b883" d="M78.8 10L64 35.4 49.2 10H0l64 110L128 10z"/><path fill="#35495e" d="M78.8 10L64 35.4 49.2 10H25.6L64 76.2 102.4 10z"/></svg>`,
  js: `<svg viewBox="0 0 128 128" width="16" height="16"><path fill="#f0db4f" d="M2 2h124v124H2z"/><path fill="#323330" d="M35.2 101.4c2.6 4.4 5 7.6 10.8 7.6 4.4 0 7.2-1.8 7.2-5.6 0-4.8-3.6-6.4-9.6-9.2l-3.2-1.4c-9.6-4-16-9.2-16-20 0-10 7.6-17.6 19.4-17.6 8.4 0 14.4 2.8 18.8 10.4l-10.2 6.6c-2.2-4-4.6-5.6-8.4-5.6-3.8 0-6.2 2.4-6.2 5.6 0 3.8 2.4 5.4 8 7.8l3.2 1.4c11.2 4.8 17.6 9.6 17.6 20.6 0 11.8-9.2 18.2-21.6 18.2-12.2 0-20-5.8-23.8-13.4l10-5.4zm44.6-1.2c2 3.4 3.8 6.4 8.2 6.4 4.2 0 6.8-1.6 6.8-8V56.8h12.4v42c0 13.2-7.8 19.2-19 19.2-10.2 0-16.2-5.2-19.2-11.6l10.8-6.2z"/></svg>`,
  jsx: `<svg viewBox="0 0 128 128" width="16" height="16"><circle cx="64" cy="64" r="11.4" fill="#61dafb"/><path fill="none" stroke="#61dafb" stroke-width="8.4" d="M107.3 45.2c-2.2-.8-4.5-1.6-6.9-2.3.6-2.4 1.1-4.8 1.5-7.1 2.1-13.2-.2-22.5-6.6-26.1-1.9-1.1-4-1.6-6.4-1.6-7 0-15.9 5.2-24.9 13.9-9-8.7-17.9-13.9-24.9-13.9-2.4 0-4.5.5-6.4 1.6-6.4 3.7-8.7 13-6.6 26.1.4 2.3.9 4.7 1.5 7.1-2.4.7-4.7 1.4-6.9 2.3C8.2 50.4.5 58.1.5 64s7.7 13.5 20.2 18.8c2.2.8 4.5 1.6 6.9 2.3-.6 2.4-1.1 4.8-1.5 7.1-2.1 13.2.2 22.5 6.6 26.1 1.9 1.1 4 1.6 6.4 1.6 7 0 15.9-5.2 24.9-13.9 9 8.7 17.9 13.9 24.9 13.9 2.4 0 4.5-.5 6.4-1.6 6.4-3.7 8.7-13 6.6-26.1-.4-2.3-.9-4.7-1.5-7.1 2.4-.7 4.7-1.4 6.9-2.3 12.5-5.3 20.2-13 20.2-18.8s-7.7-13.5-20.2-18.8z"/></svg>`,
  ts: `<svg viewBox="0 0 128 128" width="16" height="16"><rect fill="#3178c6" width="128" height="128" rx="6"/><path fill="#fff" d="M22.7 64V46.5h52.6V64H56.1v47.8H41.9V64H22.7zm56.2-17.5h15.3v30.9c0 3.5.3 6.1.9 7.7.9 2.5 2.5 4.5 4.8 5.9 2.3 1.4 5.2 2.1 8.6 2.1 3.5 0 6.4-.7 8.7-2.2 2.3-1.5 3.9-3.5 4.7-6 .5-1.6.8-4.5.8-8.6V46.5h15.3v29.8c0 7.2-.6 12.4-1.7 15.6-1.5 4.2-4 7.7-7.5 10.5-3.5 2.8-8.2 4.2-14.1 4.2-6.8 0-12.1-1.6-15.9-4.7-3.8-3.1-6.2-7.1-7.2-12-.6-2.8-.9-7.8-.9-15V46.5h.2z"/></svg>`,
  tsx: `<svg viewBox="0 0 128 128" width="16" height="16"><rect fill="#3178c6" width="128" height="128" rx="6"/><circle cx="90" cy="90" r="11" fill="#61dafb"/><path fill="#fff" d="M22.7 64V46.5h52.6V64H56.1v47.8H41.9V64H22.7z"/></svg>`,
  html: `<svg viewBox="0 0 128 128" width="16" height="16"><path fill="#e44d26" d="M19.1 113.4L9.6 4h108.8l-9.5 109.3L64 122.5z"/><path fill="#f16529" d="M64 116.7l38.2-10.6 8.1-91.1H64z"/><path fill="#ebebeb" d="M64 52.6H45.3l-1.3-14.6H64V23.8H28.9l.3 3.7 3.5 39.1H64zm0 35.2l-.1 0-15.7-4.2-1-11.3H33l2 22.2L64 102.3l.1 0z"/><path fill="#fff" d="M64 52.6v14.2h17.4l-1.6 18-15.8 4.3v14.7l29-8 .2-2.5 3.3-37.1.4-3.6H64zm0-28.8v14.2h33.8l.3-3.2.6-7.3.3-3.7H64z"/></svg>`,
  css: `<svg viewBox="0 0 128 128" width="16" height="16"><path fill="#1572b6" d="M19.1 113.4L9.6 4h108.8l-9.5 109.3L64 122.5z"/><path fill="#33a9dc" d="M64 116.7l38.2-10.6 8.1-91.1H64z"/><path fill="#ebebeb" d="M64 52.6H45.3l-1.3-14.6H64V23.8H28.9l.3 3.7 3.5 39.1H64zm0 35.2l-.1 0-15.7-4.2-1-11.3H33l2 22.2L64 102.3l.1 0z"/><path fill="#fff" d="M64 52.6v14.2h17.4l-1.6 18-15.8 4.3v14.7l29-8 .2-2.5 3.3-37.1.4-3.6H64zm0-28.8v14.2h33.8l.3-3.2.6-7.3.3-3.7H64z"/></svg>`,
  scss: `<svg viewBox="0 0 128 128" width="16" height="16"><path fill="#cd6799" d="M64 2a62 62 0 100 124A62 62 0 0064 2zm35.3 73.3c-1.4 7.1-7.6 10.4-13.4 12.2 6.1 3.1 8.2 8.6 5 14.1-3.8 6.5-13.2 8.6-21.4 5.8-6-2-10.2-6.2-11.2-12.4 0 0 5.2 1.2 5.6 5.4.4 4.2 3.8 7.2 9.2 7.4 6.8.2 12.2-3.2 12.6-8.4.4-5.8-4.2-8.4-10.6-9.2l-1.8-.2c.6-1.2 1.8-2.2 3.4-3 5.4-2.6 14.2-2.8 14.8-10.8.4-5.4-4.6-9-11.2-9.6-8.8-.8-16.2 2.8-19.8 8.4-2.2 3.4-2.8 7-2.2 10.8-3.4-1.6-5-4.6-5.4-7.8-.8-6.4 2.6-13.2 9.4-17.4 5.6-3.4 12-4.6 18.8-4 9.8.8 18.2 5.8 19 14.8.2 1.2.2 2.6-.8 3.9z"/></svg>`,
  json: `<svg viewBox="0 0 128 128" width="16" height="16"><path fill="#5b5b5b" d="M64 6.8C32.4 6.8 6.8 32.4 6.8 64s25.6 57.2 57.2 57.2S121.2 95.6 121.2 64 95.6 6.8 64 6.8zm-6.4 93.6c-12.8 0-20-5.6-20-18.4V70h10v12c0 6 2.8 8.4 8.4 8.4h3.2v10h-1.6zm0-72.8h1.6v10H56c-5.6 0-8.4 2.4-8.4 8.4v12H37.6V46.4c0-12.8 7.2-18.8 20-18.8zm12.8 72.8c12.8 0 20-5.6 20-18.4V70h-10v12c0 6-2.8 8.4-8.4 8.4h-3.2v10h1.6zm0-72.8h-1.6v10H72c5.6 0 8.4 2.4 8.4 8.4v12h10V46.4c0-12.8-7.2-18.8-20-18.8z"/></svg>`,
  md: `<svg viewBox="0 0 128 128" width="16" height="16"><rect fill="#083fa1" width="128" height="128" rx="6"/><path fill="#fff" d="M20 94V34h16l16 24 16-24h16v60H72V55.6L56 79.6 40 55.6V94H20zm80 0l-24-30h16V34h16v30h16L108 94z"/></svg>`,
  py: `<svg viewBox="0 0 128 128" width="16" height="16"><path fill="#3776ab" d="M63.4 2C38.8 2 40.8 12.8 40.8 12.8v11.2h23.2v3.2H25.6S2 24.4 2 49.6s20.6 24.2 20.6 24.2h12.2V62.2s-.6-12.2 12-12.2h22.8s11.6.2 11.6-11.2V16.4S83.4 2 63.4 2zm-12.6 8.4c2.2 0 4 1.8 4 4s-1.8 4-4 4-4-1.8-4-4 1.8-4 4-4z"/><path fill="#ffd43b" d="M64.6 126c24.6 0 22.6-10.8 22.6-10.8v-11.2H64v-3.2h38.4S126 103.6 126 78.4s-20.6-24.2-20.6-24.2H93.2v11.6s.6 12.2-12 12.2H58.4s-11.6-.2-11.6 11.2v22.4S44.6 126 64.6 126zm12.6-8.4c-2.2 0-4-1.8-4-4s1.8-4 4-4 4 1.8 4 4-1.8 4-4 4z"/></svg>`,
  java: `<svg viewBox="0 0 128 128" width="16" height="16"><path fill="#ea2d2e" d="M47.6 98s-4.2 2.4 3 3.2c8.6 1 13 .8 22.4-1 0 0 2.4 1.6 5.8 3-20.8 8.8-47-0.4-31.2-5.2zm-2.6-11.8s-4.6 3.4 2.4 4.2c9.2 1 16.4 1 28.8-1.4 0 0 1.8 1.8 4.4 2.8-25 7.4-52.8.6-35.6-5.6z"/><path fill="#ea2d2e" d="M67 73.4c5.2 6-1.4 11.4-1.4 11.4s13.2-6.8 7.2-15.4c-5.6-8-10-12 13.6-25.6 0 0-37.2 9.2-19.4 29.6z"/><path fill="#ea2d2e" d="M102.4 108.2s3 2.6-3.4 4.6c-12.2 3.8-50.8 5-61.6.2-3.8-1.8 3.4-4.2 5.8-4.8 2.4-.6 3.8-.4 3.8-.4-4.4-3-28.2 6-12.2 8.6 44 7.2 80.2-3.2 67.6-8.2zM49.8 70.4s-20 4.8-7.2 6.4c5.4.8 16.2.6 26.2-.2 8.2-.8 16.4-2.4 16.4-2.4s-2.8 1.2-4.8 2.6c-20 5.2-58.2 2.8-47.2-2.6 9.4-4.4 16.6-3.8 16.6-3.8zm35.2 19.6c20.2-10.4 10.8-20.6 4.4-19.2-1.6.4-2.4.6-2.4.6s.6-1 1.8-1.4c13-4.6 23 13.6-4.2 20.8 0 0 .2-.2.4-.8z"/><path fill="#ea2d2e" d="M73.6 2s11.2 11.2-10.6 28.4c-17.4 13.8-4 21.6 0 30.6-10.2-9.2-17.6-17.2-12.6-24.8C58 25.4 78.2 19.8 73.6 2z"/><path fill="#ea2d2e" d="M50.8 123.6c19.4 1.2 49.2-.8 50-10 0 0-1.4 3.4-16 6.2-16.4 3-36.8 2.6-48.8.8 0 0 2.4 2 14.8 3z"/></svg>`,
  default: `<svg viewBox="0 0 128 128" width="16" height="16"><path fill="#858585" d="M28 8h50l22 22v90H28V8z"/><path fill="#aaa" d="M78 8l22 22H78z"/></svg>`,
}

const getFileIcon = (fileName: string): string => {
  const ext = fileName.split('.').pop()?.toLowerCase() || ''
  return fileIconSvgs[ext] || fileIconSvgs.default
}

// 代码高亮
const highlightedCode = computed(() => {
  if (!props.fileContent?.content) return ''
  try {
    const lang = detectLanguage(props.fileContent.filePath || '')
    const highlighted = hljs.highlight(props.fileContent.content, {
      language: lang,
      ignoreIllegals: true,
    }).value

    const lines = highlighted.split('\n')
    return lines
      .map((line, i) => `<div class="code-line"><span class="line-num">${i + 1}</span><span class="line-code">${line || ' '}</span></div>`)
      .join('')
  } catch {
    return props.fileContent.content
  }
})

const detectLanguage = (filePath: string): string => {
  const ext = filePath.split('.').pop()?.toLowerCase() || ''
  const langMap: { [key: string]: string } = {
    js: 'javascript', jsx: 'javascript', ts: 'typescript', tsx: 'typescript',
    py: 'python', java: 'java', html: 'html', htm: 'html', vue: 'html',
    css: 'css', scss: 'scss', json: 'json', md: 'markdown', xml: 'xml',
  }
  return langMap[ext] || ext
}

const handleFileSelect = (path: string) => {
  selectedFile.value = path
  emit('select-file', path)
}

const copyCode = async () => {
  if (!props.fileContent?.content) return
  try {
    await navigator.clipboard.writeText(props.fileContent.content)
    message.success('已复制')
  } catch {
    const ta = document.createElement('textarea')
    ta.value = props.fileContent.content
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    message.success('已复制')
  }
}

const refreshFileTree = () => emit('refresh-tree')

watch(() => props.selectedFilePath, (v) => { selectedFile.value = v })

// 自动选择第一个文件
watch(fileList, (list) => {
  if (list.length > 0 && !selectedFile.value) {
    handleFileSelect(list[0].path)
  }
}, { immediate: true })
</script>

<style scoped>
.code-preview-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #1e1e1e;
  border-radius: 4px;
  overflow: hidden;
}

/* 文件标签栏 */
.file-tabs-bar {
  display: flex;
  align-items: center;
  background: #252526;
  border-bottom: 1px solid #3c3c3c;
  min-height: 35px;
}

.tabs-container {
  display: flex;
  flex: 1;
  overflow-x: auto;
  scrollbar-width: none;
}

.tabs-container::-webkit-scrollbar {
  display: none;
}

.file-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: #2d2d2d;
  color: #969696;
  font-size: 12px;
  cursor: pointer;
  border-right: 1px solid #3c3c3c;
  white-space: nowrap;
  transition: background 0.15s, color 0.15s;
}

.file-tab:hover {
  background: #323232;
  color: #ccc;
}

.file-tab.active {
  background: #1e1e1e;
  color: #fff;
  border-bottom: 2px solid #007acc;
}

.file-icon {
  display: inline-flex;
  align-items: center;
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}

.file-name {
  font-family: 'Segoe UI', sans-serif;
}

.tabs-actions {
  display: flex;
  padding: 0 8px;
  gap: 4px;
}

.tabs-actions :deep(.ant-btn) {
  color: #858585;
}

.tabs-actions :deep(.ant-btn:hover) {
  color: #fff;
}

/* 代码编辑区 */
.code-editor-area {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.code-display {
  flex: 1;
  overflow: auto;
}

.code-pre {
  margin: 0;
  padding: 12px 0;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  background: #1e1e1e;
  color: #d4d4d4;
}

.code-pre code {
  display: block;
}

.code-pre :deep(.code-line) {
  display: flex;
  min-height: 1.6em;
}

.code-pre :deep(.line-num) {
  display: inline-block;
  width: 45px;
  min-width: 45px;
  padding-right: 12px;
  text-align: right;
  color: #858585;
  user-select: none;
  flex-shrink: 0;
}

.code-pre :deep(.line-code) {
  flex: 1;
  padding-left: 8px;
}

.code-loading,
.code-placeholder,
.code-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  color: #858585;
  gap: 8px;
  font-size: 13px;
}

/* highlight.js 覆盖 */
:deep(.hljs-keyword) { color: #569cd6; }
:deep(.hljs-string) { color: #ce9178; }
:deep(.hljs-comment) { color: #6a9955; font-style: italic; }
:deep(.hljs-number) { color: #b5cea8; }
:deep(.hljs-function) { color: #dcdcaa; }
:deep(.hljs-tag) { color: #569cd6; }
:deep(.hljs-attr) { color: #9cdcfe; }
:deep(.hljs-title) { color: #dcdcaa; }
:deep(.hljs-variable) { color: #9cdcfe; }
:deep(.hljs-built_in) { color: #4ec9b0; }

@media (max-width: 768px) {
  .file-tab {
    padding: 6px 10px;
    font-size: 11px;
  }
  .code-pre {
    font-size: 11px;
  }
  .code-pre :deep(.line-num) {
    width: 35px;
    padding-right: 8px;
  }
}
</style>
