<template>
  <div class="table-pagination">
    <el-pagination
      :current-page="currentPage"
      :page-size="pageSize"
      :total="total"
      :disabled="loading"
      :page-sizes="[10, 20, 50, 100]"
      :pager-count="5"
      size="default"
      background
      layout="total, sizes, prev, pager, next, jumper"
      @current-change="onPageChange"
      @size-change="onSizeChange"
    />
  </div>
</template>

<script setup lang="ts">
defineProps<{ total: number; loading?: boolean }>()

const currentPage = defineModel<number>('currentPage', { required: true })
const pageSize = defineModel<number>('pageSize', { required: true })
const emit = defineEmits<{ change: [] }>()

function onPageChange(page: number) {
  if (page === currentPage.value) return
  currentPage.value = page
  emit('change')
}

function onSizeChange(size: number) {
  if (size === pageSize.value) return
  pageSize.value = size
  currentPage.value = 1
  emit('change')
}
</script>

<style scoped>
.table-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.table-pagination :deep(.el-pagination) {
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}
.table-pagination :deep(.el-pagination__sizes),
.table-pagination :deep(.el-pagination__jump) {
  margin-left: 0;
  margin-right: 0;
}
@media (max-width: 768px) {
  .table-pagination,
  .table-pagination :deep(.el-pagination) {
    justify-content: flex-start;
  }
}
</style>
