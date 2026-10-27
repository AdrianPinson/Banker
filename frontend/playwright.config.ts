import { defineConfig, devices } from "@playwright/test";
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: "http://127.0.0.1:8091",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: {
        ...devices["Desktop Chrome"],
        viewport: { width: 1440, height: 1100 },
      },
    },
  ],
  webServer: {
    command:
      'java -jar ../target/banker-1.0.0.jar --server.port=8091 "--spring.datasource.url=jdbc:h2:mem:browser;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE" --spring.datasource.username=sa --spring.datasource.password=',
    url: "http://127.0.0.1:8091/api/branch",
    reuseExistingServer: false,
    timeout: 60000,
  },
});
