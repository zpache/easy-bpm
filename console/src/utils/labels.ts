export function statusText(status?: string): string {
  switch (status) {
    case 'RUNNING': return '进行中'
    case 'APPROVED': return '已通过'
    case 'REJECTED': return '已驳回'
    case 'CANCELED': return '已撤回'
    case 'PUBLISHED': return '已发布'
    case 'DRAFT': return '草稿'
    case 'DISABLED': return '已停用'
    default: return status ?? '-'
  }
}

export function statusType(status?: string): 'primary' | 'success' | 'danger' | 'warning' | 'info' {
  switch (status) {
    case 'RUNNING': case 'PUBLISHED': return 'primary'
    case 'APPROVED': return 'success'
    case 'REJECTED': return 'danger'
    case 'CANCELED': case 'DISABLED': return 'warning'
    case 'DRAFT': return 'info'
    default: return 'info'
  }
}

export function actionText(action?: string): string {
  switch (action) {
    case 'SUBMIT': return '发起'
    case 'APPROVE': return '同意'
    case 'REJECT': return '驳回'
    case 'TRANSFER': return '转交'
    case 'REVOKE': return '撤回'
    case 'RESUBMIT': return '重新提交'
    case 'CC': return '抄送'
    case 'AUTO_PASS': return '自动通过'
    default: return action ?? '-'
  }
}

export function actionType(action?: string): 'primary' | 'success' | 'danger' | 'warning' | 'info' {
  switch (action) {
    case 'SUBMIT': return 'primary'
    case 'APPROVE': return 'success'
    case 'REJECT': return 'danger'
    case 'TRANSFER': return 'warning'
    case 'REVOKE': return 'warning'
    case 'RESUBMIT': return 'primary'
    case 'CC': return 'info'
    default: return 'info'
  }
}
