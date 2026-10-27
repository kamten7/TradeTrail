import { test, expect } from '@playwright/test'

const merchantUrl = process.env.MERCHANT_URL || 'http://127.0.0.1:15174'
const customerUrl = process.env.CUSTOMER_URL || 'http://127.0.0.1:15173'

test('merchant login, checkout, payment, shipment, receipt and return refund', async ({
  browser,
}) => {
  const merchantContext = await browser.newContext()
  const customerContext = await browser.newContext({
    viewport: { width: 390, height: 844 },
    isMobile: true,
    hasTouch: true,
  })
  const merchant = await merchantContext.newPage()
  const customer = await customerContext.newPage()
  const errors: string[] = []
  merchant.on('pageerror', (e) => errors.push(e.message))
  customer.on('pageerror', (e) => errors.push(e.message))
  await merchant.goto(merchantUrl)
  await expect(merchant.getByRole('heading', { name: '欢迎回到商迹' })).toBeVisible()
  await merchant.screenshot({ path: 'test-results/merchant-login.png', fullPage: true })
  await merchant.getByLabel('登录密码').fill('wrong')
  await merchant.getByRole('button', { name: '登录工作台' }).click()
  await expect(merchant.getByText('账号或密码不正确')).toBeVisible()
  await merchant.getByLabel('登录密码').fill('merchant123')
  await merchant.getByRole('button', { name: '登录工作台' }).click()
  await expect(merchant.getByRole('heading', { name: '商品与库存' })).toBeVisible()
  await merchant.getByRole('button', { name: '＋ 新建商品' }).click()
  const productName = '验收商品 ' + Date.now()
  await merchant.getByLabel('商品名称', { exact: true }).fill(productName)
  await merchant.getByLabel('商品描述').fill('手工制作 · 标准款')
  await merchant.getByLabel('初始库存').fill('8')
  await merchant.getByRole('button', { name: '保存商品' }).click()
  const row = merchant.getByRole('row').filter({ hasText: productName })
  await expect(row).toBeVisible()
  await row.getByRole('button', { name: '上架', exact: true }).click()
  await merchant.getByRole('button', { name: '确认', exact: true }).click()
  await expect(row.getByText('已上架')).toBeVisible()
  await expect(merchant.locator('.el-overlay:visible')).toHaveCount(0)
  await merchant.screenshot({
    path: 'test-results/merchant-products.png',
    fullPage: true,
    animations: 'disabled',
  })

  await customer.goto(customerUrl)
  await expect(customer.getByText('认真做好物')).toBeVisible()
  await customer.getByRole('searchbox').fill(productName)
  await customer.getByRole('button', { name: '加购' }).click()
  await customer.getByPlaceholder('请输入密码').fill('customer123')
  await customer.getByRole('button', { name: '登录商城' }).click()
  await expect(customer.getByRole('heading', { name: '我的购物车' })).toBeVisible()
  await customer.goto(customerUrl + '/products')
  await customer.getByRole('searchbox').fill(productName)
  await customer.getByRole('button', { name: '加购' }).click()
  await expect(customer.getByText('已加入购物车')).toBeVisible()
  await customer.goto(customerUrl + '/cart')
  await expect(customer.getByText(productName).first()).toBeVisible()
  await customer
    .getByPlaceholder('请填写收货人、联系电话和详细地址')
    .fill('测试收件人，测试电话，香港测试地址')
  const checkoutKeys: string[] = []
  await customer.route('**/api/cart/checkout', async (route) => {
    checkoutKeys.push(route.request().headers()['idempotency-key'])
    if (checkoutKeys.length === 1) {
      await route.fetch()
      await route.abort('connectionreset')
    } else await route.continue()
  })
  await customer.getByRole('button', { name: '确认下单' }).click()
  await expect(customer.getByRole('alert')).toContainText('暂时无法连接商城服务')
  await customer.getByRole('button', { name: '确认下单' }).click()
  await expect(customer.getByRole('heading', { name: '我的订单' })).toBeVisible()
  expect(checkoutKeys).toHaveLength(2)
  expect(checkoutKeys[0]).toBeTruthy()
  expect(checkoutKeys[1]).toBe(checkoutKeys[0])
  const order = customer.locator('.order-card').filter({ hasText: productName })
  await expect(order).toHaveCount(1)
  await order.getByRole('button', { name: '立即付款' }).click()
  await customer.getByRole('button', { name: '确认付款' }).click()
  await expect(order.getByText('待发货', { exact: true })).toBeVisible({ timeout: 30000 })
  await customer.screenshot({ path: 'test-results/customer-orders-mobile.png', fullPage: true })

  await merchant.getByRole('link', { name: '订单履约', exact: true }).click()
  const orderRow = merchant.getByRole('row').filter({ hasText: productName })
  await orderRow.getByRole('button', { name: '发货', exact: true }).click()
  await merchant.getByRole('button', { name: '确认发货' }).click()
  await expect(orderRow.getByText('已发货', { exact: true })).toBeVisible()
  await customer.getByRole('button', { name: '刷新', exact: true }).click()
  await order.getByRole('button', { name: '确认收货' }).click()
  await customer.getByRole('button', { name: '确认', exact: true }).click()
  await expect(order.getByText('已完成', { exact: true })).toBeVisible()
  await order.getByRole('button', { name: '申请售后' }).click()
  const reason = '验收退货 ' + Date.now()
  await customer.getByPlaceholder('请描述需要售后的原因').fill(reason)
  await customer.getByRole('button', { name: '提交', exact: true }).click()
  await expect(order.getByText('待审核', { exact: true })).toBeVisible()
  await merchant.getByRole('link', { name: '售后管理', exact: true }).click()
  const caseRow = merchant.getByRole('row').filter({ hasText: reason })
  await caseRow.getByRole('button', { name: '审核申请' }).click()
  await merchant.getByPlaceholder('请填写清晰的处理理由').fill('同意退货，收到后退款')
  await merchant.getByRole('button', { name: '确认审核' }).click()
  await expect(caseRow.getByText('等待客户寄回')).toBeVisible()
  await customer.getByRole('button', { name: '刷新', exact: true }).click()
  await order.getByRole('button', { name: '填写退货运单' }).click()
  await customer.getByPlaceholder('请输入寄回包裹的运单号').fill('RETURN-' + Date.now())
  await customer.getByRole('button', { name: '提交', exact: true }).click()
  await merchant.getByRole('button', { name: '刷新进度' }).click()
  await caseRow.getByRole('button', { name: '质检合格' }).click()
  await merchant.getByRole('button', { name: '确认验收' }).click()
  const orderNumber = await order.locator('.order-header strong').innerText()
  await merchant.getByRole('link', { name: '异常恢复', exact: true }).click()
  const failedRefund = merchant.getByRole('row').filter({ hasText: orderNumber })
  await expect
    .poll(async () => {
      await merchant.getByRole('button', { name: '刷新状态' }).click()
      await expect(merchant.locator('.el-loading-mask:visible')).toHaveCount(0)
      return failedRefund.count()
    })
    .toBe(1)
  await expect(failedRefund.getByText('模拟渠道暂时拒绝退款，请重试')).toBeVisible()
  await failedRefund.getByRole('button', { name: '重试退款' }).click()
  await expect(failedRefund).toHaveCount(0)
  await expect
    .poll(
      async () => {
        await customer.getByRole('button', { name: '刷新', exact: true }).click()
        await expect(order).toBeVisible()
        return order.getByText('退款完成', { exact: true }).count()
      },
      { timeout: 30000 },
    )
    .toBe(1)
  await expect(order.getByText('已完成', { exact: true })).toBeVisible()
  await merchant.getByRole('link', { name: '售后管理', exact: true }).click()
  await expect(caseRow.getByText('退款完成', { exact: true })).toBeVisible()
  await merchant.screenshot({ path: 'test-results/merchant-after-sales.png', fullPage: true })
  await customer.screenshot({ path: 'test-results/customer-refund-mobile.png', fullPage: true })
  expect(errors).toEqual([])
  const overflow = await customer.evaluate(
    () => document.documentElement.scrollWidth > window.innerWidth,
  )
  expect(overflow).toBe(false)
  await merchantContext.close()
  await customerContext.close()
})

test('network failure is recoverable and does not expose Failed to fetch', async ({ page }) => {
  await page.route('**/session', (route) => route.abort('connectionrefused'))
  await page.goto(merchantUrl)
  await expect(page.getByText('暂时无法连接商城服务，请确认服务已启动后重试。')).toBeVisible()
  await expect(page.getByRole('button', { name: '重新检查连接' })).toBeVisible()
  await expect(page.getByText('Failed to fetch')).toHaveCount(0)
  await page.unroute('**/session')
  await page.getByRole('button', { name: '重新检查连接' }).click()
  await expect(page.getByText('暂时无法连接商城服务，请确认服务已启动后重试。')).toHaveCount(0)
})

test('customer login page fits a narrow viewport', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 })
  await page.goto(customerUrl + '/login')
  await expect(page.getByRole('heading', { name: '欢迎来到商迹' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false)
  await page.screenshot({ path: 'test-results/customer-login-mobile.png', fullPage: true })
})
