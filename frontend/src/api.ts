import type { Account, Entry, Snapshot, Ticket } from "./types";

export async function read<T>(path: string, signal?: AbortSignal): Promise<T> {
  const response = await fetch(`/api${path}`, {
    signal: signal
      ? AbortSignal.any([signal, AbortSignal.timeout(8000)])
      : AbortSignal.timeout(8000),
  });
  if (!response.ok)
    throw new Error(
      "The branch could not be refreshed. Check the connection and try again.",
    );
  return response.json() as Promise<T>;
}
export async function snapshot(signal: AbortSignal): Promise<Snapshot> {
  const [accounts, queue, branch] = await Promise.all([
    read<Account[]>("/accounts", signal),
    read<Ticket[]>("/queue", signal),
    read<{ open: boolean }>("/branch", signal),
  ]);
  const histories = await Promise.all(
    accounts.map((a) =>
      read<Entry[]>(
        `/accounts/${encodeURIComponent(a.id)}/transactions`,
        signal,
      ),
    ),
  );
  return {
    accounts,
    queue,
    open: branch.open,
    entries: histories.flat().sort((a, b) => b.id - a.id),
  };
}
export interface Pending {
  key: string;
  path: string;
  body?: unknown;
  label: string;
}
export interface CommandState {
  storageBlocked: boolean;
  pending: Pending | null;
  busy: boolean;
  notice: { kind: "success" | "error"; message: string } | null;
}
export const PENDING_KEY = "banker.pending.v1";
type Store = Pick<Storage, "getItem" | "setItem" | "removeItem">;
const commandPath =
  /^\/(queue\/(check-in|claim-next)|tickets\/\d+\/(deposits|withdrawals|complete|close-account)|branch\/(open|close))$/;

export class Commands {
  private state: CommandState = {
    storageBlocked: false,
    pending: null,
    busy: false,
    notice: null,
  };
  private listeners = new Set<() => void>();
  constructor(
    private storage: Store,
    private send: typeof fetch = globalThis.fetch.bind(globalThis),
  ) {
    try {
      const raw = storage.getItem(PENDING_KEY);
      if (raw) {
        const pending = JSON.parse(raw) as Pending;
        if (
          !commandPath.test(pending.path) ||
          !/^[\da-f]{8}(-[\da-f]{4}){3}-[\da-f]{12}$/i.test(pending.key) ||
          typeof pending.label !== "string"
        )
          throw new Error();
        this.state = { ...this.state, pending };
      }
    } catch {
      this.state = {
        ...this.state,
        storageBlocked: true,
        notice: {
          kind: "error",
          message:
            "Saved action could not be read. Keep this tab open and check browser session storage before continuing.",
        },
      };
      this.storageFailed = true;
    }
  }
  private storageFailed = false;
  getSnapshot = () => this.state;
  subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };
  private update(next: Partial<CommandState>) {
    this.state = { ...this.state, ...next };
    this.listeners.forEach((fn) => fn());
  }
  async start(path: string, body: unknown, label: string): Promise<boolean> {
    if (this.state.pending || this.state.busy || this.storageFailed)
      return false;
    if (!commandPath.test(path)) throw new Error("Invalid command path");
    const pending: Pending = { key: crypto.randomUUID(), path, body, label };
    try {
      this.storage.setItem(PENDING_KEY, JSON.stringify(pending));
    } catch {
      this.update({
        notice: {
          kind: "error",
          message:
            "Enable browser session storage to safely submit actions. No request was sent.",
        },
      });
      return false;
    }
    this.update({ pending });
    return this.execute();
  }
  async retry(): Promise<boolean> {
    return this.state.busy ? false : this.execute();
  }
  private async execute(): Promise<boolean> {
    const pending = this.state.pending;
    if (!pending) return false;
    this.update({ busy: true, notice: null });
    try {
      const response = await this.send(`/api${pending.path}`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Idempotency-Key": pending.key,
        },
        body:
          pending.body === undefined ? undefined : JSON.stringify(pending.body),
        signal: AbortSignal.timeout(20000),
      });
      if (!response.ok) {
        if (
          response.status >= 500 ||
          response.status === 408 ||
          response.status === 429
        )
          throw new Error("uncertain");
        const problem = (await response.json().catch(() => ({}))) as {
          detail?: string;
        };
        this.storage.removeItem(PENDING_KEY);
        this.update({
          pending: null,
          notice: {
            kind: "error",
            message:
              problem.detail ||
              "This action was declined. Refresh the branch and check the details.",
          },
        });
        return false;
      }
      this.storage.removeItem(PENDING_KEY);
      this.update({
        pending: null,
        notice: { kind: "success", message: `${pending.label} — confirmed.` },
      });
      return true;
    } catch {
      this.update({
        notice: {
          kind: "error",
          message:
            "The result is unconfirmed. Retry the saved action below; it will use the same key to prevent duplication.",
        },
      });
      return false;
    } finally {
      this.update({ busy: false });
    }
  }
}
