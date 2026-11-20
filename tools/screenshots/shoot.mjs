// 控制台截图脚本：playwright-core + 本机 Chrome。
// 前置：后端 8082、console 8091 已启动，且已执行 node seed.mjs。
// 输出：docs/images/*.png。用法：node shoot.mjs
import { chromium } from 'playwright-core'
import { mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const CONSOLE = 'http://localhost:8091'
const CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
const API = 'http://127.0.0.1:8082'
const OUT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../docs/images')
mkdirSync(OUT, { recursive: true })

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function apiLogin(tenantCode, username) {
  const res = await fetch(`/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ tenantCode, username, password: 'admin123' }),
  })
  if (!res.ok) throw new Error(`login ${username} -> ${res.status} ${await res.text()}`)
  const body = await res.json()
  return body.data ?? body
}

async function injectAuth(page, tenantCode, username) {
  const data = await apiLogin(tenantCode, username)
  const user = {
    username: data.username,
    name: data.user?.displayName,
    role: data.user?.role,
  }
  await page.goto(`${CONSOLE}/login`, { waitUntil: 'domcontentloaded' })
  await page.evaluate(([token, user]) => {
    if (user.role === 'PLATFORM_ADMIN') {
      localStorage.setItem('easy_bpm_platform_token', token)
      localStorage.setItem('easy_bpm_platform_user', JSON.stringify(user))
    }
    localStorage.setItem('easy_bpm_token', token)
    localStorage.setItem('easy_bpm_user', JSON.stringify(user))
  }, [data.token, user])
}

async function shoot(page, name, route, { fullPage = false, before } = {}) {
  await page.goto(`${CONSOLE}${route}`, { waitUntil: 'networkidle' })
  await sleep(900)
  if (before) await before()
  await page.screenshot({ path: `${OUT}/${name}.png`, fullPage })
  console.log('✓', name)
}

async function main() {
  const browser = await chromium.launch({ executablePath: CHROME, headless: true })

  // ---- 租户管理员（admin）视角 ----
  const ctx = await browser.newContext({
    viewport: { width: 1600, height: 900 },
    deviceScaleFactor: 2,
  })
  const page = await ctx.newPage()

  // 登录页（未登录状态）
  await page.goto(`${CONSOLE}/login`, { waitUntil: 'networkidle' })
  await sleep(800)
  await page.screenshot({ path: `${OUT}/01-login.png` })
  console.log('✓ 01-login')

  await injectAuth(page, 'demo', 'admin')

  await shoot(page, '02-dashboard', '/dashboard', { fullPage: true })
  await shoot(page, '03-flow-list', '/flows')

  // 设计器（expense：含抄送节点 + 条件财务审批）
  await shoot(page, '04-flow-design', '/flows/expense/design', { fullPage: true })

  // 设计器：点击「＋」弹出节点类型选择菜单（最佳努力，失败跳过）
  try {
    const plus = page.locator('.designer-add, [class*="add"] button').first()
    await page.goto(`${CONSOLE}/flows/expense/design`, { waitUntil: 'networkidle' })
    await sleep(900)
    await page.getByText('＋', { exact: true }).first().click({ timeout: 3000 })
    await sleep(700)
    await page.screenshot({ path: `${OUT}/04b-flow-design-insert.png` })
    console.log('✓ 04b-flow-design-insert')
    await page.keyboard.press('Escape')
  } catch (e) {
    console.log('skip 04b:', e.message.split('\n')[0])
  }

  await shoot(page, '05-todo', '/workbench/todo')
  await shoot(page, '06-done', '/workbench/done')
  await shoot(page, '07-submitted', '/workbench/submitted', { fullPage: true })
  await shoot(page, '08-cc', '/workbench/cc', { fullPage: true })
  await shoot(page, '09-all-instances', '/workbench/all-instances', { fullPage: true })

  // 实例详情：取「病假申请」（张三待办中，含时间线与转办/强制转办操作区）
  try {
    const res = await page.evaluate(async () => {
      const token = localStorage.getItem('easy_bpm_token')
      const r = await fetch('/instances?page=1&size=50', {
        headers: { Authorization: `Bearer ${token}` },
      })
      return r.json()
    })
    const list = res.list || []
    const target =
      list.find((i) => (i.title || '').includes('病假')) ||
      list.find((i) => i.status === 'RUNNING') ||
      list[0]
    if (target) {
      await shoot(page, '10-instance-detail', `/instances/${target.id}`, { fullPage: true })
    }
  } catch (e) {
    console.log('skip 10:', e.message.split('\n')[0])
  }

  await shoot(page, '11-org', '/org')
  await shoot(page, '11b-org-depts', '/org', {
    before: async () => {
      await page.getByRole('tab', { name: '部门' }).click()
      await sleep(700)
    },
  })
  await shoot(page, '11c-org-roles', '/org', {
    before: async () => {
      await page.getByRole('tab', { name: '角色' }).click()
      await sleep(700)
    },
  })

  await shoot(page, '12-tenant', '/tenant', { fullPage: true })
  await shoot(page, '13-audit', '/audit', { fullPage: true })

  // ---- 平台管理员视角 ----
  const ctx2 = await browser.newContext({
    viewport: { width: 1600, height: 900 },
    deviceScaleFactor: 2,
  })
  const page2 = await ctx2.newPage()
  await injectAuth(page2, 'platform', 'platadmin')
  await shoot(page2, '14-platform', '/platform', { fullPage: true })

  await browser.close()
  console.log('全部截图完成 →', OUT)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
