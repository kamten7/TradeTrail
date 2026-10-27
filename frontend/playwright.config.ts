import { defineConfig } from '@playwright/test'

export default defineConfig({
  webServer:
    process.env.MERCHANT_URL || process.env.CUSTOMER_URL
      ? undefined
      : [
          {
            command:
              'java -jar ../tradetrail-boot/target/tradetrail-boot-0.1.0-SNAPSHOT.jar --tradetrail.payment.callback-url=http://127.0.0.1:18080 --tradetrail.mock-payments.refund-failures-before-success=1',
            url: 'http://127.0.0.1:18080/session',
            timeout: 60000,
            reuseExistingServer: false,
            env: {
              SERVER_PORT: '18080',
              SPRING_PROFILES_ACTIVE: '',
              SPRING_DATASOURCE_URL:
                'jdbc:h2:mem:browser-acceptance;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1',
              SPRING_DATASOURCE_USERNAME: 'sa',
              SPRING_DATASOURCE_PASSWORD: '',
            },
          },
          {
            command: 'npm --prefix merchant run dev -- --port 15174',
            url: 'http://127.0.0.1:15174',
            reuseExistingServer: false,
            env: { TRADETRAIL_API_TARGET: 'http://127.0.0.1:18080' },
          },
          {
            command: 'npm --prefix customer run dev -- --port 15173',
            url: 'http://127.0.0.1:15173',
            reuseExistingServer: false,
            env: { TRADETRAIL_API_TARGET: 'http://127.0.0.1:18080' },
          },
        ],
  testDir: './tests',
  timeout: 90000,
  expect: { timeout: 15000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: {
    browserName: 'chromium',
    headless: true,
    launchOptions: { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE },
    viewport: { width: 1440, height: 1000 },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  outputDir: 'test-results',
})
