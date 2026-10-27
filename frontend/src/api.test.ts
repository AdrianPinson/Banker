import { describe, expect, it, vi } from "vitest";
import { Commands, PENDING_KEY } from "./api";
import { moneyInput } from "./types";
class MemoryStore {
  values = new Map<string, string>();
  getItem(key: string) {
    return this.values.get(key) ?? null;
  }
  setItem(key: string, value: string) {
    this.values.set(key, value);
  }
  removeItem(key: string) {
    this.values.delete(key);
  }
}
const send = (fn: (...args: Parameters<typeof fetch>) => Promise<Response>) =>
  vi.fn(fn) as typeof fetch;
describe("safe command recovery", () => {
  it("persists the payload before sending and clears it on confirmation", async () => {
    const store = new MemoryStore();
    const transport = send(async (_url, options) => {
      const saved = JSON.parse(store.getItem(PENDING_KEY)!);
      expect(saved.key).toEqual(
        (options!.headers as Record<string, string>)["Idempotency-Key"],
      );
      expect(saved.body).toEqual({ amount: "25.00" });
      return new Response("{}");
    });
    const commands = new Commands(store, transport);
    expect(
      await commands.start(
        "/tickets/1/deposits",
        { amount: "25.00" },
        "Deposit $25",
      ),
    ).toBe(true);
    expect(store.getItem(PENDING_KEY)).toBeNull();
    expect(commands.getSnapshot().notice?.kind).toBe("success");
  });
  it("reuses exactly the same key and payload after a lost response and reload", async () => {
    const store = new MemoryStore();
    let first = true,
      balance = 1000;
    const applied = new Set<string>();
    const keys: string[] = [];
    const transport = send(async (_url, options) => {
      const key = (options!.headers as Record<string, string>)[
        "Idempotency-Key"
      ];
      keys.push(key);
      expect(options!.body).toBe('{"amount":"25.00"}');
      if (!applied.has(key)) {
        balance += 25;
        applied.add(key);
      }
      if (first) {
        first = false;
        throw new TypeError("response lost after commit");
      }
      return new Response("{}");
    });
    const original = new Commands(store, transport);
    expect(
      await original.start(
        "/tickets/1/deposits",
        { amount: "25.00" },
        "Deposit $25",
      ),
    ).toBe(false);
    expect(
      await original.start(
        "/tickets/1/withdrawals",
        { amount: "5.00" },
        "Withdraw",
      ),
    ).toBe(false);
    const restored = new Commands(store, transport);
    expect(restored.getSnapshot().pending?.label).toBe("Deposit $25");
    expect(await restored.retry()).toBe(true);
    expect(keys[0]).toBe(keys[1]);
    expect(balance).toBe(1025);
  });
  it("blocks rapid duplicate submissions while a request is pending", async () => {
    let finish!: (response: Response) => void;
    const transport = send(
      () =>
        new Promise((resolve) => {
          finish = resolve;
        }),
    );
    const commands = new Commands(new MemoryStore(), transport);
    const first = commands.start(
      "/queue/claim-next",
      { teller: "one" },
      "Serve next",
    );
    expect(
      await commands.start(
        "/queue/claim-next",
        { teller: "one" },
        "Serve next",
      ),
    ).toBe(false);
    expect(await commands.retry()).toBe(false);
    finish(new Response("{}"));
    expect(await first).toBe(true);
    expect(transport).toHaveBeenCalledTimes(1);
  });
  it("retains receipts on server errors because commit outcome may be uncertain", async () => {
    const commands = new Commands(
      new MemoryStore(),
      send(async () => new Response("{}", { status: 503 })),
    );
    expect(
      await commands.start("/branch/close", undefined, "Branch closed"),
    ).toBe(false);
    expect(commands.getSnapshot().pending).not.toBeNull();
  });
  it("clears a definite business rejection and displays its detail", async () => {
    const store = new MemoryStore();
    const commands = new Commands(
      store,
      send(
        async () =>
          new Response('{"detail":"Insufficient funds."}', { status: 409 }),
      ),
    );
    expect(
      await commands.start(
        "/tickets/1/withdrawals",
        { amount: "9000.00" },
        "Withdraw",
      ),
    ).toBe(false);
    expect(commands.getSnapshot().pending).toBeNull();
    expect(commands.getSnapshot().notice?.message).toBe("Insufficient funds.");
    expect(store.getItem(PENDING_KEY)).toBeNull();
  });
  it("never sends a mutation if saving the recovery key fails", async () => {
    const store = new MemoryStore();
    store.setItem = () => {
      throw new Error("Storage disabled");
    };
    const transport = send(async () => new Response("{}"));
    const commands = new Commands(store, transport);
    expect(await commands.start("/branch/open", undefined, "Branch open")).toBe(
      false,
    );
    expect(transport).not.toHaveBeenCalled();
  });
  it("does not overwrite unreadable recovery data", async () => {
    const store = new MemoryStore();
    store.setItem(PENDING_KEY, "{broken");
    const transport = send(async () => new Response("{}"));
    const commands = new Commands(store, transport);
    expect(await commands.start("/branch/open", undefined, "Branch open")).toBe(
      false,
    );
    expect(transport).not.toHaveBeenCalled();
    expect(store.getItem(PENDING_KEY)).toBe("{broken");
  });
});
describe("decimal input", () => {
  it.each(["0", "-5", "1.001", "1e2", "NaN", "10000000000000", "1,000", ""])(
    "rejects %s without rounding it",
    (value) => {
      expect(moneyInput(value)).toBeNull();
    },
  );
  it.each([
    ["1", "1.00"],
    ["1.2", "1.20"],
    ["0.01", "0.01"],
    ["0001.20", "1.20"],
    [" 25.00 ", "25.00"],
    ["9999999999999.99", "9999999999999.99"],
  ])("normalizes %s as %s", (value, expected) => {
    expect(moneyInput(value)).toBe(expected);
  });
});
