import { test, expect, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
async function checkIn(
  page: Page,
  first: string,
  last: string,
  id = "DEMO-1001",
) {
  await page
    .getByRole("button", { name: "Client check-in", exact: true })
    .click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Account", { exact: true }).selectOption(id);
  await dialog.getByLabel("First name").fill(first);
  await dialog.getByLabel("Last name").fill(last);
  await dialog.getByRole("button", { name: "Add to queue" }).click();
  await expect(dialog).not.toBeVisible();
}
async function serve(page: Page) {
  await page.getByRole("button", { name: "Serve next", exact: true }).click();
  await expect(page.getByTestId("active-balance")).toBeVisible();
}
test.describe.configure({ mode: "serial" });
test("full workflow: verify identity, FIFO, exact money, overdraft, history, and session completion", async ({
  page,
}) => {
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "Your branch, at a glance." }),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "Client check-in", exact: true })
    .click();
  await page.getByLabel("First name").fill("Wrong");
  await page.getByLabel("Last name").fill("River");
  await page.getByRole("button", { name: "Add to queue" }).click();
  await expect(page.getByRole("dialog").getByRole("alert")).toContainText(
    "Name does not match",
  );
  await page.getByRole("button", { name: "Cancel", exact: true }).click();
  await checkIn(page, "Alex", "River");
  await checkIn(page, "Jordan", "Vale", "DEMO-1002");
  await serve(page);
  await expect(page.getByTestId("active-balance")).toHaveText("$1,000.00");
  await page.getByLabel("Amount", { exact: true }).fill("0.001");
  await page.getByRole("button", { name: "Confirm deposit" }).click();
  await expect(page.getByRole("alert")).toContainText(
    "no more than two decimal places",
  );
  await page.getByLabel("Amount", { exact: true }).fill("0.10");
  await page.getByRole("button", { name: "Confirm deposit" }).click();
  await expect(page.getByTestId("active-balance")).toHaveText("$1,000.10");
  const desktopAccessibility = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
    .analyze();
  expect(desktopAccessibility.violations).toEqual([]);
  await page.screenshot({
    path: "test-results/banker-desktop.png",
    fullPage: true,
  });
  await page.getByRole("button", { name: "Withdraw", exact: true }).click();
  await page.getByLabel("Amount", { exact: true }).fill("1001");
  await page.getByRole("button", { name: "Confirm withdrawal" }).click();
  await expect(page.getByRole("alert")).toContainText("Withdrawal exceeds");
  await expect(page.getByTestId("active-balance")).toHaveText("$1,000.10");
  await page.getByLabel("Amount", { exact: true }).fill("10.10");
  await page.getByRole("button", { name: "Confirm withdrawal" }).click();
  await expect(page.getByTestId("active-balance")).toHaveText("$990.00");
  await expect(
    page.getByRole("button", { name: "Close branch", exact: true }),
  ).toBeDisabled();
  await page
    .getByRole("button", { name: "Complete session", exact: true })
    .click();
  await expect(page.getByTestId("active-balance")).not.toBeVisible();
  await serve(page);
  await expect(page.getByTestId("active-balance")).toHaveText("$250.00");
  await page
    .getByRole("button", { name: "Complete session", exact: true })
    .click();
  await page.getByRole("button", { name: "Activity", exact: true }).click();
  await expect(
    page.getByRole("cell", { name: "Deposit", exact: false }),
  ).toBeVisible();
  await expect(
    page.getByRole("cell", { name: "Withdrawal", exact: false }),
  ).toBeVisible();
  await page.getByLabel("Search activity").fill("Jordan");
  await expect(
    page.getByRole("heading", { name: "No transactions yet" }),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "Client directory", exact: true })
    .click();
  await page.getByLabel("Search clients").fill("Alex");
  await page
    .getByRole("button", { name: "View account for Alex River" })
    .click();
  await expect(page.getByRole("dialog")).toContainText("$990.00");
  await page.getByRole("button", { name: "Close dialog" }).click();
});
test("lost response after real commit survives reload and retry without another deposit", async ({
  page,
  request,
}) => {
  await page.goto("/");
  await checkIn(page, "Alex", "River");
  await serve(page);
  let intercepted = false;
  const keys: string[] = [];
  await page.route("**/api/tickets/*/deposits", async (route) => {
    keys.push(route.request().headers()["idempotency-key"]);
    if (!intercepted) {
      intercepted = true;
      await route.fetch();
      await route.abort("failed");
    } else await route.continue();
  });
  await page.getByLabel("Amount", { exact: true }).fill("25");
  await page.getByRole("button", { name: "Confirm deposit" }).click();
  await expect(
    page.getByRole("button", { name: "Retry saved action" }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Complete session", exact: true }),
  ).toBeDisabled();
  await page.reload();
  await expect(
    page.getByRole("region", { name: "Saved pending action" }),
  ).toContainText("25.00");
  await page.getByRole("button", { name: "Retry saved action" }).click();
  await expect(
    page.getByRole("region", { name: "Saved pending action" }),
  ).not.toBeVisible();
  await expect(page.getByTestId("active-balance")).toHaveText("$1,015.00");
  expect(keys).toHaveLength(2);
  expect(keys[0]).toBe(keys[1]);
  const history = await (
    await request.get("/api/accounts/DEMO-1001/transactions")
  ).json();
  expect(
    history.filter(
      (entry: { amount: number; kind: string }) =>
        entry.kind === "DEPOSIT" && entry.amount === 25,
    ),
  ).toHaveLength(1);
  await page
    .getByRole("button", { name: "Complete session", exact: true })
    .click();
});
test("zero-balance closure is confirmed and the branch can close and reopen", async ({
  page,
}) => {
  await page.goto("/");
  await checkIn(page, "Casey", "Brook", "DEMO-1003");
  await serve(page);
  await page
    .getByRole("button", { name: "Close account", exact: true })
    .click();
  await page.getByRole("button", { name: "Keep account" }).click();
  await expect(page.getByTestId("active-balance")).toHaveText("$0.00");
  await page
    .getByRole("button", { name: "Close account", exact: true })
    .click();
  await page.getByRole("button", { name: "Confirm account closure" }).click();
  await expect(page.getByTestId("active-balance")).not.toBeVisible();
  await page.getByRole("button", { name: "Close branch", exact: true }).click();
  await page.getByRole("button", { name: "Confirm branch closure" }).click();
  await expect(
    page.getByRole("button", { name: "Client check-in", exact: true }),
  ).toBeDisabled();
  await page.getByRole("button", { name: "Open branch", exact: true }).click();
  await page.getByRole("button", { name: "Confirm branch opening" }).click();
  await expect(
    page.getByRole("button", { name: "Client check-in", exact: true }),
  ).toBeEnabled();
});
test("responsive workspace, accessible dialog focus, and offline read recovery", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "Your branch, at a glance." }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  const accessibility = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
    .analyze();
  expect(accessibility.violations).toEqual([]);
  await page
    .getByRole("button", { name: "Client check-in", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "Close dialog" }),
  ).toBeFocused();
  const dialogAccessibility = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
    .analyze();
  expect(dialogAccessibility.violations).toEqual([]);
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await page.route("**/api/**", (route) => route.abort());
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Unable to refresh");
  await expect(
    page.getByRole("button", { name: "Client check-in", exact: true }),
  ).toBeDisabled();
  await page.unroute("**/api/**");
  await page.getByRole("button", { name: "Reconnect" }).click();
  await expect(
    page.getByRole("button", { name: "Client check-in", exact: true }),
  ).toBeEnabled();
  await page.screenshot({
    path: "test-results/banker-mobile.png",
    fullPage: true,
  });
});
