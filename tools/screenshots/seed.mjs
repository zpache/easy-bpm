// 演示数据准备：通过 OpenAPI + 控制台 API 造一批实例/审批/转办/抄送，让各页面截图有数据。
// 幂等：实例总数 >= 6 时跳过。用法：node seed.mjs
const API = 'http://127.0.0.1:8082'
const OPEN_H = {
  'X-App-Key': 'ak_demo',
  'X-App-Secret': 'sk_demo_2026',
  'Content-Type': 'application/json',
}

async function open(method, path, body) {
  const res = await fetch(`${API}/open/v1${path}`, {
    method,
    headers: OPEN_H,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await res.text()
  if (!res.ok) throw new Error(`OPEN ${method} ${path} -> ${res.status} ${text}`)
  const parsed = text ? JSON.parse(text) : null
  return parsed?.data ?? parsed
}

async function api(method, path, body, token) {
  const res = await fetch(`${API}/api${path}`, {
    method,
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await res.text()
  if (!res.ok) throw new Error(`API ${method} ${path} -> ${res.status} ${text}`)
  const parsed = text ? JSON.parse(text) : null
  return parsed?.data ?? parsed
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function login(username, tenantCode = 'demo') {
  const res = await fetch(`${API}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ tenantCode, username, password: 'admin123' }),
  })
  if (!res.ok) throw new Error(`login ${username} -> ${res.status} ${await res.text()}`)
  const body = await res.json()
  return body.data?.token ?? body.token
}

const INITIATORS = {
  u1001: { name: '张三', dept: 'D-SALES' },
  u1002: { name: '李四', dept: 'D-SALES' },
}

async function start(flowCode, userId, title, formData, key) {
  const who = INITIATORS[userId]
  const inst = await open('POST', '/instances', {
    flowCode,
    initiatorId: userId,
    initiatorName: who.name,
    initiatorDeptCode: who.dept,
    title,
    formData,
    idempotencyKey: key,
  })
  return inst.id || inst.instanceId || inst.instanceId
}

async function main() {
  const adminToken = await login('admin')
  const existing = await api('GET', '/instances?page=1&size=1', undefined, adminToken)
  const total = existing?.total ?? (existing?.list || []).length
  console.log(`当前 ${total} 个实例（幂等键兜底，重复发起返回同一实例）…`)

  // 1)（干净库无历史流程，无需清理）

  // 2) 重建 leave-cc 流程（请假审批含抄送：主管审批 → 抄送备案 → 结束）
  const flows = await api('GET', '/flows?page=1&size=50', undefined, adminToken)
  let leaveCc = flows.list.find((f) => f.code === 'leave-cc')
  if (!leaveCc) {
    const schema = {
      nodes: [
        { id: 'start', type: 'START', name: '发起人' },
        { id: 'n1', type: 'APPROVAL', name: '直属主管审批',
          props: { approverType: 'INITIATOR_LEADER', leaderLevel: 1, mode: 'ANY' } },
        { id: 'cc1', type: 'CC', name: '抄送备案',
          props: { ccUsers: ['u1003', 'u1001'] } },
        { id: 'end', type: 'END', name: '结束' },
      ],
    }
    const form = {
      fields: [
        { key: 'days', label: '请假天数', type: 'number', required: true },
        { key: 'reason', label: '请假事由', type: 'textarea', required: true },
      ],
    }
    await api('POST', '/flows', {
      code: 'leave-cc',
      name: '请假审批（含抄送）',
      category: '人事',
      icon: 'calendar',
      description: '直属主管审批后自动抄送发起人与人事备案',
      formSchema: form,
      flowSchema: schema,
    }, adminToken)
    await api('POST', '/flows/leave-cc/publish', { changelog: '演示流程初始化' }, adminToken)
    console.log('leave-cc 已创建并发布')
  } else {
    console.log('leave-cc 已存在')
  }

  // 3) 发起一批实例
  const ids = {}
  ids.A = await start('leave', 'u1001', '事假申请-一天', { days: 1, reason: '家中办事，请事假一天' }, 'demo-seed-A')
  ids.B = await start('leave-cc', 'u1001', '年假申请-三天', { days: 3, reason: '休年假三天' }, 'demo-seed-B')
  ids.C = await start('leave', 'u1001', '病假申请-两天', { days: 2, reason: '感冒发烧，病假两天' }, 'demo-seed-C')
  ids.D = await start('expense', 'u1001', '团队聚餐报销', { amount: 3000, reason: '季度团队聚餐' }, 'demo-seed-D')
  ids.E = await start('expense', 'u1001', '差旅费报销', { amount: 8800, reason: '深圳客户现场支持差旅' }, 'demo-seed-E')
  ids.F = await start('leave-cc', 'u1001', '调休申请', { days: 5, reason: '项目结束调休五天' }, 'demo-seed-F')
  console.log('实例已发起:', ids)
  await sleep(1500)

  // 4) 各节点处理
  const findTodo = async (instanceId) => {
    for (let i = 0; i < 10; i++) {
      const detail = await open('GET', `/instances/${instanceId}`)
      const tasks = detail.pendingTasks || []
      if (tasks.length) return tasks[0]
      await sleep(500)
    }
    return null
  }

  const handle = async (instanceId, action, userId, comment) => {
    const task = await findTodo(instanceId)
    if (!task) throw new Error(`实例 ${instanceId} 找不到待办`)
    await open('POST', `/tasks/${task.taskId}/${action}`, { userId, comment })
  }

  // A：主管同意 → 人事同意 → APPROVED
  await handle(ids.A, 'approve', 'u1002', '情况属实，同意')
  await handle(ids.A, 'approve', 'u1004', '已备案，同意')
  // B（leave-cc 单审批节点）：主管同意 → APPROVED，触发抄送（u1003、u1001）
  await handle(ids.B, 'approve', 'u1002', '同意')
  // C：主管同意 → 人事待办
  await handle(ids.C, 'approve', 'u1002', '同意，注意休息')
  // D：主管同意 → 金额≤5000 直接过 → APPROVED
  await handle(ids.D, 'approve', 'u1002', '同意')
  // E：主管同意 → 财务待办
  await handle(ids.E, 'approve', 'u1002', '同意')
  // F（leave-cc）：主管驳回 → REJECTED（含抄送记录）
  await handle(ids.F, 'reject', 'u1002', '项目未收尾，驳回')
  console.log('主管节点处理完成')

  // 5) C（人事 u1004）、E（财务 u1003）转办给管理员（u1001 张三）
  const transferFromCurrent = async (instanceId, comment) => {
    const detail = await open('GET', `/instances/${instanceId}`)
    const t = (detail.pendingTasks || [])[0]
    if (!t) throw new Error(`实例 ${instanceId} 无待办可转办`)
    await open('POST', `/tasks/${t.taskId}/transfer`, {
      userId: t.assigneeId, targetUserId: 'u1001', comment,
    })
    return t
  }
  const tc = await transferFromCurrent(ids.C, '赵六休假，请张三代审')
  const te = await transferFromCurrent(ids.E, '王五出差，请张三代审')
  console.log(`转办完成：C[${tc.nodeName}] E[${te.nodeName}] → 张三`)

  // 6) 张三代审 E → APPROVED（张三「已办」有记录，C 留在张三待办）
  await open('POST', `/tasks/${te.taskId}/approve`, { userId: 'u1001', comment: '代审同意，注意票据合规' })

  const detail = await open('GET', `/instances/${ids.C}`)
  console.log('造数完成。期望状态：A 通过 / B 人事待办 / C 张三待办 / D 通过 / E 财务待办 / F 已驳回')
  console.log('C 当前待办:', JSON.stringify(detail.pendingTasks))
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
